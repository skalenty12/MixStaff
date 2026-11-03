package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.UiDraw;

/**
 * «Динамический остров» сверху экрана: полностью скруглённая пилюля
 * с треком из системного плеера, текстом песни, FPS и пингом.
 */
public final class MusicIsland {
    private static final int ACCENT = 0xFF8EDFF5;
    private static final int COLLAPSED_HEIGHT = 30;
    private static final int EXPANDED_WIDTH = 340;
    private static final int EXPANDED_HEIGHT = 96;

    private static int expandX;
    private static int expandY;
    private static int prevX;
    private static int playX;
    private static int nextX;
    private static int controlsY;
    private static boolean expanded;

    private MusicIsland() {
    }

    public static void render(DrawContext context, MinecraftClient client,
                              MediaSessionBridge.Track track, long frameTime, float pulse) {
        if (!ModConfig.get().musicIsland) {
            expanded = false;
            return;
        }
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = context.getScaledWindowWidth();

        String collapsedText = track != null ? lyricOrTitle(track) : "MixerGrief";
        int textWidth = textRenderer.getWidth(Fonts.t(collapsedText));
        int statsWidth = statsWidth(textRenderer, client);
        int collapsedWidth = Math.max(210, Math.min(380, textWidth + statsWidth + 62));
        int x = screenWidth / 2 - collapsedWidth / 2;
        int y = 8;

        ModConfig config = ModConfig.get();
        float islandScale = config.musicIslandScale * 0.5F;
        int baseCenterX = screenWidth / 2;
        int baseCenterY = y + COLLAPSED_HEIGHT / 2;
        int targetCenterX = Math.round(config.musicIslandX * screenWidth);
        int targetCenterY = Math.round(config.musicIslandY * context.getScaledWindowHeight());
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(targetCenterX, targetCenterY, 0.0F);
        matrices.scale(islandScale, islandScale, 1.0F);
        matrices.translate(-baseCenterX, -baseCenterY, 0.0F);

        double mouseX = baseCenterX + (client.mouse.getX() - targetCenterX) / islandScale;
        double mouseY = baseCenterY + (client.mouse.getY() - targetCenterY) / islandScale;
        boolean hovered = inside(mouseX, mouseY, x, y, collapsedWidth, COLLAPSED_HEIGHT);
        if (hovered || HudEditOverlay.isEditing(client)) {
            renderExpanded(context, client, screenWidth, textRenderer, track, frameTime, pulse);
            matrices.pop();
            return;
        }
        expanded = false;

        // пилюля: радиус = половине высоты
        UiDraw.glassPanel(context, x, y, collapsedWidth, COLLAPSED_HEIGHT,
                COLLAPSED_HEIGHT / 2, ACCENT);

        if (track == null) {
            Text label = Fonts.t("MixerGrief").formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, label, x + 16, y + 11, 0xFFF4F4F8);
            drawStats(context, textRenderer, client, x + collapsedWidth - 16, y, true);
            matrices.pop();
            return;
        }

        drawEqualizer(context, x + 16, y + COLLAPSED_HEIGHT / 2, frameTime,
                track.playing() ? ACCENT : 0xFF4A4D5C);
        if (pulse > 0.0F) {
            int alpha = Math.round(100.0F * pulse);
            UiDraw.circle(context, x + 16, y + COLLAPSED_HEIGHT / 2,
                    4 + Math.round(5.0F * pulse), (alpha << 24) | (ACCENT & 0x00FFFFFF));
        }
        Text line = Fonts.t(fit(textRenderer, collapsedText, collapsedWidth - statsWidth - 70));
        context.drawTextWithShadow(textRenderer, line, x + 34, y + 11, 0xFFE8E9F0);
        drawStats(context, textRenderer, client, x + collapsedWidth - 16, y, false);
        matrices.pop();
    }

    private static void renderExpanded(DrawContext context, MinecraftClient client, int screenWidth,
                                       TextRenderer textRenderer, MediaSessionBridge.Track track,
                                       long frameTime, float pulse) {
        expanded = true;
        int x = screenWidth / 2 - EXPANDED_WIDTH / 2;
        int y = 6;
        expandX = x;
        expandY = y;
        controlsY = y + EXPANDED_HEIGHT - 24;
        int radius = EXPANDED_HEIGHT / 2;

        UiDraw.glassPanel(context, x, y, EXPANDED_WIDTH, EXPANDED_HEIGHT, radius, ACCENT);
        // тонкая цветная дуга слева как акцент, не выходящая за скругление
        UiDraw.circle(context, x + 26, y + radius, 4, ACCENT);
        if (pulse > 0.0F) {
            int alpha = Math.round(90.0F * pulse);
            UiDraw.circle(context, x + 26, y + radius, 5 + Math.round(5.0F * pulse),
                    (alpha << 24) | (ACCENT & 0x00FFFFFF));
        }

        int left = x + 40;
        int rightLimit = x + EXPANDED_WIDTH - 96;
        Text title = Fonts.t(fit(textRenderer,
                track != null ? track.title() : "MixerGrief", rightLimit - left))
                .formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, title, left, y + 16, 0xFFF4F4F8);

        Text artist = Fonts.t(fit(textRenderer,
                track != null ? track.artist() : " ", 150))
                .formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, artist,
                x + EXPANDED_WIDTH - 96 - textRenderer.getWidth(artist), y + 16, 0xFF8A8E9C);

        String lyric = track != null ? LyricsService.currentLine(track.title(), track.artist(),
                track.position()) : null;
        Text lyricText = lyric == null
                ? Fonts.t(track == null ? "Нет данных о медиатреке" :
                track.playing() ? "Текст песни не найден" : "на паузе").formatted(Formatting.DARK_GRAY)
                : Fonts.t(fit(textRenderer, lyric, rightLimit - left));
        context.drawTextWithShadow(textRenderer, lyricText, left, y + 34,
                lyric == null ? 0xFF6E7280 : 0xFFD9DBE4);

        int duration = track == null ? 0 : track.duration();
        int position = track == null ? 0 : track.position();
        int barWidth = rightLimit - left;
        UiDraw.roundRect(context, left, y + 54, barWidth, 4, 2, 0x33FFFFFF);
        if (duration > 0) {
            int filled = (int) (barWidth * Math.min(1.0D, position / (double) duration));
            UiDraw.roundRect(context, left, y + 54, filled, 4, 2, ACCENT);
        }

        int centerX = (left + rightLimit) / 2;
        prevX = centerX - 44;
        playX = centerX;
        nextX = centerX + 44;
        int buttonY = controlsY + 7;
        button(context, prevX, buttonY, false);
        playPause(context, playX, buttonY, track != null && track.playing());
        button(context, nextX, buttonY, true);

        drawStats(context, textRenderer, client, x + EXPANDED_WIDTH - 26, y, false);
    }

    /** FPS и пинг справа: «144 FPS» и «12 ms» со цветом по качеству связи. */
    private static void drawStats(DrawContext context, TextRenderer textRenderer,
                                  MinecraftClient client, int right, int top, boolean centered) {
        int fps = client.getCurrentFps();
        int ping = ping(client);
        int pingColor = ping <= 0 ? 0xFF7E828F : ping < 60 ? 0xFF34D399
                : ping < 120 ? 0xFFFFC94D : 0xFFFF5C5C;

        Text fpsText = Fonts.t(fps + " FPS").formatted(Formatting.GRAY);
        Text pingText = Fonts.t(ping <= 0 ? "— ms" : ping + " ms");

        int y = centered ? top + 11 : top + 20;
        int pingWidth = textRenderer.getWidth(pingText);
        int fpsWidth = textRenderer.getWidth(fpsText);
        context.drawTextWithShadow(textRenderer, pingText, right - pingWidth, y, pingColor);
        context.drawTextWithShadow(textRenderer, fpsText, right - pingWidth - 10 - fpsWidth, y, 0xFFB9BCC7);
    }

    private static int statsWidth(TextRenderer textRenderer, MinecraftClient client) {
        int ping = ping(client);
        String pingText = ping <= 0 ? "— ms" : ping + " ms";
        return textRenderer.getWidth(Fonts.t(client.getCurrentFps() + " FPS"))
                + 10 + textRenderer.getWidth(Fonts.t(pingText));
    }

    public static int ping(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return 0;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? 0 : Math.max(0, entry.getLatency());
    }

    private static void button(DrawContext context, int centerX, int centerY, boolean forward) {
        UiDraw.glassPanel(context, centerX - 12, centerY - 8, 24, 16, 8, ACCENT);
        int size = 7;
        if (forward) {
            triangle(context, centerX + 2, centerY, size, 1, 0xFFD9DBE4);
            context.fill(centerX - 6, centerY - 4, centerX - 4, centerY + 4, 0xFFD9DBE4);
        } else {
            triangle(context, centerX - 2, centerY, size, -1, 0xFFD9DBE4);
            context.fill(centerX + 4, centerY - 4, centerX + 6, centerY + 4, 0xFFD9DBE4);
        }
    }

    private static void playPause(DrawContext context, int centerX, int centerY, boolean playing) {
        UiDraw.circle(context, centerX, centerY, 13, 0x3357D9F3);
        UiDraw.circle(context, centerX, centerY, 11, 0xFF7169D7);
        UiDraw.circle(context, centerX - 3, centerY - 5, 2, 0x99FFFFFF);
        if (playing) {
            context.fill(centerX - 3, centerY - 4, centerX - 1, centerY + 4, 0xFFFFFFFF);
            context.fill(centerX + 1, centerY - 4, centerX + 3, centerY + 4, 0xFFFFFFFF);
        } else {
            triangle(context, centerX + 1, centerY, 5, 1, 0xFFFFFFFF);
        }
    }

    private static void triangle(DrawContext context, int centerX, int centerY, int size, int direction, int color) {
        for (int row = -size; row <= size; row++) {
            int half = size - Math.abs(row);
            int width = Math.max(1, (int) (half * 1.2D));
            if (direction > 0) {
                context.fill(centerX - size / 2, centerY + row, centerX - size / 2 + width, centerY + row + 1, color);
            } else {
                context.fill(centerX + size / 2 - width, centerY + row, centerX + size / 2, centerY + row + 1, color);
            }
        }
    }

    private static void drawEqualizer(DrawContext context, int x, int centerY, long frameTime, int color) {
        long time = frameTime;
        for (int index = 0; index < 3; index++) {
            int height = 4 + (int) (Math.abs(Math.sin(time / 180.0D + index * 1.4D)) * 8);
            context.fill(x + index * 4, centerY - height / 2, x + index * 4 + 3, centerY + height / 2, color);
        }
    }

    private static String lyricOrTitle(MediaSessionBridge.Track track) {
        String lyric = LyricsService.currentLine(track.title(), track.artist(), track.position());
        if (lyric != null && !lyric.isBlank()) {
            return lyric;
        }
        String artist = track.artist();
        String title = track.title();
        if (artist == null || artist.isBlank()) {
            return "♪ " + title;
        }
        return "♪ " + title + " — " + artist;
    }

    private static String fit(TextRenderer textRenderer, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (textRenderer.getWidth(Fonts.t(text)) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 3 && textRenderer.getWidth(Fonts.t(result + "…")) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    /** Обрабатывает клик по острову. Возвращает true, если клик поглощён. */
    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !expanded) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ModConfig config = ModConfig.get();
        int targetCenterX = Math.round(config.musicIslandX * client.getWindow().getScaledWidth());
        int targetCenterY = Math.round(config.musicIslandY * client.getWindow().getScaledHeight());
        float islandScale = config.musicIslandScale * 0.5F;
        mouseX = client.getWindow().getScaledWidth() / 2.0D
                + (mouseX - targetCenterX) / islandScale;
        mouseY = 23.0D + (mouseY - targetCenterY) / islandScale;
        if (!inside(mouseX, mouseY, expandX, expandY, EXPANDED_WIDTH, EXPANDED_HEIGHT)) {
            return false;
        }
        if (Math.abs(mouseY - (controlsY + 7)) <= 12) {
            if (Math.abs(mouseX - prevX) <= 14) {
                MediaSessionBridge.send("PREV");
                return true;
            }
            if (Math.abs(mouseX - playX) <= 14) {
                MediaSessionBridge.send("PLAY");
                return true;
            }
            if (Math.abs(mouseX - nextX) <= 14) {
                MediaSessionBridge.send("NEXT");
                return true;
            }
        }
        return true;
    }

    public static int[] editBounds(MinecraftClient client) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        MediaSessionBridge.Track track = MediaSessionBridge.track();
        int textWidth = client.textRenderer.getWidth(Fonts.t(
                track != null ? lyricOrTitle(track) : "MixerGrief"));
        int collapsedWidth = Math.max(210, Math.min(380, textWidth + statsWidth(client.textRenderer, client) + 62));
        float scale = ModConfig.get().musicIslandScale * 0.5F;
        int centerX = Math.round(ModConfig.get().musicIslandX * screenWidth);
        int centerY = Math.round(ModConfig.get().musicIslandY * screenHeight);
        boolean expandedBounds = expanded
                || ModConfig.get().musicIsland && HudEditOverlay.isEditing(client);
        int width = Math.round((expandedBounds ? EXPANDED_WIDTH : collapsedWidth) * scale);
        int height = Math.round((expandedBounds ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT) * scale);
        int offsetY = expandedBounds ? Math.round(31.0F * scale) : 0;
        return new int[]{centerX - width / 2, centerY + offsetY - height / 2, width, height};
    }

    public static boolean isExpanded() {
        return expanded || ModConfig.get().musicIsland
                && HudEditOverlay.isEditing(MinecraftClient.getInstance());
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
