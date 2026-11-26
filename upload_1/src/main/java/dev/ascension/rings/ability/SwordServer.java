package dev.ascension.rings.ability;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.SwordTimeline;
import dev.ascension.rings.network.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** สกิล G "Void Blade": แหวกมิติเรียกกระบี่ดำ ปักพื้น คลื่นทำลายรอบ ~100 บล็อก พร้อมพายุ */
public final class SwordServer {
    private static final class Run {
        int t = 0;
        Vec3d impact;
        double casterX, casterZ;
        boolean wasRaining, wasThundering;
    }

    private static final Map<UUID, Run> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Random RNG = new Random();

    private SwordServer() {}

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    public static void start(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (ACTIVE.containsKey(id) || AscensionServer.isActive(id) || YinYangServer.isActive(id) || !p.isAlive() || p.isSpectator()) return;
        AscensionMod.LOGGER.info("Void Blade start requested by {}", p.getName().getString());

        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Long until = COOLDOWN.get(id);
        if (until != null && now < until) {
            p.sendMessage(Text.translatable("message.ascension_rings.cooldown", (until - now) / 20 + 1).formatted(Formatting.RED), true);
            return;
        }

        // จุดปัก = ด้านหน้าผู้เล่น 12 บล็อก บนพื้นผิว
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        int ix = (int) Math.floor(p.getX() + flat.x * 12.0);
        int iz = (int) Math.floor(p.getZ() + flat.z * 12.0);
        int iy = w.isChunkLoaded(ix >> 4, iz >> 4)
                ? w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ix, iz)
                : (int) Math.floor(p.getY());

        Run run = new Run();
        run.impact = new Vec3d(ix + 0.5, iy, iz + 0.5);
        run.casterX = p.getX();
        run.casterZ = p.getZ();
        run.wasRaining = w.isRaining();
        run.wasThundering = w.isThundering();
        ACTIVE.put(id, run);

        broadcast(p, run, true);
    }

    public static void abort(ServerPlayerEntity p) {
        Run run = ACTIVE.remove(p.getUuid());
        if (run != null) finish(p.getServerWorld(), p, run, false);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Run>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Run> e = it.next();
            Run run = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            ServerWorld w = p.getServerWorld();
            if (!p.isAlive() || p.isRemoved()) {
                finish(w, p, run, false);
                it.remove();
                continue;
            }
            run.t++;
            step(w, p, run);
            if (run.t >= SwordTimeline.TOTAL) {
                finish(w, p, run, true);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------

    private static void step(ServerWorld w, ServerPlayerEntity p, Run run) {
        AscensionConfig cfg = AscensionConfig.get();
        int t = run.t;
        Vec3d c = run.impact;

        // ผู้ร่ายไม่ตายระหว่างสกิล (Resistance 5) และไม่เจ็บตอนพื้นหาย (Slow Falling)
        if (t % 10 == 1) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 4, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, false));
        }

        if (t == 1) {
            if (cfg.changeWeather) w.setWeather(0, 14000, true, true);
            sound(w, c, SoundEvents.BLOCK_BEACON_ACTIVATE, 6f, 0.5f);
        }
        if (t == SwordTimeline.RAISE_END) sound(w, c.add(0, 60, 0), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 10f, 0.5f);
        if (t == SwordTimeline.RIFT_OPEN_END) sound(w, c.add(0, 60, 0), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 12f, 0.4f);
        if (t == 110) sound(w, c.add(0, 40, 0), SoundEvents.ENTITY_WITHER_SPAWN, 12f, 0.5f);
        if (t == SwordTimeline.EMERGE_END) sound(w, c.add(0, 40, 0), SoundEvents.ENTITY_WARDEN_ROAR, 12f, 0.5f);
        if (t == SwordTimeline.CHARGE_END) sound(w, c.add(0, 30, 0), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 12f, 0.5f);

        // ฟ้าผ่าประกอบฉาก (cosmetic) ถี่ขึ้นเรื่อยๆ
        if (cfg.swordLightning && t >= 60) {
            int interval = t < SwordTimeline.IMPACT ? 12 : 4;
            if (t % interval == 0) {
                double ang = RNG.nextDouble() * Math.PI * 2;
                double dist = 8 + RNG.nextDouble() * Math.min(70, cfg.swordRadius * 0.7);
                strike(w, c.x + Math.cos(ang) * dist, c.z + Math.sin(ang) * dist);
            }
        }

        if (t == SwordTimeline.IMPACT) impact(w, p, run);
        if (t >= SwordTimeline.IMPACT && t <= SwordTimeline.IMPACT + SwordTimeline.WAVE) wave(w, p, run);
    }

    private static void impact(ServerWorld w, ServerPlayerEntity caster, Run run) {
        Vec3d c = run.impact;
        sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 16f, 0.5f);
        sound(w, c, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 16f, 0.6f);
        sound(w, c, SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 14f, 0.6f);
        w.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 2, c.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 2, c.z, 6, 4, 2, 4, 0);
        strike(w, c.x, c.z);
    }

    /** คลื่นทำลายล้าง: ขยายเป็นวงแหวนจาก r0 ถึง r1 ในแต่ละ tick */
    private static void wave(ServerWorld w, ServerPlayerEntity caster, Run run) {
        AscensionConfig cfg = AscensionConfig.get();
        double maxR = cfg.swordRadius;
        double r1 = Math.max(6.0, SwordTimeline.waveRadius(run.t, maxR));
        double r0 = run.t == SwordTimeline.IMPACT ? -1.0 : SwordTimeline.waveRadius(run.t - 1, maxR);
        if (r1 <= r0) return;

        Vec3d c = run.impact;
        double r0s = r0 < 0 ? -1.0 : r0 * r0;
        double r1s = r1 * r1;
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        int rr = (int) Math.ceil(r1);

        // 1) ทำลายบล็อก
        if (cfg.destroyBlocks) {
            BlockPos.Mutable m = new BlockPos.Mutable();
            int minY = w.getBottomY();
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 < r0s || d2 >= r1s) continue;
                    int x = cx + dx, z = cz + dz;
                    // เว้นเสาเล็กๆ ใต้เท้าผู้ร่าย
                    double ex = x + 0.5 - run.casterX, ez = z + 0.5 - run.casterZ;
                    if (ex * ex + ez * ez <= 6.25) continue;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

                    double d = Math.sqrt(d2);
                    double near = Math.max(0.0, 1.0 - d / maxR);
                    int depth = 1 + (int) Math.round(13.0 * Math.pow(near, 1.6)) + (d < 8 ? 10 : 0);

                    int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    int removed = 0;
                    while (removed < depth && y >= minY) {
                        m.set(x, y, z);
                        BlockState s = w.getBlockState(m);
                        if (s.isAir() || s.getBlock() instanceof FluidBlock) {
                            y--;
                            continue;
                        }
                        if (s.getHardness(w, m) < 0) break; // bedrock ฯลฯ
                        w.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        removed++;
                        y--;
                    }
                    // เผาพื้นที่โผล่ขึ้นมาใหม่
                    if (removed > 0 && y >= minY) {
                        m.set(x, y, z);
                        BlockState s = w.getBlockState(m);
                        if (!s.isAir() && !(s.getBlock() instanceof FluidBlock) && s.getHardness(w, m) >= 0) {
                            double roll = RNG.nextDouble();
                            BlockState scorch = null;
                            if (d < 25 && roll < 0.18) scorch = Blocks.MAGMA_BLOCK.getDefaultState();
                            else if (roll < 0.02) scorch = Blocks.CRYING_OBSIDIAN.getDefaultState();
                            else if (roll < 0.35 + 0.4 * near) scorch = Blocks.BLACKSTONE.getDefaultState();
                            if (scorch != null) w.setBlockState(m, scorch, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        }
                    }
                }
            }
        }

        // 2) ดาเมจสิ่งมีชีวิตที่คลื่นผ่าน
        Box box = new Box(c.x - r1, w.getBottomY(), c.z - r1, c.x + r1, w.getTopY(), c.z + r1);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && x != caster)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < r0s || d2 >= r1s) continue;
            e.damage(w.getDamageSources().indirectMagic(caster, caster), cfg.swordDamage);
            e.takeKnockback(2.0, -dx, -dz);
            e.setOnFireFor(8);
        }
    }

    private static void finish(ServerWorld w, ServerPlayerEntity p, Run run, boolean cooldown) {
        AscensionConfig cfg = AscensionConfig.get();
        if (cfg.changeWeather && cfg.restoreWeather) {
            if (!run.wasThundering && !run.wasRaining) w.setWeather(6000, 0, false, false);
            else if (!run.wasThundering) w.setWeather(0, 6000, true, false);
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false, false));
        broadcast(p, run, false);
        if (cooldown) COOLDOWN.put(p.getUuid(), w.getTime() + cfg.swordCooldownTicks);
    }

    private static void strike(ServerWorld w, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(x, y, z);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.WEATHER, vol, pitch);
    }

    /** ส่ง START/END ให้ผู้เล่นทุกคนในระยะ (รวมผู้ร่าย) */
    private static void broadcast(ServerPlayerEntity p, Run run, boolean start) {
        Set<ServerPlayerEntity> targets = new HashSet<>(PlayerLookup.around(p.getServerWorld(), run.impact, 400.0));
        targets.add(p);
        for (ServerPlayerEntity t : targets) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(p.getUuid());
            if (start) {
                buf.writeDouble(run.impact.x);
                buf.writeDouble(run.impact.y);
                buf.writeDouble(run.impact.z);
                buf.writeDouble(AscensionConfig.get().swordRadius);
            }
            ServerPlayNetworking.send(t, start ? Net.SWORD_START : Net.SWORD_END, buf);
        }
    }
}
