package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ascension.rings.SwordTimeline;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.client.camera.CameraEffects;
import dev.ascension.rings.client.sword.SwordState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Overlay สกิล G: แถบดำ, ความมืด, แสงฟ้าแลบ, แฟลชตอนปัก, ชื่อสกิล */
public final class SwordHud {
    private SwordHud() {}

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Timeline.clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        float t = SwordState.cinematicTime(tickDelta);
        if (t < 0f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float k = SwordState.skyIntensity(tickDelta);
        float fadeOut = 1f - Timeline.clamp01((t - (SwordTimeline.TOTAL - 20)) / 20f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ความมืดมิดทั้งจอ + vignette
        ctx.fill(0, 0, w, h, argb(k * 0.28f, 0x10001A));
        int vh = h / 3;
        ctx.fillGradient(0, 0, w, vh, argb(k * 0.7f, 0x14001E), 0x0014001E);
        ctx.fillGradient(0, h - vh, w, h, 0x0014001E, argb(k * 0.7f, 0x14001E));

        // แถบดำ cinematic
        float bars = Timeline.smooth(t / 14f) * Timeline.smooth((SwordTimeline.TOTAL - t) / 14f);
        int bar = (int) (h * 0.11f * bars);
        if (bar > 0) {
            ctx.fill(0, 0, w, bar, 0xFF000000);
            ctx.fill(0, h - bar, w, h, 0xFF000000);
        }

        // ชื่อสกิล
        float titleA = Timeline.keys(t, 76, 0, 92, 1, 140, 1, 156, 0) * fadeOut;
        if (titleA > 0.05f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, bar + 14f, 0f);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("V O I D   B L A D E"), 0, 0, argb(titleA, 0xC020FF));
            ctx.getMatrices().pop();
        }

        // ฟ้าแลบวูบวาบ (หลังรอยแยกเปิด)
        if (t > 60f && t < SwordTimeline.TOTAL - 40f) {
            float n = CameraEffects.noise(((int) (t * 1.0f)) * 0.73f);
            if (n > 0.55f) ctx.fill(0, 0, w, h, argb((n - 0.55f) * 0.7f, 0xE8D8FF));
        }

        // แฟลชขาวตอนกระบี่ปัก + แดงตามหลัง
        float flash = Timeline.keys(t, 187, 0, 191, 1, 199, 0.85f, 225, 0);
        if (flash > 0.01f) ctx.fill(0, 0, w, h, argb(flash, 0xFFFFFF));
        float red = Timeline.keys(t, 191, 0, 200, 0.4f, 260, 0);
        if (red > 0.01f) ctx.fill(0, 0, w, h, argb(red, 0x8A0020));

        RenderSystem.disableBlend();
    }
}
