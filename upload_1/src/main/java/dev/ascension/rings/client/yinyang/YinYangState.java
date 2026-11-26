package dev.ascension.rings.client.yinyang;

import dev.ascension.rings.YinYangTimeline;
import dev.ascension.rings.Timeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** สถานะสกิล J ฝั่ง client (ไม่ล็อกการควบคุมและไม่มีกล้องสั่น) */
public final class YinYangState {
    public static final class Instance {
        public final UUID uuid;
        public final Vec3d start;
        public final Vec3d book;
        public int ticks = 0;
        /** tick (ของ instance) ที่กดครั้งที่ 2 / -1 = ยังไม่กด */
        public int strikeAt = -1;
        public Vec3d target = Vec3d.ZERO;
        public double radius = 100.0;
        public final Vec3d[] p0 = {Vec3d.ZERO, Vec3d.ZERO};
        /** tick ที่ได้รับ END / -1 = ยังไม่จบ */
        public int endAt = -1;

        Instance(UUID uuid, Vec3d start, Vec3d book) {
            this.uuid = uuid;
            this.start = start;
            this.book = book;
        }
    }

    public static final Map<UUID, Instance> ACTIVE = new HashMap<>();
    private static final Random RNG = new Random();

    private YinYangState() {}

    // ---------- lifecycle ----------

    public static void start(UUID id, Vec3d start, Vec3d book) {
        ACTIVE.put(id, new Instance(id, start, book));
    }

    public static void strike(UUID id, Vec3d target, double radius) {
        Instance inst = ACTIVE.get(id);
        if (inst == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.world == null ? null : mc.world.getPlayerByUuid(id);
        Vec3d caster = p != null ? p.getPos() : inst.start;
        inst.target = target;
        inst.radius = radius;
        inst.strikeAt = inst.ticks;
        for (int i = 0; i < 2; i++) inst.p0[i] = YinYangTimeline.orbitPos(i, inst.ticks, caster);
    }

    public static void end(UUID id) {
        Instance inst = ACTIVE.get(id);
        if (inst != null && inst.endAt < 0) inst.endAt = inst.ticks;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    // ---------- queries ----------

    private static Instance local() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : ACTIVE.get(mc.player.getUuid());
    }

    public static boolean isLocalActive() {
        return local() != null;
    }

    /** เวลาของ instance ของผู้เล่นเอง (tick + tickDelta) หรือ -1 */
    public static float localTime(float tickDelta) {
        Instance i = local();
        return i == null ? -1f : i.ticks + tickDelta;
    }

    /** tick นับจากกดครั้งที่ 2 ของผู้เล่นเอง หรือ -1 ถ้ายังไม่กด */
    public static float localStrikeTime(float tickDelta) {
        Instance i = local();
        if (i == null || i.strikeAt < 0) return -1f;
        return i.ticks - i.strikeAt + tickDelta;
    }

    /** 0..1 ความเป็นขาวดำ (เทา) ของท้องฟ้าหลังระเบิด — มองจาก instance ใดก็ได้ที่ใกล้ที่สุด */
    public static float skyTone(float tickDelta) {
        float best = 0f;
        for (Instance i : ACTIVE.values()) {
            if (i.strikeAt < 0) continue;
            float k = i.ticks - i.strikeAt + tickDelta - YinYangTimeline.FLY;
            if (k < 0) continue;
            best = Math.max(best, Timeline.keys(k, 0, 0, 4, 1, 70, 0.9f, 150, 0.45f, 200, 0));
        }
        return best;
    }

    /** ระดับยกแขนขวา (ท่าโยนคัมภีร์) ของผู้เล่นคนนี้ */
    public static float armRaise(UUID id, float tickDelta) {
        Instance i = ACTIVE.get(id);
        if (i == null) return 0f;
        float t = i.ticks + tickDelta;
        return Timeline.keys(t, 0, 0, 6, 1, 26, 1, 40, 0);
    }

    /** ตำแหน่งมังกร (หลังโผล่แล้ว): โคจรรอบผู้ร่าย หรือพุ่งเป็นเส้นโค้งไปยังเป้าหมาย */
    public static Vec3d dragonPos(Instance inst, int i, double t, Vec3d caster) {
        if (inst.strikeAt >= 0 && t >= inst.strikeAt) {
            double u = YinYangTimeline.clamp01((t - inst.strikeAt) / YinYangTimeline.FLY);
            double e = Math.pow(u, 1.7);
            Vec3d p0 = inst.p0[i];
            Vec3d tg = inst.target.add(0, 3, 0);
            Vec3d dir = tg.subtract(p0);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            side = side.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : side.normalize();
            side = side.multiply(i == 0 ? 14.0 : -14.0);
            Vec3d ctrl = p0.add(tg).multiply(0.5).add(0, 22, 0).add(side);
            double a = (1 - e) * (1 - e), b = 2 * (1 - e) * e, c = e * e;
            return p0.multiply(a).add(ctrl.multiply(b)).add(tg.multiply(c));
        }
        return YinYangTimeline.orbitPos(i, t, caster);
    }

    // ---------- per tick ----------

    public static void tick(MinecraftClient mc) {
        if (ACTIVE.isEmpty()) return;
        ClientWorld world = mc.world;
        if (world == null) {
            clear();
            return;
        }
        Iterator<Instance> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Instance inst = it.next();
            inst.ticks++;
            PlayerEntity p = world.getPlayerByUuid(inst.uuid);
            spawnParticles(world, inst, p);
            if (inst.endAt >= 0 && inst.ticks - inst.endAt > YinYangTimeline.FADE + 5) it.remove();
        }
    }

    private static void spawnParticles(ClientWorld w, Instance inst, PlayerEntity caster) {
        int t = inst.ticks;
        Vec3d cp = caster != null ? caster.getPos() : inst.start;

        // ประกายรอบมือตอนโยนคัมภีร์
        if (caster != null && t < YinYangTimeline.THROW_END) {
            for (int i = 0; i < 3; i++) {
                w.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK,
                        caster.getX() + (RNG.nextDouble() - 0.5), caster.getY() + 1.6 + RNG.nextDouble() * 0.8,
                        caster.getZ() + (RNG.nextDouble() - 0.5), 0, 0.12, 0);
            }
        }

        // ละอองน้ำที่หน้าคัมภีร์ตอนมังกรพุ่งออกมา (พุ่งเข้าหาผู้เล่น)
        Vec3d toward = cp.add(0, 6, 0).subtract(inst.book);
        toward = toward.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : toward.normalize();
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            if (t >= st && t < st + YinYangTimeline.EMERGE_LEN) {
                for (int k = 0; k < 14; k++) {
                    double ox = (RNG.nextDouble() - 0.5) * 10, oy = (RNG.nextDouble() - 0.5) * 10;
                    w.addParticle(ParticleTypes.SPLASH, inst.book.x + ox, inst.book.y + oy, inst.book.z + (RNG.nextDouble() - 0.5) * 4,
                            toward.x * 0.9 + (RNG.nextDouble() - 0.5) * 0.4, toward.y * 0.9 + RNG.nextDouble() * 0.5,
                            toward.z * 0.9 + (RNG.nextDouble() - 0.5) * 0.4);
                }
            }
        }

        // ประกายตามตัวมังกร (ขาว = END_ROD, ดำ = SQUID_INK)
        if (t >= YinYangTimeline.EMERGE_START[0] + YinYangTimeline.EMERGE_LEN) {
            for (int i = 0; i < 2; i++) {
                if (t < YinYangTimeline.EMERGE_START[i] + YinYangTimeline.EMERGE_LEN + YinYangTimeline.GLIDE) continue;
                if (inst.strikeAt >= 0 && t - inst.strikeAt > YinYangTimeline.FLY) continue;
                Vec3d dp = dragonPos(inst, i, t, cp);
                for (int k = 0; k < 4; k++) {
                    w.addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK,
                            dp.x + (RNG.nextDouble() - 0.5) * 12, dp.y + (RNG.nextDouble() - 0.5) * 10, dp.z + (RNG.nextDouble() - 0.5) * 12,
                            0, -0.02, 0);
                }
            }
        }

        // ขอบคลื่นระเบิด: ประกายขาว/หมึกดำ
        if (inst.strikeAt >= 0) {
            int s = t - inst.strikeAt;
            if (s >= YinYangTimeline.FLY && s <= YinYangTimeline.FLY + YinYangTimeline.WAVE) {
                double r = YinYangTimeline.waveRadius(s, inst.radius);
                Vec3d c = inst.target;
                for (int i = 0; i < 70; i++) {
                    double a = RNG.nextDouble() * Math.PI * 2;
                    double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                    if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) continue;
                    double y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                    w.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK,
                            x, y + 0.4, z, Math.cos(a) * 0.3, 0.2 + RNG.nextDouble() * 0.3, Math.sin(a) * 0.3);
                }
            }
        }
    }
}
