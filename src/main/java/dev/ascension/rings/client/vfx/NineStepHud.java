package dev.ascension.rings.client.vfx;

import dev.ascension.rings.NineStepTimeline;
import dev.ascension.rings.client.nine.NineStepState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** HUD สกิล V: ชื่อสกิล + ตัวนับก้าว, แฟลชขาวทอง, ขอบจอเร่งความเร็วตอนพุ่ง */
public final class NineStepHud {
    private NineStepHud() {}

    private static int argb(float a, int rgb) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext ctx, float td) {
        float t = NineStepState.localTime(td);
        if (t < 0f) return;
        NineStepState.Instance in = NineStepState.local();
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ขอบจอเร่งความเร็ว (ทอง-ขาว) ตอนพุ่ง
        float ds = NineStepTimeline.dashStart();
        float tl = in.tLand();
        float dash = (float) (NineStepTimeline.smoother((t - (ds - 4f)) / 8f) * (1.0 - NineStepTimeline.smoother((t - (tl - 2f)) / 10f)));
        if (dash > 0.02f) {
            int bands = 14;
            for (int i = 0; i < bands; i++) {
                float k = 1f - (float) i / bands;
                int a = argb(0.30f * dash * k * k, 0xFFE9A8);
                int x = (int) (w * 0.20f * i / bands);
                ctx.fill(x, 0, x + Math.max(1, (int) (w * 0.20f / bands)), h, a);
                ctx.fill(w - x - Math.max(1, (int) (w * 0.20f / bands)), 0, w - x, h, a);
            }
            ctx.fillGradient(0, 0, w, (int) (h * 0.12f), argb(0.22f * dash, 0xFFF3D0), 0x00FFFFFF);
            ctx.fillGradient(0, h - (int) (h * 0.12f), w, h, 0x00FFFFFF, argb(0.22f * dash, 0xFFF3D0));
        }

        float fl = NineStepState.flash(td);
        if (fl > 0.01f) ctx.fill(0, 0, w, h, argb(fl, 0xFFF6DC));

        // ชื่อสกิล + ก้าวที่
        float titleA = (float) (NineStepTimeline.smoother((t - 3f) / 8f) * (1.0 - NineStepTimeline.smoother((t - (ds + 8f)) / 14f)));
        if (titleA > 0.02f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h * 0.2f, 0f);
            ctx.getMatrices().scale(2.2f, 2.2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.ninestep_title"), 0, 0, argb(titleA, 0xFFE9A8));
            ctx.getMatrices().pop();
            int step = (int) Math.floor((t - NineStepTimeline.LEAD) / NineStepTimeline.STEP) + 1;
            if (step >= 1) {
                step = Math.min(step, NineStepTimeline.STEPS);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.ninestep_step", step, NineStepTimeline.STEPS),
                        w / 2, (int) (h * 0.2f) + 26, argb(titleA, 0xFFFFFF));
            }
        }
    }
}
