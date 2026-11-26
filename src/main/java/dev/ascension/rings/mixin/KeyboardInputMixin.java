package dev.ascension.rings.mixin;

import dev.ascension.rings.client.ClientState;
import dev.ascension.rings.client.sword.SwordState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ล็อกการเดิน/กระโดด/ย่อ ระหว่างสกิล */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void ascension$lock(boolean slowDown, float f, CallbackInfo ci) {
        if (!ClientState.isLocalActive() && !SwordState.isLocked()) return;
        this.pressingForward = false;
        this.pressingBack = false;
        this.pressingLeft = false;
        this.pressingRight = false;
        this.movementForward = 0f;
        this.movementSideways = 0f;
        this.jumping = false;
        this.sneaking = false;
    }
}
