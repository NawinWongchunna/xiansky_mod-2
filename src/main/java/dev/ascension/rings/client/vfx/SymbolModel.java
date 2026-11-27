package dev.ascension.rings.client.vfx;

import net.minecraft.util.math.Vec3d;

/**
 * ชิ้นส่วน 3 มิติของลวดลายหยินหยาง (ไม่มี GL): จานไท่จี๋มีความหนา, วงปากั้ว (ตรีลักษณ์ 8 ชุด), วงแหวน,
 * โดมระเบิดสองสี, เสาแสง, เพชรรูน
 */
final class SymbolModel {
    static final float[] WHITE = {0.97f, 0.97f, 0.95f};
    static final float[] BLACK = {0.06f, 0.05f, 0.09f};
    static final float[] GOLD = {1.00f, 0.82f, 0.36f};
    static final float[] DEEP_GOLD = {0.62f, 0.44f, 0.14f};

    private SymbolModel() {}

    static Vec3d rot(Vec3d v, Vec3d axis, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        return v.multiply(c).add(axis.crossProduct(v).multiply(s)).add(axis.multiply(axis.dotProduct(v) * (1 - c)));
    }

    /** 0 = ขาว, 1 = ดำ (รวมตา) ตามพิกัดบนจานรัศมี 1; -1 = นอกจาน */
    static int taijiCode(double x, double y) {
        if (Math.hypot(x, y) > 1.0) return -1;
        double dUp = Math.hypot(x, y - 0.5), dDn = Math.hypot(x, y + 0.5);
        if (dUp < 0.125) return 0;
        if (dDn < 0.125) return 1;
        if (dUp < 0.5) return 1;
        if (dDn < 0.5) return 0;
        return x < 0 ? 0 : 1;
    }

    /**
     * จานไท่จี๋ 3 มิติ: หน้า+หลัง+ขอบหนา วางบนระนาบ ex/ey (normal = ex×ey) หมุนในระนาบด้วย rot
     * @param glow 0..1 ความเรืองของสีขาว
     */
    static void taiji(Mesh m, Vec3d c, Vec3d ex, Vec3d ey, double r, double thick, double rot, double glow) {
        Vec3d n = ex.crossProduct(ey).normalize();
        Vec3d cx = ex.multiply(Math.cos(rot)).add(ey.multiply(Math.sin(rot)));
        Vec3d cy = ey.multiply(Math.cos(rot)).subtract(ex.multiply(Math.sin(rot)));
        int rs = 10, as = 56;
        for (int side = -1; side <= 1; side += 2) {
            Vec3d o = c.add(n.multiply(side * thick * 0.5));
            for (int i = 0; i < rs; i++) {
                double r0 = r * i / rs, r1 = r * (i + 1) / rs;
                for (int j = 0; j < as; j++) {
                    double a0 = 2 * Math.PI * j / as, a1 = 2 * Math.PI * (j + 1) / as;
                    double am = (a0 + a1) / 2, rm = (r0 + r1) / 2 / r;
                    int code = taijiCode(Math.cos(am) * rm, Math.sin(am) * rm);
                    if (code < 0) continue;
                    float[] col = code == 0 ? WHITE : BLACK;
                    float emis = code == 0 ? (float) glow : 0.05f;
                    Vec3d p00 = o.add(cx.multiply(Math.cos(a0) * r0)).add(cy.multiply(Math.sin(a0) * r0));
                    Vec3d p01 = o.add(cx.multiply(Math.cos(a0) * r1)).add(cy.multiply(Math.sin(a0) * r1));
                    Vec3d p11 = o.add(cx.multiply(Math.cos(a1) * r1)).add(cy.multiply(Math.sin(a1) * r1));
                    Vec3d p10 = o.add(cx.multiply(Math.cos(a1) * r0)).add(cy.multiply(Math.sin(a1) * r0));
                    Vec3d nn = n.multiply(side);
                    m.v(p00, nn, col, emis);
                    m.v(p01, nn, col, emis);
                    m.v(p11, nn, col, emis);
                    m.v(p10, nn, col, emis);
                }
            }
        }
        // ขอบหนา (สีทอง)
        for (int j = 0; j < as; j++) {
            double a0 = 2 * Math.PI * j / as, a1 = 2 * Math.PI * (j + 1) / as;
            Vec3d d0 = cx.multiply(Math.cos(a0)).add(cy.multiply(Math.sin(a0)));
            Vec3d d1 = cx.multiply(Math.cos(a1)).add(cy.multiply(Math.sin(a1)));
            Vec3d h = n.multiply(thick * 0.5);
            m.v(c.add(d0.multiply(r)).add(h), d0, GOLD, 0.6f);
            m.v(c.add(d1.multiply(r)).add(h), d1, GOLD, 0.6f);
            m.v(c.add(d1.multiply(r)).subtract(h), d1, GOLD, 0.6f);
            m.v(c.add(d0.multiply(r)).subtract(h), d0, GOLD, 0.6f);
        }
    }

    /** วงแหวนแบนสีเดียว */
    static void ring(Mesh m, Vec3d c, Vec3d ex, Vec3d ey, double r0, double r1, float[] col, float emis) {
        m.annulus(c, ex, ey, r0, r1, 0, Math.PI * 2, 64, col, emis);
    }

    private static final int[] TRIGRAM = {7, 3, 5, 1, 6, 2, 4, 0}; // 乾 兌 離 震 巽 坎 艮 坤 (บิตล่าง-บน)

    /**
     * วงปากั้วแบน: ขอบนอกทอง, ตรีลักษณ์ 8 ชุดวนรอบ, ไท่จี๋กลาง (ด้านเดียว) — ใช้เป็นตัวเล็ง/วงใต้เท้า/วงบนท้องฟ้า
     * @param withTaiji วาดจานไท่จี๋ตรงกลางหรือไม่
     */
    static void bagua(Mesh m, Vec3d c, Vec3d ex, Vec3d ey, double R, double rot, double glow, boolean withTaiji) {
        Vec3d cx = ex.multiply(Math.cos(rot)).add(ey.multiply(Math.sin(rot)));
        Vec3d cy = ey.multiply(Math.cos(rot)).subtract(ex.multiply(Math.sin(rot)));
        Vec3d n = ex.crossProduct(ey).normalize();
        float e = (float) glow;
        // ขอบ 2 ชั้น
        m.annulus(c, cx, cy, R * 0.955, R, 0, Math.PI * 2, 72, GOLD, e);
        m.annulus(c.add(n.multiply(R * 0.002)), cx, cy, R * 0.86, R * 0.885, 0, Math.PI * 2, 72, DEEP_GOLD, e * 0.8f);
        m.annulus(c, cx, cy, R * 0.5, R * 0.535, 0, Math.PI * 2, 72, GOLD, e);
        // พื้นวงตรีลักษณ์ (เข้ม โปร่ง)
        float old = m.alpha;
        m.alpha = old * 0.55f;
        m.annulus(c.subtract(n.multiply(R * 0.002)), cx, cy, R * 0.535, R * 0.86, 0, Math.PI * 2, 72, BLACK, 0f);
        m.alpha = old;
        // ตรีลักษณ์ 8 ชุด: 3 เส้น (ทึบ/ขาด) เรียงจากในออกนอก
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4.0 * k;
            double half = Math.PI / 4.0 * 0.38;
            for (int line = 0; line < 3; line++) {
                double r0 = R * (0.58 + 0.085 * line), r1 = r0 + R * 0.05;
                boolean solid = ((TRIGRAM[k] >> line) & 1) == 1;
                if (solid) {
                    m.annulus(c.add(n.multiply(R * 0.003)), cx, cy, r0, r1, a - half, a + half, 6, WHITE, e);
                } else {
                    m.annulus(c.add(n.multiply(R * 0.003)), cx, cy, r0, r1, a - half, a - half * 0.18, 3, WHITE, e);
                    m.annulus(c.add(n.multiply(R * 0.003)), cx, cy, r0, r1, a + half * 0.18, a + half, 3, WHITE, e);
                }
            }
        }
        if (withTaiji) taijiFlat(m, c.add(n.multiply(R * 0.003)), cx, cy, R * 0.47, glow);
    }

    /** ไท่จี๋แบนด้านเดียว (ไม่มีความหนา) */
    static void taijiFlat(Mesh m, Vec3d c, Vec3d ex, Vec3d ey, double r, double glow) {
        Vec3d n = ex.crossProduct(ey).normalize();
        int rs = 8, as = 48;
        for (int i = 0; i < rs; i++) {
            double r0 = r * i / rs, r1 = r * (i + 1) / rs;
            for (int j = 0; j < as; j++) {
                double a0 = 2 * Math.PI * j / as, a1 = 2 * Math.PI * (j + 1) / as;
                double am = (a0 + a1) / 2, rm = (r0 + r1) / 2 / r;
                int code = taijiCode(Math.cos(am) * rm, Math.sin(am) * rm);
                if (code < 0) continue;
                float[] col = code == 0 ? WHITE : BLACK;
                float emis = code == 0 ? (float) glow : 0.05f;
                m.v(c.add(ex.multiply(Math.cos(a0) * r0)).add(ey.multiply(Math.sin(a0) * r0)), n, col, emis);
                m.v(c.add(ex.multiply(Math.cos(a0) * r1)).add(ey.multiply(Math.sin(a0) * r1)), n, col, emis);
                m.v(c.add(ex.multiply(Math.cos(a1) * r1)).add(ey.multiply(Math.sin(a1) * r1)), n, col, emis);
                m.v(c.add(ex.multiply(Math.cos(a1) * r0)).add(ey.multiply(Math.sin(a1) * r0)), n, col, emis);
            }
        }
    }

    /** ทรงกลมโปร่งสองสี (ซีกขาว/ซีกดำตามแกน X) พร้อมหมุน — โดมระเบิด */
    static void dome(Mesh m, Vec3d c, double r, double spin, int seg, float alpha) {
        float old = m.alpha;
        m.alpha = alpha;
        m.twoSided = true;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * i / seg - Math.PI / 2, a1 = Math.PI * (i + 1) / seg - Math.PI / 2;
            for (int j = 0; j < seg * 2; j++) {
                double b0 = Math.PI * j / seg + spin, b1 = Math.PI * (j + 1) / seg + spin;
                // ลายไท่จี๋ตามแนวเส้นแวง: ขาว/ดำ สลับตามคลื่นของละติจูด
                double mid = (b0 + b1) / 2 + Math.sin((a0 + a1) / 2 * 2.0) * 0.9;
                boolean white = Math.sin(mid) > 0;
                float[] col = white ? WHITE : BLACK;
                float emis = white ? 0.8f : 0.1f;
                Vec3d n00 = d(a0, b0), n01 = d(a0, b1), n11 = d(a1, b1), n10 = d(a1, b0);
                m.v(c.add(n00.multiply(r)), n00, col, emis);
                m.v(c.add(n01.multiply(r)), n01, col, emis);
                m.v(c.add(n11.multiply(r)), n11, col, emis);
                m.v(c.add(n10.multiply(r)), n10, col, emis);
            }
        }
        m.twoSided = false;
        m.alpha = old;
    }

    private static Vec3d d(double lat, double lon) {
        return new Vec3d(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
    }

    /** เสาแสงทรงกระบอกลายเกลียวขาว/ดำ (โปร่งแสง) จากฐาน base ขึ้นไปสูง height */
    static void beam(Mesh m, Vec3d base, double height, double r, double spin, float alpha) {
        float old = m.alpha;
        m.alpha = alpha;
        m.twoSided = true;
        int as = 28, hs = 14;
        for (int i = 0; i < hs; i++) {
            double y0 = height * i / hs, y1 = height * (i + 1) / hs;
            double w0 = r * (1.0 + 0.25 * Math.sin(i * 0.9 + spin)), w1 = r * (1.0 + 0.25 * Math.sin((i + 1) * 0.9 + spin));
            for (int j = 0; j < as; j++) {
                double a0 = 2 * Math.PI * j / as + spin * 0.5 + y0 * 0.03, a1 = 2 * Math.PI * (j + 1) / as + spin * 0.5 + y0 * 0.03;
                boolean white = (j & 1) == 0;
                float[] col = white ? WHITE : BLACK;
                float emis = white ? 1f : 0.2f;
                Vec3d n0 = new Vec3d(Math.cos(a0), 0, Math.sin(a0)), n1 = new Vec3d(Math.cos(a1), 0, Math.sin(a1));
                m.v(base.add(n0.multiply(w0)).add(0, y0, 0), n0, col, emis);
                m.v(base.add(n1.multiply(w0)).add(0, y0, 0), n1, col, emis);
                m.v(base.add(n1.multiply(w1)).add(0, y1, 0), n1, col, emis);
                m.v(base.add(n0.multiply(w1)).add(0, y1, 0), n0, col, emis);
            }
        }
        m.twoSided = false;
        m.alpha = old;
    }
}
