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

/**
 * VFX สกิล J (ทุกอย่างเป็น quad + PNG ที่ทำไว้):
 *  - คัมภีร์หยินหยาง: ถูกโยนขึ้นฟ้า ขยายใหญ่ไปอยู่ด้านหลังผู้เล่น แล้วเปิดหน้า
 *  - มังกรขาว/ดำ: ค่อยๆ พุ่งออกจากหน้าคัมภีร์ "เหมือนโผล่ขึ้นจากน้ำ" (เปิดภาพทีละพัดจากหัวไปหาง + วงน้ำ + หยดน้ำ)
 *  - วงแหวนปากั้ว ใต้เท้าผู้ร่าย / ใต้จุดเป้าหมาย
 *  - มังกรบินโคจร -> พุ่งไปเป้าหมายพร้อมเงาตาม -> ระเบิดหยินหยาง เสาแสงเกลียว แฟลร์ และวงคลื่นบนพื้น
 */
public final class YinYangRenderer {
    private static final Identifier[] BOOK = arr("scripture_%02d", 8);
    private static final Identifier[] DRAGON_W = arr("dragon_white_%02d", 8);
    private static final Identifier[] DRAGON_B = arr("dragon_black_%02d", 8);
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
        for (YinYangState.Instance inst : YinYangState.ACTIVE.values()) {
            float t = inst.ticks + td;
            float out = inst.endAt < 0 ? 1f : 1f - Timeline.clamp01((t - inst.endAt) / YinYangTimeline.FADE);
            if (out <= 0f) continue;
            PlayerEntity p = world.getPlayerByUuid(inst.uuid);
            Vec3d cp = p != null ? p.getLerpedPos(td) : inst.start;

            drawBagua(f, inst, t, cp, out);
            drawBook(f, inst, t, out);
            drawRipples(f, inst, t, out);
            drawDragons(f, inst, t, cp, out);
            drawExplosion(f, inst, t, out);
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
        if (t < YinYangTimeline.THROW_END) return 0.02 + 0.10 * YinYangTimeline.smooth(t / YinYangTimeline.THROW_END);
        if (t < YinYangTimeline.EXPAND_END) {
            return 0.12 + 0.88 * YinYangTimeline.smooth((t - YinYangTimeline.THROW_END) / (YinYangTimeline.EXPAND_END - YinYangTimeline.THROW_END));
        }
        return 1.0;
    }

    private static Vec3d bookCenter(YinYangState.Instance inst, double t) {
        Vec3d p0 = inst.start.add(0, 2.2, 0);
        Vec3d pm = inst.start.add(0, 13.0, 0);
        if (t < YinYangTimeline.THROW_END) return lerp(p0, pm, YinYangTimeline.smooth(t / YinYangTimeline.THROW_END));
        if (t < YinYangTimeline.EXPAND_END) {
            return lerp(pm, inst.book, YinYangTimeline.smooth((t - YinYangTimeline.THROW_END) / (YinYangTimeline.EXPAND_END - YinYangTimeline.THROW_END)));
        }
        return inst.book.add(0, 0.8 * Math.sin((t - YinYangTimeline.EXPAND_END) * 0.06), 0);
    }

    private static void drawBook(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
        double scale = bookScale(t);
        Vec3d pos = bookCenter(inst, t);
        double open = 0.18 + 0.82 * YinYangTimeline.smooth((t - YinYangTimeline.OPEN_START) / (YinYangTimeline.OPEN_END - YinYangTimeline.OPEN_START));
        float alpha = Timeline.smooth(t / 5f) * out;

        AscensionRenderer.Batch b = new AscensionRenderer.Batch(BOOK[((int) (t / 2f)) % BOOK.length], false, f);
        b.billboardRect(pos, YinYangTimeline.BOOK_HALF_W * scale * open, YinYangTimeline.BOOK_HALF_H * scale, 0.0, alpha);
        b.end();

        float fl = Timeline.keys(t, 0, 0, 10, 0.9f, 50, 0.6f, 110, 0f) * out;
        if (fl > 0.01f) {
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(FLARE_W, true, f);
            Vec3d tc = toward(pos, f.cam);
            g.billboard(pos.add(tc.multiply(0.5)), 4.0 + 22.0 * scale, t * 0.01, fl);
            g.end();
        }
    }

    // ------------------------------------------------------------------ ปากรูน้ำ + หยดน้ำ

    private static Vec3d hole(YinYangState.Instance inst, AscensionRenderer.Frame f) {
        return inst.book.add(toward(inst.book, f.cam).multiply(1.5));
    }

    private static void drawRipples(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
        Vec3d hole = hole(inst, f);
        Vec3d toCam = toward(inst.book, f.cam);

        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            float age = t - (st - 8);
            if (age < 0 || t > st + YinYangTimeline.EMERGE_LEN + 14) continue;
            float win = Timeline.smooth(age / 6f) * (1f - Timeline.smooth((t - (st + YinYangTimeline.EMERGE_LEN)) / 14f)) * out;
            if (win <= 0.01f) continue;
            for (int k = 0; k < 2; k++) {
                AscensionRenderer.Batch b = new AscensionRenderer.Batch(RIPPLE[(((int) (age / 2.5f)) + 4 * k) % RIPPLE.length], true, f);
                b.billboard(hole, k == 0 ? 12.0 : 7.5, k * 1.1, win * (k == 0 ? 0.9f : 0.7f));
                b.end();
            }
        }

        // หยดน้ำกระเด็นออกจากปากรู พุ่งเข้าหาผู้เล่น
        AscensionRenderer.Batch b = null;
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            if (t < st || t > st + YinYangTimeline.EMERGE_LEN + 30) continue;
            if (b == null) b = new AscensionRenderer.Batch(DROP, true, f);
            for (int j = 0; j < 36; j++) {
                double ts = st + j * (YinYangTimeline.EMERGE_LEN * 1.05 / 36.0);
                double age = t - ts;
                if (age < 0 || age > 28) continue;
                int sd = i * 100 + j;
                Vec3d v = f.camRight.multiply((rand(sd, 1) - 0.5) * 0.9)
                        .add(f.camUp.multiply(rand(sd, 2) * 0.6 - 0.15))
                        .add(toCam.multiply(0.5 + rand(sd, 3) * 0.7));
                Vec3d start = hole.add(f.camRight.multiply((rand(sd, 5) - 0.5) * 8)).add(f.camUp.multiply((rand(sd, 6) - 0.5) * 8));
                Vec3d pos = start.add(v.multiply(age)).add(0, -0.03 * age * age, 0);
                b.billboard(pos, 0.35 + rand(sd, 4) * 0.5, 0.0, (float) (1.0 - age / 28.0) * out);
            }
        }
        if (b != null) b.end();
    }

    // ------------------------------------------------------------------ มังกร

    /** ตำแหน่งของจุดบนแกนลำตัว (มุม alpha) เทียบกับกึ่งกลาง sprite ในโลก */
    private static Vec3d anchor(Vec3d right, Vec3d up, double alpha, double sCut) {
        double ra = YinYangTimeline.arcRadius(sCut);
        return right.multiply(2 * ra * Math.cos(alpha)).add(up.multiply(-2 * ra * Math.sin(alpha)));
    }

    private static double cutAlpha(int i, double e) {
        double sCut = 1.0 - 1.25 * YinYangTimeline.smooth(e);
        return YinYangTimeline.THETA_HEAD[i] - YinYangTimeline.SPAN * (1.0 - sCut);
    }

    private static Vec3d dragonCenter(YinYangState.Instance inst, int i, double tt, Vec3d cp, AscensionRenderer.Frame f, Vec3d hole) {
        int endEm = YinYangTimeline.EMERGE_START[i] + YinYangTimeline.EMERGE_LEN;
        Vec3d right = f.camRight.multiply(YinYangTimeline.DRAGON_HALF);
        Vec3d up = f.camUp.multiply(YinYangTimeline.DRAGON_HALF);
        Vec3d dp = YinYangState.dragonPos(inst, i, tt, cp);
        if (tt >= endEm + YinYangTimeline.GLIDE) return dp;
        double aEnd = cutAlpha(i, 1.0);
        Vec3d holeEnd = hole.subtract(anchor(right, up, aEnd, 0.0));
        return lerp(holeEnd, dp, YinYangTimeline.smooth((tt - endEm) / YinYangTimeline.GLIDE));
    }

    private static void drawDragons(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, Vec3d cp, float out) {
        Vec3d hole = hole(inst, f);
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            int endEm = st + YinYangTimeline.EMERGE_LEN;
            if (t < st) continue;

            float alpha = out;
            double s = inst.strikeAt >= 0 ? t - inst.strikeAt : -1.0;
            if (s >= YinYangTimeline.FLY) alpha *= 1f - Timeline.clamp01((float) ((s - YinYangTimeline.FLY) / 3.0));
            if (alpha <= 0.01f) continue;

            Identifier tex = (i == 0 ? DRAGON_W : DRAGON_B)[(((int) (t / 2f)) + i * 3) % DRAGON_W.length];
            Vec3d right = f.camRight.multiply(YinYangTimeline.DRAGON_HALF);
            Vec3d up = f.camUp.multiply(YinYangTimeline.DRAGON_HALF);
            AscensionRenderer.Batch b = new AscensionRenderer.Batch(tex, false, f);

            if (t < endEm) {
                // โผล่ออกจากผิวน้ำ: เปิดภาพเป็นพัดจากหัวไปหาง โดยจุดที่ยังจมอยู่ตรึงที่ปากรู
                double e = (t - st) / (double) YinYangTimeline.EMERGE_LEN;
                double a = cutAlpha(i, e);
                double sCut = 1.0 - 1.25 * YinYangTimeline.smooth(e);
                Vec3d center = hole.subtract(anchor(right, up, a, sCut));
                b.sector(center, right, up, a, YinYangTimeline.THETA_HEAD[i] + YinYangTimeline.HEAD_EXTRA, 0.32, alpha);
            } else {
                int ghosts = (s >= 0 && s < YinYangTimeline.FLY) ? 4 : 1;
                for (int g = ghosts - 1; g >= 0; g--) {
                    double tt = t - g * 1.6;
                    Vec3d pos = dragonCenter(inst, i, tt, cp, f, hole);
                    Vec3d v = dragonCenter(inst, i, tt + 0.5, cp, f, hole).subtract(dragonCenter(inst, i, tt - 0.5, cp, f, hole));
                    double vx = v.dotProduct(f.camRight), vy = v.dotProduct(f.camUp);
                    double vl = v.length() + 1.0e-6;
                    double glide = YinYangTimeline.smooth((tt - endEm) / YinYangTimeline.GLIDE);
                    double nativeDir = i == 0 ? 1.0 : -1.0;
                    double xs = 1.0 + (Math.max(-1.0, Math.min(1.0, vx / vl * 3.0)) * nativeDir - 1.0) * glide;
                    double roll = (vx >= 0 ? Math.atan2(vy, Math.max(vx, 1.0e-4)) : Math.atan2(-vy, -vx)) * 0.7 * glide;
                    double cs = Math.cos(roll), sn = Math.sin(roll);
                    Vec3d r2 = f.camRight.multiply(cs).add(f.camUp.multiply(sn)).multiply(YinYangTimeline.DRAGON_HALF * xs);
                    Vec3d u2 = f.camUp.multiply(cs).subtract(f.camRight.multiply(sn)).multiply(YinYangTimeline.DRAGON_HALF);
                    b.quad(pos, r2, u2, alpha * (g == 0 ? 1f : 0.30f / g));
                }
            }
            b.end();
        }
    }

    // ------------------------------------------------------------------ ปากั้ว + ระเบิด

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
                double u = YinYangTimeline.clamp01(s / YinYangTimeline.FLY);
                float ta = (float) (YinYangTimeline.smooth(s / 8.0) * (1.0 - YinYangTimeline.clamp01((s - YinYangTimeline.FLY) / 30.0))) * out;
                flat(b, inst.target.add(0, 0.12, 0), 6.0 + 34.0 * YinYangTimeline.smooth(u), -t * 0.03, ta);
            }
        }
        b.end();
    }

    private static void drawExplosion(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
        if (inst.strikeAt < 0) return;
        double s = t - inst.strikeAt;
        if (s < 0) return;
        Vec3d c = inst.target;

        // รวมแสงที่เป้าหมายก่อนมังกรถึง
        if (s < YinYangTimeline.FLY) {
            double u = s / YinYangTimeline.FLY;
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(FLARE_W, true, f);
            g.billboard(c.add(0, 3, 0), 6.0 + 12.0 * u, t * 0.05, (float) (0.15 + 0.7 * u) * out);
            g.end();
            return;
        }

        double k = s - YinYangTimeline.FLY;
        Vec3d burstC = c.add(0, 10, 0);

        // แฟลร์ดำ (ข้างหลัง) + ระเบิดก้นหอย + แฟลร์ขาว (additive)
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

        // เสาแสงเกลียวขาว/ดำ (ล็อกแกน Y)
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

        // วงคลื่นบนพื้นตามรัศมีทำลายล้าง
        double r = YinYangTimeline.waveRadius(s, inst.radius);
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
