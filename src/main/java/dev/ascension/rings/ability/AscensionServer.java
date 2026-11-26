package dev.ascension.rings.ability;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.network.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** ฝั่ง server: จัดการสถานะสกิล, เสียง, ดาเมจ, ความปลอดภัยของผู้เล่น */
public final class AscensionServer {
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private AscensionServer() {}

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    public static void start(ServerPlayerEntity player) {
        if (SwordServer.isActive(player.getUuid()) || YinYangServer.isActive(player.getUuid())) return;
        dev.ascension.rings.AscensionMod.LOGGER.info("Ascension start requested by {}", player.getName().getString());
        if (ACTIVE.containsKey(player.getUuid()) || !player.isAlive() || player.isSpectator()) return;

        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        Long until = COOLDOWN.get(player.getUuid());
        if (until != null && now < until) {
            long left = (until - now) / 20 + 1;
            player.sendMessage(Text.translatable("message.ascension_rings.cooldown", left).formatted(Formatting.RED), true);
            return;
        }

        ACTIVE.put(player.getUuid(), 0);
        player.setNoGravity(true);
        player.fallDistance = 0;
        broadcast(player, true);
    }

    public static void abort(ServerPlayerEntity player) {
        if (ACTIVE.remove(player.getUuid()) != null) {
            finish(player, false);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (!player.isAlive() || player.isRemoved()) {
                finish(player, false);
                it.remove();
                continue;
            }
            int t = entry.getValue() + 1;
            entry.setValue(t);
            step(player, t);
            if (t >= Timeline.TOTAL) {
                finish(player, true);
                it.remove();
            }
        }
    }

    private static void step(ServerPlayerEntity p, int t) {
        ServerWorld w = p.getServerWorld();
        p.fallDistance = 0;
        p.setNoGravity(true);

        // กันตายระหว่าง cutscene (Resistance 5 = ไม่โดนดาเมจปกติ)
        if (t % 10 == 1) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 4, false, false, false));
        }

        switch (t) {
            case 1 -> sound(p, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.7f);
            case 40 -> sound(p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 2.0f, 0.6f);
            case 70 -> sound(p, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2.5f, 0.8f);
            case 110 -> sound(p, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            case Timeline.BURST -> burst(p, w);
            default -> { }
        }
    }

    private static void burst(ServerPlayerEntity p, ServerWorld w) {
        sound(p, SoundEvents.ENTITY_GENERIC_EXPLODE, 4.0f, 0.6f);
        sound(p, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.8f);
        w.spawnParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 2, 1.5, 1.0, 1.5, 0);

        AscensionConfig cfg = AscensionConfig.get();
        double r = cfg.radius;
        Box box = p.getBoundingBox().expand(r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x != p && x.isAlive())) {
            if (e.squaredDistanceTo(p) > r * r) continue;
            e.damage(w.getDamageSources().indirectMagic(p, p), cfg.damage);
            e.takeKnockback(1.4, p.getX() - e.getX(), p.getZ() - e.getZ());
            e.setOnFireFor(4);
        }
    }

    private static void finish(ServerPlayerEntity p, boolean cooldown) {
        p.setNoGravity(false);
        p.fallDistance = 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false, false));
        broadcast(p, false);
        if (cooldown) {
            COOLDOWN.put(p.getUuid(), p.getServerWorld().getTime() + AscensionConfig.get().cooldownTicks);
        }
    }

    private static void sound(ServerPlayerEntity p, SoundEvent s, float vol, float pitch) {
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundCategory.PLAYERS, vol, pitch);
    }

    /** ส่ง START/END ให้ผู้เล่นทุกคนที่เห็นตัวนี้ + ตัวเอง */
    private static void broadcast(ServerPlayerEntity p, boolean start) {
        Set<ServerPlayerEntity> targets = new HashSet<>(PlayerLookup.tracking(p));
        targets.add(p);
        for (ServerPlayerEntity target : targets) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(p.getUuid());
            if (start) {
                buf.writeDouble(p.getX());
                buf.writeDouble(p.getY());
                buf.writeDouble(p.getZ());
            }
            ServerPlayNetworking.send(target, start ? Net.START : Net.END, buf);
        }
    }
}
