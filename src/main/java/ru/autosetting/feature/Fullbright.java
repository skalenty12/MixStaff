package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import ru.autosetting.config.ModConfig;

/**
 * Ночное видение: клиентский эффект NIGHT_VISION, который обновляется
 * до истечения. Никаких изменений гаммы и настроек игры — только эффект.
 */
public final class Fullbright {
    private static boolean applied;

    private Fullbright() {
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            applied = false;
            return;
        }
        if (!ModConfig.get().fullbright) {
            if (applied) {
                client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                applied = false;
            }
            return;
        }
        StatusEffectInstance current = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || current.getDuration() != -1) {
            client.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, -1, 0, false, false, false));
            applied = true;
        }
    }

    public static void reset() {
        applied = false;
    }
}
