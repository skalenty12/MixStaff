package ru.autosetting.mixin;

import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.feature.AntiAfk;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void mixstaff$antiAfkInput(long window, int key, int scanCode, int action, int modifiers,
                                       CallbackInfo info) {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            AntiAfk.onUserActivity();
        }
    }
}
