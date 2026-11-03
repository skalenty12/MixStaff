package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Renders the dynamic island through the existing HUD drawing pipeline. */
public final class DynamicIslandRenderer {
    private static volatile MediaSessionBridge.Track frameTrack;
    private static volatile long frameTime;
    private static volatile float framePulse;
    private static String previousTrackState = "";
    private static long pulseStartedAt;

    private DynamicIslandRenderer() {
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        frameTrack = DynamicIslandFeature.isEnabled() ? MediaSessionBridge.track() : null;
        String state = frameTrack == null ? "" : frameTrack.title() + '\u0000'
                + frameTrack.artist() + '\u0000' + frameTrack.playing();
        if (!state.equals(previousTrackState)) {
            previousTrackState = state;
            pulseStartedAt = now;
        }
        long elapsed = now - pulseStartedAt;
        framePulse = elapsed < 700L ? (float) Math.sin(Math.PI * elapsed / 700.0D) : 0.0F;
        frameTime = now;
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (!DynamicIslandFeature.isEnabled()) {
            return;
        }
        MusicIsland.render(context, client, frameTrack, frameTime, framePulse);
    }
}
