package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PermissionUtil {
    private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\s*\\[([^\\]]{1,32})\\]");

    private PermissionUtil() {
    }

    /**
     * Достаёт привилегию игрока из таб-листа (префикс перед ником) или из его команды.
     */
    public static String resolve(String nick) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return "Игрок";
        }

        PlayerListEntry entry = handler.getPlayerListEntry(nick);
        if (entry == null) {
            for (PlayerListEntry candidate : handler.getPlayerList()) {
                if (candidate.getProfile().getName().equalsIgnoreCase(nick)) {
                    entry = candidate;
                    break;
                }
            }
        }
        if (entry == null) {
            return "Игрок";
        }

        Text displayName = entry.getDisplayName();
        if (displayName != null) {
            String prefix = extractPrefix(displayName.getString());
            if (prefix != null) {
                return prefix;
            }
        }

        if (entry.getScoreboardTeam() != null) {
            String prefix = entry.getScoreboardTeam().getPrefix().getString();
            if (!prefix.isBlank()) {
                return prefix.trim();
            }
        }

        return "Игрок";
    }

    private static String extractPrefix(String display) {
        String clean = display.replaceAll("§.", "").trim();
        Matcher matcher = BRACKET_PREFIX.matcher(clean);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        int space = clean.indexOf(' ');
        if (space > 0) {
            String first = clean.substring(0, space).trim();
            if (!first.isEmpty() && first.length() <= 32) {
                return first;
            }
        }
        return null;
    }
}
