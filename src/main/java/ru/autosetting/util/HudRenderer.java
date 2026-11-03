package ru.autosetting.util;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
import ru.autosetting.config.ModConfig;
import ru.autosetting.feature.AnydeskCopy;
import ru.autosetting.feature.AutoSpecManager;
import ru.autosetting.feature.DynamicIslandRenderer;
import ru.autosetting.feature.MuteAlertManager;
import ru.autosetting.feature.ReportsHud;
import ru.autosetting.feature.TargetHud;

public final class HudRenderer {
    private HudRenderer() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((DrawContext context, RenderTickCounter tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) {
                return;
            }
            DynamicIslandRenderer.render(context, client);
            AnydeskCopy.renderHud(context, client);
            if (!ModConfig.get().hudHints) {
                return;
            }
            AutoSpecManager.renderHud(context, client);
            MuteAlertManager.renderHud(context, client);
            TargetHud.render(context, client);
            ReportsHud.render(context, client);
        });
    }
}
