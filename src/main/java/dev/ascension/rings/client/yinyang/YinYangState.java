package dev.ascension.rings.client.yinyang;

import dev.ascension.rings.YinYangTimeline;
import dev.ascension.rings.Timeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
        /** เวลาที่มังกรใช้พุ่งไปเป้า (tick) */
        public int fly = YinYangTimeline.FLY_MIN;
        /** tick ที่ได้รับ END / -1 = ยังไม่จบ */
        public int endAt = -1;
        /** กำลังพุ่งเข้าไปในคัมภีร์ (Shift+J) */
        public boolean diving = false;

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

    public static void strike(UUID id, Vec3d target, double radius, int fly) {
        Instance inst = ACTIVE.get(id);
        if (inst == null) return;
        inst.target = target;
        inst.radius = radius;
        inst.fly = fly;
        inst.strikeAt = inst.ticks;
    }

    public static void end(UUID id) {
        Instance inst = ACTIVE.get(id);
        if (inst != null && inst.endAt < 0) inst.endAt = inst.ticks;
    }

    public static void clear() {
        ACTIVE.clear();
        inRealm = false;
        flash = 0;
    }

    // ---------- โลกหยินหยาง ----------

    /** ผู้เล่นอยู่ในโลกหยินหยาง (ตั้งค่าทุก tick จากมิติของ ClientWorld) */
    public static volatile boolean inRealm = false;
    /** tick ตั้งแต่เข้าโลกหยินหยาง */
    public static int realmTicks = 0;
    /** แฟลชขาวตอนเข้า/ออกโลก: นับถอยหลัง (FLASH_MAX..0) */
    public static int flash = 0;
    public static final int FLASH_MAX = 50;

    /** เซิร์ฟเวอร์แจ้ง: 0 = เริ่มพุ่งเข้าคัมภีร์, 1 = เข้าโลกแล้ว, 2 = ออกจากโลกแล้ว */
    public static void realmEvent(int mode) {
        if (mode == 0) {
            Instance i = local();
            if (i != null) i.diving = true;
        } else {
            ACTIVE.clear();
            flash = FLASH_MAX;
            if (mode == 1) realmTicks = 0;
        }
    }

    /** 0..1 ความขาวของหน้าจอตอนพุ่งเข้าใกล้คัมภีร์ */
    public static float diveWhite(float td) {
        Instance i = local();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (i == null || !i.diving || mc.player == null) return 0f;
        double d = mc.player.getPos().add(0, 1, 0).distanceTo(i.book);
        float k = (float) YinYangTimeline.clamp01(1.0 - (d - 4.5) / 22.0);
        return k * k;
    }

    /** ตำแหน่งหัวมังกรยักษ์ในโลกหยินหยาง: วนรอบแท่น ขาว/ดำ ไล่กันแบบไท่จี๋ */
    public static Vec3d realmHead(int i, double t) {
        double ang = t * 0.0105 + i * Math.PI;
        double rad = 100.0 + 9.0 * Math.sin(t * 0.017 + i * 1.7);
        double y = 64.0 + 58.0 + 20.0 * Math.sin(t * 0.013 + i * Math.PI);
        return new Vec3d(0.5 + Math.cos(ang) * rad, y, 0.5 + Math.sin(ang) * rad);
    }

    // ---------- queries ----------

    private static Instance local() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : ACTIVE.get(mc.player.getUuid());
    }

    public static boolean isLocalActive() {
        return local() != null;
    }

    /** ผู้เล่นเองมีมังกรโคจรรอคำสั่งอยู่ (กดครั้งที่ 2 ได้) */
    public static boolean isLocalOrbiting() {
        Instance i = local();
        return i != null && i.strikeAt < 0 && i.endAt < 0 && i.ticks >= YinYangTimeline.READY;
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

    /** เวลาบินของมังกร (tick) ของผู้เล่นเอง */
    public static int localFly() {
        Instance i = local();
        return i == null ? YinYangTimeline.FLY_MIN : i.fly;
    }

    /** จุดที่เล็งอยู่ตอนนี้ (อัปเดตทุกเฟรมโดย renderer) ใช้แสดงระยะบน HUD */
    public static volatile Vec3d aimNow = null;

    /**
     * จุดที่เมาส์/crosshair ชี้ไป ไกลสุด AIM_MAX บล็อก:
     * ยิง ray จากกล้อง ถ้าชนบล็อก = จุดนั้น, ถ้าไม่ชน (ท้องฟ้า/ไกลเกิน) = เดิน ray ไปหาผิวภูมิประเทศที่โหลดอยู่
     */
    public static Vec3d computeAim(MinecraftClient mc, float td) {
        if (mc.player == null || mc.world == null) return null;
        ClientWorld w = mc.world;
        Vec3d eye = mc.player.getCameraPosVec(td);
        Vec3d look = mc.player.getRotationVec(td);
        double max = YinYangTimeline.AIM_MAX;
        HitResult h = w.raycast(new RaycastContext(eye, eye.add(look.multiply(max)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (h.getType() == HitResult.Type.BLOCK) return h.getPos();
        for (double d = 3.0; d <= max; d += 3.0) {
            Vec3d p = eye.add(look.multiply(d));
            int bx = (int) Math.floor(p.x), bz = (int) Math.floor(p.z);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) break;
            double top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
            if (p.y <= top) return new Vec3d(p.x, top, p.z);
        }
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        double x = eye.x + flat.x * 80, z = eye.z + flat.z * 80;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double y = w.isChunkLoaded(bx >> 4, bz >> 4) ? w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz) : mc.player.getY();
        return new Vec3d(x, y, z);
    }

    /** 0..1 ความเป็นขาวดำ (เทา) ของท้องฟ้าหลังระเบิด — มองจาก instance ใดก็ได้ที่ใกล้ที่สุด */
    public static float skyTone(float tickDelta) {
        float best = 0f;
        for (Instance i : ACTIVE.values()) {
            if (i.strikeAt < 0) continue;
            float k = i.ticks - i.strikeAt + tickDelta - i.fly;
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

    /**
     * ระนาบหน้าคัมภีร์ (ใช้ตัดตัวมังกรตอนพุ่งออกมา และเป็นทิศหันหน้าของเล่ม): ชี้จากคัมภีร์เข้าหาผู้ร่าย
     * ใช้ตำแหน่งตอนเริ่ม (นิ่ง) ไม่ใช่ตำแหน่งปัจจุบัน และกดแนวตั้งลงครึ่งหนึ่งให้เล่มตั้งตรง
     */
    public static Vec3d pageNormal(Instance inst, Vec3d cp) {
        Vec3d d = inst.start.add(0, 6, 0).subtract(inst.book);
        d = new Vec3d(d.x, d.y * 0.35, d.z);
        return d.lengthSquared() < 1.0e-6 ? new Vec3d(0, 0, 1) : d.normalize();
    }

    private static Vec3d bezier2(Vec3d p0, Vec3d p1, Vec3d p2, double e) {
        double a = (1 - e) * (1 - e), b = 2 * (1 - e) * e, c = e * e;
        return p0.multiply(a).add(p1.multiply(b)).add(p2.multiply(c));
    }

    private static Vec3d bezier3(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double x) {
        double a = 1 - x;
        return p0.multiply(a * a * a).add(p1.multiply(3 * a * a * x)).add(p2.multiply(3 * a * x * x)).add(p3.multiply(x * x * x));
    }

    /** เส้นทางของหัวมังกร i ที่เวลา t: ลอดออกจากหน้าคัมภีร์ -> วนเข้าวงโคจร -> (กดครั้งที่ 2) พุ่งไปเป้า */
    public static Vec3d headPos(Instance inst, int i, double t, Vec3d cp) {
        if (inst.strikeAt >= 0 && t >= inst.strikeAt) {
            double u = YinYangTimeline.clamp01((t - inst.strikeAt) / inst.fly);
            double e = Math.pow(u, 1.7);
            Vec3d p0 = YinYangTimeline.orbitPos(i, inst.strikeAt, cp);
            Vec3d tg = inst.target.add(0, 3, 0);
            Vec3d dir = tg.subtract(p0);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            side = side.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : side.normalize();
            side = side.multiply(i == 0 ? 14.0 : -14.0);
            Vec3d ctrl = p0.add(tg).multiply(0.5).add(0, 22, 0).add(side);
            return bezier2(p0, ctrl, tg, e);
        }
        int st = YinYangTimeline.EMERGE_START[i];
        double d = YinYangTimeline.EMERGE_FLIGHT;
        if (t >= st + d) return YinYangTimeline.orbitPos(i, t, cp);

        Vec3d n = pageNormal(inst, cp);
        Vec3d p0 = inst.book.subtract(n.multiply(10));
        Vec3d lat = n.crossProduct(new Vec3d(0, 1, 0));
        lat = lat.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : lat.normalize();
        // จุดควบคุมโค้งเบี่ยงข้างตัวละคร (มังกรขาว/ดำเบี่ยงคนละฝั่ง) ให้ความเร็วสม่ำเสมอ ไม่ชะลอจนหยุด
        Vec3d p1 = inst.book.add(n.multiply(40)).add(lat.multiply(i == 0 ? -20.0 : 20.0)).add(0, 15, 0);
        Vec3d p3 = YinYangTimeline.orbitPos(i, st + d, cp);
        Vec3d vel = YinYangTimeline.orbitPos(i, st + d + 1, cp).subtract(p3);
        Vec3d p2 = p3.subtract(vel.multiply(30));
        double x = (t - st) / d;
        if (x < 0) {
            // ก่อนเริ่มโผล่ ให้ลำตัวต่อเส้นตรงถอยหลังเข้าไปหลังหน้าคัมภีร์ (ถูกตัดซ่อนไว้)
            Vec3d dir = p1.subtract(p0).normalize();
            double speed0 = 3.0 * p1.subtract(p0).length() / d;
            return p0.subtract(dir.multiply(-x * d * speed0));
        }
        return bezier3(p0, p1, p2, p3, x);
    }

    // ---------- per tick ----------

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) {
            if (!ACTIVE.isEmpty() || inRealm) clear();
            return;
        }
        inRealm = dev.ascension.rings.YinYangRealm.isRealm(world);
        if (flash > 0) flash--;
        if (inRealm) {
            realmTicks++;
            realmParticles(world, mc);
        }
        if (ACTIVE.isEmpty()) return;
        Iterator<Instance> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Instance inst = it.next();
            inst.ticks++;
            PlayerEntity p = world.getPlayerByUuid(inst.uuid);
            spawnParticles(world, inst, p);
            if (inst.endAt >= 0 && inst.ticks - inst.endAt > YinYangTimeline.FADE + 5) it.remove();
        }
    }

    private static void realmParticles(ClientWorld w, MinecraftClient mc) {
        if (mc.player == null) return;
        Vec3d pp = mc.player.getPos();
        for (int i = 0; i < 3; i++) {
            boolean white = RNG.nextBoolean();
            w.addParticle(white ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK,
                    pp.x + (RNG.nextDouble() - 0.5) * 60, pp.y + RNG.nextDouble() * 26 - 2, pp.z + (RNG.nextDouble() - 0.5) * 60,
                    0, 0.03 + RNG.nextDouble() * 0.04, 0);
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
            if (t >= st - 4 && t < st + YinYangTimeline.EMERGE_FX) {
                for (int k = 0; k < 14; k++) {
                    double ox = (RNG.nextDouble() - 0.5) * 10, oy = (RNG.nextDouble() - 0.5) * 10;
                    w.addParticle(ParticleTypes.SPLASH, inst.book.x + ox, inst.book.y + oy, inst.book.z + (RNG.nextDouble() - 0.5) * 4,
                            toward.x * 0.9 + (RNG.nextDouble() - 0.5) * 0.4, toward.y * 0.9 + RNG.nextDouble() * 0.5,
                            toward.z * 0.9 + (RNG.nextDouble() - 0.5) * 0.4);
                }
            }
        }

        // ประกายตามตัวมังกร (ขาว = END_ROD, ดำ = SQUID_INK)
        if (t >= YinYangTimeline.EMERGE_START[0] + 30) {
            for (int i = 0; i < 2; i++) {
                if (t < YinYangTimeline.EMERGE_START[i] + 30) continue;
                if (inst.strikeAt >= 0 && t - inst.strikeAt > inst.fly) continue;
                Vec3d dp = headPos(inst, i, t, cp);
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
            if (s >= inst.fly && s <= inst.fly + YinYangTimeline.WAVE) {
                double r = YinYangTimeline.waveRadius(s, inst.fly, inst.radius);
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
