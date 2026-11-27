package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

/** วาด {@link Mesh} ด้วยแสงที่คำนวณเอง (Lambert + rim light) ลงบนบัฟเฟอร์ POSITION_COLOR */
final class MeshRender {
    private static final Vec3d LIGHT = new Vec3d(0.40, 0.80, 0.45).normalize();

    /** พารามิเตอร์แสง */
    static final class Lit {
        final float ambient, rimK;
        final float[] rim;

        Lit(float ambient, float rimK, float[] rim) {
            this.ambient = ambient;
            this.rimK = rimK;
            this.rim = rim;
        }
    }

    static final Lit NEUTRAL = new Lit(0.55f, 0.35f, new float[]{0.8f, 0.85f, 1.0f});

    private MeshRender() {}

    static void draw(AscensionRenderer.Frame f, Mesh m, Lit lit, float alphaMul, boolean additive) {
        if (m.count == 0 || alphaMul <= 0.003f) return;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(!additive);

        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        double[] d = m.d;
        for (int i = 0; i < m.count; i++) {
            int o = i * Mesh.STRIDE;
            double cx = d[o] - f.cam.x, cy = d[o + 1] - f.cam.y, cz = d[o + 2] - f.cam.z;
            double nx = d[o + 3], ny = d[o + 4], nz = d[o + 5];
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double tx = 0, ty = 1, tz = 0;
            if (len > 1.0e-6) {
                tx = -cx / len;
                ty = -cy / len;
                tz = -cz / len;
            }
            double facing = nx * tx + ny * ty + nz * tz;
            if (d[o + 11] > 0.5 && facing < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
                facing = -facing;
            }
            double nl = Math.max(0.0, nx * LIGHT.x + ny * LIGHT.y + nz * LIGHT.z);
            double rim = Math.pow(1.0 - Math.min(1.0, Math.abs(facing)), 2.5);
            float emis = (float) d[o + 9];
            float sh = (float) (lit.ambient + 0.55 * nl);
            float k = sh * (1f - emis) + emis;
            float rk = (float) (rim * lit.rimK);
            float r = (float) d[o + 6] * k + rk * lit.rim[0];
            float g = (float) d[o + 7] * k + rk * lit.rim[1];
            float b = (float) d[o + 8] * k + rk * lit.rim[2];
            buf.vertex(f.m, (float) cx, (float) cy, (float) cz)
                    .color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), (float) d[o + 10] * alphaMul).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
    }
}
