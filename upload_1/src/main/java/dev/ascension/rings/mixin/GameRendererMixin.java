package dev.ascension.rings.mixin;

import dev.ascension.rings.client.ClientState;
import dev.ascension.rings.client.camera.CameraEffects;
import dev.ascension.rings.client.sword.SwordCamera;
import dev.ascension.rings.client.sword.SwordState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FOV punch ระหว่าง cutscene */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void ascension$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        float t = ClientState.cinematicTime(tickDelta);
        if (t >= 0) {
            cir.setReturnValue(cir.getReturnValue() + CameraEffects.fov(t));
            return;
        }
        float ts = SwordState.cinematicTime(tickDelta);
        if (ts >= 0) cir.setReturnValue(cir.getReturnValue() + SwordCamera.fov(ts));
    }
}
