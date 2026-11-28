package dev.ascension.rings.client.vfx;

import net.minecraft.util.math.Vec3d;

/**
 * ชิ้นส่วน 3 มิติของสกิล "เก้าก้าวย่างอากาศ" (ธีมทอง-ขาว) — ไม่มี GL ใช้ Vec3d + Mesh อย่างเดียว:
 *  - แท่นก้าว (ring): จานโปร่งขาว + ปากั้ว + ขอบทองหนา + กลีบบัว + เพชรโคจร (ยิ่งก้าวสูงยิ่งหนาแน่น)
 *  - ร่างผู้ใช้/ภาพติดตา (figure): หุ่นโปร่งแสงสีทอง มีผ้าคลุมพริ้ว
 *  - ปีกแสง + รัศมีหลังศีรษะ (ก้าวที่ 9)
 *  - ลำแสงพุ่ง (dash): คลื่นกรวย + เส้นความเร็ว
 *  - รอยแหวกมิติ (rift): ขอบแหลมทอง + แสงขาวด้านใน
 */
final class NineStepModel {
    static final float[] GOLD = {1.00f, 0.82f, 0.36f};
    static final float[] WHITE = {1.00f, 0.99f, 0.94f};
    static final float[] PALE = {1.00f, 0.92f, 0.65f};
    static final float[] DEEP = {0.78f, 0.55f, 0.16f};

    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final Vec3d FX = new Vec3d(0, 0, 1);
    private static final Vec3d FY = new Vec3d(1, 0, 0); // FX × FY = +Y

    private NineStepModel() {}

    // ------------------------------------------------------------------ แท่นก้าว (หมอก + ระลอกน้ำ ไม่ใช่แผ่นแข็ง)

    /**
     * จุดเหยียบแบบ "เหยียบหมอก/ผิวน้ำ": จานหมอกไล่ระดับโปร่งแสง + เส้นหมอกก้นหอยลอยพลิ้ว
     * + ระลอกคลื่นแผ่ออกจากเท้า + อักขระบางๆ ลอยเหนือผิว + ละอองแสงลอยขึ้น — ไม่มีขอบหนา/กลีบ/เพชรใหญ่
     * ใช้วาดในชั้น additive (GLOW)
     * @param age tick นับจากตอนเหยียบ (ใช้ขยายระลอก)
     */
    static void mist(Mesh m, Vec3d c, double r, double rot, int step, double time, double age, float alpha) {
        float old = m.alpha;
        boolean ts = m.twoSided;
        m.twoSided = true;

        // 1) จานหมอกไล่ระดับ (กลางเข้ม ขอบจางหาย)
        int bands = 8;
        for (int b = 0; b < bands; b++) {
            double r0 = r * b / bands, r1 = r * (b + 1) / bands, k = (b + 0.5) / bands;
            m.alpha = alpha * (float) (0.34 * Math.pow(1.0 - k, 1.7) + 0.015);
            m.annulus(c.add(0, 0.04, 0), FX, FY, r0, r1, 0, Math.PI * 2, 40, k < 0.5 ? WHITE : PALE, 1f);
        }

        // 2) เส้นหมอกก้นหอย (ผ้าบางๆ ตั้งขึ้น) หมุนช้าๆ
        for (int w = 0; w < 8; w++) {
            Vec3d[] pts = new Vec3d[10];
            for (int i = 0; i < pts.length; i++) {
                double u = i / 9.0;
                double ang = rot * 0.7 + w * Math.PI * 2 / 8 + u * 2.4;
                double rad = r * (0.12 + 0.88 * u);
                double y = 0.06 + 0.35 * Math.sin(Math.PI * u) * (0.6 + 0.4 * Math.sin(time * 0.08 + w)) + 0.05 * u;
                pts[i] = c.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
            }
            m.alpha = alpha * 0.28f;
            m.ribbon(pts, UP, 0.45, 0.03, w % 2 == 0 ? WHITE : PALE, 1f);
        }

        // 3) ระลอกน้ำแผ่ออกจากจุดที่เหยียบ
        for (int j = 0; j < 3; j++) {
            double a = age - j * 6;
            if (a < 0) continue;
            double rr = 0.5 + a * 0.16;
            double fade = 1.0 - Math.min(1.0, a / 46.0);
            if (rr > r * 1.7 || fade <= 0) continue;
            m.alpha = alpha * (float) (0.75 * fade * fade);
            m.annulus(c.add(0, 0.07, 0), FX, FY, rr, rr + 0.09 + 0.004 * a, 0, Math.PI * 2, 56, WHITE, 1f);
        }

        // 4) อักขระบางๆ ลอยเหนือผิว (จางๆ ไม่ใช่แผ่นทึบ)
        m.alpha = alpha * 0.4f;
        SymbolModel.bagua(m, c.add(0, 0.09, 0), FX, FY, r * 0.8, rot, 0.45, step >= 8);

        // 5) ละอองแสงลอยขึ้น
        int n = 6 + step;
        for (int i = 0; i < n; i++) {
            double a = i * 2.399 + rot;
            double rad = r * (0.2 + 0.7 * hash(i, 9));
            double h = ((time * 0.02 + hash(i, 10)) % 1.0) * 2.2;
            m.alpha = alpha * (float) (1.0 - h / 2.2);
            double sz = 0.05 + 0.04 * hash(i, 11);
            m.gem(c.add(Math.cos(a) * rad, 0.15 + h, Math.sin(a) * rad), UP.multiply(sz * 1.6), FX.multiply(sz), FY.multiply(sz), i % 2 == 0 ? WHITE : GOLD, 1f);
        }
        m.twoSided = ts;
        m.alpha = old;
    }

    // ------------------------------------------------------------------ ร่างผู้ใช้ / ภาพติดตา

    /**
     * หุ่นมนุษย์สูง ~1.8 บล็อก
     * @param feet  ตำแหน่งปลายเท้า
     * @param fwd   ทิศหน้า (หน่วย) / @param up แกนลำตัว (หน่วย)
     * @param stride 0..1 ก้าวขา / @param spread 0..1 กางแขน
     * @param ghost true = ภาพติดตาสีทองล้วน เรืองแสง
     */
    static void figure(Mesh m, Vec3d feet, Vec3d fwd, Vec3d up, double stride, double spread, double time, boolean ghost, float alpha) {
        float old = m.alpha;
        m.alpha = alpha;
        Vec3d right = up.crossProduct(fwd).normalize();
        float[] skin = ghost ? GOLD : new float[]{0.93f, 0.80f, 0.68f};
        float[] robe = ghost ? PALE : WHITE;
        float[] trim = ghost ? GOLD : GOLD;
        float e = ghost ? 1f : 0.35f;

        Vec3d hip = feet.add(up.multiply(0.9));
        Vec3d chest = hip.add(up.multiply(0.55));
        Vec3d neck = hip.add(up.multiply(0.8));
        Vec3d head = hip.add(up.multiply(1.02));

        // ขา
        Vec3d fFoot = feet.add(fwd.multiply(0.55 * stride)).add(up.multiply(0.25 * stride));
        Vec3d bFoot = feet.subtract(fwd.multiply(0.4 * stride));
        m.limb(hip.add(right.multiply(0.12)), fFoot.add(right.multiply(0.12)), 0.1, 0.07, robe, e);
        m.limb(hip.subtract(right.multiply(0.12)), bFoot.subtract(right.multiply(0.12)), 0.1, 0.07, robe, e);
        m.limb(fFoot.add(right.multiply(0.12)), fFoot.add(right.multiply(0.12)).add(fwd.multiply(0.2)), 0.07, 0.05, trim, e);
        m.limb(bFoot.subtract(right.multiply(0.12)), bFoot.subtract(right.multiply(0.12)).add(fwd.multiply(0.2)), 0.07, 0.05, trim, e);

        // ลำตัวและเข็มขัด
        m.box(hip.add(up.multiply(0.4)), right.multiply(0.24), up.multiply(0.4), fwd.multiply(0.13), robe, e);
        m.box(hip.add(up.multiply(0.08)), right.multiply(0.26), up.multiply(0.05), fwd.multiply(0.15), trim, 0.8f);
        m.sphere(head, 0.2, 8, skin, ghost ? 1f : 0.3f);
        // ผมมวย
        m.sphere(head.add(up.multiply(0.2)).subtract(fwd.multiply(0.04)), 0.1, 6, ghost ? GOLD : new float[]{0.12f, 0.1f, 0.12f}, e);
        m.limb(neck, neck.add(up.multiply(0.04)), 0.09, 0.09, skin, e);

        // แขน
        for (int side = -1; side <= 1; side += 2) {
            Vec3d sh = chest.add(up.multiply(0.18)).add(right.multiply(0.27 * side));
            Vec3d hand = sh.add(right.multiply(side * (0.1 + 0.95 * spread)))
                    .add(up.multiply(-0.55 + 0.95 * spread))
                    .add(fwd.multiply((side > 0 ? 0.35 : -0.25) * stride));
            Vec3d elbow = Mesh.lerp(sh, hand, 0.5).add(up.multiply(-0.08)).add(right.multiply(side * 0.08));
            m.limb(sh, elbow, 0.08, 0.07, robe, e);
            m.limb(elbow, hand, 0.07, 0.05, skin, e);
            // แขนเสื้อกว้าง
            Vec3d sleeve = Mesh.lerp(elbow, hand, 0.9).add(up.multiply(-0.35));
            m.tri(elbow.add(fwd.multiply(0.1)), elbow.subtract(fwd.multiply(0.1)), sleeve, null, robe, e);
        }

        // ผ้าคลุม/ชายเสื้อพริ้วไปด้านหลัง
        for (int k = -1; k <= 1; k++) {
            Vec3d[] pts = new Vec3d[7];
            Vec3d o = hip.add(right.multiply(0.12 * k)).add(up.multiply(0.05));
            for (int i = 0; i < pts.length; i++) {
                double t = i / 6.0;
                pts[i] = o.subtract(fwd.multiply(0.15 + 1.6 * t * (0.6 + 0.4 * Math.abs(stride + 0.3)))).add(up.multiply(-0.5 * t + 0.12 * Math.sin(time * 0.25 + t * 5 + k)))
                        .add(right.multiply(0.25 * k * t));
            }
            m.ribbon(pts, right.multiply(1.0), 0.2, 0.04, k == 0 ? WHITE : PALE, ghost ? 1f : 0.45f);
        }
        m.alpha = old;
    }

    // ------------------------------------------------------------------ ปีกแสง + รัศมี

    static void wings(Mesh m, Vec3d back, Vec3d right, Vec3d up, Vec3d fwd, double spread, double time, float alpha) {
        if (spread <= 0.01) return;
        float old = m.alpha;
        m.alpha = alpha;
        for (int side = -1; side <= 1; side += 2) {
            for (int row = 0; row < 2; row++) {
                int n = row == 0 ? 8 : 6;
                for (int k = 0; k < n; k++) {
                    double u = (double) k / (n - 1);
                    double theta = Math.toRadians(70 - 150 * u) * spread + Math.sin(time * 0.1 + k + side) * 0.04;
                    double len = (row == 0 ? 3.6 : 2.2) * (0.55 + 0.45 * Math.sin(Math.PI * (0.15 + 0.85 * u))) * spread + 0.4;
                    Vec3d root = back.add(up.multiply(0.5 - 0.3 * row)).add(right.multiply(0.2 * side));
                    Vec3d dir = right.multiply(side * Math.cos(theta)).add(up.multiply(Math.sin(theta))).subtract(fwd.multiply(0.35 + 0.1 * row)).normalize();
                    Vec3d[] pts = new Vec3d[5];
                    for (int i = 0; i < 5; i++) {
                        double t = i / 4.0;
                        pts[i] = root.add(dir.multiply(len * t)).add(up.multiply(-0.4 * t * t * len * 0.3));
                    }
                    m.ribbon(pts, dir.crossProduct(fwd).normalize(), 0.34 + 0.08 * (1 - row), 0.03, row == 0 ? WHITE : PALE, 1f);
                }
            }
        }
        m.alpha = old;
    }

    static void halo(Mesh m, Vec3d c, Vec3d right, Vec3d up, double r, double rot, float alpha) {
        float old = m.alpha;
        m.alpha = alpha;
        m.annulus(c, right, up, r * 0.94, r, 0, Math.PI * 2, 56, GOLD, 1f);
        m.annulus(c, right, up, r * 0.62, r * 0.65, 0, Math.PI * 2, 56, WHITE, 1f);
        for (int i = 0; i < 12; i++) {
            double a = rot + Math.PI / 6 * i;
            Vec3d p = c.add(right.multiply(Math.cos(a) * r * 0.8)).add(up.multiply(Math.sin(a) * r * 0.8));
            double s = r * 0.07;
            m.gem(p, up.multiply(s * 1.6), right.multiply(s), right.crossProduct(up).normalize().multiply(s), i % 2 == 0 ? WHITE : GOLD, 1f);
        }
        m.alpha = old;
    }

    // ------------------------------------------------------------------ พุ่ง

    /** คลื่นกรวย + เส้นความเร็วด้านหลัง (dir = ทิศพุ่ง) */
    static void dash(Mesh m, Vec3d head, Vec3d dir, double len, double time, float alpha) {
        float old = m.alpha;
        Vec3d d = dir.normalize();
        Vec3d a = d.crossProduct(UP);
        a = a.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : a.normalize();
        Vec3d b = d.crossProduct(a).normalize();
        // วงคลื่นกรวย
        boolean ts = m.twoSided;
        m.twoSided = true;
        for (int i = 0; i < 9; i++) {
            double u = i / 8.0;
            Vec3d c = head.subtract(d.multiply(len * 0.12 + len * 0.55 * u * u));
            double r = 1.0 + 7.0 * Math.pow(u, 0.7);
            m.alpha = alpha * (float) (0.65 * (1.0 - u * 0.85));
            m.annulus(c, a, b, r * 0.9, r, 0, Math.PI * 2, 40, i % 2 == 0 ? WHITE : GOLD, 1f);
        }
        // เส้นความเร็ว
        m.alpha = alpha;
        for (int i = 0; i < 26; i++) {
            double ang = i * 2.399 + time * 0.02;
            double rad = 0.8 + 4.5 * (0.2 + 0.8 * hash(i, 1));
            Vec3d off = a.multiply(Math.cos(ang) * rad).add(b.multiply(Math.sin(ang) * rad));
            double l = len * (0.35 + 0.65 * hash(i, 2));
            Vec3d p0 = head.add(off).subtract(d.multiply(1.5 + hash(i, 3) * 3.0));
            Vec3d[] pts = {p0, p0.subtract(d.multiply(l * 0.35)), p0.subtract(d.multiply(l * 0.7)), p0.subtract(d.multiply(l))};
            m.ribbon(pts, off.normalize().crossProduct(d).normalize(), 0.12, 0.01, i % 3 == 0 ? GOLD : WHITE, 1f);
        }
        m.twoSided = ts;
        m.alpha = old;
    }

    // ------------------------------------------------------------------ รอยแหวกมิติ

    /**
     * รอยแยกรูปวงรีตั้ง: ขอบแหลมสีทอง + แสงขาวด้านใน
     * @param open 0..1
     */
    static void rift(Mesh m, Vec3d c, Vec3d right, Vec3d up, double h, double open, double time, float alpha) {
        if (open <= 0.01) return;
        float old = m.alpha;
        boolean ts = m.twoSided;
        m.twoSided = true;
        double w = h * 0.42 * open, hh = h * open;
        Vec3d n = right.crossProduct(up).normalize();
        // แสงด้านใน (วงรี = จานยืด)
        m.alpha = alpha * 0.75f;
        Vec3d ex = right.multiply(w), ey = up.multiply(hh);
        for (int ring = 0; ring < 5; ring++) {
            double r0 = ring / 5.0, r1 = (ring + 1) / 5.0;
            for (int j = 0; j < 40; j++) {
                double a0 = 2 * Math.PI * j / 40, a1 = 2 * Math.PI * (j + 1) / 40;
                float[] col = Mesh.mix(WHITE, GOLD, r0 * 0.9);
                Vec3d q00 = c.add(ex.multiply(Math.cos(a0) * r0)).add(ey.multiply(Math.sin(a0) * r0));
                Vec3d q01 = c.add(ex.multiply(Math.cos(a0) * r1)).add(ey.multiply(Math.sin(a0) * r1));
                Vec3d q11 = c.add(ex.multiply(Math.cos(a1) * r1)).add(ey.multiply(Math.sin(a1) * r1));
                Vec3d q10 = c.add(ex.multiply(Math.cos(a1) * r0)).add(ey.multiply(Math.sin(a1) * r0));
                m.v(q00, n, col, 1f);
                m.v(q01, n, col, 1f);
                m.v(q11, n, col, 1f);
                m.v(q10, n, col, 1f);
            }
        }
        // ขอบแหลม
        m.alpha = alpha;
        int sp = 30;
        for (int i = 0; i < sp; i++) {
            double a0 = 2 * Math.PI * i / sp, a1 = 2 * Math.PI * (i + 0.7) / sp, am = (a0 + a1) / 2;
            double jag = 0.18 + 0.28 * hash(i, 5) + 0.05 * Math.sin(time * 0.3 + i);
            Vec3d p0 = c.add(ex.multiply(Math.cos(a0))).add(ey.multiply(Math.sin(a0)));
            Vec3d p1 = c.add(ex.multiply(Math.cos(a1))).add(ey.multiply(Math.sin(a1)));
            Vec3d tip = c.add(ex.multiply(Math.cos(am) * (1 + jag))).add(ey.multiply(Math.sin(am) * (1 + jag)))
                    .add(n.multiply((hash(i, 6) - 0.5) * 0.6));
            m.tri(p0, p1, tip, null, i % 2 == 0 ? GOLD : PALE, 1f);
        }
        m.twoSided = ts;
        m.alpha = old;
    }

    private static double hash(int a, int b) {
        double x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }
}
