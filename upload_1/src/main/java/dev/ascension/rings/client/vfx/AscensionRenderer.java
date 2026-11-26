package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ascension.rings.AscensionMod;
import dev.ascension.rings.Timeline;
import dev.ascension.rings.client.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * วาด VFX ในโลก (ทุกอย่างเป็น quad + texture จากรูปที่ให้มา):
 *  - magic_circle : วงเวทย์ทอง-แดง วางพื้นตรงจุดเริ่ม + ใต้เท้าผู้เล่น (หมุน)
 *  - lily         : ดอกเถาวัลย์แดง ผลิบานรอบวงเวทย์
 *  - ring_0..3    : วงแหวนไฟ 3 ชั้นรอบตัว (flipbook)
 *  - vortex_0..1  : แกนวอร์เท็กซ์เหนือหัวตอนชาร์จ
 *  - orb          : ลูกพลัง comet วนเข้าหาแกน และพุ่งออกตอน burst
 */
public final class AscensionRenderer {
    private static final double TAU = Math.PI * 2;
    private static final int LILY_COUNT = 14;
    /** สัดส่วน lily.png (กว้าง/สูง) = 310/616 */
    private static final double LILY_ASPECT = 310.0 / 616.0;

    private static final Identifier[] RING = new Identifier[4];
    private static final Identifier[] VORTEX = new Identifier[2];
    private static final Identifier ORB = tex("orb");
    private static final Identifier CIRCLE = tex("magic_circle");
    private static final Identifier LILY = tex("lily");

    static {
        for (int i = 0; i < RING.length; i++) RING[i] = tex("ring_" + i);
        for (int i = 0; i < VORTEX.length; i++) VORTEX[i] = tex("vortex_" + i);
    }

    private AscensionRenderer() {}

    static Identifier tex(String name) {
        return new Identifier(AscensionMod.MOD_ID, "textures/vfx/" + name + ".png");
    }

    /** ข้อมูลต่อเฟรมของผู้เล่นหนึ่งคน */
    static final class Frame {
        Matrix4f m;
        Vec3d cam, camRight, camUp;
        float t;
        Vec3d pos, start;
        float out;
    }

    public static void render(WorldRenderContext ctx) {
        if (ClientState.ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        float td = ctx.tickDelta();
        Frame f = makeFrame(ctx);
        beginWorldState();

        for (ClientState.Instance inst : ClientState.ACTIVE.values()) {
            PlayerEntity p = world.getPlayerByUuid(inst.uuid);
            if (p == null) continue;
            f.t = inst.ticks + td;
            f.pos = p.getLerpedPos(td);
            f.start = inst.start;
            f.out = 1f - Timeline.clamp01((f.t - (Timeline.TOTAL - 20)) / 20f);

            drawCircles(f);
            drawLilies(f);
            drawCore(f);
            drawRings(f);
            drawBurst(f);
        }

        endWorldState();
    }

    /** เตรียมข้อมูลกล้อง/เมทริกซ์ของเฟรมนี้ (ใช้ร่วมกับ SwordRenderer) */
    static Frame makeFrame(WorldRenderContext ctx) {
        Camera camera = ctx.camera();
        Quaternionf rot = camera.getRotation();
        Vector3f r = new Vector3f(-1, 0, 0).rotate(rot);
        Vector3f u = new Vector3f(0, 1, 0).rotate(rot);
        Frame f = new Frame();
        f.m = ctx.matrixStack().peek().getPositionMatrix();
        f.cam = camera.getPos();
        f.camRight = new Vec3d(r.x, r.y, r.z);
        f.camUp = new Vec3d(u.x, u.y, u.z);
        return f;
    }

    static void beginWorldState() {
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    static void endWorldState() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------
    // เอฟเฟกต์แต่ละชั้น
    // ------------------------------------------------------------------

    /** วงเวทย์: พื้นตรงจุดเริ่ม (ใหญ่) + ใต้เท้า (เล็ก, ตามตัวขึ้นฟ้า) */
    private static void drawCircles(Frame f) {
        float open = Timeline.smooth((f.t - 6f) / 36f);
        if (open <= 0f) return;
        float alpha = open * f.out;
        double expand = f.t > Timeline.BURST ? 1.0 + (f.t - Timeline.BURST) * 0.05 : 1.0;

        Batch b = new Batch(CIRCLE, false, f);
        flat(b, new Vec3d(f.start.x, f.start.y + 0.03, f.start.z), 7.0 * open * expand, f.t * 0.015, alpha);
        flat(b, new Vec3d(f.pos.x, f.pos.y - 0.15, f.pos.z), 3.2 * open, -f.t * 0.03, alpha * 0.9f);
        b.end();
    }

    private static void flat(Batch b, Vec3d c, double s, double angle, float alpha) {
        Vec3d right = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).multiply(s);
        Vec3d up = new Vec3d(-Math.sin(angle), 0, Math.cos(angle)).multiply(s);
        b.quad(c, right, up, alpha);
    }

    /** ดอกเถาวัลย์แดงผลิบานรอบวงเวทย์ (billboard ล็อกแกน Y) */
    private static void drawLilies(Frame f) {
        if (f.t < 24f) return;
        Batch b = new Batch(LILY, false, f);
        for (int i = 0; i < LILY_COUNT; i++) {
            float grow = Timeline.smooth((f.t - (24f + i * 3f)) / 18f);
            if (grow <= 0f) continue;

            double ang = i * (TAU / LILY_COUNT) + rand(i, 0) * 0.35;
            double rad = 3.5 + rand(i, 1) * 3.5;
            double x = f.start.x + Math.cos(ang) * rad;
            double z = f.start.z + Math.sin(ang) * rad;

            double hh = 0.95 * (0.8 + 0.5 * rand(i, 2)) * grow; // ครึ่งความสูง
            double hw = hh * LILY_ASPECT;

            double dx = f.cam.x - x, dz = f.cam.z - z;
            double len = Math.hypot(dx, dz);
            if (len < 1e-4) continue;
            Vec3d right = new Vec3d(dz / len, 0, -dx / len).multiply(hw);
            Vec3d up = new Vec3d(0, hh, 0);
            b.quad(new Vec3d(x, f.start.y + hh * 0.95, z), right, up, grow * f.out);
        }
        b.end();
    }

    /** แกนวอร์เท็กซ์เหนือหัว + ลูกพลังวนเข้าหาแกน */
    private static void drawCore(Frame f) {
        if (f.t < Timeline.CORE_START) return;
        float g = Timeline.smooth((f.t - Timeline.CORE_START) / 48f);
        float vanish = f.t < Timeline.BURST ? 1f : 1f - Timeline.clamp01((f.t - Timeline.BURST) / 6f);
        if (vanish <= 0f) return;
        Vec3d core = f.pos.add(0, 3.4, 0);

        double size = 2.8 * g * (1.0 + 0.06 * Math.sin(f.t * 0.8));
        Batch v = new Batch(VORTEX[((int) (f.t / 3f)) % 2], false, f);
        v.billboard(core, size, f.t * 0.3, vanish * f.out);
        v.end();

        Batch o = new Batch(ORB, true, f);
        for (int k = 0; k < 6; k++) {
            double ang = f.t * 0.22 + k * (TAU / 6);
            double rad = 5.5 + (0.9 - 5.5) * g;
            double cs = Math.cos(ang), sn = Math.sin(ang);
            Vec3d c = core.add(cs * rad, Math.sin(f.t * 0.1 + k) * 0.6, sn * rad);
            Vec3d dir = new Vec3d(-sn - cs * 0.3, 0, cs - sn * 0.3).normalize();
            o.streak(c, dir, 2.4, 1.0, g * vanish * f.out);
        }
        o.end();
    }

    /** วงแหวนไฟ 3 ชั้นรอบตัว (billboard, flipbook 4 เฟรม) */
    private static void drawRings(Frame f) {
        for (int i = 0; i < 3; i++) {
            float g = Timeline.smooth((f.t - i * 20f) / 14f);
            if (g <= 0f) continue;
            double hy = 0.35 + i * 0.7 + 0.12 * Math.sin(f.t * 0.15 + i * 2.0);
            double expand = f.t > Timeline.BURST ? 1.0 + (f.t - Timeline.BURST) * 0.12 : 1.0;
            double size = (1.5 + i * 0.25) * g * expand;
            float alpha = g * f.out * (f.t > Timeline.BURST ? 1f - Timeline.clamp01((f.t - Timeline.BURST) / 24f) : 1f);
            if (alpha <= 0f) continue;

            int frame = (((int) (f.t / 2f)) + i) % 4;
            Batch b = new Batch(RING[frame], true, f);
            b.billboard(f.pos.add(0, hy, 0), size, 0.0, alpha);
            b.end();
        }
    }

    /** ตอน burst: ลูกพลังพุ่งออกรอบตัว 2 ชั้น */
    private static void drawBurst(Frame f) {
        float k = f.t - Timeline.BURST;
        if (k < 0f || k > 30f) return;
        float alpha = 1f - k / 30f;
        Batch b = new Batch(ORB, true, f);
        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i < 16; i++) {
                double ang = i * (TAU / 16) + layer * 0.2;
                Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                double dist = 2.0 + k * 1.1 * (1.0 + 0.4 * layer);
                Vec3d c = f.pos.add(0, 1.5 + layer * 1.2, 0).add(dir.multiply(dist));
                b.streak(c, dir, 3.5, 1.4, alpha);
            }
        }
        b.end();
    }

    private static double rand(int i, int k) {
        double x = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
        return x - Math.floor(x);
    }

    // ------------------------------------------------------------------
    // ตัวช่วยวาด quad (Tessellator + POSITION_TEXTURE_COLOR, ไม่โดน lighting = เรืองแสง)
    // ------------------------------------------------------------------

    static final class Batch {
        private final BufferBuilder buf;
        private final Frame f;

        Batch(Identifier texture, boolean additive, Frame f) {
            this.f = f;
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            if (additive) {
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            buf = Tessellator.getInstance().getBuffer();
            buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        }

        /** center เป็นพิกัดโลก, right/up คือเวกเตอร์ครึ่งขนาด */
        void quad(Vec3d center, Vec3d right, Vec3d up, float alpha) {
            if (alpha <= 0f) return;
            Vec3d c = center.subtract(f.cam);
            vert(c.subtract(right).subtract(up), 0f, 1f, alpha);
            vert(c.add(right).subtract(up), 1f, 1f, alpha);
            vert(c.add(right).add(up), 1f, 0f, alpha);
            vert(c.subtract(right).add(up), 0f, 0f, alpha);
        }

        /** หันเข้าหากล้อง พร้อมหมุน roll */
        void billboard(Vec3d center, double halfSize, double roll, float alpha) {
            double cs = Math.cos(roll), sn = Math.sin(roll);
            Vec3d right = f.camRight.multiply(cs).add(f.camUp.multiply(sn)).multiply(halfSize);
            Vec3d up = f.camUp.multiply(cs).subtract(f.camRight.multiply(sn)).multiply(halfSize);
            quad(center, right, up, alpha);
        }

        /** แถบยาวตามทิศ dir (หัว comet ชี้ไปทาง dir) หันหน้าเข้ากล้อง */
        void streak(Vec3d center, Vec3d dir, double length, double width, float alpha) {
            Vec3d rel = center.subtract(f.cam);
            Vec3d side = dir.crossProduct(rel);
            if (side.lengthSquared() < 1e-6) return;
            side = side.normalize().multiply(width / 2.0);
            quad(center, dir.multiply(length / 2.0), side, alpha);
        }

        /** quad ที่ตัดภาพด้านบนออก: texture v ไล่จาก v0 (ขอบบน) ถึง 1 (ขอบล่าง) */
        void quadV(Vec3d center, Vec3d right, Vec3d up, float alpha, float v0) {
            if (alpha <= 0f) return;
            Vec3d c = center.subtract(f.cam);
            vert(c.subtract(right).subtract(up), 0f, 1f, alpha);
            vert(c.add(right).subtract(up), 1f, 1f, alpha);
            vert(c.add(right).add(up), 1f, v0, alpha);
            vert(c.subtract(right).add(up), 0f, v0, alpha);
        }

        /** billboard สี่เหลี่ยมผืนผ้า (กว้าง halfW, สูง halfH) พร้อมหมุน roll */
        void billboardRect(Vec3d center, double halfW, double halfH, double roll, float alpha) {
            double cs = Math.cos(roll), sn = Math.sin(roll);
            Vec3d right = f.camRight.multiply(cs).add(f.camUp.multiply(sn)).multiply(halfW);
            Vec3d up = f.camUp.multiply(cs).subtract(f.camRight.multiply(sn)).multiply(halfH);
            quad(center, right, up, alpha);
        }

        /**
         * ตัดเป็นพัด (sector) ของ sprite วงกลมรอบกึ่งกลางภาพ ตั้งแต่มุม a0 ถึง a1 (เรเดียน, วัดในพิกัด texture ที่แกน y ชี้ลง)
         * alpha ไล่จาก 0 ที่ขอบ a0 ขึ้นไปเต็มที่ภายใน fadeWidth (ใช้ทำเอฟเฟกต์ "โผล่ออกมาจากผิวน้ำ")
         * right/up คือเวกเตอร์ครึ่งขนาดของ sprite ทั้งภาพ
         */
        void sector(Vec3d center, Vec3d right, Vec3d up, double a0, double a1, double fadeWidth, float alpha) {
            if (alpha <= 0f || a1 <= a0) return;
            Vec3d c = center.subtract(f.cam);
            int n = Math.max(1, (int) Math.ceil((a1 - a0) / 0.04));
            double step = (a1 - a0) / n;
            for (int i = 0; i < n; i++) {
                double s0 = a0 + i * step, s1 = s0 + step;
                double mid = (s0 + s1) * 0.5;
                float a = alpha * (float) dev.ascension.rings.YinYangTimeline.smooth((mid - a0) / fadeWidth);
                if (a <= 0f) continue;
                float u0 = (float) (0.5 + 0.5 * Math.cos(s0)), v0 = (float) (0.5 + 0.5 * Math.sin(s0));
                float u1 = (float) (0.5 + 0.5 * Math.cos(s1)), v1 = (float) (0.5 + 0.5 * Math.sin(s1));
                vert(c, 0.5f, 0.5f, a);
                vert(c.add(right.multiply(2 * u0 - 1)).add(up.multiply(1 - 2 * v0)), u0, v0, a);
                vert(c.add(right.multiply(2 * u1 - 1)).add(up.multiply(1 - 2 * v1)), u1, v1, a);
                vert(c.add(right.multiply(2 * u1 - 1)).add(up.multiply(1 - 2 * v1)), u1, v1, a);
            }
        }

        private void vert(Vec3d p, float u, float v, float a) {
            buf.vertex(f.m, (float) p.x, (float) p.y, (float) p.z).texture(u, v).color(1f, 1f, 1f, a).next();
        }

        void end() {
            Tessellator.getInstance().draw();
        }
    }
}
