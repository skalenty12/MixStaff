package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class SoundUtil {
    private SoundUtil() {
    }

    /**
     * Звук звоночка — используется при запросе слежки и при срабатывании AutoMute.
     */
    public static void playBell() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_USE, 1.0F));
    }
}
