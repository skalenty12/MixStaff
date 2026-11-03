package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.UiDraw;

import java.util.List;

/**
 * Плашка reports-list: общее количество жалоб и количество по категориям.
 */
public final class ReportsHud {
    public static final int WIDTH = 320;
    private static final int HEIGHT = 62;

    private ReportsHud() {
    }

    public static void render(DrawContext context, MinecraftClient client) {
        ModConfig config = ModConfig.get();
        if (!config.reportsHud) {
            return;
        }
        List<ReportsStore.Entry> entries = ReportsStore.entries();
        int[] placement = placement(context.getScaledWindowWidth(),
                context.getScaledWindowHeight(), entries.size());
        renderAt(context, client, placement[0], placement[1], entries);
    }

    /** Текущий масштаб плашки из конфига. */
    public static float scale() {
        return Math.max(0.7F, Math.min(1.6F, ModConfig.get().reportsScale));
    }

    /** Экранные координаты и размеры плашки: x, y, width, height. */
    public static int[] placement(int screenWidth, int screenHeight, int total) {
        float scale = scale();
        int width = Math.round(WIDTH * scale);
        int height = Math.round(height(total) * scale);
        int x = Math.round(ModConfig.get().reportsX * Math.max(0, screenWidth - width));
        int y = Math.round(ModConfig.get().reportsY * Math.max(0, screenHeight - height));
        return new int[]{x, y, width, height};
    }

    /** Рисует плашку в точке x/y с масштабом из конфига. */
    public static void renderAt(DrawContext context, MinecraftClient client, int x, int y,
                                List<ReportsStore.Entry> entries) {
        float scale = scale();
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        renderPanel(context, client, 0, 0, entries);
        matrices.pop();
    }

    public static int height(List<String> rawEntries) {
        return height(rawEntries == null ? 0 : rawEntries.size());
    }

    public static int height(int total) {
        return HEIGHT;
    }

    public static void renderPanel(DrawContext context, MinecraftClient client,
                                   int x, int y, List<ReportsStore.Entry> entries) {
        TextRenderer textRenderer = client.textRenderer;
        int total = entries.size();
        int height = height(total);

        UiDraw.glassPanel(context, x, y, WIDTH, height, 10, 0xFF8A78F8);

        Text title = Fonts.t("Жалобы").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, title, x + 12, y + 7, 0xFFF4F4F8);

        Text count = Fonts.t("всего " + total).formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, count,
                x + WIDTH - 12 - textRenderer.getWidth(count), y + 7, 0xFF9A9EAC);

        ReportsStore.Category[] categories = ReportsStore.Category.values();
        int cardWidth = 94;
        int gap = 4;
        int startX = x + (WIDTH - (cardWidth * categories.length + gap * (categories.length - 1))) / 2;
        for (int index = 0; index < categories.length; index++) {
            ReportsStore.Category category = categories[index];
            int cardX = startX + index * (cardWidth + gap);
            int categoryCount = 0;
            for (ReportsStore.Entry entry : entries) {
                if (entry.category() == category) {
                    categoryCount++;
                }
            }
            UiDraw.roundRect(context, cardX, y + 27, cardWidth, 27, 5, 0x335B6A8F);
            UiDraw.roundRect(context, cardX + 8, y + 27, cardWidth - 16, 1, 1,
                    0x557CE8FA);
            Text label = Fonts.t(category.label);
            context.drawTextWithShadow(textRenderer, label,
                    cardX + (cardWidth - textRenderer.getWidth(label)) / 2, y + 30, category.color);
            Text value = Fonts.t(Integer.toString(categoryCount)).formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, value,
                    cardX + (cardWidth - textRenderer.getWidth(value)) / 2, y + 41, 0xFFF4F4F8);
        }
    }
}
