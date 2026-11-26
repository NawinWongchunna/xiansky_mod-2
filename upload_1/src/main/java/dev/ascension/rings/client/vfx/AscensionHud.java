package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Overlay ตอน cutscene: แถบดำ, vignette แดง, ดอกเถาวัลย์ข้างจอ, flash ขาว, ชื่อสกิล */
public final class AscensionHud {
    private static final Identifier LILY = AscensionRenderer.tex("lily");
    private static final int LILY_W = 310, LILY_H = 616;

    private AscensionHud() {}

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Timeline.clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        float t = ClientState.cinematicTime(tickDelta);
        if (t < 0f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float k = ClientState.skyIntensity(tickDelta);
        float fadeOut = 1f - Timeline.clamp01((t - (Timeline.TOTAL - 14)) / 14f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // โทนแดงทั้งจอ + vignette บน/ล่าง
        ctx.fill(0, 0, w, h, argb(k * 0.18f, 0x500000));
        int vh = h / 3;
        ctx.fillGradient(0, 0, w, vh, argb(k * 0.55f, 0x300000), 0x00300000);
        ctx.fillGradient(0, h - vh, w, h, 0x00300000, argb(k * 0.55f, 0x300000));

        // ดอกเถาวัลย์ overlay ขวาจอ (โผล่ช่วงชาร์จ แล้วจางหาย)
        float lilyA = Timeline.keys(t, 24, 0, 55, 0.85f, 105, 0.85f, 125, 0) * fadeOut;
        if (lilyA > 0.01f) {
            int lh = (int) (h * 0.95f);
            int lw = (int) (lh * (LILY_W / (float) LILY_H));
            int x = w - lw - (int) (w * 0.04f);
            int y = (h - lh) / 2;
            RenderSystem.setShaderColor(1f, 1f, 1f, lilyA);
            ctx.drawTexture(LILY, x, y, lw, lh, 0, 0, LILY_W, LILY_H, LILY_W, LILY_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // แถบดำ cinematic
        float bars = Timeline.smooth(t / 12f) * Timeline.smooth((Timeline.TOTAL - t) / 12f);
        int bar = (int) (h * 0.11f * bars);
        if (bar > 0) {
            ctx.fill(0, 0, w, bar, 0xFF000000);
            ctx.fill(0, h - bar, w, h, 0xFF000000);
        }

        // ชื่อสกิล
        float titleA = Timeline.keys(t, 66, 0, 80, 1, 112, 1, 120, 0) * fadeOut;
        if (titleA > 0.05f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, bar + 14f, 0f);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("A S C E N S I O N"), 0, 0, argb(titleA, 0xFF3030));
            ctx.getMatrices().pop();
        }

        // flash ขาวตอนปล่อยพลัง
        float flash = Timeline.keys(t, 116, 0, 120, 1, 124, 0.85f, 140, 0);
        if (flash > 0.01f) {
            ctx.fill(0, 0, w, h, argb(flash, 0xFFFFFF));
        }

        RenderSystem.disableBlend();
    }
}
