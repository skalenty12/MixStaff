package ru.autosetting.feature;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import ru.autosetting.config.ModConfig;

/** Client-side public control API for the dynamic island. */
public final class DynamicIslandFeature {
    private static KeyBinding toggleKey;

    private DynamicIslandFeature() {
    }

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosetting.dynamic_island",
                InputUtil.Type.KEYSYM,
                ModConfig.get().musicIslandKey,
                "category.autosetting"
        ));
        if (isEnabled()) {
            MediaSessionBridge.start();
        }
    }

    public static void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            toggle();
        }
        if (isEnabled()) {
            MediaSessionBridge.start();
        }
        DynamicIslandRenderer.tick();
    }

    public static boolean isEnabled() {
        return ModConfig.get().musicIsland;
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }

    public static void setEnabled(boolean enabled) {
        ModConfig config = ModConfig.get();
        config.musicIsland = enabled;
        config.save();
        if (enabled) {
            MediaSessionBridge.start();
        } else {
            MediaSessionBridge.stop();
        }
    }

    public static void setSource(MediaSessionBridge.Source source) {
        MediaSessionBridge.setSource(source);
    }
}
