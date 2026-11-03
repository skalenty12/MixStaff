package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.AutoSettingLog;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.SoundUtil;
import ru.autosetting.util.UiDraw;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реакция на найденные нарушения: уведомление в чат с кнопкой [МУТ],
 * звонок и подсказка на экране. Мут не выдаётся сам — мод только подсказывает.
 */
public final class MuteAlertManager {
    private static final long HUD_MS = 12_000L;
    private static final long DEDUP_MS = 45_000L;

    private static final Map<String, Long> LAST_ALERT = new HashMap<>();
    private static final Map<String, String> LAST_REASON = new HashMap<>();

    private static String hudNick;
    private static String hudText;
    private static long hudUntil;

    private MuteAlertManager() {
    }

    /** Вызывается из обработчика чата для каждого сообщения игрока. */
    public static void onMessage(String nick, String body) {
        ModConfig config = ModConfig.get();
        if (!config.autoMute || !config.autoMuteDetect || nick == null || body == null) {
            return;
        }
        String text = body.trim();
        // Одиночный ник, «+», «лол» и прочие короткие сообщения — не нарушение.
        if (text.length() < 4 || text.matches("^[A-Za-z0-9_]{3,16}$")) {
            return;
        }
        if (text.indexOf(' ') < 0 && !containsCyrillic(text) && text.length() < 8) {
            return;
        }

        List<Violation> violations = ChatViolationDetector.analyze(nick, text);
        if (violations.isEmpty()) {
            return;
        }

        Violation main = violations.get(0);
        AutoSettingLog.debug("нарушение: nick=" + nick + " rule=" + main.rule().clause()
                + " detail=" + main.detail() + " body='" + body + "'");
        String key = nick.toLowerCase(Locale.ROOT) + "|" + main.rule().clause();
        long now = System.currentTimeMillis();
        Long last = LAST_ALERT.get(key);
        if (last != null && now - last < DEDUP_MS) {
            return;
        }
        if (LAST_ALERT.size() > 1000) {
            LAST_ALERT.clear();
        }
        LAST_ALERT.put(key, now);
        LAST_REASON.put(nick.toLowerCase(Locale.ROOT), buildReason(main, body, violations));

        hudNick = nick;
        hudText = main.rule().clause() + " " + main.rule().title() + " — " + main.detail();
        hudUntil = now + HUD_MS;

        if (config.sounds) {
            SoundUtil.playBell();
        }

        MutableText button = Text.literal("[МУТ]").setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        ".mute " + nick + " " + main.rule().clause()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Открыть AutoMute: " + nick + ", пункт " + main.rule().clause()))));

        MutableText message = Text.literal("")
                .append(Text.literal("[AutoMute] ").formatted(Formatting.AQUA))
                .append(Text.literal(nick).formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(" — " + main.rule().clause() + " ").formatted(Formatting.YELLOW))
                .append(Text.literal(main.detail()).formatted(Formatting.GRAY))
                .append(Text.literal(" (").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(main.rule().punishment()).formatted(Formatting.DARK_GRAY))
                .append(Text.literal(") ").formatted(Formatting.DARK_GRAY))
                .append(button);

        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    public static void renderHud(DrawContext context, MinecraftClient client) {
        if (hudNick == null || System.currentTimeMillis() > hudUntil) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() / 2 - 44;

        Text line1 = Fonts.t("Нарушение: " + hudNick).formatted(Formatting.RED, Formatting.BOLD);
        Text line2 = Fonts.t(hudText).formatted(Formatting.YELLOW);
        Text line3 = Fonts.t("M или [МУТ] в чате — открыть AutoMute").formatted(Formatting.GRAY);

        int width = Math.max(textRenderer.getWidth(line1.getString()),
                Math.max(textRenderer.getWidth(line2.getString()), textRenderer.getWidth(line3.getString()))) + 16;
        ModConfig config = ModConfig.get();
        int baseCenterY = y + 15;
        int targetCenterX = Math.round(config.muteHudX * context.getScaledWindowWidth());
        int targetCenterY = Math.round(config.muteHudY * context.getScaledWindowHeight());
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(targetCenterX, targetCenterY, 0.0F);
        matrices.scale(config.muteHudScale, config.muteHudScale, 1.0F);
        matrices.translate(-centerX, -baseCenterY, 0.0F);
        UiDraw.glassPanel(context, centerX - width / 2 - 8, y - 5, width + 16, 40,
                8, 0xFFFF7F96);
        context.drawTextWithShadow(textRenderer, line1,
                centerX - textRenderer.getWidth(line1.getString()) / 2, y, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, line2,
                centerX - textRenderer.getWidth(line2.getString()) / 2, y + 11, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, line3,
                centerX - textRenderer.getWidth(line3.getString()) / 2, y + 22, 0xFFFFFF);
        matrices.pop();
    }

    /** Причина мута для конкретного игрока (последнее найденное нарушение). */
    public static String reasonFor(String nick) {
        if (nick == null) {
            return "";
        }
        return LAST_REASON.getOrDefault(nick.toLowerCase(Locale.ROOT), "");
    }

    private static boolean containsCyrillic(String text) {
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (symbol >= 'а' && symbol <= 'я' || symbol >= 'А' && symbol <= 'Я' || symbol == 'ё' || symbol == 'Ё') {
                return true;
            }
        }
        return false;
    }

    private static String buildReason(Violation main, String body, List<Violation> all) {        StringBuilder builder = new StringBuilder(main.rule().reasonPrefix());
        if (all.size() > 1) {
            builder.append(" + ");
            for (int index = 1; index < all.size(); index++) {
                if (index > 1) {
                    builder.append(", ");
                }
                builder.append(all.get(index).rule().clause());
            }
        }
        builder.append(": ").append(main.detail());
        String excerpt = body.trim().replaceAll("\\s+", " ");
        if (!excerpt.isEmpty()) {
            builder.append(" — \"").append(excerpt.length() > 60 ? excerpt.substring(0, 60) + "..." : excerpt)
                    .append('"');
        }
        String reason = builder.toString();
        return reason.length() > 140 ? reason.substring(0, 140) : reason;
    }
}
