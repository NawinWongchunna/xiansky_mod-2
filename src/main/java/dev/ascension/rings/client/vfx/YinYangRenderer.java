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
 * VFX สกิล J (ทุกอย่างเป็นโมเดล 3 มิติที่สร้างจากโค้ด — มีแค่แฟลร์/หยดน้ำเล็กๆ ที่ยังเป็น sprite เรืองแสง):
 *  - คัมภีร์ 3 มิติ ({@link ScriptureModel}): โยนขึ้นฟ้าพร้อมหมุน ขยายไปอยู่ด้านหลังผู้เล่น แล้วเปิดเล่มจริง
 *  - มังกรขาว/ดำ ({@link DragonModel}) พุ่งออกจากหน้าคัมภีร์โดยตัดด้วยระนาบ + วงคลื่นน้ำ
 *  - ปากั้ว 3 มิติใต้เท้า/ที่จุดเล็ง, ระเบิดเป็นโดมไท่จี๋ + เสาแสงเกลียวขาวดำ + วงคลื่นบนพื้น
 *  - ข้างในโลกหยินหยาง: มังกรยักษ์วนรอบแท่น + ปากั้วยักษ์บนท้องฟ้า
 */
public final class YinYangRenderer {
    private static final Identifier FLARE_W = yy("flare_white");
    private static final Identifier FLARE_D = yy("flare_dark");
    private static final Identifier DROP = yy("droplet");

    private static final Mesh BOOK_M = new Mesh();
    private static final Mesh DRAGON_M = new Mesh();
    private static final Mesh FX_M = new Mesh();
    private static final Mesh GLOW_M = new Mesh();

    private static final Vec3d FLAT_X = new Vec3d(0, 0, 1);
    private static final Vec3d FLAT_Y = new Vec3d(1, 0, 0); // FLAT_X × FLAT_Y = +Y (หงายขึ้น)
    private static final Vec3d UP = new Vec3d(0, 1, 0);

    private YinYangRenderer() {}

    private static Identifier yy(String name) {
        return new Identifier(AscensionMod.MOD_ID, "textures/vfx/yy/" + name + ".png");
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;
        boolean realm = YinYangState.inRealm;
        if (!realm && YinYangState.ACTIVE.isEmpty()) return;

        float td = ctx.tickDelta();
        AscensionRenderer.Frame f = AscensionRenderer.makeFrame(ctx);
        AscensionRenderer.beginWorldState();

        if (realm) {
            drawRealm(f, YinYangState.realmTicks + td);
            AscensionRenderer.endWorldState();
            return;
        }

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

            FX_M.clear();
            fxBagua(FX_M, inst, t, cp);
            if (mine && aim != null && inst.strikeAt < 0) fxAim(FX_M, aim, t);
            fxExplosion(FX_M, inst, t);
            MeshRender.draw(f, FX_M, MeshRender.NEUTRAL, out, false);

            drawBook(f, inst, t, out);
            drawRipples(f, inst, t, cp, out);
            drawDragons(f, inst, t, cp, out);
            drawExplosionGlow(f, inst, t, out);
        }
        AscensionRenderer.endWorldState();
    }

    // ------------------------------------------------------------------ คัมภีร์ 3 มิติ

    private static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return a.add(b.subtract(a).multiply(k));
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
        // หมุนรอบแกนตั้งตอนโยน แล้วค่อยๆ หันหน้าเข้าหาผู้เล่น
        double roll = (1.0 - YinYangTimeline.smoother(t / (YinYangTimeline.EXPAND_END - 6.0))) * Math.PI * 2.0 * 1.25;
        float alpha = (float) YinYangTimeline.smoother(t / 10.0) * out;

        Vec3d n0 = YinYangState.pageNormal(inst, inst.start);
        Vec3d N = SymbolModel.rot(n0, UP, roll).normalize();
        Vec3d R = UP.crossProduct(N);
        R = R.lengthSquared() < 1.0e-6 ? new Vec3d(1, 0, 0) : R.normalize();
        Vec3d U = N.crossProduct(R).normalize();

        ScriptureModel.Frame bf = new ScriptureModel.Frame(pos, R, U, N, scale * breathe);
        BOOK_M.clear();
        ScriptureModel.build(BOOK_M, bf, open, t);
        double appear = YinYangTimeline.smooth((t - YinYangTimeline.OPEN_END) / 24.0);
        ScriptureModel.halo(BOOK_M, bf, t, appear);
        MeshRender.draw(f, BOOK_M, MeshRender.NEUTRAL, alpha, false);

        float fl = Timeline.keys(t, 0, 0, 10, 0.9f, 50, 0.6f, 110, 0f) * out;
        if (fl > 0.01f) {
            AscensionRenderer.Batch g = new AscensionRenderer.Batch(FLARE_W, true, f);
            Vec3d tc = f.cam.subtract(pos);
            tc = tc.lengthSquared() < 1.0e-6 ? N : tc.normalize();
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
        Vec3d ax = n.crossProduct(UP);
        ax = ax.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : ax.normalize();
        Vec3d ay = ax.crossProduct(n).normalize();

        // วงคลื่นน้ำ 3 ชั้นขยายออกบนระนาบหน้าคัมภีร์
        GLOW_M.clear();
        GLOW_M.twoSided = true;
        float peak = 0f;
        for (int i = 0; i < 2; i++) {
            int st = YinYangTimeline.EMERGE_START[i];
            float age = t - (st - 4);
            if (age < 0 || t > st + YinYangTimeline.EMERGE_FX + 14) continue;
            float win = Timeline.smooth(age / 5f) * (1f - Timeline.smooth((t - (st + YinYangTimeline.EMERGE_FX)) / 14f));
            if (win <= 0.01f) continue;
            peak = Math.max(peak, win);
            float[] col = i == 0 ? new float[]{0.85f, 0.95f, 1f} : new float[]{0.65f, 0.35f, 1f};
            for (int k = 0; k < 3; k++) {
                double r = ((age * 0.55 + k * 4.2) % 12.6) + 1.0;
                double w = 0.5 + r * 0.06;
                GLOW_M.alpha = (float) (1.0 - r / 14.0) * win;
                SymbolModel.ring(GLOW_M, hole, ax, ay, r, r + w, col, 1f);
            }
        }
        GLOW_M.twoSided = false;
        GLOW_M.alpha = 1f;
        if (peak > 0f) MeshRender.draw(f, GLOW_M, MeshRender.NEUTRAL, out, true);

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
            Vec3d[] body = DragonModel.body(head, t, 1.0);

            // ตัดด้วยระนาบหน้าคัมภีร์ขณะกำลังพุ่งออกมา (ส่วนที่ยังอยู่ "ใต้น้ำ" ถูกซ่อน)
            Vec3d clipP = null, clipN = null;
            if (t < st + 70) {
                clipP = inst.book;
                clipN = n;
            }
            DragonModel.Scheme sc = i == 0 ? DragonModel.WHITE : DragonModel.BLACK;
            DRAGON_M.clear();
            Vec3d pearl = DragonModel.build(DRAGON_M, sc, body, t, 1.0, clipP, clipN);
            MeshRender.draw(f, DRAGON_M, sc.lit(), alpha, false);
            glowDragon(f, body, pearl, i == 0, t, alpha, clipP, clipN, 1.0);
        }
    }

    /** ประกายเรืองรอบหัว/ลำตัว/ไข่มุกเพลิง */
    private static void glowDragon(AscensionRenderer.Frame f, Vec3d[] body, Vec3d pearl, boolean white, float t, float alpha,
                                   Vec3d clipP, Vec3d clipN, double sz) {
        AscensionRenderer.Batch g = new AscensionRenderer.Batch(white ? FLARE_W : FLARE_D, white, f);
        for (int k = 0; k < body.length; k += 10) {
            if (clipP != null && body[k].subtract(clipP).dotProduct(clipN) < 0) continue;
            double size = (k == 0 ? 6.0 : 2.8 - 1.8 * k / body.length) * sz;
            g.billboard(body[k], size, t * 0.02 + k, alpha * (k == 0 ? 0.6f : 0.35f));
        }
        g.end();
        if (pearl != null && (clipP == null || pearl.subtract(clipP).dotProduct(clipN) >= 0)) {
            AscensionRenderer.Batch p = new AscensionRenderer.Batch(FLARE_W, true, f);
            p.billboard(pearl, 4.5 * sz, t * 0.05, alpha * 0.9f);
            p.end();
        }
    }

    // ------------------------------------------------------------------ ปากั้ว + จุดเล็ง + ระเบิด (3 มิติ)

    private static void fxBagua(Mesh m, YinYangState.Instance inst, float t, Vec3d cp) {
        float a = Timeline.smooth((t - 40f) / 20f);
        if (a > 0.01f) {
            m.alpha = a * 0.95f;
            SymbolModel.bagua(m, cp.add(0, 0.08, 0), FLAT_X, FLAT_Y, 6.0 * Timeline.smooth((t - 40f) / 24f), t * 0.02, 0.7, true);
        }
        if (inst.strikeAt >= 0) {
            double s = t - inst.strikeAt;
            if (s >= 0) {
                double u = YinYangTimeline.clamp01(s / inst.fly);
                float ta = (float) (YinYangTimeline.smooth(s / 8.0) * (1.0 - YinYangTimeline.clamp01((s - inst.fly) / 30.0)));
                if (ta > 0.01f) {
                    m.alpha = ta;
                    SymbolModel.bagua(m, inst.target.add(0, 0.14, 0), FLAT_X, FLAT_Y, 6.0 + 34.0 * YinYangTimeline.smooth(u), -t * 0.03, 0.9, true);
                }
            }
        }
        m.alpha = 1f;
    }

    /** ตัวเล็งที่จุดเมาส์ชี้: ปากั้วหมุน + วงขอบ + เสาแสงตั้ง */
    private static void fxAim(Mesh m, Vec3d aim, float t) {
        float a = (float) YinYangTimeline.smoother((t - YinYangTimeline.READY) / 12.0);
        if (a <= 0.01f) return;
        double pulse = 1.0 + 0.08 * Math.sin(t * 0.3);
        m.alpha = a * 0.95f;
        SymbolModel.bagua(m, aim.add(0, 0.16, 0), FLAT_X, FLAT_Y, 7.0 * pulse, t * 0.05, 0.95, true);
        m.alpha = a * 0.8f;
        SymbolModel.ring(m, aim.add(0, 0.2, 0), FLAT_X, FLAT_Y, 10.4 * pulse, 11.0 * pulse, SymbolModel.GOLD, 0.9f);
        SymbolModel.beam(m, aim, 46.0, 0.9, t * 0.1, a * 0.4f);
        m.alpha = 1f;
    }

    private static void fxExplosion(Mesh m, YinYangState.Instance inst, float t) {
        if (inst.strikeAt < 0) return;
        double s = t - inst.strikeAt;
        if (s < 0) return;
        Vec3d c = inst.target;
        int fly = inst.fly;
        if (s < fly) return;
        double k = s - fly;

        // โดมไท่จี๋ขยายตัว
        if (k < 46) {
            double r = 6.0 + 62.0 * YinYangTimeline.smooth(k / 22.0);
            float da = (float) (0.75 * (1.0 - YinYangTimeline.smooth((k - 14.0) / 32.0)));
            SymbolModel.dome(m, c.add(0, 8, 0), r, k * 0.06, 22, da);
            SymbolModel.dome(m, c.add(0, 8, 0), r * 0.62, -k * 0.09, 16, da * 0.8f);
        }
        // เสาแสงเกลียวขาว/ดำ
        if (k < 84) {
            double r = (5.0 + 12.0 * YinYangTimeline.smooth(k / 10.0)) * (1.0 - 0.5 * YinYangTimeline.smooth((k - 35.0) / 45.0));
            float pa = (float) (YinYangTimeline.smooth(k / 4.0) * (1.0 - YinYangTimeline.smooth((k - 40.0) / 44.0)));
            SymbolModel.beam(m, c, 150.0, r, k * 0.12, pa * 0.7f);
        }
        // วงคลื่นบนพื้น: ขาว + ดำ ซ้อนกัน
        double rw = YinYangTimeline.waveRadius(s, fly, inst.radius);
        if (rw > 1.0) {
            double x = YinYangTimeline.clamp01(k / YinYangTimeline.WAVE);
            m.alpha = (float) (1.0 - x * x);
            double w = Math.max(1.6, rw * 0.025);
            SymbolModel.ring(m, c.add(0, 0.6, 0), FLAT_X, FLAT_Y, Math.max(0, rw - w), rw, SymbolModel.WHITE, 1f);
            SymbolModel.ring(m, c.add(0, 0.7, 0), FLAT_X, FLAT_Y, Math.max(0, rw - w * 2.2), Math.max(0, rw - w * 1.1), SymbolModel.BLACK, 0f);
            m.alpha = 1f;
        }
    }

    private static void drawExplosionGlow(AscensionRenderer.Frame f, YinYangState.Instance inst, float t, float out) {
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
        if (k < 36) {
            AscensionRenderer.Batch w = new AscensionRenderer.Batch(FLARE_W, true, f);
            w.billboard(burstC, 60.0 * (0.4 + 0.6 * YinYangTimeline.smooth(k / 6.0)), t * 0.03,
                    (float) (1.0 - YinYangTimeline.smooth(k / 34.0)) * out);
            w.end();
        }
    }

    // ------------------------------------------------------------------ โลกหยินหยาง (ข้างใน)

    private static final Vec3d CENTER = new Vec3d(0.5, 64.0, 0.5);

    private static void drawRealm(AscensionRenderer.Frame f, float t) {
        // ปากั้วยักษ์บนฟ้า หมุนช้าๆ + ไท่จี๋ตรงกลาง
        FX_M.clear();
        FX_M.alpha = 0.9f;
        SymbolModel.bagua(FX_M, CENTER.add(0, 190, 0), FLAT_X, FLAT_Y.multiply(-1), 175.0, t * 0.003, 0.9, true);
        // ไท่จี๋ลอยเหนือแท่น
        FX_M.alpha = 1f;
        SymbolModel.taiji(FX_M, CENTER.add(0, 34, 0), FLAT_X, FLAT_Y, 9.0, 2.0, t * 0.02, 0.9);
        SymbolModel.beam(FX_M, CENTER.add(0, 1.2, 0), 26.0, 1.4, t * 0.05, 0.18f);
        MeshRender.draw(f, FX_M, MeshRender.NEUTRAL, 1f, false);

        // มังกรยักษ์ขาว/ดำไล่กันรอบแท่น
        final double sz = 3.2;
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            DoubleFunction<Vec3d> head = tt -> YinYangState.realmHead(idx, tt);
            Vec3d[] body = DragonModel.body(head, t, sz);
            DragonModel.Scheme sc = i == 0 ? DragonModel.WHITE : DragonModel.BLACK;
            DRAGON_M.clear();
            Vec3d pearl = DragonModel.build(DRAGON_M, sc, body, t, sz, null, null);
            MeshRender.draw(f, DRAGON_M, sc.lit(), 1f, false);
            glowDragon(f, body, pearl, i == 0, t, 1f, null, null, sz);
        }
    }

    private static double rand(int i, int k) {
        double x = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }
}
