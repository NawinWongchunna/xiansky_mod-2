package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.YinYangTimeline;
import dev.ascension.rings.client.yinyang.YinYangState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Overlay สกิล J: ชื่อสกิล, คำใบ้ปุ่มกดอีกครั้ง, แฟลชขาว-ดำตอนระเบิด (ไม่มีแถบดำ ไม่มีกล้องสั่น) */
public final class YinYangHud {
    private YinYangHud() {}

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Timeline.clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        float t = YinYangState.localTime(tickDelta);
        if (t < 0f) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float s = YinYangState.localStrikeTime(tickDelta);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // ชื่อสกิล
        float titleA = Timeline.keys(t, 30, 0, 50, 1, 100, 1, 125, 0);
        if (titleA > 0.05f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h * 0.16f, 0f);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("YIN-YANG SCRIPTURE"), 0, 0, argb(titleA, 0xF2E6B8));
            ctx.getMatrices().pop();
        }

        // คำใบ้: กดอีกครั้งเพื่อสั่งมังกร
        if (s < 0f && t >= YinYangTimeline.READY) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 0.15);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.yinyang_ready"),
                    w / 2, h - 62, argb(pulse, 0xF2E6B8));
        }

        if (s >= 0f) {
            float k = s - YinYangTimeline.FLY;
            // ก่อนระเบิด: ขอบจอสว่างขึ้นเล็กน้อย
            float pre = Timeline.keys(s, 0, 0, YinYangTimeline.FLY, 0.25f);
            if (k < 0f && pre > 0.01f) ctx.fill(0, 0, w, h, argb(pre, 0xFFFFFF));
            if (k >= 0f) {
                // แฟลชขาว -> วูบดำ -> เทาจางลง (โลกขาวดำ)
                float white = Timeline.keys(k, 0, 0, 2, 1, 12, 0.85f, 32, 0);
                if (white > 0.01f) ctx.fill(0, 0, w, h, argb(white, 0xFFFFFF));
                float dark = Timeline.keys(k, 10, 0, 16, 0.5f, 40, 0);
                if (dark > 0.01f) ctx.fill(0, 0, w, h, argb(dark, 0x000000));
                float gray = Timeline.keys(k, 12, 0, 30, 0.22f, 120, 0.16f, 200, 0);
                if (gray > 0.01f) ctx.fill(0, 0, w, h, argb(gray, 0x808080));
            }
        }

        RenderSystem.disableBlend();
    }
}
