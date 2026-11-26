package dev.ascension.rings.mixin;

import dev.ascension.rings.client.ClientState;
import dev.ascension.rings.client.sword.SwordState;
import dev.ascension.rings.client.yinyang.YinYangState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ท้องฟ้า/เมฆ เปลี่ยนเป็นสีแดงเข้มตามความเข้มของสกิล */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Unique
    private static final Vec3d ASCENSION_SKY = new Vec3d(0.32, 0.0, 0.03);
    @Unique
    private static final Vec3d VOID_SKY = new Vec3d(0.07, 0.0, 0.10);
    @Unique
    private static final Vec3d MONO_SKY = new Vec3d(0.62, 0.62, 0.64);

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void ascension$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        float k = ClientState.skyIntensity(tickDelta);
        float ks = SwordState.skyIntensity(tickDelta);
        float ky = YinYangState.skyTone(tickDelta);
        if (k <= 0f && ks <= 0f && ky <= 0f) return;
        Vec3d c = cir.getReturnValue().multiply(1.0 - k).add(ASCENSION_SKY.multiply(k));
        c = c.multiply(1.0 - ks).add(VOID_SKY.multiply(ks));
        cir.setReturnValue(c.multiply(1.0 - ky).add(MONO_SKY.multiply(ky)));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void ascension$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        float k = ClientState.skyIntensity(tickDelta);
        float ks = SwordState.skyIntensity(tickDelta);
        float ky = YinYangState.skyTone(tickDelta);
        if (k <= 0f && ks <= 0f && ky <= 0f) return;
        Vec3d c = cir.getReturnValue().multiply(1.0 - k * 0.8).add(ASCENSION_SKY.multiply(k * 1.5));
        c = c.multiply(1.0 - ks * 0.9).add(VOID_SKY.multiply(ks * 1.2));
        cir.setReturnValue(c.multiply(1.0 - ky * 0.9).add(MONO_SKY.multiply(ky * 0.8)));
    }
}
