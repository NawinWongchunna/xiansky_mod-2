package dev.ascension.rings;

/**
 * ไทม์ไลน์สกิล G "Void Blade" (tick, 20 tick = 1 วินาที) ใช้ร่วม server/client
 *
 *   0 ─  25  RAISE    ยกมือขึ้น เมฆเริ่มมืด พายุเริ่มก่อตัว
 *  25 ─  75  RIFT     รอยแยกมิติเปิดบนฟ้า
 *  75 ─ 150  EMERGE   กระบี่ค่อยๆ โผล่ออกมาจากรอยแยก
 * 150 ─ 175  CHARGE   กระบี่ลอยนิ่งสั่นสะเทือน ฟ้าผ่ารัวขึ้น
 * 175 ─ 190  PLUNGE   กระบี่พุ่งปักพื้น
 * 190        IMPACT   แฟลช + คลื่นทำลายล้างแผ่ออกรอบทิศ
 * 190 ─ 290  WAVE     คลื่นขยายถึงรัศมีสูงสุด (ค่าเริ่มต้น 100 บล็อก)
 * 360 ─ 420  FADE     กระบี่จางหาย พายุสงบ
 */
public final class SwordTimeline {
    public static final int TOTAL = 420;
    public static final int RAISE_END = 25;
    public static final int RIFT_OPEN_END = 75;
    public static final int EMERGE_END = 150;
    public static final int CHARGE_END = 175;
    public static final int IMPACT = 190;
    public static final int WAVE = 100;
    public static final int FADE_START = 360;
    /** ผู้เล่นขยับไม่ได้จนถึง tick นี้ */
    public static final int LOCK_END = 200;

    /** ความสูงของรอยแยกเหนือจุดปัก (บล็อก) */
    public static final double RIFT_H = 100.0;
    /** ความสูงปลายกระบี่ตอนลอยนิ่ง */
    public static final double HOVER = 36.0;
    /** ปลายกระบี่ตอนปักพื้น (ติดลบ = จมลงไป) */
    public static final double EMBED = -3.0;

    private SwordTimeline() {}

    /** ความสูงของ "ปลายกระบี่" เทียบกับพื้นตรงจุดปัก */
    public static double tipOffset(float t) {
        if (t < RIFT_OPEN_END) return RIFT_H;
        if (t < EMERGE_END) {
            float k = Timeline.smooth((t - RIFT_OPEN_END) / (EMERGE_END - RIFT_OPEN_END));
            return RIFT_H + (HOVER - RIFT_H) * k;
        }
        if (t < CHARGE_END) {
            float k = Timeline.clamp01((t - EMERGE_END) / 25f);
            return HOVER + 0.6 * Math.sin(t * 0.9) * k;
        }
        if (t < IMPACT) {
            float k = (t - CHARGE_END) / (float) (IMPACT - CHARGE_END);
            return HOVER + (EMBED - HOVER) * k * k;
        }
        double shake = t < IMPACT + 12 ? 0.4 * Math.sin(t * 2.0) * (1.0 - (t - IMPACT) / 12.0) : 0.0;
        return EMBED + shake;
    }

    /** รัศมีคลื่นทำลายล้าง ณ เวลา t (ขยายเร็วตอนแรก แล้วช้าลง) */
    public static double waveRadius(float t, double maxRadius) {
        float f = Timeline.clamp01((t - IMPACT) / WAVE);
        return maxRadius * (1.0 - (1.0 - f) * (1.0 - f));
    }
}
