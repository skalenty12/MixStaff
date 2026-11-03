package ru.autosetting.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Простая векторная отрисовка без текстур: скруглённые прямоугольники,
 * круги и переключатели в стиле современного клиента.
 */
public final class UiDraw {
    private UiDraw() {
    }

    public static void roundRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                int dy = r - row;
                inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            } else if (row >= height - r) {
                int dy = row - (height - r) + 1;
                inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            }
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    public static void glassPanel(DrawContext context, int x, int y, int width, int height,
                                  int radius, int accent) {
        glassPanel(context, x, y, width, height, radius, accent, 255);
    }

    public static void glassPanel(DrawContext context, int x, int y, int width, int height,
                                  int radius, int accent, int opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        roundRect(context, x - 2, y + 3, width + 4, height + 4, radius + 2,
                scaleAlpha(0x26020A1A, opacity));
        roundRect(context, x, y, width, height, radius, scaleAlpha(0x58D8F7FF, opacity));
        roundRect(context, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1),
                scaleAlpha(0xA51A2540, opacity));
        roundRect(context, x + 2, y + 2, width - 4, Math.max(1, height / 3),
                Math.max(0, radius - 2), scaleAlpha(withAlpha(accent, 0x12), opacity));
        roundRect(context, x + radius, y + 1, Math.max(1, width - radius * 2), 1, 1,
                scaleAlpha(0x80FFFFFF, opacity));
        roundRect(context, x + radius, y + height - 2,
                Math.max(1, width - radius * 2), 1, 1, scaleAlpha(0x268FEAFF, opacity));
        circle(context, x + radius, y + 2, 1, scaleAlpha(0xBFFFFFFF, opacity));
        circle(context, x + width - radius, y + 2, 1, scaleAlpha(0x66C7F5FF, opacity));
    }

    public static void glassIconStroke(DrawContext context, int x1, int y1, int x2, int y2,
                                       int thickness, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            circle(context, x1, y1, thickness / 2, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x1 + (x2 - x1) * step / steps;
            int y = y1 + (y2 - y1) * step / steps;
            circle(context, x, y, Math.max(0, thickness / 2), color);
        }
    }

    public static void glassSettingsIcon(DrawContext context, int centerX, int centerY,
                                         int radius, int color) {
        gear(context, centerX, centerY, radius, withAlpha(color, 210), 0x66515D7E);
        circle(context, centerX, centerY, Math.max(2, radius / 3), 0xBFEAFCFF);
    }

    public static void glassPlayerIcon(DrawContext context, int centerX, int centerY,
                                       int size, int color) {
        int headRadius = Math.max(2, size / 5);
        circle(context, centerX, centerY - size / 5, headRadius + 1, 0x55E9FBFF);
        circle(context, centerX, centerY - size / 5, headRadius, withAlpha(color, 220));
        int bodyWidth = size * 3 / 5;
        int bodyHeight = size * 2 / 5;
        roundRect(context, centerX - bodyWidth / 2, centerY + size / 8,
                bodyWidth, bodyHeight, bodyWidth / 2, withAlpha(color, 210));
        roundRect(context, centerX - bodyWidth / 3, centerY + size / 8,
                bodyWidth * 2 / 3, 1, 1, 0xBFEAFCFF);
    }

    public static void glassServerIcon(DrawContext context, int centerX, int centerY,
                                       int size, int color) {
        int rowWidth = size;
        int rowHeight = Math.max(4, size / 3);
        for (int row = 0; row < 2; row++) {
            int y = centerY - rowHeight - 1 + row * (rowHeight + 3);
            roundRect(context, centerX - rowWidth / 2, y, rowWidth, rowHeight,
                    Math.max(2, rowHeight / 3), 0x6659678D);
            roundRect(context, centerX - rowWidth / 2 + 1, y + 1, rowWidth - 2, 1, 1, 0xAAFFFFFF);
            circle(context, centerX - rowWidth / 2 + 3, y + rowHeight / 2,
                    1, withAlpha(color, 245));
            roundRect(context, centerX - rowWidth / 2 + 7, y + rowHeight / 2,
                    Math.max(2, rowWidth - 11), 1, 1, 0x99E9FBFF);
        }
    }

    public static void circle(DrawContext context, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
            context.fill(centerX - half, centerY + dy, centerX + half, centerY + dy + 1, color);
        }
    }

    public static void toggle(DrawContext context, int x, int y, int width, int height,
                              boolean on, int accent, int off) {
        roundRect(context, x, y, width, height, height / 2, on ? accent : off);
        int knob = height - 6;
        int knobX = on ? x + width - knob - 3 : x + 3;
        roundRect(context, knobX, y + 3, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    /** Шестерёнка: диск с восемью зубцами и тёмным отверстием в центре. */
    public static void gear(DrawContext context, int centerX, int centerY, int radius, int color) {
        gear(context, centerX, centerY, radius, color, 0xFF14151F);
    }

    public static void gear(DrawContext context, int centerX, int centerY, int radius,
                            int color, int holeColor) {
        int tooth = Math.max(3, radius / 2);
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0D * index / 8.0D;
            int toothX = centerX + (int) Math.round(Math.cos(angle) * (radius + 1));
            int toothY = centerY + (int) Math.round(Math.sin(angle) * (radius + 1));
            context.fill(toothX - tooth / 2, toothY - tooth / 2,
                    toothX - tooth / 2 + tooth, toothY - tooth / 2 + tooth, color);
        }
        circle(context, centerX, centerY, radius, color);
        circle(context, centerX, centerY, Math.max(2, radius * 2 / 5), holeColor);
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int scaleAlpha(int color, int opacity) {
        int alpha = color >>> 24;
        int scaled = alpha * Math.max(0, Math.min(255, opacity)) / 255;
        return withAlpha(color, scaled);
    }
}
