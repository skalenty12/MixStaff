package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;

/**
 * Свободный обзор от третьего лица, активный только пока зажат бинд: камера вращается мышью,
 * а направление взгляда и движения игрока не меняется.
 * <p>
 * Ванильный поворот отключается в {@code MouseMixin}, а нужный угол
 * камере выставляет {@code CameraMixin}.
 */
public final class Freelook {
    private static boolean active;
    private static float yaw;
    private static float pitch;

    private Freelook() {
    }

    public static boolean isActive() {
        return active;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** Включает свободную камеру при нажатии, сохраняя исходный угол игрока. */
    public static void start(MinecraftClient client) {
        if (client.player == null || active) {
            return;
        }
        yaw = client.player.getYaw();
        pitch = client.player.getPitch();
        active = true;
    }

    public static void stop() {
        active = false;
    }

    /** deltaX/deltaY — смещение курсора в пикселях, как его отдаёт GLFW. */
    public static void addDelta(double deltaX, double deltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        double sensitivity = client.options == null
                ? 0.5D : client.options.getMouseSensitivity().getValue();
        double factor = sensitivity * 0.6D + 0.2D;
        double scale = factor * factor * factor * 8.0D * 0.15D;
        yaw += (float) (deltaX * scale);
        pitch += (float) (deltaY * scale);
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
    }
}
