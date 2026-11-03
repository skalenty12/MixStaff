package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Определяет настоящий ник отправителя сообщения по таб-листу,
 * чтобы клан-теги и префиксы привилегий не попадали в AutoSpeck и AutoMute.
 */
public final class PlayerNames {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern DIGITS = Pattern.compile("(?<!\\d)(\\d{9,10})(?!\\d)");

    private PlayerNames() {
    }

    public static List<String> online() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        List<String> names = new ArrayList<>();
        if (handler == null) {
            return names;
        }
        for (PlayerListEntry entry : handler.getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    public static boolean isOnline(String nick) {
        if (nick == null || nick.isBlank()) {
            return false;
        }
        for (String name : online()) {
            if (name.equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    /** Настоящий ник игрока из строки чата: первое совпадение с таб-листом. */
    public static String resolve(String plain, String fallback) {
        if (plain == null || plain.isBlank()) {
            return fallback;
        }
        List<String> names = online();
        if (names.isEmpty()) {
            return fallback;
        }
        Matcher matcher = TOKEN.matcher(plain);
        while (matcher.find()) {
            String token = matcher.group();
            for (String name : names) {
                if (name.equalsIgnoreCase(token)) {
                    return name;
                }
            }
        }
        return fallback;
    }

    /** Первое 9–10-значное число (AnyDesk) в строке. */
    public static String findAnydesk(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DIGITS.matcher(text.replaceAll("\\s+", ""));
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
