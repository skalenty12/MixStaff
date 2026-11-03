package ru.autosetting.feature;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import ru.autosetting.AutoSettingClient;
import ru.autosetting.config.ModConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AutoProof: как только отправляется /push (кнопка PUSH в меню наказаний),
 * мод сразу делает скриншот экрана в папку proofs.
 */
public final class AutoProofManager {
    private static final long COOLDOWN_MS = 1_500L;

    private static long suppressUntil;
    private static long lastCaptureAt;

    private AutoProofManager() {
    }

    public static void init() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            maybeCapture(command);
            return true;
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            maybeCapture(message);
            return true;
        });
    }

    /** Чтобы AutoMute не делал второй скриншот поверх своего собственного. */
    public static void suppressFor(long millis) {
        suppressUntil = Math.max(suppressUntil, System.currentTimeMillis() + millis);
    }

    private static void maybeCapture(String raw) {
        if (!ModConfig.get().autoProof || raw == null) {
            return;
        }
        String command = raw.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.equals("push") && !command.startsWith("push ")) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < suppressUntil || now - lastCaptureAt < COOLDOWN_MS) {
            return;
        }
        lastCaptureAt = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        Runnable task = () -> capture(client);
        if (client.isOnThread()) {
            task.run();
        } else {
            client.execute(task);
        }
    }

    private static void capture(MinecraftClient client) {
        File directory = new File(client.runDirectory, "proofs");
        if (!directory.exists() && !directory.mkdirs()) {
            AutoSettingClient.LOGGER.warn("Не удалось создать папку {}", directory.getAbsolutePath());
        }

        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        File target = new File(directory, "proof_" + time + ".png");

        try {
            NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            Util.getIoWorkerExecutor().execute(() -> {
                try {
                    image.writeTo(target);
                } catch (Exception exception) {
                    AutoSettingClient.LOGGER.error("AutoProof: не удалось сохранить скриншот", exception);
                } finally {
                    image.close();
                }
            });
            client.inGameHud.getChatHud().addMessage(
                    Text.literal("[AutoProof] Скриншот: proofs/" + target.getName()).formatted(Formatting.GRAY));
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.error("AutoProof: не удалось сделать скриншот", exception);
        }
    }
}
