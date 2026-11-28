package dev.ascension.rings.mixin;

import dev.ascension.rings.client.nine.NineStepState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** เอนตัวไปข้างหน้าตอนพุ่ง (สกิล V) */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void ascension$lean(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, CallbackInfo ci) {
        float[] sw = NineStepState.stepWalk(player.getUuid(), tickDelta);
        if (sw != null) {
            // เอนตัวไปข้างหน้าเล็กน้อยและโยกตามจังหวะก้าว
            float lean = (10.0f + 5.0f * (float) Math.sin(Math.PI * sw[0])) * sw[2];
            matrices.translate(0.0, 0.9, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lean));
            matrices.translate(0.0, -0.9, 0.0);
        }
        float k = NineStepState.dashPose(player.getUuid(), tickDelta);
        if (k <= 0f) return;
        matrices.translate(0.0, 0.9, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-72.0f * k));
        matrices.translate(0.0, -0.9, 0.0);
    }
}
