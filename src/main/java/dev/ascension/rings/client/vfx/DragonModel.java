package dev.ascension.rings.client.vfx;

import net.minecraft.util.math.Vec3d;

import java.util.function.DoubleFunction;

/**
 * มังกรจีน 3 มิติ (เวอร์ชันดีเทลสูง) สร้างจากโค้ดล้วนๆ — ไม่ใช้ไฟล์ภาพ/โมเดล
 *  ลำตัว  : ท่อ 96 ปล้อง x 14 จุด เลื้อยตามเส้นทางหัว + คลื่น, เกล็ดแยกชิ้นสีไม่เท่ากัน, เกล็ดหลังซ้อนทับ, แผ่นท้อง
 *  ครีบ   : แผงสันหลังเป็นแพทั้งแถว (เยื่อโปร่ง + หนามแหลมทุกสามปล้อง), ปลายหางเป็นเปลวพริ้ว
 *  หัว    : กะโหลก/จมูกมีรูจมูก คิ้วนูน เขี้ยว ฟันสองแถว ขากรรไกรอ้าหุบ ตา 3 มิติพร้อมรูม่านตาแนวตั้ง
 *           เขากวางแตกกิ่ง ครีบแก้ม หูครีบ หนวดยาว 4 เส้น เครา แผงแผงคอยาว
 *  ขา     : ต้นขา-แข้ง-เท้า เล็บโค้ง 4 นิ้ว + พู่ที่ข้อศอก
 *  ไข่มุก : ไข่มุกเพลิงลอยนำหน้า (คืนตำแหน่งเพื่อวาดแสง)
 * ทั้งหมดผูกกับ Vec3d เท่านั้น (ไม่มี GL) เพื่อทดสอบนอกเกมได้
 */
final class DragonModel {
    static final class Scheme {
        final float[] back, belly, accent, rim, eye, horn;
        final float rimK, ambient;

        Scheme(float[] back, float[] belly, float[] accent, float[] rim, float[] eye, float[] horn, float rimK, float ambient) {
            this.back = back;
            this.belly = belly;
            this.accent = accent;
            this.rim = rim;
            this.eye = eye;
            this.horn = horn;
            this.rimK = rimK;
            this.ambient = ambient;
        }

        MeshRender.Lit lit() {
            return new MeshRender.Lit(ambient, rimK, rim);
        }
    }

    static final Scheme WHITE = new Scheme(
            new float[]{0.96f, 0.94f, 0.88f}, new float[]{1.00f, 0.84f, 0.45f}, new float[]{1.00f, 0.78f, 0.22f},
            new float[]{0.70f, 0.90f, 1.00f}, new float[]{1.00f, 0.30f, 0.15f}, new float[]{1.00f, 0.84f, 0.32f}, 0.55f, 0.50f);
    static final Scheme BLACK = new Scheme(
            new float[]{0.16f, 0.10f, 0.24f}, new float[]{0.66f, 0.20f, 0.58f}, new float[]{0.84f, 0.36f, 1.00f},
            new float[]{0.72f, 0.38f, 1.00f}, new float[]{1.00f, 0.86f, 0.30f}, new float[]{0.72f, 0.32f, 0.96f}, 0.95f, 0.62f);

    private static final Vec3d UP = new Vec3d(0, 1, 0);

    static final int N = 96;          // จำนวนปล้องลำตัว
    static final int M = 14;          // จุดรอบหน้าตัดท่อ
    static final double LENGTH = 38.0;
    static final double RMAX = 2.0;

    private DragonModel() {}

    // ------------------------------------------------------------------ เส้นทางลำตัว

    private static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return a.add(b.subtract(a).multiply(k));
    }

    /** วางจุดลำตัวตามประวัติเส้นทางของหัว โดยรักษาความยาวคงที่ (ระยะห่างเท่ากัน) */
    static Vec3d[] body(DoubleFunction<Vec3d> head, double t, double sz) {
        double ds = LENGTH * sz / (N - 1);
        Vec3d[] out = new Vec3d[N];
        Vec3d cur = head.apply(t);
        out[0] = cur;
        int k = 1;
        double dist = 0.0, tt = t;
        int guard = 0;
        while (k < N && guard++ < 3000) {
            tt -= 0.25;
            Vec3d nx = head.apply(tt);
            double seg = nx.distanceTo(cur);
            while (k < N && k * ds <= dist + seg + 1.0e-9) {
                double fr = seg < 1.0e-9 ? 0.0 : (k * ds - dist) / seg;
                out[k] = lerp(cur, nx, fr);
                k++;
            }
            dist += seg;
            cur = nx;
        }
        while (k < N) out[k++] = cur;
        return out;
    }

    private static void frames(Vec3d[] p, Vec3d[] tan, Vec3d[] side, Vec3d[] up) {
        int n = p.length;
        for (int k = 0; k < n; k++) {
            Vec3d d = p[Math.max(k - 1, 0)].subtract(p[Math.min(k + 1, n - 1)]);
            tan[k] = d.lengthSquared() < 1.0e-10 ? (k > 0 ? tan[k - 1] : new Vec3d(1, 0, 0)) : d.normalize();
        }
        Vec3d s0 = tan[0].crossProduct(UP);
        if (s0.lengthSquared() < 1.0e-4) s0 = tan[0].crossProduct(new Vec3d(1, 0, 0));
        side[0] = s0.normalize();
        for (int k = 1; k < n; k++) {
            Vec3d s = side[k - 1].subtract(tan[k].multiply(side[k - 1].dotProduct(tan[k])));
            if (s.lengthSquared() < 1.0e-8) s = tan[k].crossProduct(UP);
            side[k] = s.lengthSquared() < 1.0e-8 ? side[k - 1] : s.normalize();
        }
        for (int k = 0; k < n; k++) up[k] = side[k].crossProduct(tan[k]).normalize();
    }

    /** รัศมีลำตัวที่ u (0 = คอติดหัว, 1 = ปลายหาง) ของ RMAX */
    private static double radius(double u) {
        double r;
        if (u < 0.06) r = 0.62 + 0.10 * (u / 0.06);          // คอ
        else if (u < 0.30) r = 0.72 + 0.28 * Math.sin((u - 0.06) / 0.24 * Math.PI / 2);  // อกใหญ่
        else r = Math.pow(1.0 - (u - 0.30) / 0.70, 0.9);
        return Math.max(0.07, r) * RMAX;
    }

    private static double hash(int a, int b) {
        double x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }

    /** คืน [f0,f1] ของส่วนที่มองเห็นบนเส้น a->b เทียบระนาบ (d>=0 คือด้านที่เห็น) หรือ null ถ้ามองไม่เห็น */
    private static double[] clipEdge(double da, double db) {
        if (da >= 0 && db >= 0) return new double[]{0, 1};
        if (da < 0 && db < 0) return null;
        double c = da / (da - db);
        return da >= 0 ? new double[]{0, c} : new double[]{c, 1};
    }

    private static Vec3d loc(Vec3d o, Vec3d F, Vec3d U, Vec3d S, double x, double y, double z) {
        return o.add(F.multiply(x)).add(U.multiply(y)).add(S.multiply(z));
    }

    // ------------------------------------------------------------------ สร้างโมเดล

    /**
     * @param S     จุดกึ่งกลางลำตัวจากหัวไปหาง (ยาว N)
     * @param time  เวลา (tick) สำหรับแอนิเมชัน
     * @param sz    ขนาดรวม (1 = ปกติ, 3 = ยักษ์)
     * @param clipP/clipN ระนาบตัด (ส่วนที่ d<0 ถูกซ่อน) หรือ null
     * @return ตำแหน่งไข่มุกเพลิง
     */
    static Vec3d build(Mesh m, Scheme sc, Vec3d[] S, double time, double sz, Vec3d clipP, Vec3d clipN) {
        int n = S.length;

        // 1) คลื่นลำตัว
        Vec3d[] tan = new Vec3d[n], side = new Vec3d[n], up = new Vec3d[n];
        frames(S, tan, side, up);
        Vec3d[] P = new Vec3d[n];
        for (int k = 0; k < n; k++) {
            double u = k / (double) (n - 1);
            double amp = 1.5 * sz * Math.min(1.0, u * 3.5) * (0.35 + 0.9 * u);
            double ph = time * 0.28 - u * 9.0;
            P[k] = S[k].add(side[k].multiply(Math.sin(ph) * amp)).add(up[k].multiply(Math.cos(ph) * amp * 0.45));
        }
        frames(P, tan, side, up);

        m.alpha = 1f;
        m.twoSided = false;

        // 2) ท่อลำตัว: จุดบนผิว + normal + สี (แต่ละ "เกล็ด" เป็นช่องสีเรียบของตัวเอง)
        double[] R = new double[n];
        Vec3d[][] rp = new Vec3d[n][M];
        Vec3d[][] rn = new Vec3d[n][M];
        float[][][] rc = new float[n][M][];
        double[][] rd = new double[n][M];
        for (int k = 0; k < n; k++) {
            double u = k / (double) (n - 1);
            double r = radius(u) * sz;
            R[k] = r;
            double bump = (k & 1) == 0 ? 1.035 : 0.97; // ลายปล้องสลับ
            for (int j = 0; j < M; j++) {
                double phi = 2 * Math.PI * j / M;
                double cs = Math.cos(phi), sn = Math.sin(phi);
                Vec3d dir = side[k].multiply(cs).add(up[k].multiply(sn));
                rn[k][j] = dir;
                double flat = sn < 0 ? 0.86 : 0.97; // ท้องแบนกว่าหลัง
                rp[k][j] = P[k].add(side[k].multiply(cs * r * bump)).add(up[k].multiply(sn * r * flat * bump));
                double bw = Math.max(0.0, Math.min(1.0, (-sn - 0.18) / 0.5));
                float[] col = Mesh.mix(sc.back, sc.belly, bw);
                // เกล็ดแต่ละช่องสีต่างกันเล็กน้อย + แถบข้างลำตัวเป็นสีเน้น
                double side1 = Math.max(0.0, 1.0 - Math.abs(sn) * 3.0) * 0.22;
                col = Mesh.mix(col, sc.accent, side1 + Math.max(0.0, (u - 0.82) / 0.18) * 0.55);
                float v = (float) (0.86 + 0.26 * hash(k, j));
                if (bw > 0.5) v = (k % 3 == 0) ? 0.82f : 1.0f; // แผ่นท้องเป็นแถบ
                rc[k][j] = Mesh.scale(col, v);
                rd[k][j] = clipP == null ? 1.0 : rp[k][j].subtract(clipP).dotProduct(clipN);
            }
        }
        for (int k = 0; k < n - 1; k++) {
            for (int j = 0; j < M; j++) {
                int j1 = (j + 1) % M;
                double[] e0 = clipEdge(rd[k][j], rd[k + 1][j]);
                double[] e1 = clipEdge(rd[k][j1], rd[k + 1][j1]);
                if (e0 == null || e1 == null) continue;
                Vec3d a0 = lerp(rp[k][j], rp[k + 1][j], e0[0]), b0 = lerp(rp[k][j], rp[k + 1][j], e0[1]);
                Vec3d a1 = lerp(rp[k][j1], rp[k + 1][j1], e1[0]), b1 = lerp(rp[k][j1], rp[k + 1][j1], e1[1]);
                float[] c = rc[k][j];
                m.v(a0, rn[k][j], c, 0f);
                m.v(a1, rn[k][j1], c, 0f);
                m.v(b1, rn[k + 1][j1], c, 0f);
                m.v(b0, rn[k + 1][j], c, 0f);
            }
        }

        // 3) เกล็ดหลังซ้อนทับ (สามเหลี่ยมชี้ไปทางหาง)
        double ds = LENGTH * sz / (n - 1);
        for (int k = 2; k < n - 2; k++) {
            if (rd[k][0] < 0 && clipP != null) continue;
            for (int j = 1; j < M / 2; j++) { // ครึ่งบน (sn > 0)
                if (((j + k) & 1) != 0) continue;
                int j1 = j + 1;
                Vec3d mid = Mesh.lerp(rp[k][j], rp[k][j1], 0.5);
                Vec3d out = rn[k][j].add(rn[k][j1]).normalize();
                Vec3d apex = mid.add(out.multiply(R[k] * 0.16)).subtract(tan[k].multiply(ds * 1.7));
                float[] col = Mesh.mix(Mesh.scale(rc[k][j], 1.08), sc.accent, 0.28);
                m.tri(rp[k][j], rp[k][j1], apex, P[k], col, 0.12f);
            }
        }

        // 4) แผงสันหลัง: เยื่อโปร่งเป็นแถว + หนามแหลมทุกสามปล้อง (ช่วงคอยาวเป็นแผงคอ)
        m.alpha = 0.62f;
        m.twoSided = true;
        Vec3d prevTop = null, prevBase = null;
        for (int k = 3; k < n - 3; k++) {
            if (clipP != null && P[k].subtract(clipP).dotProduct(clipN) < 0) {
                prevTop = null;
                continue;
            }
            double u = k / (double) (n - 1);
            double r = R[k] * 0.97;
            double spike = (k % 3 == 0) ? 1.0 : 0.42;
            double h = (R[k] * 0.9 + 0.5 * sz + (u < 0.3 ? 1.9 * sz * (1 - u / 0.3) : 0.0)) * (0.55 + 0.6 * spike)
                    + 0.28 * sz * Math.sin(time * 0.4 - k * 0.5);
            Vec3d base = P[k].add(up[k].multiply(r));
            Vec3d top = base.add(up[k].multiply(h)).subtract(tan[k].multiply((0.8 + 0.7 * (1 - u)) * sz * spike))
                    .add(side[k].multiply(Math.sin(time * 0.3 - k * 0.4) * 0.28 * sz));
            if (prevTop != null) {
                m.quad(prevBase, base, top, prevTop, null, sc.accent, 0.55f);
            }
            prevTop = top;
            prevBase = base;
        }
        m.alpha = 1f;
        m.twoSided = false;
        // หนามแหลมทึบทุกสามปล้อง
        for (int k = 3; k < n - 3; k += 3) {
            if (clipP != null && P[k].subtract(clipP).dotProduct(clipN) < 0) continue;
            double u = k / (double) (n - 1);
            double r = R[k] * 0.97;
            double h = (R[k] * 0.9 + 0.5 * sz + (u < 0.3 ? 1.9 * sz * (1 - u / 0.3) : 0.0)) * 1.15;
            Vec3d b0 = P[k].add(up[k].multiply(r)).add(side[k].multiply(R[k] * 0.16));
            Vec3d b1 = P[k].add(up[k].multiply(r)).subtract(side[k].multiply(R[k] * 0.16));
            Vec3d apex = P[k].add(up[k].multiply(r + h)).subtract(tan[k].multiply((0.9 + 0.8 * (1 - u)) * sz));
            m.tri(b0, b1, apex, P[k], sc.horn, 0.35f);
        }

        // 5) ขา 4 ข้าง (ต้นขา - แข้ง - เท้า + เล็บโค้ง + พู่ข้อศอก)
        int[] legK = {(int) (0.26 * n), (int) (0.52 * n)};
        for (int li = 0; li < 2; li++) {
            int k = legK[li];
            if (clipP != null && P[k].subtract(clipP).dotProduct(clipN) < 0) continue;
            double r = R[k];
            for (int s = -1; s <= 1; s += 2) {
                double sw = Math.sin(time * 0.25 + li * 1.7 + (s > 0 ? 0 : 1.4)) * 0.35 * sz;
                Vec3d a = P[k].add(side[k].multiply(s * r * 0.72)).subtract(up[k].multiply(r * 0.5));
                Vec3d b = a.add(side[k].multiply(s * 1.5 * sz)).subtract(up[k].multiply(1.5 * sz)).subtract(tan[k].multiply(0.9 * sz + sw));
                Vec3d d = b.add(side[k].multiply(s * 0.5 * sz)).subtract(up[k].multiply(1.4 * sz)).add(tan[k].multiply(0.8 * sz - sw));
                m.limb(a, b, 0.78 * sz, 0.46 * sz, sc.back, 0f);        // ต้นขา
                m.sphere(b, 0.5 * sz, 5, sc.belly, 0f);                  // ข้อศอก
                m.limb(b, d, 0.42 * sz, 0.28 * sz, sc.belly, 0f);       // แข้ง
                Vec3d foot = d.add(tan[k].multiply(0.45 * sz)).subtract(up[k].multiply(0.25 * sz));
                m.limb(d, foot, 0.3 * sz, 0.34 * sz, sc.back, 0f);      // เท้า
                for (int cl = -1; cl <= 2; cl++) {
                    double sp = (cl - 0.5) * 0.3 * sz;
                    Vec3d c0 = foot.add(side[k].multiply(sp * s * 0.8));
                    Vec3d c1 = c0.add(tan[k].multiply(0.5 * sz)).subtract(up[k].multiply(0.35 * sz)).add(side[k].multiply(sp * 0.25));
                    Vec3d c2 = c1.add(tan[k].multiply(0.22 * sz)).subtract(up[k].multiply(0.4 * sz));
                    m.chain(new Vec3d[]{c0, c1, c2}, 0.14 * sz, 0.02 * sz, sc.horn, 0.45f);
                }
                // พู่ขนที่ข้อศอก
                Vec3d[] tuft = new Vec3d[6];
                for (int i = 0; i < 6; i++) {
                    double q = i / 5.0;
                    tuft[i] = b.add(tan[k].multiply(-(1.0 + 3.4 * q) * sz))
                            .add(up[k].multiply((0.2 + 0.5 * Math.sin(time * 0.3 + q * 5 + li) * q) * sz))
                            .add(side[k].multiply(s * (0.4 + 0.7 * q) * sz));
                }
                m.alpha = 0.8f;
                m.ribbon(tuft, up[k], 0.34 * sz, 0.02 * sz, sc.accent, 0.5f);
                m.alpha = 1f;
            }
        }

        // 6) ปลายหาง: เปลวพริ้ว 3 แฉก
        {
            int k = n - 4;
            if (clipP == null || P[k].subtract(clipP).dotProduct(clipN) >= 0) {
                for (int f = -1; f <= 1; f++) {
                    Vec3d[] pts = new Vec3d[9];
                    for (int i = 0; i < 9; i++) {
                        double q = i / 8.0;
                        pts[i] = P[k].add(tan[k].multiply(-(0.5 + 4.5 * q) * sz))
                                .add(side[k].multiply((f * 1.3 * q + Math.sin(time * 0.35 - q * 5 + f) * 0.7 * q) * sz))
                                .add(up[k].multiply((Math.cos(time * 0.3 - q * 4 + f * 2) * 0.8 * q + 0.2 * Math.abs(f) * q) * sz));
                    }
                    m.alpha = 0.85f;
                    m.ribbon(pts, up[k], 0.7 * sz * (f == 0 ? 1.0 : 0.7), 0.04 * sz, sc.accent, 0.55f);
                    m.alpha = 1f;
                }
            }
        }

        // 7) หัว (ถ้ายังอยู่ใต้ผิวหน้าคัมภีร์ทั้งหัวก็ไม่วาด)
        if (clipP != null && P[0].subtract(clipP).dotProduct(clipN) < -2.0 * sz) return P[0];
        return head(m, sc, P[0], tan[0], side[0], up[0], time, sz);
    }

    private static Vec3d head(Mesh m, Scheme sc, Vec3d o, Vec3d F, Vec3d S, Vec3d U, double time, double sz) {
        final double hs = 1.55 * sz;
        final float[] dark = Mesh.scale(sc.back, 0.35);
        final float[] tooth = {0.98f, 0.96f, 0.88f};
        m.alpha = 1f;
        m.twoSided = false;

        // --- กะโหลก + จมูก: 2 ชั้น (กะโหลกหลัง, ปากบน) ---
        Vec3d bBL = loc(o, F, U, S, -0.5 * hs, -0.95 * hs, -1.5 * hs), bBR = loc(o, F, U, S, -0.5 * hs, -0.95 * hs, 1.5 * hs);
        Vec3d bTL = loc(o, F, U, S, -0.5 * hs, 1.15 * hs, -1.2 * hs), bTR = loc(o, F, U, S, -0.5 * hs, 1.15 * hs, 1.2 * hs);
        Vec3d mBL = loc(o, F, U, S, 1.9 * hs, -0.6 * hs, -1.0 * hs), mBR = loc(o, F, U, S, 1.9 * hs, -0.6 * hs, 1.0 * hs);
        Vec3d mTL = loc(o, F, U, S, 1.9 * hs, 0.75 * hs, -0.85 * hs), mTR = loc(o, F, U, S, 1.9 * hs, 0.75 * hs, 0.85 * hs);
        Vec3d fBL = loc(o, F, U, S, 4.2 * hs, -0.3 * hs, -0.62 * hs), fBR = loc(o, F, U, S, 4.2 * hs, -0.3 * hs, 0.62 * hs);
        Vec3d fTL = loc(o, F, U, S, 4.2 * hs, 0.5 * hs, -0.52 * hs), fTR = loc(o, F, U, S, 4.2 * hs, 0.5 * hs, 0.52 * hs);
        Vec3d ctr = loc(o, F, U, S, 1.8 * hs, 0.1 * hs, 0);
        float[] snout = Mesh.mix(sc.back, sc.belly, 0.25);
        // กะโหลกหลัง
        m.quad(bTL, bTR, mTR, mTL, ctr, sc.back, 0f);
        m.quad(bBL, bBR, mBR, mBL, ctr, sc.belly, 0f);
        m.quad(bBL, bTL, mTL, mBL, ctr, sc.back, 0f);
        m.quad(bBR, bTR, mTR, mBR, ctr, sc.back, 0f);
        m.quad(bBL, bBR, bTR, bTL, ctr, sc.back, 0f);
        // ปากบน (จมูก)
        m.quad(mTL, mTR, fTR, fTL, ctr, snout, 0f);
        m.quad(mBL, mBR, fBR, fBL, ctr, sc.belly, 0f);
        m.quad(mBL, mTL, fTL, fBL, ctr, snout, 0f);
        m.quad(mBR, mTR, fTR, fBR, ctr, snout, 0f);
        m.quad(fBL, fBR, fTR, fTL, ctr, snout, 0f);

        // สันจมูกนูน + รูจมูกบานสองข้าง
        Vec3d[] ridge = new Vec3d[5];
        for (int i = 0; i < 5; i++) {
            double q = i / 4.0;
            ridge[i] = loc(o, F, U, S, (1.2 + 3.1 * q) * hs, (0.88 - 0.35 * q) * hs, 0);
        }
        m.chain(ridge, 0.26 * hs, 0.2 * hs, sc.horn, 0.2f);
        for (int s = -1; s <= 1; s += 2) {
            Vec3d nb = loc(o, F, U, S, 4.15 * hs, 0.28 * hs, s * 0.33 * hs);
            m.sphere(nb, 0.2 * hs, 5, snout, 0f);
            m.sphere(loc(o, F, U, S, 4.28 * hs, 0.3 * hs, s * 0.3 * hs), 0.09 * hs, 4, dark, 0f);
        }

        // คิ้วนูนเหนือตา
        for (int s = -1; s <= 1; s += 2) {
            Vec3d[] br = new Vec3d[4];
            for (int i = 0; i < 4; i++) {
                double q = i / 3.0;
                br[i] = loc(o, F, U, S, (0.7 + 2.0 * q) * hs, (0.95 + 0.12 * Math.sin(q * 3.14)) * hs, s * (1.02 - 0.42 * q) * hs);
            }
            m.chain(br, 0.24 * hs, 0.1 * hs, sc.horn, 0.3f);
        }

        // --- ขากรรไกรล่าง อ้า/หุบ ---
        double ang = 0.16 + 0.12 * Math.sin(time * 0.18);
        double px = 0.1 * hs, py = -0.8 * hs;
        double[][] jaw = {{0.1, -0.7, 1.05}, {0.1, -1.2, 0.95}, {3.9, -0.62, 0.5}, {3.9, -0.98, 0.45}};
        Vec3d[] jl = new Vec3d[4], jr = new Vec3d[4];
        for (int i = 0; i < 4; i++) {
            double x = jaw[i][0] * hs, y = jaw[i][1] * hs, z = jaw[i][2] * hs;
            double dx = x - px, dy = y - py;
            double xr = px + dx * Math.cos(ang) + dy * Math.sin(ang);
            double yr = py - dx * Math.sin(ang) + dy * Math.cos(ang);
            jl[i] = loc(o, F, U, S, xr, yr, -z);
            jr[i] = loc(o, F, U, S, xr, yr, z);
        }
        Vec3d jc = loc(o, F, U, S, 1.8 * hs, -0.85 * hs, 0);
        m.quad(jl[0], jr[0], jr[2], jl[2], jc, sc.belly, 0f);
        m.quad(jl[1], jr[1], jr[3], jl[3], jc, sc.belly, 0f);
        m.quad(jl[0], jl[1], jl[3], jl[2], jc, sc.back, 0f);
        m.quad(jr[0], jr[1], jr[3], jr[2], jc, sc.back, 0f);
        m.quad(jl[2], jr[2], jr[3], jl[3], jc, sc.belly, 0f);

        // ฟัน: แถวบน/ล่าง + เขี้ยวยาว 2 คู่
        for (int s = -1; s <= 1; s += 2) {
            for (double x = 0.9; x < 4.0; x += 0.42) {
                double q = (x + 0.4) / 4.6;
                double hw = (1.0 + (0.52 - 1.0) * q) * hs;
                double yb = (-0.62 + 0.3 * q) * hs;
                boolean fang = x > 3.3 && x < 3.5;
                double len = (fang ? 0.95 : 0.42) * hs;
                Vec3d a = loc(o, F, U, S, (x - 0.12) * hs, yb, s * hw), b = loc(o, F, U, S, (x + 0.12) * hs, yb, s * hw);
                m.tri(a, b, loc(o, F, U, S, x * hs, yb - len, s * hw), null, tooth, 0.35f);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            for (double x = 1.3; x < 3.9; x += 0.5) {
                double q = x / 3.9;
                Vec3d base0 = Mesh.lerp(s < 0 ? jl[0] : jr[0], s < 0 ? jl[2] : jr[2], q);
                Vec3d up2 = U.multiply(0.3 * hs);
                Vec3d a = base0.add(F.multiply(-0.1 * hs)), b = base0.add(F.multiply(0.1 * hs));
                m.tri(a, b, base0.add(up2), null, tooth, 0.3f);
            }
        }

        // เคราใต้คาง: ริบบิ้น 2 เส้นพริ้ว
        for (int s = -1; s <= 1; s += 2) {
            Vec3d[] bd = new Vec3d[7];
            Vec3d chin = Mesh.lerp(s < 0 ? jl[3] : jr[3], s < 0 ? jl[1] : jr[1], 0.5).subtract(F.multiply(0.4 * hs));
            for (int i = 0; i < 7; i++) {
                double q = i / 6.0;
                bd[i] = chin.subtract(F.multiply(2.6 * q * hs)).subtract(U.multiply((0.5 + 1.8 * q) * hs))
                        .add(S.multiply(s * (0.2 + 0.4 * q) * hs + Math.sin(time * 0.3 - q * 5 + s) * 0.35 * q * hs));
            }
            m.alpha = 0.85f;
            m.ribbon(bd, S, 0.14 * hs, 0.02 * hs, sc.accent, 0.4f);
            m.alpha = 1f;
        }

        // ครีบแก้ม 3 อัน/ข้าง ชี้ไปด้านหลัง
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 3; i++) {
                Vec3d b0 = loc(o, F, U, S, (0.4 - 0.3 * i) * hs, (-0.2 - 0.35 * i) * hs, s * (1.35 + 0.1 * i) * hs);
                Vec3d b1 = loc(o, F, U, S, (1.2 - 0.3 * i) * hs, (-0.15 - 0.3 * i) * hs, s * (1.2 + 0.1 * i) * hs);
                Vec3d tip = loc(o, F, U, S, (-1.5 - 0.5 * i) * hs, (-0.1 - 0.5 * i + Math.sin(time * 0.35 + i) * 0.15) * hs, s * (2.0 + 0.5 * i) * hs);
                m.alpha = 0.9f;
                m.twoSided = true;
                m.tri(b0, b1, tip, null, sc.accent, 0.5f);
                m.alpha = 1f;
                m.twoSided = false;
            }
        }

        // --- ตา: เบ้า + ลูกตาเรืองแสง + รูม่านตาแนวตั้ง ---
        for (int s = -1; s <= 1; s += 2) {
            Vec3d e = loc(o, F, U, S, 1.35 * hs, 0.5 * hs, s * 0.98 * hs);
            m.sphere(e.subtract(S.multiply(s * 0.03 * hs)), 0.36 * hs, 6, dark, 0f);
            m.sphere(e.add(F.multiply(0.04 * hs)).add(S.multiply(s * 0.05 * hs)), 0.28 * hs, 6, sc.eye, 1.0f);
            Vec3d p0 = e.add(F.multiply(0.2 * hs)).add(S.multiply(s * 0.3 * hs)).add(U.multiply(0.2 * hs));
            Vec3d p1 = e.add(F.multiply(0.2 * hs)).add(S.multiply(s * 0.3 * hs)).subtract(U.multiply(0.2 * hs));
            m.limb(p0, p1, 0.035 * hs, 0.035 * hs, dark, 0f);
        }

        // --- เขากวาง: ลำหลัก 6 จุด + กิ่ง 2 กิ่ง ---
        for (int s = -1; s <= 1; s += 2) {
            Vec3d[] pts = new Vec3d[6];
            for (int i = 0; i < 6; i++) {
                double u = i / 5.0;
                pts[i] = loc(o, F, U, S, (0.3 - 3.4 * u) * hs, (1.0 + 2.1 * u - 0.9 * u * u) * hs, s * (0.7 + 1.1 * u) * hs);
            }
            m.chain(pts, 0.3 * hs, 0.04 * hs, sc.horn, 0.25f);
            // วงแหวนที่โคนเขา
            m.sphere(pts[0], 0.36 * hs, 5, sc.accent, 0.4f);
            for (int br = 0; br < 2; br++) {
                Vec3d b0 = pts[2 + br];
                Vec3d b1 = b0.add(U.multiply((1.0 + 0.3 * br) * hs)).add(S.multiply(s * (0.5 + 0.3 * br) * hs)).add(F.multiply(0.5 * hs));
                Vec3d b2 = b1.add(U.multiply(0.8 * hs)).add(F.multiply(0.9 * hs)).add(S.multiply(s * 0.2 * hs));
                m.chain(new Vec3d[]{b0, b1, b2}, 0.16 * hs, 0.02 * hs, sc.horn, 0.25f);
            }
        }

        // หูครีบ (ใบพัดเล็กหลังเขา)
        for (int s = -1; s <= 1; s += 2) {
            Vec3d base = loc(o, F, U, S, -0.3 * hs, 0.5 * hs, s * 1.25 * hs);
            Vec3d a = base.add(U.multiply(0.5 * hs)), c = base.subtract(U.multiply(0.4 * hs));
            Vec3d tip = base.subtract(F.multiply(1.8 * hs)).add(S.multiply(s * 1.3 * hs)).add(U.multiply((0.2 + 0.2 * Math.sin(time * 0.4 + s)) * hs));
            m.alpha = 0.85f;
            m.twoSided = true;
            m.tri(a, c, tip, null, sc.accent, 0.5f);
            m.alpha = 1f;
            m.twoSided = false;
        }

        // --- หนวดยาว 4 เส้น (2 ข้าง x 2) พริ้วตามเวลา ---
        for (int s = -1; s <= 1; s += 2) {
            for (int w = 0; w < 2; w++) {
                Vec3d[] pts = new Vec3d[12];
                double lift = w == 0 ? 0.0 : -0.6;
                for (int i = 0; i < 12; i++) {
                    double u = i / 11.0;
                    double lat = s * (0.6 + 4.5 * u * (w == 0 ? 1.0 : 0.7)) * hs * 0.8 + Math.sin(time * 0.3 - u * 6.0 + s + w) * 0.95 * u * hs;
                    double vert = (lift - 0.2 - 1.1 * u) * hs + Math.cos(time * 0.27 - u * 5.0 + w * 2) * 0.7 * u * hs;
                    pts[i] = loc(o, F, U, S, (3.8 - 10.0 * u) * hs, vert, lat);
                }
                m.chain(new Vec3d[]{pts[0], pts[1], pts[2]}, 0.09 * hs, 0.06 * hs, sc.accent, 0.4f);
                m.alpha = 0.9f;
                m.ribbon(java.util.Arrays.copyOfRange(pts, 2, 12), U, 0.2 * hs, 0.03 * hs, sc.accent, 0.55f);
                m.alpha = 1f;
            }
        }

        // --- แผงแผงคอ (ผมยาวปลิวไปด้านหลัง) ---
        for (int g = -2; g <= 2; g++) {
            Vec3d[] pts = new Vec3d[10];
            for (int i = 0; i < 10; i++) {
                double u = i / 9.0;
                pts[i] = loc(o, F, U, S, (-0.6 - 6.0 * u) * hs,
                        (1.1 - 0.5 * u + 0.35 * Math.sin(time * 0.33 - u * 6.0 + g) * u + Math.abs(g) * -0.1 * u) * hs,
                        (g * 0.5 + g * 0.45 * u + Math.cos(time * 0.29 - u * 5.0 + g * 1.7) * 0.4 * u) * hs);
            }
            m.alpha = 0.8f;
            m.ribbon(pts, U.multiply(0.8).add(S.multiply(0.6 * Math.signum(g))).normalize(), 0.42 * hs * (1.0 - 0.15 * Math.abs(g)), 0.03 * hs, g % 2 == 0 ? sc.accent : sc.horn, 0.5f);
            m.alpha = 1f;
        }

        // --- ไข่มุกเพลิงลอยนำหน้า ---
        Vec3d pearl = loc(o, F, U, S, 9.5 * hs, (1.0 + 0.5 * Math.sin(time * 0.15)) * hs, 0);
        m.sphere(pearl, 0.8 * hs, 7, new float[]{1.0f, 0.95f, 0.7f}, 1.0f);
        m.alpha = 0.75f;
        for (int fl = 0; fl < 5; fl++) {
            double a0 = fl * 1.2566 + time * 0.2;
            Vec3d[] pts = new Vec3d[6];
            for (int i = 0; i < 6; i++) {
                double q = i / 5.0;
                pts[i] = pearl.add(S.multiply(Math.cos(a0 + q * 2.0) * (0.7 + 0.7 * q) * hs))
                        .add(U.multiply(Math.sin(a0 + q * 2.0) * (0.7 + 0.7 * q) * hs))
                        .subtract(F.multiply(q * 1.8 * hs));
            }
            m.ribbon(pts, F.crossProduct(pts[3].subtract(pearl)).normalize(), 0.3 * hs, 0.02 * hs, sc.accent, 0.9f);
        }
        m.alpha = 1f;
        return pearl;
    }
}
