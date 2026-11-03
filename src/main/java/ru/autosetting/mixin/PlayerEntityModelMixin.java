package ru.autosetting.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.config.ModConfig;

/**
 * Big-Head: увеличивает голову игрока.
 * <p>
 * Слой шапки (hat) — дочерняя часть головы, поэтому его масштаб оставляем 1.0,
 * иначе масштабы перемножаются и причёска/шлем «раздуваются» вдвое.
 */
@Mixin(PlayerEntityModel.class)
@SuppressWarnings("rawtypes")
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void autosetting$applyBigHead(PlayerEntityRenderState state, CallbackInfo callbackInfo) {
        ModConfig config = ModConfig.get();
        float scale = config.bigHead ? Math.max(1.0F, config.bigHeadScale) : 1.0F;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        model.head.xScale = scale;
        model.head.yScale = scale;
        model.head.zScale = scale;

        model.hat.xScale = 1.0F;
        model.hat.yScale = 1.0F;
        model.hat.zScale = 1.0F;
    }
}
