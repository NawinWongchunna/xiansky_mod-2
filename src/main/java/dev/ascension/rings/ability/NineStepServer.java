package dev.ascension.rings.ability;

import dev.ascension.rings.AscensionConfig;
import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.ModSounds;
import dev.ascension.rings.NineStepTimeline;
import dev.ascension.rings.network.Net;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * สกิล V "Nine-Step Void Walk / เก้าก้าวย่างอากาศ"
 *  แหวกมิติตรงหน้า -> ก้าวบนอากาศ 9 ก้าว (ไต่ขึ้นเป็นบันได) -> พุ่งไกลไปยังจุดที่เล็ง/พิกัดที่สั่ง -> ลงจอดพร้อมคลื่นกระแทก
 *  ถ้าไกลเกินระยะบินจริง (หรือข้ามมิติ) จะพุ่งเข้ารอยแหวกแล้ววาร์ปไปโผล่ที่ปลายทาง
 */
public final class NineStepServer {
    private static final class Run {
        int t = 0;
        Vec3d start, dir, end, landing;
        boolean jump;
        int dashT;
        int tEnd;
        net.minecraft.registry.RegistryKey<World> srcDim, destDim;
        boolean wasInvulnerable;
        boolean turned = false;
    }

    private static final Map<UUID, Run> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /** ระดับเสียงระฆังของก้าว 1..9 (บันไดเสียง pentatonic; ฐาน 0.62 เพื่อไม่เกินเพดาน pitch 2.0 ของเกม) */
    private static final float[] STEP_PITCH = {0.62f, 0.70f, 0.78f, 0.93f, 1.04f, 1.24f, 1.39f, 1.56f, 1.86f};

    private NineStepServer() {}

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    // ------------------------------------------------------------------ เริ่มสกิล

    /** ปุ่ม V: aim = จุดที่เล็งตามเมาส์ (null = พุ่งไปข้างหน้าตามที่หันหน้า) */
    public static void press(ServerPlayerEntity p, Vec3d aim) {
        AscensionConfig cfg = AscensionConfig.get();
        Vec3d pos = p.getPos();
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();

        Vec3d dest;
        if (aim != null && Double.isFinite(aim.x) && Double.isFinite(aim.y) && Double.isFinite(aim.z)) {
            Vec3d d = new Vec3d(aim.x - pos.x, 0, aim.z - pos.z);
            double hd = d.length();
            Vec3d u = hd > 0.5 ? d.multiply(1.0 / hd) : flat;
            double use = Math.max(cfg.nineStepMinDash, Math.min(cfg.nineStepMaxRange, hd));
            dest = new Vec3d(pos.x + u.x * use, aim.y, pos.z + u.z * use);
        } else {
            dest = new Vec3d(pos.x + flat.x * Math.max(96.0, cfg.nineStepMinDash), pos.y, pos.z + flat.z * Math.max(96.0, cfg.nineStepMinDash));
        }
        begin(p, dest, p.getServerWorld());
    }

    /** คำสั่ง /lift และ API ภายใน */
    public static boolean begin(ServerPlayerEntity p, Vec3d dest, ServerWorld destWorld) {
        UUID id = p.getUuid();
        if (ACTIVE.containsKey(id) || AscensionServer.isActive(id) || SwordServer.isActive(id) || YinYangServer.isActive(id)
                || !p.isAlive() || p.isSpectator()) return false;
        ServerWorld sw = p.getServerWorld();
        long now = sw.getTime();
        Long until = COOLDOWN.get(id);
        if (until != null && now < until) {
            p.sendMessage(Text.translatable("message.ascension_rings.cooldown", (until - now) / 20 + 1).formatted(Formatting.RED), true);
            return false;
        }
        AscensionConfig cfg = AscensionConfig.get();

        Vec3d start = p.getPos();
        Vec3d landing = safeSpot(destWorld, dest.x, dest.y, dest.z);
        boolean sameDim = sw == destWorld;
        Vec3d to = landing.subtract(start);
        double hd = Math.hypot(to.x, to.z);
        Vec3d dir;
        if (sameDim && hd > 1.0) {
            dir = new Vec3d(to.x / hd, 0, to.z / hd);
        } else {
            Vec3d look = p.getRotationVec(1.0f);
            dir = new Vec3d(look.x, 0, look.z);
            dir = dir.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : dir.normalize();
        }

        Run run = new Run();
        run.start = start;
        run.dir = dir;
        run.landing = landing;
        run.srcDim = sw.getRegistryKey();
        run.destDim = destWorld.getRegistryKey();
        run.jump = !sameDim || to.length() > cfg.nineStepMaxFlight;
        if (run.jump) {
            run.end = NineStepTimeline.riftFoot(start, dir);
            run.dashT = NineStepTimeline.JUMP_DASH;
        } else {
            run.end = landing;
            Vec3d s9 = NineStepTimeline.stair(start, dir, NineStepTimeline.STEPS);
            run.dashT = NineStepTimeline.dashTicks(landing.distanceTo(s9));
        }
        run.tEnd = NineStepTimeline.dashStart() + run.dashT;
        run.wasInvulnerable = p.isInvulnerable();
        ACTIVE.put(id, run);

        p.setInvulnerable(true);
        p.setNoGravity(true);
        broadcast(p, run);
        sound(sw, start.add(0, 1, 0), ModSounds.NS_RIFT, 5f, 1f);
        AscensionMod.LOGGER.info("Nine-Step by {}: {} -> {} (jump={})", p.getName().getString(), start, landing, run.jump);
        return true;
    }

    // ------------------------------------------------------------------ tick

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
            if (!p.isAlive() || p.isRemoved()) {
                restore(p, run);
                it.remove();
                continue;
            }
            run.t++;
            ServerWorld w = p.getServerWorld();
            int t = run.t;

            // ท่าทาง/เสียงตามจังหวะ
            if (t >= NineStepTimeline.LEAD && t < NineStepTimeline.stepsEnd() && (t - NineStepTimeline.LEAD) % NineStepTimeline.STEP == 0) {
                int i = (t - NineStepTimeline.LEAD) / NineStepTimeline.STEP;
                sound(w, p.getPos().add(0, 1, 0), ModSounds.NS_BELL, 3.5f, STEP_PITCH[i]);
            }
            if (t == NineStepTimeline.stepsEnd()) {
                sound(w, p.getPos().add(0, 1, 0), ModSounds.NS_BELL, 4f, STEP_PITCH[8]);
                sound(w, p.getPos().add(0, 1, 0), ModSounds.NS_CHOIR, 5f, 1f);
            }
            if (t == NineStepTimeline.dashStart()) {
                sound(w, p.getPos().add(0, 1, 0), ModSounds.NS_DASH, 8f, 1f);
                sound(w, p.getPos().add(0, 1, 0), ModSounds.WHOOSH, 6f, 1.5f);
            }

            // การเคลื่อนที่ทำฝั่ง client ตามสูตรเวลาเดียวกัน (กล้องหมุนอิสระ ไม่มีแพ็กเก็ตเทเลพอร์ตรัวๆ)
            // server แค่ปิดการชนบล็อกฝั่ง server เพื่อไม่ให้เด้งกลับ "moved wrongly" ตอนพุ่งผ่านภูมิประเทศ
            p.noClip = true;
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0;

            if (t >= run.tEnd) {
                land(server, p, run);
                it.remove();
            }
        }
    }

    private static void land(MinecraftServer server, ServerPlayerEntity p, Run run) {
        ServerWorld dw = server.getWorld(run.destDim);
        if (dw == null) dw = p.getServerWorld();
        Vec3d l = run.landing;
        if (run.jump) {
            p.teleport(dw, l.x, l.y, l.z, p.getYaw(), p.getPitch());
        } else {
            // ปกติ client พามาถึงจุดลงจอดเองแล้ว ถ้าคลาดเกิน 6 บล็อก (แลค) ค่อยดึงเข้าที่
            if (p.getPos().distanceTo(l) > 6.0) {
                p.networkHandler.requestTeleport(l.x, l.y, l.z, 0f, 0f, EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT));
            }
        }
        restore(p, run);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false, false));
        COOLDOWN.put(p.getUuid(), dw.getTime() + AscensionConfig.get().nineStepCooldownTicks);

        sound(dw, l.add(0, 1, 0), ModSounds.NS_BOOM, 8f, 1f);
        sound(dw, l.add(0, 1, 0), ModSounds.NS_BELL, 5f, 1.86f);
        float dmg = AscensionConfig.get().nineStepDamage;
        Box box = new Box(l, l).expand(8, 4, 8);
        for (LivingEntity e : dw.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && x != p)) {
            double dx = e.getX() - l.x, dz = e.getZ() - l.z;
            if (dmg > 0) e.damage(dw.getDamageSources().indirectMagic(p, p), dmg);
            e.takeKnockback(1.6, -dx, -dz);
        }
    }

    public static void abort(ServerPlayerEntity p) {
        Run run = ACTIVE.remove(p.getUuid());
        if (run != null) {
            restore(p, run);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(p.getUuid());
            for (ServerPlayerEntity t : PlayerLookup.around(p.getServerWorld(), p.getPos(), 400.0)) {
                ServerPlayNetworking.send(t, Net.NS_END, PacketByteBufs.copy(buf));
            }
        }
    }

    private static void restore(ServerPlayerEntity p, Run run) {
        p.noClip = p.isSpectator();
        p.setNoGravity(false);
        p.setInvulnerable(run.wasInvulnerable);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ จุดลงจอดปลอดภัย

    /** หาที่ยืนได้ใกล้ (x,y,z) ที่สุด: ช่องว่าง 2 บล็อกมีพื้นแข็งรอง ไม่ใช่ของเหลว; ไม่เจอใช้ผิวสูงสุดของ chunk */
    static Vec3d safeSpot(ServerWorld w, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        w.getChunk(bx >> 4, bz >> 4); // โหลด chunk ปลายทาง (ถ้ายังไม่โหลด)
        int minY = w.getBottomY() + 1, maxY = w.getTopY() - 3;
        int by = (int) Math.floor(y);
        for (int d = 0; d <= 24; d++) {
            for (int sg = 0; sg < 2; sg++) {
                if (d == 0 && sg == 1) continue;
                int yy = by + (sg == 0 ? d : -d);
                if (yy < minY || yy > maxY) continue;
                if (standable(w, bx, yy, bz)) return new Vec3d(bx + 0.5, yy, bz + 0.5);
            }
        }
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
        return new Vec3d(bx + 0.5, Math.max(top, minY), bz + 0.5);
    }

    private static boolean standable(ServerWorld w, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z), head = feet.up(), below = feet.down();
        BlockState bf = w.getBlockState(feet), bh = w.getBlockState(head), bb = w.getBlockState(below);
        return bf.getCollisionShape(w, feet).isEmpty() && bf.getFluidState().isEmpty()
                && bh.getCollisionShape(w, head).isEmpty() && bh.getFluidState().isEmpty()
                && !bb.getCollisionShape(w, below).isEmpty() && bb.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------ เครือข่าย / เสียง

    private static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.MASTER, vol, pitch);
    }

    private static void writeVec(PacketByteBuf buf, Vec3d v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static void broadcast(ServerPlayerEntity p, Run run) {
        Set<ServerPlayerEntity> targets = new HashSet<>(PlayerLookup.around(p.getServerWorld(), run.start, 400.0));
        MinecraftServer server = p.getServer();
        if (server != null) {
            ServerWorld dw = server.getWorld(run.destDim);
            if (dw != null) targets.addAll(PlayerLookup.around(dw, run.landing, 400.0));
        }
        targets.add(p);
        for (ServerPlayerEntity t : targets) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(p.getUuid());
            buf.writeIdentifier(run.srcDim.getValue());
            buf.writeIdentifier(run.destDim.getValue());
            writeVec(buf, run.start);
            writeVec(buf, run.dir);
            writeVec(buf, run.end);
            writeVec(buf, run.landing);
            buf.writeInt(run.dashT);
            buf.writeBoolean(run.jump);
            ServerPlayNetworking.send(t, Net.NS_START, buf);
        }
    }
}
