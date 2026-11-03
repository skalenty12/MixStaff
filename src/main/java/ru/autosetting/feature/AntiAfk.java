package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.autosetting.config.ModConfig;

/** Disconnects the client after prolonged real input inactivity. */
public final class AntiAfk {
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final Text DISCONNECT_REASON = Text.literal("Я спас тебя от 1 варна");
    private static long lastActivityAt = System.currentTimeMillis();
    private static boolean disconnecting;

    private AntiAfk() {
    }

    public static void onUserActivity() {
        lastActivityAt = System.currentTimeMillis();
    }

    public static void reset() {
        lastActivityAt = System.currentTimeMillis();
        disconnecting = false;
    }

    public static void tick(MinecraftClient client) {
        if (!ModConfig.get().antiAfk || client.player == null || client.getNetworkHandler() == null) {
            reset();
            return;
        }
        if (client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed() || client.options.rightKey.isPressed()
                || client.options.jumpKey.isPressed() || client.options.sneakKey.isPressed()
                || client.options.sprintKey.isPressed() || client.options.attackKey.isPressed()
                || client.options.useKey.isPressed()) {
            onUserActivity();
        }
        if (!disconnecting && System.currentTimeMillis() - lastActivityAt >= IDLE_TIMEOUT_MS) {
            disconnecting = true;
            client.getNetworkHandler().getConnection().disconnect(DISCONNECT_REASON);
        }
    }
}
