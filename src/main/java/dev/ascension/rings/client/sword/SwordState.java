package dev.ascension.rings.client.sword;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.SwordTimeline;
import dev.ascension.rings.Timeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
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

/** สถานะสกิล G ฝั่ง client */
public final class SwordState {
    public static final class Instance {
        public final UUID uuid;
        public final Vec3d impact;
        public final double radius;
        public int ticks = 0;

        Instance(UUID uuid, Vec3d impact, double radius) {
            this.uuid = uuid;
            this.impact = impact;
            this.radius = radius;
        }
    }

    public static final Map<UUID, Instance> ACTIVE = new HashMap<>();
    private static final Random RNG = new Random();
    private static Perspective savedPerspective = null;

    private SwordState() {}

    // ---------- lifecycle ----------

    public static void start(UUID id, Vec3d impact, double radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ACTIVE.put(id, new Instance(id, impact, radius));
        if (isLocal(mc, id) && AscensionConfig.get().cinematic) {
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    public static void stop(UUID id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ACTIVE.remove(id) != null && isLocal(mc, id)) restoreLocal(mc);
    }

    public static void clear() {
        ACTIVE.clear();
        savedPerspective = null;
    }

    private static void restoreLocal(MinecraftClient mc) {
        if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    private static boolean isLocal(MinecraftClient mc, UUID id) {
        return mc.player != null && mc.player.getUuid().equals(id);
    }

    // ---------- queries ----------

    public static boolean isLocalActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && ACTIVE.containsKey(mc.player.getUuid());
    }

    /** ผู้เล่นถูกล็อกการควบคุมอยู่ไหม (จนถึง LOCK_END) */
    public static boolean isLocked() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        Instance i = ACTIVE.get(mc.player.getUuid());
        return i != null && i.ticks < SwordTimeline.LOCK_END;
    }

    public static float localTime(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1f;
        Instance i = ACTIVE.get(mc.player.getUuid());
        return i == null ? -1f : i.ticks + tickDelta;
    }

    public static float cinematicTime(float tickDelta) {
        return AscensionConfig.get().cinematic ? localTime(tickDelta) : -1f;
    }

    /** 0..1 ความมืด/สีม่วงดำของท้องฟ้า */
    public static float skyIntensity(float tickDelta) {
        float t = cinematicTime(tickDelta);
        if (t < 0) return 0f;
        return Timeline.keys(t, 0, 0, 20, 0.15f, 80, 0.85f, 190, 1.0f, 200, 0.6f, 330, 0.85f, 420, 0f);
    }

    /** 0..1 ระดับยกแขนขวาของผู้เล่นคนนี้ (0 = ไม่ได้ใช้สกิล) */
    public static float armRaise(UUID id, float tickDelta) {
        Instance i = ACTIVE.get(id);
        if (i == null) return 0f;
        float t = i.ticks + tickDelta;
        return Timeline.keys(t, 0, 0, 10, 1, 185, 1, 200, 0);
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
            if (inst.ticks >= SwordTimeline.TOTAL + 10) {
                it.remove();
                if (isLocal(mc, inst.uuid)) restoreLocal(mc);
            }
        }
    }

    private static void spawnParticles(ClientWorld w, Instance inst, PlayerEntity caster) {
        int t = inst.ticks;
        Vec3d c = inst.impact;

        // ประกายดำ/ม่วงรอบมือผู้ร่ายตอนยกมือ
        if (caster != null && t < SwordTimeline.IMPACT) {
            for (int i = 0; i < 2; i++) {
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, caster.getX() + (RNG.nextDouble() - 0.5) * 0.8,
                        caster.getY() + 2.2 + RNG.nextDouble() * 0.6, caster.getZ() + (RNG.nextDouble() - 0.5) * 0.8,
                        0, 0.08, 0);
            }
        }

        // เถ้า/วิญญาณลอยขึ้นรอบจุดปักตอนชาร์จ
        if (t >= SwordTimeline.RIFT_OPEN_END && t < SwordTimeline.IMPACT) {
            for (int i = 0; i < 6; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                double d = 3 + RNG.nextDouble() * 22;
                w.addParticle(ParticleTypes.LARGE_SMOKE, c.x + Math.cos(a) * d, c.y + 0.5, c.z + Math.sin(a) * d,
                        0, 0.15 + RNG.nextDouble() * 0.2, 0);
            }
        }

        // ขอบคลื่นกระแทก
        if (t >= SwordTimeline.IMPACT && t <= SwordTimeline.IMPACT + SwordTimeline.WAVE) {
            double r = SwordTimeline.waveRadius(t, inst.radius);
            int n = 70;
            for (int i = 0; i < n; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) continue;
                double y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                w.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SOUL_FIRE_FLAME,
                        x, y + 0.3, z, Math.cos(a) * 0.3, 0.25 + RNG.nextDouble() * 0.3, Math.sin(a) * 0.3);
            }
        }
    }
}
