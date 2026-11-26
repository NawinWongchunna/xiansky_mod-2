package dev.ascension.rings.client;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.Timeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** สถานะสกิลฝั่ง client: ผู้เล่นทุกคนที่กำลังใช้สกิล (รวมตัวเอง) */
public final class ClientState {
    public static final class Instance {
        public final UUID uuid;
        public final Vec3d start;
        public int ticks = 0;

        Instance(UUID uuid, Vec3d start) {
            this.uuid = uuid;
            this.start = start;
        }
    }

    public static final Map<UUID, Instance> ACTIVE = new HashMap<>();
    private static final Random RNG = new Random();
    private static Perspective savedPerspective = null;

    private ClientState() {}

    // ---------- lifecycle ----------

    public static void start(UUID id, Vec3d pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ACTIVE.put(id, new Instance(id, pos));
        if (isLocal(mc, id)) {
            mc.player.setNoGravity(true);
            if (AscensionConfig.get().cinematic) {
                savedPerspective = mc.options.getPerspective();
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        }
    }

    public static void stop(UUID id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ACTIVE.remove(id) != null && isLocal(mc, id)) {
            restoreLocal(mc);
        }
    }

    public static void clear() {
        ACTIVE.clear();
        savedPerspective = null;
    }

    private static void restoreLocal(MinecraftClient mc) {
        if (mc.player != null) {
            mc.player.setNoGravity(false);
            mc.player.fallDistance = 0;
        }
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

    /** เวลา (tick + partial) ของตัวเอง หรือ -1 ถ้าไม่ได้ใช้สกิล */
    public static float localTime(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1f;
        Instance i = ACTIVE.get(mc.player.getUuid());
        return i == null ? -1f : i.ticks + tickDelta;
    }

    /** เหมือน localTime แต่คืนค่าเฉพาะตอนเปิด cinematic ใน config */
    public static float cinematicTime(float tickDelta) {
        return AscensionConfig.get().cinematic ? localTime(tickDelta) : -1f;
    }

    /** 0..1 ความเข้มของท้องฟ้าสีแดง/มืด */
    public static float skyIntensity(float tickDelta) {
        float t = cinematicTime(tickDelta);
        if (t < 0) return 0f;
        return Timeline.keys(t, 0, 0, 50, 0.8f, 120, 1.0f, 132, 0.55f, 160, 0f);
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

            PlayerEntity entity = world.getPlayerByUuid(inst.uuid);
            if (entity != null) {
                spawnParticles(world, entity, inst.ticks);
                if (isLocal(mc, inst.uuid)) driveLocal(mc, inst.ticks);
            }

            if (inst.ticks >= Timeline.TOTAL + 10) {
                it.remove();
                if (isLocal(mc, inst.uuid)) restoreLocal(mc);
            }
        }
    }

    /** client เป็นคนขับการเคลื่อนที่ของตัวเอง (server ไม่ได้ยัด velocity) */
    private static void driveLocal(MinecraftClient mc, int t) {
        ClientPlayerEntity p = mc.player;
        p.setNoGravity(true);
        p.fallDistance = 0;
        p.setVelocity(0, Timeline.verticalVelocity(t, p.isOnGround()), 0);
    }

    private static void spawnParticles(ClientWorld w, Entity e, int t) {
        Vec3d c = new Vec3d(e.getX(), e.getY() + 1.0, e.getZ());

        // ดูดพลังรอบๆ เข้ามาหาตัว
        if (t < Timeline.BURST) {
            int n = t < 60 ? 2 : 4;
            for (int i = 0; i < n; i++) {
                double th = RNG.nextDouble() * Math.PI * 2;
                double ph = (RNG.nextDouble() - 0.5) * Math.PI;
                double rad = 5 + RNG.nextDouble() * 5;
                Vec3d from = c.add(Math.cos(th) * Math.cos(ph) * rad, Math.sin(ph) * rad * 0.7, Math.sin(th) * Math.cos(ph) * rad);
                Vec3d v = c.subtract(from).normalize().multiply(0.35);
                w.addParticle(ParticleTypes.FLAME, from.x, from.y, from.z, v.x, v.y, v.z);
                if (RNG.nextBoolean()) {
                    w.addParticle(new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.4f),
                            from.x, from.y, from.z, v.x, v.y, v.z);
                }
            }
        }

        // ระเบิดวงกว้าง
        if (t == Timeline.BURST) {
            w.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
            for (int i = 0; i < 72; i++) {
                double a = i * (Math.PI * 2 / 72);
                w.addParticle(ParticleTypes.FLAME, c.x, c.y, c.z, Math.cos(a) * 0.9, (RNG.nextDouble() - 0.5) * 0.2, Math.sin(a) * 0.9);
                if (i % 3 == 0) {
                    w.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, Math.cos(a) * 0.6, 0.05, Math.sin(a) * 0.6);
                }
            }
        }
    }
}
