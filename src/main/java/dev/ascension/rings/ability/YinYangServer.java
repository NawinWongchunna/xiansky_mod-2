package dev.ascension.rings.ability;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.ModSounds;
import dev.ascension.rings.YinYangTimeline;
import dev.ascension.rings.network.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * สกิล J "Yin-Yang Scripture"
 *  กดครั้งแรก: โยนคัมภีร์ขึ้นฟ้า -> มังกรขาว/ดำพุ่งออกมาโคจรรอบตัว
 *  กดอีกครั้ง: มังกรพุ่งไปยังจุดที่เล็ง -> ระเบิดขาวดำ ทำลายทุกอย่างในรัศมี 100 บล็อก พื้นกลายเป็นลายหยินหยาง
 */
public final class YinYangServer {
    private static final class Run {
        int t = 0;
        int s = -1; // tick นับจากกดครั้งที่ 2 (-1 = ยังไม่กด)
        Vec3d start;
        Vec3d book;
        Vec3d target;
    }

    private static final Map<UUID, Run> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private YinYangServer() {}

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    /** ใช้ร่วมกันทั้งปุ่ม J และคำสั่ง /yinyang: ยังไม่เริ่ม = เริ่ม, กำลังโคจร = สั่งโจมตี */
    public static void press(ServerPlayerEntity p) {
        Run run = ACTIVE.get(p.getUuid());
        if (run == null) start(p);
        else strike(p, run);
    }

    private static void start(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (AscensionServer.isActive(id) || SwordServer.isActive(id) || !p.isAlive() || p.isSpectator()) return;
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Long until = COOLDOWN.get(id);
        if (until != null && now < until) {
            p.sendMessage(Text.translatable("message.ascension_rings.cooldown", (until - now) / 20 + 1).formatted(Formatting.RED), true);
            return;
        }
        AscensionMod.LOGGER.info("Yin-Yang Scripture start by {}", p.getName().getString());

        Run run = new Run();
        run.start = p.getPos();
        run.book = YinYangTimeline.bookPos(run.start, p.getRotationVec(1.0f));
        ACTIVE.put(id, run);
        broadcast(p, run, Net.YY_START);
        sound(w, run.start.add(0, 2, 0), ModSounds.CHARGE, 3f, 1f);
    }

    private static void strike(ServerPlayerEntity p, Run run) {
        if (run.s >= 0) return;
        if (run.t < YinYangTimeline.READY) {
            p.sendMessage(Text.translatable("message.ascension_rings.yinyang_not_ready").formatted(Formatting.GRAY), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        Vec3d target;
        HitResult hit = p.raycast(160.0, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            target = hit.getPos();
        } else {
            Vec3d look = p.getRotationVec(1.0f);
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
            double x = p.getX() + flat.x * 60, z = p.getZ() + flat.z * 60;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            double y = w.isChunkLoaded(bx >> 4, bz >> 4) ? w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz) : p.getY();
            target = new Vec3d(x, y, z);
        }
        run.target = target;
        run.s = 0;
        AscensionMod.LOGGER.info("Yin-Yang strike by {} at {}", p.getName().getString(), target);
        broadcast(p, run, Net.YY_STRIKE);
        sound(w, p.getPos().add(0, 3, 0), ModSounds.WHOOSH, 4f, 1f);
        sound(w, p.getPos().add(0, 3, 0), ModSounds.CHARGE, 4f, 1.2f);
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
            if (run.s >= 0) run.s++;
            step(w, p, run);
            boolean done = run.s >= YinYangTimeline.STRIKE_END || (run.s < 0 && run.t >= YinYangTimeline.ORBIT_MAX);
            if (done) {
                finish(w, p, run, true);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------

    private static void step(ServerWorld w, ServerPlayerEntity p, Run run) {
        int t = run.t;
        Vec3d b = run.book;

        if (t == YinYangTimeline.THROW_END) sound(w, b, ModSounds.WHOOSH, 8f, 0.7f);
        if (t == YinYangTimeline.OPEN_START) sound(w, b, ModSounds.SCRIPTURE, 10f, 1f);
        if (t == YinYangTimeline.EMERGE_START[0]) {
            sound(w, b, ModSounds.DRAGON_WHITE, 12f, 1f);
            sound(w, b, ModSounds.WHOOSH, 8f, 0.9f);
        }
        if (t == YinYangTimeline.EMERGE_START[1]) {
            sound(w, b, ModSounds.DRAGON_BLACK, 12f, 1f);
            sound(w, b, ModSounds.WHOOSH, 8f, 0.7f);
        }
        if (t == YinYangTimeline.READY) sound(w, p.getPos().add(0, 2, 0), ModSounds.GONG, 3f, 1.2f);

        if (run.s < 0) return;
        int s = run.s;
        if (s == 1) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false, false));
        }
        if (s == YinYangTimeline.FLY) impact(w, p, run);
        if (s >= YinYangTimeline.FLY && s <= YinYangTimeline.FLY + YinYangTimeline.WAVE) wave(w, p, run);
    }

    private static void impact(ServerWorld w, ServerPlayerEntity caster, Run run) {
        Vec3d c = run.target;
        sound(w, c, ModSounds.LIGHT_BURST, 24f, 1f);
        sound(w, c, ModSounds.EXPLOSION, 28f, 1f);
        sound(w, c, ModSounds.GONG, 24f, 0.8f);
        w.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 3, c.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 3, c.z, 5, 5, 2, 5, 0);
    }

    /** คลื่นทำลายล้างหยินหยาง: วงแหวนที่ขยายตัว ทำลายทุกอย่าง แล้วทาพื้นเป็นลายก้นหอยขาว-ดำ */
    private static void wave(ServerWorld w, ServerPlayerEntity caster, Run run) {
        AscensionConfig cfg = AscensionConfig.get();
        double maxR = cfg.yinYangRadius;
        double r1 = Math.max(6.0, YinYangTimeline.waveRadius(run.s, maxR));
        double r0 = run.s == YinYangTimeline.FLY ? -1.0 : YinYangTimeline.waveRadius(run.s - 1, maxR);
        if (r1 <= r0) return;

        Vec3d c = run.target;
        double r0s = r0 < 0 ? -1.0 : r0 * r0;
        double r1s = r1 * r1;
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        int rr = (int) Math.ceil(r1);

        if (cfg.destroyBlocks) {
            BlockPos.Mutable m = new BlockPos.Mutable();
            int minY = w.getBottomY();
            int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
            BlockState air = Blocks.AIR.getDefaultState();
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 < r0s || d2 >= r1s) continue;
                    int x = cx + dx, z = cz + dz;
                    double ex = x + 0.5 - caster.getX(), ez = z + 0.5 - caster.getZ();
                    if (ex * ex + ez * ez <= 6.25) continue; // เว้นเสาใต้เท้าผู้ร่าย
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

                    double d = Math.sqrt(d2);
                    double near = Math.max(0.0, 1.0 - d / maxR);
                    int depth = 3 + (int) Math.round(9.0 * Math.pow(near, 1.3)) + (d < 10 ? 10 : 0);

                    int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                    int removed = 0, guard = 0;
                    boolean stopped = false;
                    while (y >= minY && guard++ < 420) {
                        m.set(x, y, z);
                        BlockState s = w.getBlockState(m);
                        if (s.isAir()) {
                            y--;
                            continue;
                        }
                        if (s.getBlock() instanceof FluidBlock || s.getHardness(w, m) < 0) {
                            stopped = true;
                            break;
                        }
                        // ต้นไม้ ใบไม้ หญ้า ดอกไม้ ฯลฯ ถูกทำลายหมด ไม่นับเป็นความลึก
                        boolean free = s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.getCollisionShape(w, m).isEmpty();
                        if (!free && removed >= depth) {
                            stopped = true;
                            break;
                        }
                        w.setBlockState(m, air, flags);
                        if (!free) removed++;
                        y--;
                    }
                    // ทาผิวใหม่เป็นลายหยินหยาง (ก้นหอยขาว/ดำ)
                    if (cfg.yinYangPaintGround && stopped && removed > 0) {
                        m.set(x, y, z);
                        BlockState s = w.getBlockState(m);
                        if (!s.isAir() && !(s.getBlock() instanceof FluidBlock) && s.getHardness(w, m) >= 0) {
                            double ang = Math.atan2(dz, dx);
                            boolean white = Math.sin(ang + d * 0.06) > 0;
                            w.setBlockState(m, (white ? Blocks.WHITE_CONCRETE : Blocks.BLACK_CONCRETE).getDefaultState(), flags);
                        }
                    }
                }
            }
        }

        // ดาเมจสิ่งมีชีวิตที่คลื่นผ่าน
        Box box = new Box(c.x - r1, w.getBottomY(), c.z - r1, c.x + r1, w.getTopY(), c.z + r1);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && x != caster)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < r0s || d2 >= r1s) continue;
            e.damage(w.getDamageSources().indirectMagic(caster, caster), cfg.yinYangDamage);
            e.takeKnockback(2.5, -dx, -dz);
        }
    }

    private static void finish(ServerWorld w, ServerPlayerEntity p, Run run, boolean cooldown) {
        broadcast(p, run, Net.YY_END);
        if (cooldown) COOLDOWN.put(p.getUuid(), w.getTime() + AscensionConfig.get().yinYangCooldownTicks);
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.MASTER, vol, pitch);
    }

    /** ส่ง START / STRIKE / END ให้ผู้เล่นทุกคนในระยะ (รวมผู้ร่าย) */
    private static void broadcast(ServerPlayerEntity p, Run run, net.minecraft.util.Identifier channel) {
        Set<ServerPlayerEntity> targets = new HashSet<>(PlayerLookup.around(p.getServerWorld(), run.book, 400.0));
        if (run.target != null) targets.addAll(PlayerLookup.around(p.getServerWorld(), run.target, 400.0));
        targets.add(p);
        for (ServerPlayerEntity t : targets) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(p.getUuid());
            if (channel == Net.YY_START) {
                writeVec(buf, run.start);
                writeVec(buf, run.book);
            } else if (channel == Net.YY_STRIKE) {
                writeVec(buf, run.target);
                buf.writeDouble(AscensionConfig.get().yinYangRadius);
            }
            ServerPlayNetworking.send(t, channel, buf);
        }
    }

    private static void writeVec(PacketByteBuf buf, Vec3d v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }
}
