package ru.autosetting.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Живой фон меню MixStaff: дрейфующие звёзды, медленно плывущие тёплые
 * свечения и «дышащий» прожектор из левого верхнего угла.
 */
public final class MenuBackdrop {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS = {
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    };

    private final List<Star> stars = new ArrayList<>();
    private int cachedWidth;
    private int cachedHeight;

    public void render(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, 0xFF05060A);
        ensureStars(width, height);

        double time = System.currentTimeMillis() / 1000.0D;

        // медленно плывущие тёплые свечения
        drawGlow(context, (int) (width * 0.22D + Math.sin(time * 0.18D) * 40.0D),
                (int) (height * 0.30D + Math.cos(time * 0.14D) * 26.0D), 180, 0x0CFF8A00);
        drawGlow(context, (int) (width * 0.42D + Math.cos(time * 0.11D) * 34.0D),
                (int) (height * 0.65D + Math.sin(time * 0.16D) * 30.0D), 150, 0x0AFFC94D);
        drawGlow(context, (int) (width * 0.80D + Math.sin(time * 0.09D) * 40.0D),
                (int) (height * 0.22D + Math.cos(time * 0.12D) * 22.0D), 130, 0x08FF8A00);

        for (Star star : stars) {
            double x = star.x + time * star.driftX;
            double y = star.y + time * star.driftY;
            x = ((x % width) + width) % width;
            y = ((y % height) + height) % height;
            float twinkle = 0.40F + 0.60F * (0.5F + 0.5F
                    * (float) Math.sin(time * star.speed + star.phase));
            int alpha = (int) (twinkle * 205);
            int color = star.warm ? (alpha << 24) | 0xFFC94D : (alpha << 24) | 0xFFFFFF;
            context.fill((int) x, (int) y, (int) (x + star.size), (int) (y + star.size), color);
        }

        double breath = 0.88D + 0.12D * Math.sin(time * 0.5D);
        double sway = Math.sin(time * 0.23D) * 0.05D;
        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            int alpha = (int) (86 * breath * Math.pow(1.0D - t, 1.5D));
            if (alpha <= 1) {
                continue;
            }
            int beam = (int) (width * ((0.72D + sway) * (1.0D - t) + 0.05D));
            context.fill(0, y, beam, y + 1, (alpha << 24) | 0xFF8A00);
        }
        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            int alpha = (int) (50 * breath * Math.pow(1.0D - t, 2.1D));
            if (alpha <= 1) {
                continue;
            }
            int beam = (int) (width * ((0.34D - sway) * (1.0D - t) + 0.03D));
            context.fill(0, y, beam, y + 1, (alpha << 24) | 0xFFC94D);
        }

        for (int row = 0; row < 110; row++) {
            int alpha = (int) (105 * (row / 110.0F));
            context.fill(0, height - 110 + row, width, height - 110 + row + 1, alpha << 24);
        }
    }

    private static void drawGlow(DrawContext context, int centerX, int centerY, int radius, int color) {
        UiDraw.circle(context, centerX, centerY, radius, color);
        UiDraw.circle(context, centerX, centerY, radius * 2 / 3, color);
    }

    private void ensureStars(int width, int height) {
        if (!stars.isEmpty() && cachedWidth == width && cachedHeight == height) {
            return;
        }
        stars.clear();
        cachedWidth = width;
        cachedHeight = height;
        Random random = new Random(2026L);
        int count = Math.max(110, width * height / 2200);
        for (int index = 0; index < count; index++) {
            stars.add(new Star(
                    random.nextFloat() * width,
                    random.nextFloat() * height,
                    random.nextFloat() < 0.18F ? 2.0F : 1.0F,
                    random.nextFloat() * 6.2832F,
                    0.7F + random.nextFloat() * 1.8F,
                    random.nextFloat() < 0.35F,
                    -6.0F - random.nextFloat() * 14.0F,
                    -2.0F - random.nextFloat() * 6.0F));
        }
    }

    public static void drawClock(DrawContext context, TextRenderer textRenderer, int width) {
        Text clock = Fonts.t(clockText());
        context.drawTextWithShadow(textRenderer, clock, width - 24 - textRenderer.getWidth(clock), 22,
                0xFFD5D7E0);
    }

    public static String clockText() {
        LocalTime now = LocalTime.now();
        String day = DAYS[LocalDate.now().getDayOfWeek().getValue() - 1];
        return now.format(TIME) + ", " + day;
    }

    public static String fit(TextRenderer textRenderer, String text, int maxWidth) {
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

    public static boolean hovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private record Star(float x, float y, float size, float phase, float speed, boolean warm,
                        float driftX, float driftY) {
    }
}
