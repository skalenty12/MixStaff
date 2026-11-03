package ru.autosetting.feature;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import ru.autosetting.AutoSettingClient;
import ru.autosetting.config.ModConfig;
import ru.autosetting.ui.AutoMuteScreen;
import ru.autosetting.ui.SettingScreen;
import ru.autosetting.util.PermissionUtil;
import ru.autosetting.util.SoundUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * По нажатию кнопки: скриншот в .minecraft/mutes, затем /push ник.
 * Пока открыт GUI пуша, сверху рисуется привилегия, ник и причина.
 */
public final class AutoMuteManager {
    private static final long OVERLAY_MS = 60_000L;

    private static int pendingTicks = -1;
    private static String pendingNick;
    private static String pendingReason;
    private static Overlay overlay;

    private AutoMuteManager() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.afterRender(screen).register(
                        (currentScreen, context, mouseX, mouseY, tickDelta) -> renderOverlay(context, currentScreen)
                )
        );
    }

    public static void requestMute(String nick, String reason) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            AutoSettingClient.LOGGER.warn("AutoMute отменён: нет активного подключения к серверу");
            return;
        }
        pendingNick = nick;
        pendingReason = reason == null ? "" : reason;
        pendingTicks = 2;
        client.setScreen(null);
    }

    public static void tick() {
        if (pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks == 0) {
                performMute();
            }
        }
        if (overlay != null && System.currentTimeMillis() > overlay.expiresAt()) {
            overlay = null;
        }
    }

    private static void performMute() {
        MinecraftClient client = MinecraftClient.getInstance();
        String nick = pendingNick;
        String reason = pendingReason;
        pendingNick = null;
        pendingReason = null;
        pendingTicks = -1;

        if (client.player == null || client.getNetworkHandler() == null || nick == null || nick.isBlank()) {
            AutoSettingClient.LOGGER.warn("AutoMute отменён: игрок отключился до выполнения");
            return;
        }

        File directory = new File(client.runDirectory, "mutes");
        if (!directory.exists() && !directory.mkdirs()) {
            AutoSettingClient.LOGGER.warn("Не удалось создать папку {}", directory.getAbsolutePath());
        }

        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        String fileName = time + "_" + nick.replaceAll("[^A-Za-z0-9_.-]", "_") + ".png";
        File target = new File(directory, fileName);

        // Снимок делаем синхронно — до отправки /push, чтобы в кадр не попал GUI пуша.
        try {
            NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            Util.getIoWorkerExecutor().execute(() -> {
                try {
                    image.writeTo(target);
                } catch (Exception exception) {
                    AutoSettingClient.LOGGER.error("Не удалось сохранить скриншот", exception);
                } finally {
                    image.close();
                }
            });
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.error("Не удалось сделать скриншот", exception);
            client.inGameHud.getChatHud().addMessage(Text.literal(
                    "[AutoMute] Не удалось сохранить скриншот доказательства").formatted(Formatting.RED));
        }

        String privilege = PermissionUtil.resolve(nick);
        overlay = new Overlay(privilege, nick, reason, System.currentTimeMillis() + OVERLAY_MS);

        // Свой скриншот уже сделан выше — AutoProof не должен делать второй.
        AutoProofManager.suppressFor(3_000L);
        try {
            client.getNetworkHandler().sendChatCommand("push " + nick);
        } catch (RuntimeException exception) {
            AutoSettingClient.LOGGER.error("Не удалось отправить /push для {}", nick, exception);
            client.inGameHud.getChatHud().addMessage(Text.literal(
                    "[AutoMute] Ошибка отправки /push " + nick).formatted(Formatting.RED));
            return;
        }

        if (ModConfig.get().sounds) {
            SoundUtil.playBell();
        }

        client.inGameHud.getChatHud().addMessage(
                Text.literal("[AutoMute] Скриншот: mutes/" + fileName + " → /push " + nick)
                        .formatted(Formatting.GRAY));
    }

    private static void renderOverlay(DrawContext context, Screen screen) {
        if (screen instanceof SettingScreen || screen instanceof AutoMuteScreen) {
            return;
        }
        Overlay current = overlay;
        if (current == null) {
            return;
        }
        if (System.currentTimeMillis() > current.expiresAt()) {
            overlay = null;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int centerX = screen.width / 2;

        Text line1 = Text.literal(current.privilege() + "  " + current.nick())
                .formatted(Formatting.AQUA, Formatting.BOLD);
        Text line2 = Text.literal("Причина: " + (current.reason().isBlank() ? "не указана" : current.reason()))
                .formatted(Formatting.RED);

        int width = Math.max(textRenderer.getWidth(line1.getString()), textRenderer.getWidth(line2.getString())) + 24;
        context.fill(centerX - width / 2, 4, centerX + width / 2, 36, 0xC0000000);
        context.drawTextWithShadow(textRenderer, line1,
                centerX - textRenderer.getWidth(line1.getString()) / 2, 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, line2,
                centerX - textRenderer.getWidth(line2.getString()) / 2, 20, 0xFFFFFF);
    }

    private record Overlay(String privilege, String nick, String reason, long expiresAt) {
    }
}
