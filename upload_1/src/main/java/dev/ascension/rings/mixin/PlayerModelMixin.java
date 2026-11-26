package dev.ascension.rings.mixin;

import dev.ascension.rings.client.sword.SwordState;
import dev.ascension.rings.client.yinyang.YinYangState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ยกแขนขวาขึ้นฟ้าตอนร่ายสกิล G */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ascension$raiseArm(T entity, float limbAngle, float limbDistance, float animationProgress,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        float td = MinecraftClient.getInstance().getTickDelta();
        float k = Math.max(SwordState.armRaise(player.getUuid(), td), YinYangState.armRaise(player.getUuid(), td));
        if (k <= 0f) return;
        float target = (float) (-Math.PI * 0.93);
        this.rightArm.pitch = this.rightArm.pitch + (target - this.rightArm.pitch) * k;
        this.rightArm.yaw = this.rightArm.yaw * (1f - k);
        this.rightArm.roll = this.rightArm.roll + (0.12f - this.rightArm.roll) * k;
        ((PlayerEntityModel<?>) (Object) this).rightSleeve.copyTransform(this.rightArm);
    }
}
