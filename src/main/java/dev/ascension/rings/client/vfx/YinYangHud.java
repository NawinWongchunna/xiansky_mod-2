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

    /** โลกหยินหยาง: ขาวบังตอนพุ่งเข้าคัมภีร์, แฟลชตอนเข้า/ออก, ชื่อโลก + คำใบ้ปุ่มออก */
    private static void renderRealmLayer(DrawContext ctx, MinecraftClient mc, int w, int h, float td) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float dive = YinYangState.diveWhite(td);
        if (dive > 0.01f) ctx.fill(0, 0, w, h, argb(dive, 0xFFFFFF));
        if (YinYangState.flash > 0) {
            float fl = Math.max(0f, (YinYangState.flash - td) / YinYangState.FLASH_MAX);
            ctx.fill(0, 0, w, h, argb(fl * fl, 0xFFFFFF));
        }
        if (YinYangState.inRealm) {
            float rt = YinYangState.realmTicks + td;
            float titleA = Timeline.keys(rt, 20, 0, 45, 1, 110, 1, 150, 0);
            if (titleA > 0.05f) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h * 0.2f, 0f);
                ctx.getMatrices().scale(2.4f, 2.4f, 1f);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.yinyang_realm"), 0, 0, argb(titleA, 0xF2E6B8));
                ctx.getMatrices().pop();
            }
            float hint = Timeline.keys(rt, 100, 0, 130, 0.8f);
            if (hint > 0.02f) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.yinyang_leave"),
                        w / 2, h - 62, argb(hint * (0.7f + 0.3f * (float) Math.sin(rt * 0.12)), 0xF2E6B8));
            }
        }
        RenderSystem.disableBlend();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        renderRealmLayer(ctx, mc, w, h, tickDelta);

        float t = YinYangState.localTime(tickDelta);
        if (t < 0f) return;
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
                    w / 2, h - 74, argb(pulse, 0xF2E6B8));
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.translatable("message.ascension_rings.yinyang_dive"),
                    w / 2, h - 62, argb(pulse * 0.9f, 0xCFE8FF));
            net.minecraft.util.math.Vec3d aim = YinYangState.aimNow;
            if (aim != null && mc.player != null) {
                int dist = (int) Math.round(aim.distanceTo(mc.player.getPos()));
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(dist + " m"), w / 2, h - 50, argb(0.9f, 0xFFFFFF));
            }
        }

        if (s >= 0f) {
            int fly = YinYangState.localFly();
            float k = s - fly;
            // ก่อนระเบิด: ขอบจอสว่างขึ้นเล็กน้อย
            float pre = Timeline.keys(s, 0, 0, fly, 0.25f);
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
