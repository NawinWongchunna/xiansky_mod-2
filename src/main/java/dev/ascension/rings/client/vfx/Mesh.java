package dev.ascension.rings.client.vfx;

import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * ตาข่าย 3 มิติที่ "บันทึก" ไว้ในหน่วยความจำ (ยังไม่แตะ OpenGL) — ใช้ Vec3d อย่างเดียว
 * 1 quad = 4 จุดยอดติดกัน; สามเหลี่ยมจะซ้ำจุดสุดท้ายเป็น quad เสื่อม
 * จุดยอดละ 12 ค่า: x y z | nx ny nz | r g b | emissive | alpha | twoSided
 */
final class Mesh {
    static final int STRIDE = 12;
    static final Vec3d UP = new Vec3d(0, 1, 0);

    double[] d = new double[STRIDE * 4096];
    int count = 0;

    /** ค่าปัจจุบันที่ใช้กับจุดยอดถัดไป */
    float alpha = 1f;
    boolean twoSided = false;

    int vertexCount() {
        return count;
    }

    void clear() {
        count = 0;
    }

    void v(Vec3d p, Vec3d n, float[] col, float emis) {
        if ((count + 1) * STRIDE > d.length) d = Arrays.copyOf(d, d.length * 2);
        int o = count * STRIDE;
        d[o] = p.x;
        d[o + 1] = p.y;
        d[o + 2] = p.z;
        d[o + 3] = n.x;
        d[o + 4] = n.y;
        d[o + 5] = n.z;
        d[o + 6] = col[0];
        d[o + 7] = col[1];
        d[o + 8] = col[2];
        d[o + 9] = emis;
        d[o + 10] = alpha;
        d[o + 11] = twoSided ? 1 : 0;
        count++;
    }

    // ------------------------------------------------------------------ พื้นฐาน

    static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return a.add(b.subtract(a).multiply(k));
    }

    static float[] mix(float[] a, float[] b, double k) {
        float kk = (float) Math.max(0, Math.min(1, k));
        return new float[]{a[0] + (b[0] - a[0]) * kk, a[1] + (b[1] - a[1]) * kk, a[2] + (b[2] - a[2]) * kk};
    }

    static float[] scale(float[] a, double k) {
        return new float[]{(float) (a[0] * k), (float) (a[1] * k), (float) (a[2] * k)};
    }

    /** quad เรียบ: normal ได้จาก cross แล้วพลิกให้ชี้ออกจาก center (ถ้า center != null) */
    void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d4, Vec3d center, float[] col, float emis) {
        Vec3d n = b.subtract(a).crossProduct(d4.subtract(a));
        if (n.lengthSquared() < 1.0e-12) {
            n = c.subtract(a).crossProduct(d4.subtract(b));
            if (n.lengthSquared() < 1.0e-12) return;
        }
        n = n.normalize();
        if (center != null && n.dotProduct(a.subtract(center)) < 0) n = n.multiply(-1);
        v(a, n, col, emis);
        v(b, n, col, emis);
        v(c, n, col, emis);
        v(d4, n, col, emis);
    }

    void tri(Vec3d a, Vec3d b, Vec3d c, Vec3d center, float[] col, float emis) {
        Vec3d n = b.subtract(a).crossProduct(c.subtract(a));
        if (n.lengthSquared() < 1.0e-12) return;
        n = n.normalize();
        if (center != null && n.dotProduct(a.subtract(center)) < 0) n = n.multiply(-1);
        v(a, n, col, emis);
        v(b, n, col, emis);
        v(c, n, col, emis);
        v(c, n, col, emis);
    }

    /** quad ที่กำหนด normal และสีรายจุด (ผิวโค้งเรียบ) */
    void smoothQuad(Vec3d[] p, Vec3d[] n, float[][] col, float emis) {
        for (int i = 0; i < 4; i++) v(p[i], n[i], col[i], emis);
    }

    /** แท่ง 4 เหลี่ยมระหว่างจุด A-B (เขา เล็บ) */
    void limb(Vec3d a, Vec3d b, double r0, double r1, float[] col, float emis) {
        Vec3d w = b.subtract(a);
        if (w.lengthSquared() < 1.0e-10) return;
        w = w.normalize();
        Vec3d u1 = w.crossProduct(UP);
        if (u1.lengthSquared() < 1.0e-4) u1 = w.crossProduct(new Vec3d(1, 0, 0));
        u1 = u1.normalize();
        Vec3d u2 = w.crossProduct(u1).normalize();
        Vec3d[] da = {u1.add(u2), u1.subtract(u2), u1.multiply(-1).subtract(u2), u2.subtract(u1)};
        for (int i = 0; i < 4; i++) {
            Vec3d d0 = da[i].normalize(), d1 = da[(i + 1) % 4].normalize();
            v(a.add(d0.multiply(r0)), d0, col, emis);
            v(a.add(d1.multiply(r0)), d1, col, emis);
            v(b.add(d1.multiply(r1)), d1, col, emis);
            v(b.add(d0.multiply(r1)), d0, col, emis);
        }
    }

    void chain(Vec3d[] pts, double r0, double r1, float[] col, float emis) {
        int n = pts.length - 1;
        for (int i = 0; i < n; i++) {
            double a = r0 + (r1 - r0) * i / n, b = r0 + (r1 - r0) * (i + 1) / n;
            limb(pts[i], pts[i + 1], a, b, col, emis);
        }
    }

    /** ริบบิ้นกว้าง w ตามทิศ wide (สองด้าน) */
    void ribbon(Vec3d[] pts, Vec3d wide, double w0, double w1, float[] col, float emis) {
        boolean old = twoSided;
        twoSided = true;
        int n = pts.length - 1;
        for (int i = 0; i < n; i++) {
            double a = w0 + (w1 - w0) * i / n, b = w0 + (w1 - w0) * (i + 1) / n;
            Vec3d na = pts[i].add(wide.multiply(a)), nb = pts[i].subtract(wide.multiply(a));
            Vec3d ma = pts[i + 1].add(wide.multiply(b)), mb = pts[i + 1].subtract(wide.multiply(b));
            Vec3d nrm = pts[i + 1].subtract(pts[i]).crossProduct(wide);
            if (nrm.lengthSquared() < 1.0e-12) continue;
            nrm = nrm.normalize();
            v(na, nrm, col, emis);
            v(nb, nrm, col, emis);
            v(mb, nrm, col, emis);
            v(ma, nrm, col, emis);
        }
        twoSided = old;
    }

    /** ทรงกลม (UV) รัศมี r ที่ c */
    void sphere(Vec3d c, double r, int seg, float[] col, float emis) {
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * i / seg - Math.PI / 2, a1 = Math.PI * (i + 1) / seg - Math.PI / 2;
            for (int j = 0; j < seg * 2; j++) {
                double b0 = Math.PI * j / seg, b1 = Math.PI * (j + 1) / seg;
                Vec3d n00 = dir(a0, b0), n01 = dir(a0, b1), n11 = dir(a1, b1), n10 = dir(a1, b0);
                v(c.add(n00.multiply(r)), n00, col, emis);
                v(c.add(n01.multiply(r)), n01, col, emis);
                v(c.add(n11.multiply(r)), n11, col, emis);
                v(c.add(n10.multiply(r)), n10, col, emis);
            }
        }
    }

    private static Vec3d dir(double lat, double lon) {
        return new Vec3d(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
    }

    /** เพชร 8 หน้า (octahedron) — หยดน้ำ/รูน */
    void gem(Vec3d c, Vec3d ax, Vec3d ay, Vec3d az, float[] col, float emis) {
        Vec3d px = c.add(ax), nx = c.subtract(ax), py = c.add(ay), ny = c.subtract(ay), pz = c.add(az), nz = c.subtract(az);
        tri(py, px, pz, c, col, emis);
        tri(py, pz, nx, c, col, emis);
        tri(py, nx, nz, c, col, emis);
        tri(py, nz, px, c, col, emis);
        tri(ny, pz, px, c, col, emis);
        tri(ny, nx, pz, c, col, emis);
        tri(ny, nz, nx, c, col, emis);
        tri(ny, px, nz, c, col, emis);
    }

    /** จานกลม (พัดสามเหลี่ยม) ที่ c วางบนระนาบแกน ex/ey รัศมี r ด้านเดียวปกติคือ ex×ey */
    void disc(Vec3d c, Vec3d ex, Vec3d ey, double r, double a0, double a1, int seg, float[] col, float emis) {
        for (int i = 0; i < seg; i++) {
            double p0 = a0 + (a1 - a0) * i / seg, p1 = a0 + (a1 - a0) * (i + 1) / seg;
            Vec3d q0 = c.add(ex.multiply(Math.cos(p0) * r)).add(ey.multiply(Math.sin(p0) * r));
            Vec3d q1 = c.add(ex.multiply(Math.cos(p1) * r)).add(ey.multiply(Math.sin(p1) * r));
            Vec3d n = ex.crossProduct(ey).normalize();
            v(c, n, col, emis);
            v(q0, n, col, emis);
            v(q1, n, col, emis);
            v(q1, n, col, emis);
        }
    }

    /** วงแหวนแบน r0..r1 */
    void annulus(Vec3d c, Vec3d ex, Vec3d ey, double r0, double r1, double a0, double a1, int seg, float[] col, float emis) {
        Vec3d n = ex.crossProduct(ey).normalize();
        for (int i = 0; i < seg; i++) {
            double p0 = a0 + (a1 - a0) * i / seg, p1 = a0 + (a1 - a0) * (i + 1) / seg;
            double c0 = Math.cos(p0), s0 = Math.sin(p0), c1 = Math.cos(p1), s1 = Math.sin(p1);
            v(c.add(ex.multiply(c0 * r0)).add(ey.multiply(s0 * r0)), n, col, emis);
            v(c.add(ex.multiply(c0 * r1)).add(ey.multiply(s0 * r1)), n, col, emis);
            v(c.add(ex.multiply(c1 * r1)).add(ey.multiply(s1 * r1)), n, col, emis);
            v(c.add(ex.multiply(c1 * r0)).add(ey.multiply(s1 * r0)), n, col, emis);
        }
    }

    /** กล่อง (แกนสามแกนครึ่งความยาว) */
    void box(Vec3d c, Vec3d ax, Vec3d ay, Vec3d az, float[] col, float emis) {
        Vec3d[] s = {ax, ay, az};
        for (int k = 0; k < 3; k++) {
            Vec3d a = s[k], b = s[(k + 1) % 3], e = s[(k + 2) % 3];
            for (int sg = -1; sg <= 1; sg += 2) {
                Vec3d o = c.add(a.multiply(sg));
                quad(o.subtract(b).subtract(e), o.add(b).subtract(e), o.add(b).add(e), o.subtract(b).add(e), c, col, emis);
            }
        }
    }
}
