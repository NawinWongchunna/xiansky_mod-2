package dev.ascension.rings;

import net.minecraft.util.math.Vec3d;

/** เวลา/เส้นทางของสกิล V "เก้าก้าวย่างอากาศ" ที่ server และ client ใช้ร่วมกัน (คำนวณจากสูตรเดียวกัน จึงตรงกันเสมอ) */
public final class NineStepTimeline {
    /** แหวกมิติเปิดตรงหน้า + ท่าเตรียม */
    public static final int LEAD = 16;
    /** tick ต่อ 1 ก้าว */
    public static final int STEP = 9;
    public static final int STEPS = 9;
    /** หยุดลอยรวมพลังที่ก้าวที่ 9 */
    public static final int CHARGE = 14;
    /** พุ่งเข้ารอยแหวก (โหมดวาร์ปข้ามระยะไกล/ข้ามมิติ) */
    public static final int JUMP_DASH = 14;
    /** เอฟเฟกต์ตอนลงจอด */
    public static final int LAND_FX = 34;
    public static final int FADE = 24;
    public static final int MIN_DASH_TICKS = 26;
    public static final int MAX_DASH_TICKS = 100;

    /** ระยะราบ/ระยะสูงต่อก้าว */
    public static final double STEP_H = 6.5;
    public static final double STEP_V = 1.4;
    /** รอยแหวกอยู่ห่างจากก้าวที่ 9 กี่บล็อก */
    public static final double RIFT_AHEAD = 12.0;

    private NineStepTimeline() {}

    public static int stepsEnd() {
        return LEAD + STEP * STEPS;
    }

    public static int dashStart() {
        return stepsEnd() + CHARGE;
    }

    public static int dashTicks(double dist) {
        return (int) Math.max(MIN_DASH_TICKS, Math.min(MAX_DASH_TICKS, Math.round(dist / 3.6)));
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    public static double smoother(double x) {
        x = clamp01(x);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    /** ตำแหน่งเท้าของก้าวที่ i (0 = จุดเริ่ม, 9 = ก้าวสุดท้าย) */
    public static Vec3d stair(Vec3d start, Vec3d dir, int i) {
        return start.add(dir.x * STEP_H * i, STEP_V * i, dir.z * STEP_H * i);
    }

    /** จุดเท้าที่รอยแหวกกลางอากาศ (ปลายบันได) */
    public static Vec3d riftFoot(Vec3d start, Vec3d dir) {
        Vec3d s9 = stair(start, dir, STEPS);
        return s9.add(dir.x * RIFT_AHEAD, 0, dir.z * RIFT_AHEAD);
    }

    /** ตำแหน่งเท้าของผู้ร่าย ณ เวลา t (tick, ต่อเนื่อง) ตั้งแต่ 0 จนถึงจบพุ่ง; end = ปลายทางของการพุ่ง */
    public static Vec3d pos(Vec3d start, Vec3d dir, Vec3d end, int dashT, double t) {
        if (t <= LEAD) return start;
        if (t < stepsEnd()) {
            double k = (t - LEAD) / STEP;
            int i = (int) Math.floor(k);
            double f = k - i;
            Vec3d a = stair(start, dir, i), b = stair(start, dir, i + 1);
            double e = smoother(f);
            return a.add(b.subtract(a).multiply(e)).add(0, 0.55 * Math.sin(Math.PI * f), 0);
        }
        Vec3d s9 = stair(start, dir, STEPS);
        if (t < dashStart()) {
            return s9.add(0, 0.18 * Math.sin((t - stepsEnd()) * 0.35), 0);
        }
        double u = clamp01((t - dashStart()) / dashT);
        double e = Math.pow(u, 1.6);
        return s9.add(end.subtract(s9).multiply(e));
    }
}
