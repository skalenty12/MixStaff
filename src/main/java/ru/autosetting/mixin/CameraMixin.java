package ru.autosetting.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.feature.Freelook;

/** Даёт Freelook вращать настоящую камеру третьего лица, не поворачивая игрока. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean mixstaff$forceThirdPerson(boolean thirdPerson) {
        return Freelook.isActive() || thirdPerson;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), index = 0)
    private float mixstaff$freeYaw(float vanillaYaw) {
        return Freelook.isActive() ? Freelook.yaw() : vanillaYaw;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"), index = 1)
    private float mixstaff$freePitch(float vanillaPitch) {
        return Freelook.isActive() ? Freelook.pitch() : vanillaPitch;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void mixstaff$freelook(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                   boolean inverseView, float tickDelta, CallbackInfo info) {
        if (Freelook.isActive()) {
            setRotation(Freelook.yaw(), Freelook.pitch());
        }
    }
}
