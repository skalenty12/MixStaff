package ru.autosetting.feature;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.AutoSettingLog;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.PlayerNames;
import ru.autosetting.util.SoundUtil;
import ru.autosetting.util.UiDraw;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ловит сообщения чата и ищет в них просьбу о слежке:
 * spec / speck / спек (в том числе внутри предложения).
 * <p>
 * Ник определяется по событию чата (если сервер передаёт его явно), а если нет —
 * разбором строки: поддерживаются форматы {@code Ник: текст}, {@code Ник » текст},
 * {@code [Ранг] Ник: текст}, {@code [Ник] текст} и подобные.
 */
public final class AutoSpecManager {
    private static final Pattern LATIN_SPEC = Pattern.compile(
            "(?<![a-z0-9_])(?:spec|spek|spect|spectate|spectator)(?![a-z0-9_])");
    private static final Pattern CYR_SPEC = Pattern.compile(
            "(?<![а-яё])(?:спек|спека|спекни|заспек|спектать|спекта)(?![а-яё])");
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** Слова, которые часто стоят вместо ника (префиксы, ранги, служебные метки). */
    private static final Set<String> NOT_NICKS = Set.of(
            "vip", "premium", "deluxe", "elite", "legend", "legendary", "hero", "titan", "king",
            "queen", "lord", "duke", "baron", "god", "holy", "admin", "administrator", "moder",
            "moderator", "helper", "owner", "staff", "curator", "builder", "dev", "developer",
            "media", "youtube", "twitch", "streamer", "donate", "donator", "player", "global",
            "local", "chat", "team", "clan", "guild", "level", "lvl", "info", "msg", "pm"
    );

    private static final char[] SEPARATORS = {':', '»', '›', '►', '▸', '➥', '≫', '|', '→', '>'};

    private static final long NOTIFY_MS = 10_000L;
    private static final long DEBOUNCE_MS = 4_000L;

    private static String notifyNick;
    private static long notifyUntil;
    private static long lastNotifyAt;
    private static String lastRequester;
    private static String lastSender;
    private static String lastMessage = "";

    private AutoSpecManager() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                handleMessage(message.getString(), null);
            }
        });

        ClientReceiveMessageEvents.CHAT.register(
                (Text message, SignedMessage signedMessage, GameProfile sender,
                 MessageType.Parameters parameters, Instant timestamp) ->
                        handleMessage(message.getString(), sender == null ? null : sender.getName())
        );
    }

    private static void handleMessage(String plain, String knownNick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || plain == null || plain.isBlank()) {
            return;
        }

        ReportsStore.capture(plain);

        String ownNick = client.getSession().getUsername();
        // Сначала ищем настоящий ник в таб-листе: так клан-тег и префикс
        // привилегии никогда не попадут в уведомление или в мут.
        String extracted = knownNick != null && !knownNick.isBlank()
                ? knownNick : extractNickFromLine(plain);
        String nick = PlayerNames.resolve(plain, extracted);
        boolean realPlayer = PlayerNames.isOnline(nick);
        // В событии CHAT приходит только текст сообщения — не режем его по разделителям.
        String body = knownNick != null && !knownNick.isBlank()
                ? plain.trim() : extractBody(plain);
        boolean systemLine = knownNick == null && looksLikeSystem(plain);

        AutoSettingLog.chat(plain, nick);

        if (!systemLine && realPlayer && !nick.equalsIgnoreCase(ownNick)) {
            MuteAlertManager.onMessage(nick, body);
            lastSender = nick;
        }
        lastMessage = body;

        if (systemLine) {
            return;
        }
        if (!ModConfig.get().autoSpeck || !isSpecRequest(plain)) {
            return;
        }
        if (realPlayer && nick.equalsIgnoreCase(ownNick)) {
            return;
        }

        AutoSettingLog.debug("spec-запрос: raw='" + plain + "' nick=" + nick + " body='" + body + "'");
        notifyRequester(realPlayer || knownNick != null ? nick : null);
    }

    /**
     * @param nick может быть {@code null}, если ник распознать не удалось —
     *             тогда показываем уведомление без кнопки /spec.
     */
    private static void notifyRequester(String nick) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        String label = nick == null ? "?" : nick;
        lastRequester = nick;

        String debounceKey = nick == null ? "?" : nick;
        if (notifyNick != null && notifyNick.equalsIgnoreCase(debounceKey) && now - lastNotifyAt < DEBOUNCE_MS) {
            return;
        }
        notifyNick = label;
        lastNotifyAt = now;
        notifyUntil = now + NOTIFY_MS;

        if (ModConfig.get().sounds) {
            SoundUtil.playBell();
        }

        MutableText message = Text.literal("")
                .append(Text.literal("[AutoSpeck] ").formatted(Formatting.AQUA));

        if (nick == null) {
            message.append(Text.literal("Кто-то запросил слежку, но ник не распознан. ").formatted(Formatting.YELLOW))
                    .append(Text.literal("(напиши /spec вручную)").formatted(Formatting.GRAY));
        } else {
            MutableText button = Text.literal("[SPEC]").setStyle(Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/spec " + nick))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Text.literal("Прописать /spec " + nick))));

            message.append(Text.literal("Игрок ").formatted(Formatting.YELLOW))
                    .append(Text.literal(nick).formatted(Formatting.WHITE, Formatting.BOLD))
                    .append(Text.literal(" запросил слежку ").formatted(Formatting.YELLOW))
                    .append(button);
        }

        client.inGameHud.getChatHud().addMessage(message);
    }

    public static void renderHud(DrawContext context, MinecraftClient client) {
        if (notifyNick == null || System.currentTimeMillis() > notifyUntil) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() / 2 + 22;

        boolean known = !"?".equals(notifyNick);
        Text line1 = Fonts.t(known
                ? "Игрок " + notifyNick + " запросил слежку"
                : "Кто-то запросил слежку — ник не распознан").formatted(Formatting.YELLOW);
        Text line2 = Fonts.t(known ? "[SPEC] — в чате или клавиша J" : "Напиши /spec вручную")
                .formatted(Formatting.GREEN);

        int width = Math.max(textRenderer.getWidth(line1.getString()), textRenderer.getWidth(line2.getString())) + 16;
        ModConfig config = ModConfig.get();
        int baseCenterY = y + 9;
        int targetCenterX = Math.round(config.specHudX * context.getScaledWindowWidth());
        int targetCenterY = Math.round(config.specHudY * context.getScaledWindowHeight());
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(targetCenterX, targetCenterY, 0.0F);
        matrices.scale(config.specHudScale, config.specHudScale, 1.0F);
        matrices.translate(-centerX, -baseCenterY, 0.0F);
        UiDraw.glassPanel(context, centerX - width / 2 - 8, y - 5, width + 16, 30,
                8, 0xFF81E4FF);
        context.drawTextWithShadow(textRenderer, line1,
                centerX - textRenderer.getWidth(line1.getString()) / 2, y, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, line2,
                centerX - textRenderer.getWidth(line2.getString()) / 2, y + 11, 0xFFFFFF);
        matrices.pop();
    }

    public static void specLastRequester() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || lastRequester == null) {
            return;
        }
        client.player.networkHandler.sendChatCommand("spec " + lastRequester);
        client.inGameHud.getChatHud().addMessage(
                Text.literal("[AutoSpeck] /spec " + lastRequester).formatted(Formatting.DARK_AQUA));
    }

    public static String getLastRequester() {
        return lastRequester;
    }

    public static String getLastSender() {
        return lastSender;
    }

    public static String getLastMessage() {
        return lastMessage;
    }

    /**
     * Служебные строки сервера и логи античита: в них нет игрока,
     * которого можно замутить, поэтому мут-детектор их не смотрит.
     * Запросы слежки в таких строках тоже не ищем.
     */
    private static boolean looksLikeSystem(String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return true;
        }
        return lower.contains("античит") || lower.contains("anticheat")
                || lower.contains("[ac]") || lower.contains("acguard")
                || lower.contains("[сервер]") || lower.contains("[server]")
                || lower.contains("[система]") || lower.contains("[system]")
                || lower.contains("консоль") || lower.contains("console");
    }

    private static boolean isSpecRequest(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (CYR_SPEC.matcher(lower).find()) {
            return true;
        }
        return LATIN_SPEC.matcher(lower).find();
    }

    private static int separatorIndex(String plain) {
        int best = -1;
        for (char separator : SEPARATORS) {
            int index = plain.indexOf(separator);
            if (index > 0 && (best == -1 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static String extractBody(String plain) {
        int separator = separatorIndex(plain);
        if (separator > 0 && separator + 1 < plain.length()) {
            return plain.substring(separator + 1).trim();
        }
        return plain.trim();
    }

    /**
     * Пытается вытащить ник отправителя из строки чата произвольного формата.
     */
    private static String extractNickFromLine(String plain) {
        String clean = plain.replaceAll("§.", "");

        // 1. Классический вариант: часть до разделителя содержит ник.
        int separator = separatorIndex(clean);
        if (separator > 0) {
            String head = clean.substring(0, separator);
            String nick = lastNickToken(head);
            if (nick != null) {
                return nick;
            }
        }

        // 2. Формат [Ник] текст.
        Matcher brackets = Pattern.compile("\\[([A-Za-z0-9_]{3,16})]").matcher(clean);
        while (brackets.find()) {
            String candidate = brackets.group(1);
            if (!NOT_NICKS.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        // 3. Формат без разделителя: ник — первое осмысленное слово строки.
        String[] tokens = clean.trim().split("\\s+");
        for (String token : tokens) {
            String candidate = token.replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
            if (NICK.matcher(candidate).matches()
                    && !NOT_NICKS.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    /** Последний похожий на ник токен в строке (ники стоят в конце префикса). */
    private static String lastNickToken(String head) {
        String[] parts = head.replaceAll("\\[[^\\]]*]", " ").trim().split("\\s+");
        for (int index = parts.length - 1; index >= 0; index--) {
            String candidate = parts[index].replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
            if (NICK.matcher(candidate).matches()
                    && !NOT_NICKS.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }
}
