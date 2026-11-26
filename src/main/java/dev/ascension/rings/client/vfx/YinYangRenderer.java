package dev.ascension.rings.client.vfx;

import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.YinYangTimeline;
import dev.ascension.rings.client.yinyang.YinYangState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.function.DoubleFunction;

/**
 * VFX สกิล J:
 *  - คัมภีร์หยินหยาง (sprite): โยนขึ้นฟ้าพร้อมหมุน ขยายใหญ่ไปอยู่ด้านหลังผู้เล่น แล้วเปิดหน้า (สมูท: easing แบบ smootherstep + ครอสเฟดเฟรม)
 *  - มังกรขาว/ดำ เป็นโมเดล 3 มิติ (DragonModel) พุ่งออกจากหน้าคัมภีร์โดยตัดด้วยระนาบ + วงน้ำ + หยดน้ำ
 *  - ปากั้ว ใต้เท้า / ใต้จุดเล็ง, ตัวเล็งตามเมาส์, ระเบิดหยินหยาง เสาแสง แฟลร์ วงคลื่นบนพื้น
 */
public final class YinYangRenderer {
    private static final Identifier[] BOOK = arr("scripture_%02d", 8);
    private static final Identifier[] BURST = arr("burst_%02d", 12);
    private static final Identifier[] PILLAR = arr("light_pillar_%02d", 8);
    private static final Identifier[] BAGUA = arr("bagua_ring_%02d", 8);
    private static final Identifier[] RIPPLE = arr("ripple_%02d", 8);
    private static final Identifier FLARE_W = yy("flare_white");
    private static final Identifier FLARE_D = yy("flare_dark");
    private static final Identifier DROP = yy("droplet");
    private static final Identifier EDGE = yy("ring_edge");

    private YinYangRenderer() {}

    private static Identifier yy(String name) {
        return new Identifier(AscensionMod.MOD_ID, "textures/vfx/yy/" + name + ".png");
    }

    private static Identifier[] arr(String pattern, int n) {
        Identifier[] a = new Identifier[n];
        for (int i = 0; i < n; i++) a[i] = yy(String.format(pattern, i));
        return a;
    }

    public static void render(WorldRenderContext ctx) {
        if (YinYangState.ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        float td = ctx.tickDelta();
        AscensionRenderer.Frame f = AscensionRenderer.makeFrame(ctx);
        AscensionRenderer.beginWorldState();

        // จุดเล็งตามเมาส์ (เฉพาะตัวเราเอง ตอนมังกรโคจรรอคำสั่ง)
        Vec3d aim = null;
        if (YinYangState.isLocalOrbiting()) {
            aim = YinYangState.computeAim(mc, td);
        }
        YinYangState.aimNow = aim;

        for (YinYangState.Instance inst : YinYangState.ACTIVE.values()) {
            float t = inst.ticks + td;
            float out = inst.endAt < 0 ? 1f : 1f - Timeline.clamp01((t - inst.endAt) / YinYangTimeline.FADE);
            if (out <= 0f) continue;
            PlayerEntity p = world.getPlayerByUuid(inst.uuid);
            Vec3d cp = p != null ? p.getLerpedPos(td) : inst.start;
            boolean mine = mc.player != null && inst.uuid.equals(mc.player.getUuid());

            drawBagua(f, inst, t, cp, out);
            drawBook(f, inst, t, out);
            drawRipples(f, inst, t, cp, out);
            drawDragons(f, inst, t, cp, out);
            drawExplosion(f, inst, t, out);
            if (mine && aim != null && inst.strikeAt < 0) drawAim(f, aim, t, out);
        }
        AscensionRenderer.endWorldState();
    }

    // ------------------------------------------------------------------ คัมภีร์

    private static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return a.add(b.subtract(a).multiply(k));
    }

    private static Vec3d toward(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        return d.lengthSquared() < 1.0e-6 ? new Vec3d(0, 0, 1) : d.normalize();
    }

    private static double bookScale(double t) {
        double k = YinYangTimeline.smoother((t - 2.0) / (YinYangTimeline.EXPAND_END - 2.0));
        return 0.03 + 0.97 * k;
    }

    /** เส้นทางคัมภีร์: ลอยขึ้นจากมือเป็นเส้นโค้ง (ขึ้นสูงก่อน แล้วค่อยไหลไปด้านหลัง) ต่อเนื่องไม่มีสะดุด */
    private static Vec3d bookCenter(YinYangState.Instance inst, double t) {
        Vec3d p0 = inst.start.add(0, 2.2, 0);
        Vec3d top = inst.start.add(0, 15.0, 0);
        double kUp = YinYangTimeline.smoother(t / YinYangTimeline.THROW_END);
        double kBack = YinYangTimeline.smoother((t - 14.0) / (YinYangTimeline.EXPAND_END - 14.0));
        Vec3d a = lerp(p0, top, kUp);
        Vec3d pos = lerp(a, inst.book, kBack);
        if (t > YinYangTimeline.EXPAND_END) {
            double fade = YinYangTimeline.smooth((t - YinYangTimeline.EXPAND_END) / 20.0);
            pos = pos.add(0, 0.8 * Math.sin((t - YinYangTimeline.EXPAND_END) * 0.05) * fade, 0);
        }
        return pos;
    }

    private static void drawBook(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
        double scale = bookScale(t);
        Vec3d pos = bookCenter(inst, t);
        double open = 0.16 + 0.84 * YinYangTimeline.smoother((t - YinYangTimeline.OPEN_START) / (YinYangTimeline.OPEN_END - YinYangTimeline.OPEN_START));
        double breathe = t > YinYangTimeline.OPEN_END ? 1.0 + 0.008 * Math.sin(t * 0.07) : 1.0;
        // หมุนตอนโยน แล้วค่อยๆ ตั้งตรง
        double roll = (1.0 - YinYangTimeline.smoother(t / (YinYangTimeline.EXPAND_END - 6.0))) * Math.PI * 2.0 * 1.25;
        float alpha = (float) YinYangTimeline.smoother(t / 10.0) * out;

        // ครอสเฟดระหว่างเฟรม (ไม่กระตุกตอนเปลี่ยนเฟรม)
        double fr = t / 2.6;
        int i0 = ((int) Math.floor(fr)) % BOOK.length;
        int i1 = (i0 + 1) % BOOK.length;
        float mix = (float) (fr - Math.floor(fr));
        double hw = YinYangTimeline.BOOK_HALF_W * scale * open * breathe, hh = YinYangTimeline.BOOK_HALF_H * scale * breathe;

        AscensionRenderer.Batch b0 = new AscensionRenderer.Batch(BOOK[i0], false, f);
        b0.billboardRect(pos, hw, hh, roll, alpha);
        b0.end();
        AscensionRenderer.Batch b1 = new AscensionRenderer.Batch(BOOK[i1], false, f);
        b1.billboardRect(pos, hw, hh, roll, alpha * mix);
        b1.end();

        float fl = Timeline.keys(t, 0, 0, 10, 0.9f, 50, 0.6f, 110, 0f) * out;
        if (fl > 0.01f) {
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(FLARE_W, true, f);
            Vec3d tc = toward(pos, f.cam);
            g.billboard(pos.add(tc.multiply(0.5)), 4.0 + 22.0 * scale, t * 0.01, fl);
            g.end();
        }
    }

    // ------------------------------------------------------------------ วงน้ำ + หยดน้ำ (ที่ปากรูบนหน้าคัมภีร์)

    private static Vec3d hole(YinYangState.Instance inst, Vec3d cp) {
        return inst.book.add(YinYangState.pageNormal(inst, cp).multiply(1.0));
    }

    private static void drawRipples(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, Vec3d cp, float out) {
        Vec3d hole = hole(inst, cp);
        Vec3d n = YinYangState.pageNormal(inst, cp);
        // แกนบนระนาบหน้าคัมภีร์ (ไว้กระจายหยดน้ำ)
        Vec3d ax = n.crossProduct(new Vec3d(0, 1, 0));
        ax = ax.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : ax.normalize();
        Vec3d ay = ax.crossProduct(n).normalize();

        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            float age = t - (st - 4);
            if (age < 0 || t > st + YinYangTimeline.EMERGE_FX + 14) continue;
            float win = Timeline.smooth(age / 5f) * (1f - Timeline.smooth((t - (st + YinYangTimeline.EMERGE_FX)) / 14f)) * out;
            if (win <= 0.01f) continue;
            for (int k = 0; k < 2; k++) {
                AscensionRenderer.Batch b = new AscensionRenderer.Batch(RIPPLE[(((int) (age / 2.5f)) + 4 * k) % RIPPLE.length], true, f);
                // วงน้ำวางบนระนาบหน้าคัมภีร์
                b.quad(hole, ax.multiply(k == 0 ? 13.0 : 8.0), ay.multiply(k == 0 ? 13.0 : 8.0), win * (k == 0 ? 0.9f : 0.7f));
                b.end();
            }
        }

        // หยดน้ำกระเด็นพุ่งออกมาทางผู้เล่น
        AscensionRenderer.Batch b = null;
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            if (t < st || t > st + YinYangTimeline.EMERGE_FX + 30) continue;
            if (b == null) b = new AscensionRenderer.Batch(DROP, true, f);
            for (int j = 0; j < 40; j++) {
                double ts = st + j * (YinYangTimeline.EMERGE_FX / 40.0);
                double age = t - ts;
                if (age < 0 || age > 28) continue;
                int sd = i * 100 + j;
                Vec3d v = ax.multiply((rand(sd, 1) - 0.5) * 0.9)
                        .add(ay.multiply(rand(sd, 2) * 0.6 - 0.15))
                        .add(n.multiply(0.6 + rand(sd, 3) * 0.9));
                Vec3d start = hole.add(ax.multiply((rand(sd, 5) - 0.5) * 6)).add(ay.multiply((rand(sd, 6) - 0.5) * 6));
                Vec3d pos = start.add(v.multiply(age)).add(0, -0.03 * age * age, 0);
                b.billboard(pos, 0.35 + rand(sd, 4) * 0.5, 0.0, (float) (1.0 - age / 28.0) * out);
            }
        }
        if (b != null) b.end();
    }

    // ------------------------------------------------------------------ มังกร 3 มิติ

    private static void drawDragons(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, Vec3d cp, float out) {
        Vec3d n = YinYangState.pageNormal(inst, cp);
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            if (t < st - 6) continue;

            float alpha = out;
            double s = inst.strikeAt >= 0 ? t - inst.strikeAt : -1.0;
            if (s >= inst.fly) alpha *= 1f - Timeline.clamp01((float) ((s - inst.fly) / 3.0));
            if (alpha <= 0.01f) continue;

            final int idx = i;
            DoubleFunction<Vec3d> head = tt -> YinYangState.headPos(inst, idx, tt, cp);
            Vec3d[] body = DragonModel.body(head, t);

            // ตัดด้วยระนาบหน้าคัมภีร์ขณะกำลังพุ่งออกมา (ส่วนที่ยังอยู่ "ใต้น้ำ" ถูกซ่อน)
            Vec3d clipP = null, clipN = null;
            if (t < st + 70) {
                clipP = inst.book;
                clipN = n;
            }
            DragonModel.render(f, i == 0 ? DragonModel.WHITE : DragonModel.BLACK, body, t, alpha, clipP, clipN);

            // ประกายเรืองรอบหัวและลำตัว
            boolean white = i == 0;
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(white ? FLARE_W : FLARE_D, white, f);
            for (int k = 0; k < body.length; k += 9) {
                if (clipP != null && body[k].subtract(clipP).dotProduct(clipN) < 0) continue;
                double sz = k == 0 ? 7.0 : 3.2 - 2.0 * k / body.length;
                g.billboard(body[k], sz, t * 0.02 + k, alpha * (k == 0 ? 0.8f : 0.45f));
            }
            g.end();
        }
    }

    // ------------------------------------------------------------------ ปากั้ว + จุดเล็ง + ระเบิด

    private static void flat(AscensionRenderer.Batch b, Vec3d c, double s, double angle, float alpha) {
        Vec3d right = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).multiply(s);
        Vec3d up = new Vec3d(-Math.sin(angle), 0, Math.cos(angle)).multiply(s);
        b.quad(c, right, up, alpha);
    }

    private static void drawBagua(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, Vec3d cp, float out) {
        AscensionRenderer.Batch b = new AscensionRenderer.Batch(BAGUA[((int) (t / 3f)) % BAGUA.length], false, f);
        float a = Timeline.smooth((t - 40f) / 20f) * out;
        if (a > 0.01f) flat(b, cp.add(0, 0.06, 0), 6.0 * Timeline.smooth((t - 40f) / 24f), t * 0.02, a * 0.9f);
        if (inst.strikeAt >= 0) {
            double s = t - inst.strikeAt;
            if (s >= 0) {
                double u = YinYangTimeline.clamp01(s / inst.fly);
                float ta = (float) (YinYangTimeline.smooth(s / 8.0) * (1.0 - YinYangTimeline.clamp01((s - inst.fly) / 30.0))) * out;
                flat(b, inst.target.add(0, 0.12, 0), 6.0 + 34.0 * YinYangTimeline.smooth(u), -t * 0.03, ta);
            }
        }
        b.end();
    }

    /** ตัวเล็งที่จุดเมาส์ชี้: ปากั้วหมุน + วงขอบ + ลำแสงตั้ง */
    private static void drawAim(AscensionRenderer.Frame f, Vec3d aim, float t, float out) {
        float a = (float) YinYangTimeline.smoother((t - YinYangTimeline.READY) / 12.0) * out;
        if (a <= 0.01f) return;
        double pulse = 1.0 + 0.08 * Math.sin(t * 0.3);

        AscensionRenderer.Batch b = new AscensionRenderer.Batch(BAGUA[((int) (t / 2f)) % BAGUA.length], false, f);
        flat(b, aim.add(0, 0.15, 0), 7.0 * pulse, t * 0.05, a * 0.95f);
        b.end();
        AscensionRenderer.Batch e = new AscensionRenderer.Batch(EDGE, false, f);
        flat(e, aim.add(0, 0.2, 0), 11.0 * pulse, -t * 0.03, a * 0.8f);
        e.end();

        double dx = f.cam.x - aim.x, dz = f.cam.z - aim.z;
        double len = Math.hypot(dx, dz);
        if (len > 1.0e-4) {
            Vec3d right = new Vec3d(dz / len, 0, -dx / len).multiply(1.6);
            AscensionRenderer.Batch p = new AscensionRenderer.Batch(PILLAR[((int) (t / 2f)) % PILLAR.length], false, f);
            p.quad(new Vec3d(aim.x, aim.y + 22.0, aim.z), right, new Vec3d(0, 22.0, 0), a * 0.55f);
            p.end();
        }
    }

    private static void drawExplosion(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
        if (inst.strikeAt < 0) return;
        double s = t - inst.strikeAt;
        if (s < 0) return;
        Vec3d c = inst.target;
        int fly = inst.fly;

        // รวมแสงที่เป้าหมายก่อนมังกรถึง
        if (s < fly) {
            double u = s / fly;
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(FLARE_W, true, f);
            g.billboard(c.add(0, 3, 0), 6.0 + 12.0 * u, t * 0.05, (float) (0.15 + 0.7 * u) * out);
            g.end();
            return;
        }

        double k = s - fly;
        Vec3d burstC = c.add(0, 10, 0);

        if (k < 34) {
            AscensionRenderer.Batch d = new AscensionRenderer.Batch(FLARE_D, false, f);
            d.billboard(burstC, 52.0, -t * 0.02, (float) (0.9 * (1.0 - YinYangTimeline.smooth(k / 30.0))) * out);
            d.end();
        }
        if (k < 30) {
            AscensionRenderer.Batch b = new AscensionRenderer.Batch(BURST[Math.min(BURST.length - 1, (int) (k / 2.0))], false, f);
            b.billboard(burstC, 20.0 + 3.2 * k, 0.0, (float) (1.0 - YinYangTimeline.smooth((k - 18.0) / 11.0)) * out);
            b.end();
        }
        if (k < 36) {
            AscensionRenderer.Batch w = new AscensionRenderer.Batch(FLARE_W, true, f);
            w.billboard(burstC, 60.0 * (0.4 + 0.6 * YinYangTimeline.smooth(k / 6.0)), t * 0.03,
                    (float) (1.0 - YinYangTimeline.smooth(k / 34.0)) * out);
            w.end();
        }

        if (k < 80) {
            double halfH = 70.0;
            double halfW = (8.0 + 16.0 * YinYangTimeline.smooth(k / 10.0)) * (1.0 - 0.5 * YinYangTimeline.smooth((k - 35.0) / 40.0));
            float pa = (float) (YinYangTimeline.smooth(k / 4.0) * (1.0 - YinYangTimeline.smooth((k - 40.0) / 40.0))) * out;
            double dx = f.cam.x - c.x, dz = f.cam.z - c.z;
            double len = Math.hypot(dx, dz);
            if (len > 1.0e-4 && pa > 0.01f) {
                Vec3d right = new Vec3d(dz / len, 0, -dx / len).multiply(halfW);
                Vec3d up = new Vec3d(0, halfH, 0);
                AscensionRenderer.Batch b = new AscensionRenderer.Batch(PILLAR[((int) (t / 2f)) % PILLAR.length], false, f);
                b.quad(new Vec3d(c.x, c.y + halfH * 0.92, c.z), right, up, pa);
                b.end();
            }
        }

        double r = YinYangTimeline.waveRadius(s, fly, inst.radius);
        if (r > 1.0) {
            double x = YinYangTimeline.clamp01(k / YinYangTimeline.WAVE);
            float ra = (float) (1.0 - x * x) * out;
            AscensionRenderer.Batch b = new AscensionRenderer.Batch(EDGE, false, f);
            flat(b, c.add(0, 0.6, 0), r / 0.9, t * 0.01, ra);
            flat(b, c.add(0, 0.7, 0), r * 0.78 / 0.9, -t * 0.012, ra * 0.7f);
            b.end();
        }
    }

    private static double rand(int i, int k) {
        double x = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }
}
