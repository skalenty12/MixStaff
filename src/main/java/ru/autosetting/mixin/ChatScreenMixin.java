package ru.autosetting.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.autosetting.feature.HudEditOverlay;

/** Позволяет двигать и масштабировать панели HUD прямо при открытом чате. */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mixstaff$hudClick(double mouseX, double mouseY, int button,
                                   CallbackInfoReturnable<Boolean> info) {
        if (HudEditOverlay.mouseClicked(MinecraftClient.getInstance(), mouseX, mouseY, button)) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void mixstaff$hudScroll(double mouseX, double mouseY, double horizontalAmount,
                                    double verticalAmount, CallbackInfoReturnable<Boolean> info) {
        if (HudEditOverlay.mouseScrolled(MinecraftClient.getInstance(), mouseX, mouseY, verticalAmount)) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mixstaff$hudOverlay(DrawContext context, int mouseX, int mouseY,
                                     float delta, CallbackInfo info) {
        HudEditOverlay.render(context, MinecraftClient.getInstance(), mouseX, mouseY);
    }
}
