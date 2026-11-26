package dev.ascension.rings.client.vfx;

import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.SwordTimeline;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.client.sword.SwordState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * VFX ของสกิล G:
 *  - รอยแยกมิติแนวนอนบนฟ้า (rift 12 เฟรม เปิดแล้วหุบ)
 *  - กระบี่ดำค่อยๆ โผล่ออกจากรอยแยก (ตัดส่วนที่ยังอยู่ในรอยแยก) แล้วพุ่งปักพื้น
 *  - เปลวออร่าปีศาจที่โคนกระบี่ + ตามขอบคลื่นกระแทก
 *  - วงคลื่นกระแทกแผ่บนพื้น
 */
public final class SwordRenderer {
    private static final Identifier[] SWORD = new Identifier[8];
    private static final Identifier[] RIFT = new Identifier[12];
    private static final Identifier[] FLAME = new Identifier[12];
    private static final Identifier SHOCK = blade("shockwave");

    /** บล็อกต่อพิกเซลของ sprite กระบี่ (384x896 px) */
    private static final double S = 0.075;
    private static final double SPR_W = 384 * S;
    private static final double SPR_H = 896 * S;
    /** ปลายกระบี่อยู่ที่ y=785px จากบนของ sprite -> ขอบล่างต่ำกว่าปลายอยู่ (896-785)px */
    private static final double TIP_MARGIN = (896 - 785) * S;

    static {
        for (int i = 0; i < SWORD.length; i++) SWORD[i] = blade(String.format("sword_%02d", i));
        for (int i = 0; i < RIFT.length; i++) RIFT[i] = blade(String.format("rift_%02d", i));
        for (int i = 0; i < FLAME.length; i++) FLAME[i] = blade(String.format("flame_%02d", i));
    }

    private SwordRenderer() {}

    private static Identifier blade(String name) {
        return new Identifier(AscensionMod.MOD_ID, "textures/vfx/blade/" + name + ".png");
    }

    public static void render(WorldRenderContext ctx) {
        if (SwordState.ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        AscensionRenderer.Frame f = AscensionRenderer.makeFrame(ctx);
        AscensionRenderer.beginWorldState();
        for (SwordState.Instance inst : SwordState.ACTIVE.values()) {
            float t = inst.ticks + ctx.tickDelta();
            float out = 1f - Timeline.clamp01((t - SwordTimeline.FADE_START) / (SwordTimeline.TOTAL - SwordTimeline.FADE_START));
            drawRift(f, inst, t, out);
            drawShock(f, inst, t, out);
            drawFlames(f, inst, t, out, world);
            drawSword(f, inst, t, out);
        }
        AscensionRenderer.endWorldState();
    }

    // ------------------------------------------------------------------

    private static void drawRift(AscensionRenderer.Frame f, SwordState.Instance inst, float t, float out) {
        if (t < SwordTimeline.RAISE_END) return;
        // เปิดช้าๆ แล้วหุบหลังกระบี่ปัก
        float p = t < SwordTimeline.IMPACT + 5
                ? Timeline.smooth((t - SwordTimeline.RAISE_END) / 50f)
                : 1f - Timeline.smooth((t - (SwordTimeline.IMPACT + 5)) / 35f);
        if (p <= 0.01f) return;

        int frame = p < 0.999f ? Math.min(9, (int) (p * 10f)) : 9 + ((int) (t / 3f)) % 3;
        Vec3d c = inst.impact.add(0, SwordTimeline.RIFT_H, 0);
        double halfH = 40.0;               // ด้านยาวของรอยแยก = 80 บล็อก (หมุนให้นอนแนวนอน)
        double halfW = halfH * 420.0 / 640.0;

        AscensionRenderer.Batch b = new AscensionRenderer.Batch(RIFT[frame], false, f);
        b.billboardRect(c, halfW, halfH, Math.PI / 2, Math.min(1f, p * 3f) * out);
        b.end();
    }

    private static void drawSword(AscensionRenderer.Frame f, SwordState.Instance inst, float t, float out) {
        if (t < SwordTimeline.RIFT_OPEN_END) return;
        Vec3d c = inst.impact;
        double tip = c.y + SwordTimeline.tipOffset(t);
        double bottom = tip - TIP_MARGIN;
        double yE = c.y + SwordTimeline.RIFT_H;      // เส้นที่กระบี่โผล่ออกมา
        double top = bottom + SPR_H;
        double yTop = Math.min(top, yE);
        if (yTop <= bottom + 0.01) return;

        float v0 = (float) Math.max(0.0, (top - yE) / SPR_H);
        float alpha = Timeline.smooth((t - SwordTimeline.RIFT_OPEN_END) / 10f) * out;

        double dx = f.cam.x - c.x, dz = f.cam.z - c.z;
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-4) return;
        Vec3d right = new Vec3d(dz / len, 0, -dx / len).multiply(SPR_W / 2.0);
        Vec3d up = new Vec3d(0, (yTop - bottom) / 2.0, 0);
        Vec3d center = new Vec3d(c.x, (bottom + yTop) / 2.0, c.z);

        AscensionRenderer.Batch b = new AscensionRenderer.Batch(SWORD[((int) (t / 2f)) % SWORD.length], false, f);
        b.quadV(center, right, up, alpha, v0);
        b.end();
    }

    private static void drawShock(AscensionRenderer.Frame f, SwordState.Instance inst, float t, float out) {
        if (t < SwordTimeline.IMPACT) return;
        double r = SwordTimeline.waveRadius(t, inst.radius);
        if (r < 1.0) return;
        float frac = Timeline.clamp01((t - SwordTimeline.IMPACT) / SwordTimeline.WAVE);
        float alpha = (1f - frac * frac) * out;
        Vec3d c = inst.impact;

        AscensionRenderer.Batch b = new AscensionRenderer.Batch(SHOCK, true, f);
        for (int k = 0; k < 2; k++) {
            double rr = r * (k == 0 ? 1.0 : 0.72);
            double half = rr / 0.90;
            double ang = t * 0.01 * (k == 0 ? 1 : -1);
            Vec3d right = new Vec3d(Math.cos(ang), 0, Math.sin(ang)).multiply(half);
            Vec3d up = new Vec3d(-Math.sin(ang), 0, Math.cos(ang)).multiply(half);
            b.quad(new Vec3d(c.x, c.y + 0.5 + k * 0.1, c.z), right, up, alpha * (k == 0 ? 1f : 0.7f));
        }
        b.end();
    }

    private static void drawFlames(AscensionRenderer.Frame f, SwordState.Instance inst, float t, float out, ClientWorld world) {
        if (t < SwordTimeline.IMPACT) return;
        Vec3d c = inst.impact;
        float base = Timeline.smooth((t - SwordTimeline.IMPACT) / 10f) * out;
        double r = SwordTimeline.waveRadius(t, inst.radius);
        float frac = Timeline.clamp01((t - SwordTimeline.IMPACT) / SwordTimeline.WAVE);

        AscensionRenderer.Batch b = null;
        // เปลวรอบโคนกระบี่ + ตามขอบคลื่น (วาดทีละเฟรม texture)
        for (int i = 0; i < 12; i++) {
            int fr = (((int) (t / 2f)) + i * 3) % FLAME.length;
            b = new AscensionRenderer.Batch(FLAME[fr], false, f);
            // โคนกระบี่
            double a1 = i * (Math.PI * 2 / 12) + t * 0.01;
            double rad = 5.0 + 3.0 * Math.sin(t * 0.07 + i);
            flame(b, f, c.x + Math.cos(a1) * rad, c.y, c.z + Math.sin(a1) * rad, 6.0, base);
            // ขอบคลื่น
            if (r > 3.0) {
                double a2 = i * (Math.PI * 2 / 12) + 0.26;
                double x = c.x + Math.cos(a2) * r, z = c.z + Math.sin(a2) * r;
                double y = c.y;
                if (world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                    y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                }
                flame(b, f, x, y, z, 7.0 + 5.0 * (1.0 - frac), (1f - frac * 0.8f) * out);
            }
            b.end();
        }
    }

    /** เปลว billboard ล็อกแกน Y ตั้งบนพื้นที่ (x, y, z) ครึ่งความสูง = size */
    private static void flame(AscensionRenderer.Batch b, AscensionRenderer.Frame f, double x, double y, double z, double size, float alpha) {
        double dx = f.cam.x - x, dz = f.cam.z - z;
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-4) return;
        Vec3d right = new Vec3d(dz / len, 0, -dx / len).multiply(size);
        Vec3d up = new Vec3d(0, size, 0);
        b.quad(new Vec3d(x, y + size * 0.95, z), right, up, alpha);
    }
}
