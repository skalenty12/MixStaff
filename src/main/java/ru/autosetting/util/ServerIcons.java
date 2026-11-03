package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.autosetting.AutoSettingClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Иконки серверов из favicon после пинга: скруглённый аватар на плитке. */
public final class ServerIcons {
    private static final Map<String, Identifier> ICONS = new HashMap<>();
    private static final Map<String, byte[]> LAST = new HashMap<>();

    private ServerIcons() {
    }

    public static void draw(DrawContext context, ServerInfo info, int x, int y, int size) {
        Identifier texture = texture(info);
        if (texture == null) {
            return;
        }
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0.0F, 0.0F,
                size, size, size, size);
    }

    public static boolean hasIcon(ServerInfo info) {
        return texture(info) != null;
    }

    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            for (Identifier id : ICONS.values()) {
                client.getTextureManager().destroyTexture(id);
            }
        }
        ICONS.clear();
        LAST.clear();
    }

    private static Identifier texture(ServerInfo info) {
        if (info == null) {
            return null;
        }
        byte[] favicon = info.getFavicon();
        if (favicon == null || favicon.length == 0) {
            return null;
        }
        String key = key(info);
        byte[] previous = LAST.get(key);
        if (!Arrays.equals(previous, favicon)) {
            register(key, favicon);
        }
        return ICONS.get(key);
    }

    private static String key(ServerInfo info) {
        if (info.address != null && !info.address.isBlank()) {
            return info.address.toLowerCase();
        }
        return info.name == null ? "server" : info.name.toLowerCase();
    }

    private static void register(String key, byte[] favicon) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
        try {
            NativeImage image = NativeImage.read(favicon);
            roundCorners(image, Math.max(8, Math.min(image.getWidth(), image.getHeight()) / 5));
            Identifier id = Identifier.of("autosetting", "server_icon/" + Math.abs(key.hashCode()));
            Identifier old = ICONS.remove(key);
            if (old != null) {
                client.getTextureManager().destroyTexture(old);
            }
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            ICONS.put(key, id);
            LAST.put(key, favicon);
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.debug("Не удалось разобрать иконку сервера {}", key, exception);
        }
    }

    private static void roundCorners(NativeImage image, int radius) {
        int width = image.getWidth();
        int height = image.getHeight();
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        int r2 = r * r;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx = 0;
                int dy = 0;
                if (x < r && y < r) {
                    dx = r - x;
                    dy = r - y;
                } else if (x >= width - r && y < r) {
                    dx = x - (width - r - 1);
                    dy = r - y;
                } else if (x < r && y >= height - r) {
                    dx = r - x;
                    dy = y - (height - r - 1);
                } else if (x >= width - r && y >= height - r) {
                    dx = x - (width - r - 1);
                    dy = y - (height - r - 1);
                }
                if (dx * dx + dy * dy > r2) {
                    image.setColorArgb(x, y, 0);
                }
            }
        }
    }
}
