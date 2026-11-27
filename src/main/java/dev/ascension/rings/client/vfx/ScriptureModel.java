package dev.ascension.rings.client.vfx;

import net.minecraft.util.math.Vec3d;

/**
 * คัมภีร์หยินหยาง 3 มิติ: ปกหนังมีขอบทอง + ตราไท่จี๋, กระดาษหนาเรียงเป็นชั้นมีขอบหน้ากระดาษ,
 * ตัวอักษรเรืองแสงสีทอง, ไท่จี๋ลายบนหน้ากระดาษ, หน้ากระดาษพลิกเองได้, ไท่จี๋หมุน + ปากั้วลอยเหนือเล่ม, รูนลอยวน
 * เปิดเล่มจริง (ปกซ้ายหมุนรอบสัน) ไม่ใช่การเปลี่ยนความกว้างของภาพ
 */
final class ScriptureModel {
    static final double WH = 22.0;      // ความกว้างครึ่งเล่ม
    static final double HH = 18.0;      // ความสูงครึ่งเล่ม
    static final double T = 1.5;        // ความหนาชุดกระดาษ/ครึ่งเล่ม
    static final double GAMMA = 0.16;   // มุมเอียงของแต่ละครึ่ง (รูปตัว V ตื้นๆ)

    static final float[] LEATHER = {0.20f, 0.07f, 0.07f};
    static final float[] PAPER = {0.96f, 0.91f, 0.78f};
    static final float[] PAPER_DK = {0.80f, 0.72f, 0.55f};
    static final float[] INK = {0.45f, 0.28f, 0.08f};

    private ScriptureModel() {}

    private static double hash(int a, int b) {
        double x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }

    /** กรอบอ้างอิงของเล่ม (หน่วยโลก) */
    static final class Frame {
        final Vec3d c, R, U, N;
        final double s;

        Frame(Vec3d c, Vec3d R, Vec3d U, Vec3d N, double s) {
            this.c = c;
            this.R = R;
            this.U = U;
            this.N = N;
            this.s = s;
        }

        Vec3d p(double x, double y, double z) {
            return c.add(R.multiply(x * s)).add(U.multiply(y * s)).add(N.multiply(z * s));
        }

        Vec3d dir(double x, double y, double z) {
            return R.multiply(x).add(U.multiply(y)).add(N.multiply(z));
        }
    }

    /** ครึ่งเล่ม: จุดที่ระยะ rho จากสัน, ความสูง y, ยกจากผิวกระดาษ h (ตามเส้นตั้งฉากของครึ่งนั้น) */
    private static final class Half {
        final int side;
        final double beta;
        final double dx, dz, nx, nz;
        final double time;

        Half(int side, double open, double time) {
            this.side = side;
            this.time = time;
            this.beta = side > 0 ? GAMMA : GAMMA + (Math.PI - 2 * GAMMA) * open;
            this.dx = Math.cos(beta);
            this.dz = Math.sin(beta);
            if (side > 0) {
                nx = -Math.sin(beta);
                nz = Math.cos(beta);
            } else {
                nx = Math.sin(beta);
                nz = -Math.cos(beta);
            }
        }

        /** คลื่นเบาๆ ของหน้ากระดาษ (ยกขึ้นตาม normal) */
        double wave(double rho) {
            double k = rho / WH;
            return 0.55 * k * k * Math.sin(rho * 0.32 - time * 0.09 + (side > 0 ? 0.0 : 1.7));
        }

        Vec3d pt(Frame f, double rho, double y, double h) {
            double hh = h + wave(rho);
            double x = dx * rho + nx * hh;
            double z = dz * rho + nz * hh;
            return f.p(x, y, z);
        }

        Vec3d nrm(Frame f) {
            return f.dir(nx, 0, nz).normalize();
        }
    }

    /**
     * @param open  0 = ปิด, 1 = เปิดเต็ม (ใช้ easing มาแล้ว)
     * @param time  tick สำหรับแอนิเมชัน
     */
    static void build(Mesh m, Frame f, double open, double time) {
        m.alpha = 1f;
        m.twoSided = false;
        Half right = new Half(1, open, time), left = new Half(-1, open, time);
        half(m, f, right, false, time);
        half(m, f, left, true, time);

        // สันปก
        Vec3d s0 = f.p(0, -HH - 0.9, -0.2), s1 = f.p(0, HH + 0.9, -0.2);
        Vec3d bk = f.dir(0, 0, -1);
        m.box(s0.add(s1).multiply(0.5).add(bk.multiply(T * 2 * f.s * 0.2)), f.R.multiply(0.5 * f.s), f.U.multiply((HH + 0.9) * f.s), f.N.multiply(T * 0.5 * f.s), LEATHER, 0f);

        // หน้ากระดาษที่พลิก (เฉพาะตอนเปิดเต็ม)
        if (open > 0.97) {
            for (int k = 0; k < 3; k++) {
                double ph = ((time * 1.0 + k * 20.0) % 60.0) / 30.0; // 0..2 ; พลิกในช่วง 0..1
                if (ph < 0 || ph > 1) continue;
                leaf(m, f, ph, k, time);
            }
        }
    }

    private static boolean ink(int i, int j, int nR, int nY) {
        if (i < 1 || i > nR - 2 || j < 2 || j > nY - 3) return false;
        if ((j & 1) != 0) return false;
        int len = 5 + (int) (hash(j, 3) * (nR - 8));
        return i < len && hash(i, j) > 0.22;
    }

    private static void half(Mesh m, Frame f, Half h, boolean left, double time) {
        final int nR = 24, nY = 30;
        final double r0 = 0.6;
        Vec3d n = h.nrm(f);
        double dr = (WH - r0) / nR, dy = 2 * HH / nY;

        // --- ผิวกระดาษด้านบน (มีตัวอักษรเรืองแสง + ไท่จี๋ลายพิมพ์) ---
        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nY; j++) {
                double ra = r0 + i * dr, rb = ra + dr, ya = -HH + j * dy, yb = ya + dy;
                float[] col = PAPER;
                float emis = 0f;
                boolean isInk = ink(i, j, nR, nY);
                double tx = (ra + dr * 0.5) / WH, ty = (ya + dy * 0.5) / HH;
                // ไท่จี๋ลายพิมพ์ใหญ่กลางหน้า (เฉพาะหน้าบนแต่ละฝั่ง)
                double px = (tx - 0.56) / 0.36, py = ty / 0.58;
                int code = SymbolModel.taijiCode(px, py);
                if (code >= 0 && Math.hypot(px, py) < 1.0) {
                    col = code == 0 ? new float[]{1f, 0.97f, 0.85f} : new float[]{0.10f, 0.07f, 0.06f};
                    emis = code == 0 ? 0.55f : 0.0f;
                } else if (isInk) {
                    col = INK;
                    emis = 0.1f;
                    // ตัวอักษรบางส่วนเรืองทอง
                    if (hash(i + 7, j) > 0.55) {
                        col = SymbolModel.GOLD;
                        emis = 0.8f;
                    }
                } else if (i == 0 || i == nR - 1 || j == 0 || j == nY - 1) {
                    col = SymbolModel.DEEP_GOLD; // กรอบหน้า
                    emis = 0.2f;
                } else {
                    col = Mesh.scale(PAPER, 0.93 + 0.07 * hash(i, j + 40));
                }
                Vec3d a = h.pt(f, ra, ya, 0), b = h.pt(f, rb, ya, 0), c = h.pt(f, rb, yb, 0), d = h.pt(f, ra, yb, 0);
                Vec3d nn = left ? n : n;
                m.v(a, nn, col, emis);
                m.v(b, nn, col, emis);
                m.v(c, nn, col, emis);
                m.v(d, nn, col, emis);
            }
        }

        // --- ขอบหน้ากระดาษ (ชั้นกระดาษเป็นริ้ว) ---
        int layers = 8;
        for (int l = 0; l < layers; l++) {
            double h0 = -T * 2 * l / layers, h1 = -T * 2 * (l + 1) / layers;
            float[] col = (l & 1) == 0 ? PAPER : PAPER_DK;
            // ขอบนอก (rho = WH)
            for (int j = 0; j < nY; j += 3) {
                double ya = -HH + j * dy, yb = Math.min(HH, ya + 3 * dy);
                m.quad(h.pt(f, WH, ya, h0), h.pt(f, WH, yb, h0), h.pt(f, WH, yb, h1), h.pt(f, WH, ya, h1), null, col, 0f);
            }
            // ขอบบน/ล่าง
            for (int e = -1; e <= 1; e += 2) {
                double yy = e * HH;
                for (int i = 0; i < nR; i += 3) {
                    double ra = r0 + i * dr, rb = Math.min(WH, ra + 3 * dr);
                    m.quad(h.pt(f, ra, yy, h0), h.pt(f, rb, yy, h0), h.pt(f, rb, yy, h1), h.pt(f, ra, yy, h1), null, col, 0f);
                }
            }
        }

        // --- ปกหนัง (ด้านล่างชุดกระดาษ ใหญ่กว่าเล็กน้อย) + ขอบทอง + ตราไท่จี๋ ---
        double hc = -T * 2 - 0.35;
        int cr = 14, cy = 14;
        double crr = (WH + 0.9 - 0.0) / cr, cyy = 2 * (HH + 0.9) / cy;
        Vec3d nb = n.multiply(-1);
        for (int i = 0; i < cr; i++) {
            for (int j = 0; j < cy; j++) {
                double ra = i * crr, rb = ra + crr, ya = -(HH + 0.9) + j * cyy, yb = ya + cyy;
                float[] col = LEATHER;
                float emis = 0f;
                boolean border = i == cr - 1 || j == 0 || j == cy - 1 || i == 0;
                if (border) {
                    col = SymbolModel.GOLD;
                    emis = 0.35f;
                }
                double px = ((ra + rb) * 0.5 - WH * 0.5) / (WH * 0.34), py = ((ya + yb) * 0.5) / (HH * 0.5);
                int code = SymbolModel.taijiCode(px, py);
                if (!border && code >= 0 && Math.hypot(px, py) < 1.0) {
                    col = code == 0 ? SymbolModel.WHITE : SymbolModel.BLACK;
                    emis = code == 0 ? 0.6f : 0.05f;
                }
                m.v(h.pt(f, ra, ya, hc), nb, col, emis);
                m.v(h.pt(f, ra, yb, hc), nb, col, emis);
                m.v(h.pt(f, rb, yb, hc), nb, col, emis);
                m.v(h.pt(f, rb, ya, hc), nb, col, emis);
            }
        }
        // ขอบปก (เชื่อมระหว่างปกกับกระดาษ)
        double e0 = -T * 2 - 0.35, e1 = -T * 2;
        for (int j = 0; j < 2; j++) {
            double ya = j == 0 ? -(HH + 0.9) : HH, yb = j == 0 ? -HH : HH + 0.9;
            m.quad(h.pt(f, 0, ya, e1), h.pt(f, WH + 0.9, ya, e1), h.pt(f, WH + 0.9, yb, e1), h.pt(f, 0, yb, e1), null, LEATHER, 0f);
        }
        m.quad(h.pt(f, WH, -HH, e1), h.pt(f, WH + 0.9, -HH, e1), h.pt(f, WH + 0.9, HH, e1), h.pt(f, WH, HH, e1), null, SymbolModel.DEEP_GOLD, 0.2f);
    }

    /** หน้ากระดาษที่กำลังพลิกจากขวาไปซ้าย (บางมาก สองด้าน) */
    private static void leaf(Mesh m, Frame f, double tau, int k, double time) {
        double e = tau * tau * (3 - 2 * tau);
        double beta = GAMMA + (Math.PI - 2 * GAMMA) * e;
        double bend = Math.sin(tau * Math.PI) * 1.1;
        int nR = 20, nY = 18;
        double dr = WH / nR, dy = 2 * HH / nY;
        m.twoSided = true;
        Vec3d prevA = null;
        double x = 0, z = 0.25 + 0.2 * k;
        double[] xs = new double[nR + 1], zs = new double[nR + 1];
        for (int i = 0; i <= nR; i++) {
            xs[i] = x;
            zs[i] = z;
            double ang = beta + bend * (i / (double) nR);
            x += Math.cos(ang) * dr;
            z += Math.sin(ang) * dr;
        }
        for (int i = 0; i < nR; i++) {
            for (int j = 0; j < nY; j++) {
                double ya = -HH + j * dy, yb = ya + dy;
                boolean ink = ink(i, j, nR, nY);
                float[] col = ink ? SymbolModel.GOLD : Mesh.scale(PAPER, 0.95);
                float emis = ink ? 0.9f : 0.05f;
                Vec3d a = f.p(xs[i], ya, zs[i]), b = f.p(xs[i + 1], ya, zs[i + 1]);
                Vec3d c = f.p(xs[i + 1], yb, zs[i + 1]), d = f.p(xs[i], yb, zs[i]);
                Vec3d nn = b.subtract(a).crossProduct(d.subtract(a)).normalize();
                m.v(a, nn, col, emis);
                m.v(b, nn, col, emis);
                m.v(c, nn, col, emis);
                m.v(d, nn, col, emis);
            }
        }
        m.twoSided = false;
    }

    /** ไท่จี๋หมุน + ปากั้วลอยเหนือเล่ม + รูนลอยวน */
    static void halo(Mesh m, Frame f, double time, double appear) {
        if (appear <= 0.01) return;
        double s = f.s * appear;
        Vec3d c = f.c.add(f.U.multiply((HH + 9.5) * f.s));
        SymbolModel.bagua(m, c, f.R, f.U, 11.0 * s, -time * 0.025, 0.85, false);
        SymbolModel.taiji(m, c.add(f.N.multiply(0.2 * s)), f.R, f.U, 5.0 * s, 1.0 * s, time * 0.06, 0.8);

        // รูนลอย
        for (int i = 0; i < 26; i++) {
            double ph = ((time * 0.006 + i / 26.0) % 1.0);
            double ang = i * 2.399 + time * 0.012;
            double rad = (0.55 + 0.55 * hash(i, 1)) * WH * f.s;
            Vec3d p = f.c.add(f.R.multiply(Math.cos(ang) * rad)).add(f.U.multiply((-HH + 2 * HH * ph) * f.s))
                    .add(f.N.multiply((3.0 + 4.0 * hash(i, 2) + 2.0 * Math.sin(time * 0.05 + i)) * f.s));
            double sz = (0.55 + 0.5 * hash(i, 3)) * f.s * appear * Math.sin(Math.PI * ph);
            float[] col = i % 3 == 0 ? SymbolModel.WHITE : (i % 3 == 1 ? SymbolModel.GOLD : new float[]{0.8f, 0.5f, 1f});
            m.gem(p, f.U.multiply(sz * 1.6), f.R.multiply(sz), f.N.multiply(sz * 0.6), col, 1f);
        }
    }
}
