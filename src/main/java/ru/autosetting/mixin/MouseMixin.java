package ru.autosetting.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.feature.Freelook;
import ru.autosetting.feature.AntiAfk;
import ru.autosetting.feature.HudEditOverlay;
import ru.autosetting.feature.MusicIsland;

/** Клики по динамическому острову и свободный обзор (Freelook). */
@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow
    private double x;
    @Shadow
    private double y;
    @Shadow
    private double cursorDeltaX;
    @Shadow
    private double cursorDeltaY;
    @Shadow
    private boolean hasResolutionChanged;

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void mixstaff$freelook(long window, double xPos, double yPos, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() != null && window == client.getWindow().getHandle()
                && (xPos != x || yPos != y)) {
            AntiAfk.onUserActivity();
        }
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            double mouseX = xPos * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
            double mouseY = yPos * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
            HudEditOverlay.mouseDragged(client, mouseX, mouseY, 0);
        }
        if (!Freelook.isActive() || client.currentScreen != null
                || client.player == null || hasResolutionChanged) {
            return;
        }
        if (client.getWindow() == null || window != client.getWindow().getHandle()) {
            return;
        }
        // Ванильный поворот не трогаем: смещение уходит только в камеру.
        Freelook.addDelta(xPos - x, yPos - y);
        x = xPos;
        y = yPos;
        cursorDeltaX = 0.0D;
        cursorDeltaY = 0.0D;
        info.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void mixstaff$islandClick(long window, int button, int action, int mods, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS) {
            AntiAfk.onUserActivity();
        }
        if (action == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            HudEditOverlay.mouseReleased(button);
        }
        if (action != 1) {
            return;
        }
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        if (MusicIsland.handleClick(client.mouse.getX(), client.mouse.getY(), button)) {
            info.cancel();
        }
    }
}
