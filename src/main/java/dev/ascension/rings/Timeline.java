package dev.ascension.rings;

/**
 * ไทม์ไลน์ของสกิล (หน่วย tick, 20 tick = 1 วินาที) ใช้ร่วมกันทั้ง server และ client
 *
 *  0 ─ 60   RISE      ลอยขึ้น วงแหวนไฟค่อยๆ โผล่ วงเวทย์กาง
 *  40─ 90   GATHER    ดอกเถาวัลย์ผลิบาน ดูดพลังรอบๆ เข้ามา
 *  70─120   OVERLOAD  แกนวอร์เท็กซ์ + ลูกพลังวนเข้า กล้องสั่นหนักขึ้น ฟ้าแดง
 * 120       BURST     flash ขาว shockwave ดาเมจรอบตัว
 * 132─160   RELEASE   ลอยลงช้าๆ กล้องกลับมาปกติ
 */
public final class Timeline {
    public static final int TOTAL = 160;
    public static final int RISE_END = 60;
    public static final int CORE_START = 70;
    public static final int BURST = 120;
    public static final int DESCEND = 132;

    private Timeline() {}

    /** ความเร็วแนวตั้ง (blocks/tick) ที่ client ใช้ขับตัวผู้เล่นเอง */
    public static double verticalVelocity(int t, boolean onGround) {
        if (t < RISE_END) return 0.22 * (1.0 - t / (double) RISE_END) + 0.012; // ลอยขึ้น ~7 บล็อก
        if (t < DESCEND) return 0.012 * Math.sin(t * 0.25);                    // ลอยนิ่งๆ โยกเบาๆ
        return onGround ? 0.0 : -0.09;                                          // ลอยลง
    }

    /** interpolate แบบ smoothstep ระหว่าง keyframe: keys(t, t0,v0, t1,v1, ...) */
    public static float keys(float t, float... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) {
                float t0 = kv[i - 2], v0 = kv[i - 1], t1 = kv[i], v1 = kv[i + 1];
                float k = (t - t0) / (t1 - t0);
                k = k * k * (3f - 2f * k);
                return v0 + (v1 - v0) * k;
            }
        }
        return kv[kv.length - 1];
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float smooth(float k) {
        k = clamp01(k);
        return k * k * (3f - 2f * k);
    }
}
