package dev.ascension.rings.client.nine;

import dev.ascension.rings.NineStepTimeline;
import dev.ascension.rings.client.yinyang.YinYangState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** สถานะฝั่ง client ของสกิล V (เก้าก้าวย่างอากาศ): ทุกอย่างคำนวณจาก NineStepTimeline เดียวกับ server */
public final class NineStepState {
    public static final class Instance {
        public UUID uuid;
        public Identifier srcDim, destDim;
        public Vec3d start, dir, end, landing;
        public int dashT;
        public boolean jump;
        public int ticks = 0;
        public int endAt = -1;

        public int tLand() {
            return NineStepTimeline.dashStart() + dashT;
        }

        public int tFinish() {
            return tLand() + NineStepTimeline.LAND_FX + NineStepTimeline.FADE;
        }

        /** ตำแหน่งเท้าผู้ร่ายที่เวลา t (หลังลงจอด = จุดลงจอด) */
        public Vec3d casterPos(double t) {
            if (t >= tLand()) return jump ? landing : end;
            return NineStepTimeline.pos(start, dir, end, dashT, t);
        }

        /** แกนทิศพุ่งจริง (3 มิติ) */
        public Vec3d flightDir() {
            Vec3d s9 = NineStepTimeline.stair(start, dir, NineStepTimeline.STEPS);
            Vec3d d = end.subtract(s9);
            return d.lengthSquared() < 1.0e-4 ? dir : d.normalize();
        }
    }

    public static final Map<UUID, Instance> ACTIVE = new LinkedHashMap<>();
    private static final Random RNG = new Random();

    private NineStepState() {}

    public static void start(UUID id, Identifier src, Identifier dest, Vec3d start, Vec3d dir, Vec3d end, Vec3d landing, int dashT, boolean jump) {
        Instance i = new Instance();
        i.uuid = id;
        i.srcDim = src;
        i.destDim = dest;
        i.start = start;
        i.dir = dir;
        i.end = end;
        i.landing = landing;
        i.dashT = dashT;
        i.jump = jump;
        ACTIVE.put(id, i);
    }

    public static void end(UUID id) {
        Instance i = ACTIVE.get(id);
        if (i != null && i.endAt < 0) i.endAt = i.ticks;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static Instance local() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : ACTIVE.get(mc.player.getUuid());
    }

    public static boolean isLocalActive() {
        return local() != null;
    }

    public static float localTime(float td) {
        Instance i = local();
        return i == null ? -1f : i.ticks + td;
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) {
            if (!ACTIVE.isEmpty()) ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) return;
        Identifier dim = w.getRegistryKey().getValue();
        var it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Instance i = it.next();
            i.ticks++;
            if (i.ticks > i.tFinish() || (i.endAt >= 0 && i.ticks > i.endAt + NineStepTimeline.FADE)) {
                it.remove();
                continue;
            }
            particles(w, dim, i);
        }
        drive(mc, dim);
    }

    /**
     * ขยับตัวละครของเราเองตามเส้นทางเดียวกับ server (client-authoritative) -> สมูท กล้องหมุนอิสระ
     * ไม่ขยับเมื่ออยู่คนละมิติ และโหมดวาร์ปหยุดก่อนถึงเวลา server วาร์ป 2 tick กันตำแหน่งตีกัน
     */
    private static void drive(MinecraftClient mc, Identifier dim) {
        Instance i = local();
        if (i == null || mc.player == null || !dim.equals(i.srcDim)) return;
        double t = i.ticks;
        double stop = i.jump ? i.tLand() - 2 : i.tLand();
        if (t > stop) return;
        Vec3d p = i.casterPos(t);
        mc.player.setPosition(p.x, p.y, p.z);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = 0f;
    }

    /** ท่าพุ่งของผู้เล่นคนนี้ 0..1 (แขนไคว่หลัง ตัวเอนไปข้างหน้า) */
    public static float dashPose(UUID id, float td) {
        Instance i = ACTIVE.get(id);
        if (i == null) return 0f;
        float t = i.ticks + td;
        float ds = NineStepTimeline.dashStart();
        float tl = i.tLand();
        return (float) (NineStepTimeline.smoother((t - (ds - 3f)) / 5f) * (1.0 - NineStepTimeline.smoother((t - (tl - 2f)) / 6f)));
    }

    private static void particles(ClientWorld w, Identifier dim, Instance i) {
        int t = i.ticks;
        boolean src = dim.equals(i.srcDim), dest = dim.equals(i.destDim);
        int ds = NineStepTimeline.dashStart();
        if (src && t < i.tLand() && !(i.jump && t > ds + i.dashT)) {
            Vec3d p = i.casterPos(t).add(0, 1.0, 0);
            int n = t >= ds ? 6 : (t >= NineStepTimeline.LEAD ? 2 : 0);
            for (int k = 0; k < n; k++) {
                w.addParticle(k % 3 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.END_ROD,
                        p.x + (RNG.nextDouble() - 0.5) * 1.2, p.y + (RNG.nextDouble() - 0.5) * 1.6, p.z + (RNG.nextDouble() - 0.5) * 1.2,
                        (RNG.nextDouble() - 0.5) * 0.06, (RNG.nextDouble() - 0.2) * 0.06, (RNG.nextDouble() - 0.5) * 0.06);
            }
            // หมอกพวยพุ่งตอนเหยียบแต่ละก้าว
            for (int s = 0; s <= NineStepTimeline.STEPS; s++) {
                int ts = s == 0 ? 3 : NineStepTimeline.LEAD + s * NineStepTimeline.STEP;
                if (t != ts) continue;
                Vec3d c = NineStepTimeline.stair(i.start, i.dir, s);
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI * 2 / 16 + RNG.nextDouble() * 0.3, sp = 0.08 + RNG.nextDouble() * 0.08;
                    w.addParticle(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, Math.cos(a) * sp, 0.01 + RNG.nextDouble() * 0.02, Math.sin(a) * sp);
                }
                for (int k = 0; k < 6; k++) {
                    w.addParticle(ParticleTypes.END_ROD, c.x + (RNG.nextDouble() - 0.5), c.y + 0.1, c.z + (RNG.nextDouble() - 0.5), 0, 0.04 + RNG.nextDouble() * 0.05, 0);
                }
            }
            // ละอองทองหลุดจากวงอักขระของแต่ละก้าว
            if (t >= NineStepTimeline.LEAD && t < ds && t % 2 == 0) {
                for (int s = 0; s <= NineStepTimeline.STEPS; s++) {
                    Vec3d c = NineStepTimeline.stair(i.start, i.dir, s);
                    if (t < NineStepTimeline.LEAD + s * NineStepTimeline.STEP - 4) continue;
                    double a = RNG.nextDouble() * Math.PI * 2, r = 0.8 + RNG.nextDouble() * 1.4;
                    w.addParticle(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.05 + RNG.nextDouble() * 0.05, 0);
                }
            }
        }
        if (dest && t >= i.tLand() && t < i.tLand() + 14) {
            Vec3d l = i.landing;
            for (int k = 0; k < 14; k++) {
                double a = RNG.nextDouble() * Math.PI * 2, sp = 0.3 + RNG.nextDouble() * 0.7;
                w.addParticle(k % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, l.x, l.y + 0.2, l.z, Math.cos(a) * sp, 0.1 + RNG.nextDouble() * 0.5, Math.sin(a) * sp);
            }
        }
    }

    /** FOV ที่เพิ่มขึ้นตอนพุ่ง (องศา) — ขยายเร็วแล้วค่อยๆ คืน เพื่อความรู้สึกความเร็ว */
    public static float fovBoost(float td) {
        Instance i = local();
        if (i == null) return 0f;
        float t = i.ticks + td;
        float ds = NineStepTimeline.dashStart();
        float tl = i.tLand();
        float up = (float) NineStepTimeline.smoother((t - (ds - 6f)) / 10f);
        float down = (float) NineStepTimeline.smoother((t - (tl - 2f)) / 16f);
        return 42f * up * (1f - down) + 6f * (float) NineStepTimeline.smoother((t - NineStepTimeline.stepsEnd() + 6) / 8f) * (1f - up);
    }

    /** แฟลชขาว-ทองของจอ 0..1 */
    public static float flash(float td) {
        Instance i = local();
        if (i == null) return 0f;
        float t = i.ticks + td;
        int ds = NineStepTimeline.dashStart();
        float f = 0f;
        f = Math.max(f, pulse(t, ds, 0.55f, 8f));
        int land = i.tLand();
        f = Math.max(f, pulse(t, land, i.jump ? 1.0f : 0.85f, i.jump ? 22f : 16f));
        f = Math.max(f, pulse(t, NineStepTimeline.LEAD, 0.35f, 8f));
        if (i.jump) {
            // เข้ารอยแหวก: ขาวบังตาก่อนวาร์ป
            f = Math.max(f, (float) NineStepTimeline.smoother((t - (land - 5f)) / 5f) * (t < land ? 1f : 0f));
        }
        return f;
    }

    private static float pulse(float t, float at, float peak, float len) {
        if (t < at || t > at + len) return 0f;
        float k = (t - at) / len;
        return peak * (1f - k) * (1f - k);
    }

    public static boolean inRealmGuard() {
        return YinYangState.inRealm;
    }
}
