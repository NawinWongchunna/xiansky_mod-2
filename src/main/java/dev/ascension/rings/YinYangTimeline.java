package dev.ascension.rings;

import net.minecraft.util.math.Vec3d;

/**
 * ไทม์ไลน์สกิล J "Yin-Yang Scripture" (หน่วย tick) ใช้ร่วมกันทั้ง server และ client
 *
 *  กดครั้งที่ 1
 *    0 ─ 24   THROW   โยนคัมภีร์ขึ้นฟ้า
 *   24 ─ 64   EXPAND  คัมภีร์ขยายใหญ่ ลอยไปอยู่ด้านหลังผู้เล่น
 *   36 ─ 84   OPEN    คัมภีร์เปิดหน้า
 *   92 ─ 162  มังกรขาวพุ่งออกจากหน้าคัมภีร์ (เหมือนพุ่งขึ้นจากน้ำ)
 *  122 ─ 192  มังกรดำพุ่งออกตามมา
 *  อีก 40 tick มังกรร่อนมาโคจรรอบผู้เล่น
 *  200+       READY   กด J อีกครั้งได้
 *  กดครั้งที่ 2 (s = tick นับจากกด)
 *    0 ─ 36   FLY     มังกรพุ่งไปที่เป้าที่เล็ง
 *   36        IMPACT  ระเบิดขาวดำ คลื่นทำลายล้างแผ่ 80 tick
 */
public final class YinYangTimeline {
    public static final int THROW_END = 24;
    public static final int EXPAND_END = 64;
    public static final int OPEN_START = 36;
    public static final int OPEN_END = 84;

    public static final int[] EMERGE_START = {92, 122};
    public static final int EMERGE_LEN = 70;
    public static final int GLIDE = 40;
    public static final int READY = 200;
    /** ถ้าไม่กดครั้งที่ 2 ภายในเวลานี้ มังกรกลับเข้าคัมภีร์/สกิลจบเอง */
    public static final int ORBIT_MAX = 1800;

    public static final int FLY = 36;
    public static final int WAVE = 80;
    public static final int STRIKE_END = FLY + WAVE + 70;
    public static final int FADE = 30;

    public static final double ORBIT_R = 26.0;
    public static final double ORBIT_H = 11.0;
    public static final double ORBIT_W = 0.05;

    public static final double DRAGON_HALF = 11.0;
    public static final double BOOK_HALF_W = 24.0;
    public static final double BOOK_HALF_H = 24.0 * 560.0 / 720.0;
    public static final double BOOK_BACK = 38.0;
    public static final double BOOK_UP = 34.0;

    // รูปทรง sprite มังกร (ดู yy_dragon.py): แกนลำตัวอยู่บนส่วนโค้งวงกลมรอบกึ่งกลางภาพ
    public static final double SPAN = 3.45;
    public static final double HEAD_EXTRA = 0.60;
    public static final double[] THETA_HEAD = {Math.toRadians(-8), Math.toRadians(172)};

    private YinYangTimeline() {}

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double smooth(double k) {
        k = clamp01(k);
        return k * k * (3 - 2 * k);
    }

    /** รัศมีคลื่นทำลายล้าง ที่ s tick หลังกดครั้งที่ 2 */
    public static double waveRadius(double s, double max) {
        double x = clamp01((s - FLY) / WAVE);
        return max * (1.0 - Math.pow(1.0 - x, 2.2));
    }

    /** รัศมีของแกนลำตัวใน sprite (หน่วย = ความกว้างภาพ) ที่พารามิเตอร์ s (0 = หาง, 1 = หัว) */
    public static double arcRadius(double s) {
        return 0.335 * (0.80 + 0.20 * Math.sqrt(Math.max(0.0, Math.min(1.0, s))));
    }

    /** ตำแหน่งโคจรของมังกร i (0 ขาว, 1 ดำ) ณ เวลา t รอบจุด center */
    public static Vec3d orbitPos(int i, double t, Vec3d center) {
        double a = 0.9 + i * Math.PI + ORBIT_W * t;
        double h = ORBIT_H + 3.0 * Math.sin(t * 0.04 + i * Math.PI);
        double r = ORBIT_R + 2.5 * Math.sin(t * 0.03 + i * 1.7);
        return center.add(Math.cos(a) * r, h, Math.sin(a) * r);
    }

    /** ตำแหน่งคัมภีร์ (คำนวณที่ server ครั้งเดียวแล้วส่งให้ client): อยู่ด้านหลังผู้เล่น */
    public static Vec3d bookPos(Vec3d casterPos, Vec3d look) {
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        return casterPos.add(-flat.x * BOOK_BACK, BOOK_UP, -flat.z * BOOK_BACK);
    }
}
