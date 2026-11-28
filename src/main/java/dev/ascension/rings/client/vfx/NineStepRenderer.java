package dev.ascension.rings.client.vfx;

import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.NineStepTimeline;
import dev.ascension.rings.client.nine.NineStepState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * VFX สกิล V "เก้าก้าวย่างอากาศ" — โมเดล 3 มิติทั้งหมด วาด 2 ชั้นเพื่อให้ดูมีมิติ:
 *  ชั้นทึบ (แท่นก้าว/ร่างกายภาพติดตา) + ชั้นเรืองแสง additive (รอยแหวกมิติ ปีก รัศมี รอยพุ่ง คลื่นกรวย)
 * รอยพุ่งสร้างจากการสุ่มตำแหน่งผู้ร่ายย้อนหลังด้วยสูตรเดียวกับ server จึงโค้งเนียนตรงกับตัวจริงเสมอ
 */
public final class NineStepRenderer {
    private static final Identifier FLARE = new Identifier(AscensionMod.MOD_ID, "textures/vfx/yy/flare_white.png");
    private static final Mesh SOLID = new Mesh();
    private static final Mesh GLOW = new Mesh();

    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private static final Vec3d FLAT_X = new Vec3d(0, 0, 1);
    private static final Vec3d FLAT_Y = new Vec3d(1, 0, 0);

    private NineStepRenderer() {}

    public static void render(WorldRenderContext ctx) {
        if (NineStepState.ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;
        Identifier dim = world.getRegistryKey().getValue();

        AscensionRenderer.Frame f = AscensionRenderer.makeFrame(ctx);
        AscensionRenderer.beginWorldState();
        for (NineStepState.Instance inst : NineStepState.ACTIVE.values()) {
            boolean src = dim.equals(inst.srcDim), dest = dim.equals(inst.destDim);
            if (!src && !dest) continue;
            drawInstance(f, inst, ctx.tickDelta(), src, dest);
        }
        AscensionRenderer.endWorldState();
    }

    private static double sm(double x) {
        return NineStepTimeline.smoother(x);
    }

    private static void drawInstance(AscensionRenderer.Frame f, NineStepState.Instance in, float td, boolean src, boolean dest) {
        double t = in.ticks + td;
        final int ds = NineStepTimeline.dashStart();
        final int tl = in.tLand();
        final int L = NineStepTimeline.LEAD, S = NineStepTimeline.STEP;
        float out = in.endAt < 0 ? 1f : 1f - (float) NineStepTimeline.clamp01((t - in.endAt) / NineStepTimeline.FADE);
        out *= 1f - (float) sm((t - (tl + NineStepTimeline.LAND_FX)) / NineStepTimeline.FADE);
        if (out <= 0.005f) return;

        SOLID.clear();
        GLOW.clear();
        GLOW.twoSided = true;
        SOLID.twoSided = false;

        Vec3d dir = in.dir;
        Vec3d fd = in.flightDir();
        Vec3d right = UP.crossProduct(dir).normalize();

        if (src) {
            // ---- รอยแหวกมิติ (ประตู) ปลายบันได: เปิดตอนเริ่ม ปิดหลังพุ่งผ่าน
            Vec3d gate = NineStepTimeline.riftFoot(in.start, dir).add(0, 1.6, 0);
            double gOpen = sm((t - 2) / L) * (1.0 - sm((t - (ds + (in.jump ? in.dashT : 8))) / 16.0));
            if (gOpen > 0.01) {
                NineStepModel.rift(GLOW, gate, right, UP, 4.6, gOpen, t, 0.95f);
                GLOW.alpha = (float) gOpen * 0.7f;
                NineStepModel.halo(GLOW, gate, right, UP, 5.6 * gOpen, t * 0.015, (float) gOpen * 0.7f);
                GLOW.alpha = 1f;
            }

            // ---- แท่นก้าวทั้ง 9 + จุดเริ่ม และภาพติดตา
            for (int i = 0; i <= NineStepTimeline.STEPS; i++) {
                double ti = i == 0 ? 3 : L + i * S;
                float a = (float) (sm((t - ti + 6) / 6.0) * (1.0 - sm((t - (ds + 6)) / 34.0)));
                if (a > 0.01f) {
                    Vec3d c = NineStepTimeline.stair(in.start, dir, i).add(0, 0.06, 0);
                    NineStepModel.mist(GLOW, c, 2.6 + 0.12 * i, i * 0.3 + t * 0.012, Math.max(0, Math.min(8, i - 1)), t, Math.max(0, t - ti), a);
                }
                if (i < NineStepTimeline.STEPS) {
                    double tg = L + i * S + S * 0.5;
                    float ga = (float) (0.5 * sm((t - tg) / 4.0) * (1.0 - sm((t - (ds + 2)) / 18.0)));
                    if (ga > 0.02f) {
                        Vec3d c = NineStepTimeline.stair(in.start, dir, i);
                        NineStepModel.figure(GLOW, c, dir, UP, 0.6, 0.12 + 0.08 * i, t, true, ga);
                    }
                }
            }

            // ---- เส้นนำสายตาถึงปลายทาง + ตัวระบุจุดลงจอด (เฉพาะเที่ยวบินจริง)
            if (!in.jump && t > ds - 10 && t < tl) {
                float ta = (float) (sm((t - (ds - 10)) / 10.0) * 0.9);
                GLOW.alpha = ta;
                Vec3d l = in.landing.add(0, 0.12, 0);
                SymbolModel.bagua(GLOW, l, FLAT_X, FLAT_Y, 4.0 + 5.0 * sm((t - ds) / in.dashT), -t * 0.05, 0.95, true);
                SymbolModel.beam(GLOW, in.landing, 60.0, 1.0, t * 0.15, ta * 0.45f);
                GLOW.alpha = 1f;
            }
        }

        // ---- ตัวผู้ร่าย: ปีกแสง รัศมี รอยพุ่ง
        boolean casterHere = t < tl ? src : dest;
        if (casterHere) {
            Vec3d p = in.casterPos(t);
            boolean flying = t >= ds && t < tl;
            Vec3d fwd = flying ? fd : dir;
            Vec3d wr = UP.crossProduct(fwd);
            wr = wr.lengthSquared() < 1.0e-4 ? right : wr.normalize();
            double spread = sm((t - (NineStepTimeline.stepsEnd() - 4)) / 12.0) * (1.0 - sm((t - tl) / 12.0));
            if (spread > 0.02) {
                Vec3d back = p.add(0, 1.25, 0);
                NineStepModel.wings(GLOW, back, wr, UP, fwd, spread, t, 0.95f);
                NineStepModel.halo(GLOW, p.add(0, 2.05, 0).subtract(fwd.multiply(0.8)), wr, UP, 1.15, t * 0.02, (float) spread * 0.9f);
            }
            if (flying) drawDash(f, in, t, p, fd);
        }

        // ---- ลงจอด (ฝั่งโลกปลายทาง)
        if (dest) drawLanding(f, in, t, dir, right);

        MeshRender.draw(f, SOLID, MeshRender.NEUTRAL, out, false);
        MeshRender.draw(f, GLOW, MeshRender.NEUTRAL, out, true);
        drawFlares(f, in, t, out, src, dest);
    }

    // ------------------------------------------------------------------ พุ่ง

    private static void drawDash(AscensionRenderer.Frame f, NineStepState.Instance in, double t, Vec3d p, Vec3d d) {
        final int ds = NineStepTimeline.dashStart();
        final int tl = in.tLand();
        float ramp = (float) (sm((t - (ds - 2)) / 6.0) * (1.0 - sm((t - (tl - 3)) / 6.0)));
        Vec3d head = p.add(0, 1.0, 0);

        NineStepModel.dash(GLOW, head.add(d.multiply(0.4)), d, 30.0, t, 0.85f * ramp);

        // รอยพุ่งแบบ ribbon: สุ่มตำแหน่งย้อนหลังตามสูตรเวลา (ไกลขึ้นเมื่อเร็วขึ้น) 3 ชั้น
        if (t > ds + 1) {
            int n = 24;
            Vec3d[] pts = new Vec3d[n];
            for (int k = 0; k < n; k++) {
                double tk = Math.max(ds, t - k * 0.8);
                pts[k] = in.casterPos(tk).add(0, 1.0, 0);
            }
            Vec3d mid = pts[n / 2];
            Vec3d toCam = f.cam.subtract(mid);
            Vec3d wide = d.crossProduct(toCam);
            wide = wide.lengthSquared() < 1.0e-6 ? UP : wide.normalize();
            GLOW.alpha = 0.30f * ramp;
            GLOW.ribbon(pts, wide, 2.6, 0.05, NineStepModel.GOLD, 1f);
            GLOW.alpha = 0.55f * ramp;
            GLOW.ribbon(pts, wide, 1.0, 0.03, NineStepModel.PALE, 1f);
            GLOW.alpha = 1.0f * ramp;
            GLOW.ribbon(pts, wide, 0.32, 0.01, NineStepModel.WHITE, 1f);
            GLOW.alpha = 1f;
        }

        // คลื่นกระแทกเหนือเสียงที่จุดออกตัว (วงรัศมีขยาย ขวางทิศพุ่ง)
        Vec3d s9 = NineStepTimeline.stair(in.start, in.dir, NineStepTimeline.STEPS);
        Vec3d a = d.crossProduct(UP);
        a = a.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0, 0) : a.normalize();
        Vec3d b = d.crossProduct(a).normalize();
        for (int j = 0; j < 3; j++) {
            double age = t - ds - j * 3;
            if (age < 0 || age > 18) continue;
            double r = 2.0 + age * 2.8;
            GLOW.alpha = (float) (0.85 * (1.0 - age / 18.0));
            Vec3d c = s9.add(0, 1.0, 0).add(d.multiply(1.0 + j * 0.8));
            SymbolModel.ring(GLOW, c, a, b, r * 0.93, r, j % 2 == 0 ? NineStepModel.WHITE : NineStepModel.GOLD, 1f);
        }
        GLOW.alpha = 1f;

        // เงาภาพติดตาตามหลังตอนพุ่ง (ทุก 4 tick) จางลง
        for (int k = 1; k <= 5; k++) {
            double tk = t - k * 3.5;
            if (tk < ds) break;
            Vec3d c = in.casterPos(tk);
            float ga = 0.38f * (1f - k / 6f) * ramp;
            NineStepModel.figure(GLOW, c, d, UP, 0.5, 0.9, t, true, ga);
        }
    }

    // ------------------------------------------------------------------ ลงจอด

    private static void drawLanding(AscensionRenderer.Frame f, NineStepState.Instance in, double t, Vec3d dir, Vec3d right) {
        double k = t - in.tLand();
        if (k < -2 || k > NineStepTimeline.LAND_FX) return;
        Vec3d l = in.landing;
        double kk = Math.max(0, k);

        // ปากั้วบนพื้น
        float ba = (float) (sm((k + 2) / 3.0) * (1.0 - sm((k - 10) / 22.0)));
        if (ba > 0.01f) {
            SOLID.alpha = ba;
            SymbolModel.bagua(SOLID, l.add(0, 0.1, 0), FLAT_X, FLAT_Y, 7.0 * sm(kk / 8.0) + 0.5, t * 0.04, 0.95, true);
            SOLID.alpha = 1f;
        }
        // วงคลื่นบนพื้น + โดมแสง + เสาแสง
        for (int j = 0; j < 2; j++) {
            double a = kk - j * 3;
            if (a < 0 || a > 18) continue;
            double r = 1.0 + a * 0.95;
            GLOW.alpha = (float) (1.0 - a / 18.0);
            SymbolModel.ring(GLOW, l.add(0, 0.25, 0), FLAT_X, FLAT_Y, r * 0.92, r, j == 0 ? NineStepModel.WHITE : NineStepModel.GOLD, 1f);
        }
        GLOW.alpha = 1f;
        if (kk < 24) {
            float da = (float) (0.5 * (1.0 - sm((kk - 4) / 20.0)));
            SymbolModel.dome(GLOW, l.add(0, 0.4, 0), 3.0 + 10.0 * sm(kk / 12.0), kk * 0.08, 18, da);
        }
        if (kk < 24) {
            float pa = (float) (sm((k + 2) / 3.0) * (1.0 - sm(kk / 24.0)));
            SymbolModel.beam(GLOW, l, 46.0, 1.9 * (1.0 - 0.4 * kk / 24.0), t * 0.2, pa * 0.55f);
        }
        // วาร์ปข้ามระยะ: รอยแหวกด้านออกปิดลงด้านหลังผู้ร่าย
        if (in.jump) {
            double open = sm((k + 4) / 5.0) * (1.0 - sm((k - 6) / 20.0));
            if (open > 0.01) {
                Vec3d c = l.add(0, 1.8, 0).subtract(dir.multiply(2.6));
                NineStepModel.rift(GLOW, c, right, UP, 4.6, open, t, 0.95f);
            }
        }
    }

    // ------------------------------------------------------------------ แฟลร์เรืองแสง (sprite จุดเล็กๆ เสริมความฟุ้ง)

    private static void drawFlares(AscensionRenderer.Frame f, NineStepState.Instance in, double t, float out, boolean src, boolean dest) {
        final int ds = NineStepTimeline.dashStart();
        final int tl = in.tLand();
        // Batch เปิด buffer ทันทีที่สร้าง -> สร้างเฉพาะเมื่อมีแฟลร์ให้วาดจริง ไม่งั้น buffer ค้างแล้วเฟรมถัดไปพัง
        AscensionRenderer.Batch g = null;
        boolean casterHere = t < tl ? src : dest;
        if (casterHere) {
            Vec3d p = in.casterPos(t).add(0, 1.0, 0);
            double base = sm((t - NineStepTimeline.LEAD) / 10.0) * (1.0 - sm((t - tl) / 10.0));
            double size = 2.0 + 3.0 * sm((t - NineStepTimeline.stepsEnd()) / 10.0) + (t >= ds && t < tl ? 5.0 : 0.0);
            if (base > 0.02) {
                if (g == null) g = new AscensionRenderer.Batch(FLARE, true, f);
                g.billboard(p, size, t * 0.03, (float) (0.5 * base) * out);
            }
        }
        if (src) {
            Vec3d gate = NineStepTimeline.riftFoot(in.start, in.dir).add(0, 1.6, 0);
            double gOpen = sm((t - 2) / NineStepTimeline.LEAD) * (1.0 - sm((t - (ds + (in.jump ? in.dashT : 8))) / 16.0));
            if (gOpen > 0.02) {
                if (g == null) g = new AscensionRenderer.Batch(FLARE, true, f);
                g.billboard(gate, 7.0 * gOpen, t * 0.02, (float) (0.55 * gOpen) * out);
            }
        }
        if (dest) {
            double k = t - tl;
            if (k >= 0 && k < 18) {
                if (g == null) g = new AscensionRenderer.Batch(FLARE, true, f);
                g.billboard(in.landing.add(0, 1.2, 0), 14.0 * (1.0 - k / 18.0) + 3.0, t * 0.05, (float) (1.0 - k / 18.0) * out);
            }
        }
        if (g != null) g.end();
    }
}
