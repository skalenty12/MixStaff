package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ru.autosetting.AutoSettingClient;

import java.lang.reflect.Constructor;

/**
 * Optional integration with In-Game Account Switcher without a compile-time dependency.
 */
public final class IasCompat {
    private IasCompat() {
    }

    public static void hideAccountText() {
        try {
            Class<?> config = Class.forName("ru.vidtu.ias.config.IASConfig");
            config.getField("titleText").setBoolean(null, false);
            config.getField("titleButton").setBoolean(null, false);
        } catch (ReflectiveOperationException | LinkageError exception) {
            AutoSettingClient.LOGGER.debug("IAS title integration unavailable", exception);
        }
    }

    public static boolean openAccountMenu(MinecraftClient client, Screen parent) {
        try {
            Class<?> screenClass = Class.forName("ru.vidtu.ias.screen.AccountScreen");
            for (Constructor<?> constructor : screenClass.getConstructors()) {
                if (constructor.getParameterCount() != 1
                        || !Screen.class.isAssignableFrom(constructor.getParameterTypes()[0])
                        || !constructor.getParameterTypes()[0].isAssignableFrom(parent.getClass())
                        || !Screen.class.isAssignableFrom(constructor.getDeclaringClass())) {
                    continue;
                }
                Object screen = constructor.newInstance(parent);
                client.setScreen((Screen) screen);
                return true;
            }
            throw new NoSuchMethodException("IAS AccountScreen(Screen) constructor not found");
        } catch (ReflectiveOperationException | LinkageError exception) {
            AutoSettingClient.LOGGER.warn("Не удалось открыть меню IAS", exception);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("IAS не установлен или несовместим"), false);
            }
            return false;
        }
    }
}
