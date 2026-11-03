package ru.autosetting.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.TargetUtil;
import ru.autosetting.util.UiDraw;

import java.util.List;
import java.util.Locale;

/**
 * Колесо действий: открывается на игрока под прицелом.
 * Сектора разбиты на разделы «Проверка» и «Удобство».
 */
public final class WheelScreen extends Screen {
    private static final int ACCENT_CHECK = 0xFF8B5CF6;
    private static final int ACCENT_UTIL = 0xFF3B9EFF;
    private static final int CHECK_BG = 0xE61B1728;
    private static final int UTIL_BG = 0xE6161B2A;
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF80848F;
    private static final float GAP_DEGREES = 3.0F;

    private static final List<Item> ITEMS = List.of(
            new Item("Discord", "revise start %NICK% discord", Section.CHECK, true),
            new Item("AnyDesk", "revise start %NICK% anydesk", Section.CHECK, true),
            new Item("+5 минут", "revise addtime 5m", Section.CHECK, false),
            new Item("Стоп таймер", "revise stoptime", Section.CHECK, false),
            new Item("Финиш", "revise finish", Section.CHECK, false),
            new Item("FTStaff", "ftstaff", Section.UTILITY, false),
            new Item("V", "v", Section.UTILITY, false),
            new Item("Fly", "fly", Section.UTILITY, false),
            new Item("FlySpeed", "flyspeed", Section.UTILITY, false)
    );

    private final String targetNick;
    private final int wheelKey;
    private final long openedAt = System.currentTimeMillis();
    private int selected = -1;

    public WheelScreen(String targetNick, int wheelKey) {
        super(Text.literal("Wheel"));
        this.targetNick = targetNick;
        this.wheelKey = wheelKey;
    }

    /** Открывает колесо на игрока под прицелом. */
    public static void open(MinecraftClient client) {
        client.setScreen(new WheelScreen(TargetUtil.findTargetNick(client), ModConfig.get().wheelKey));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x9E070710);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        float progress = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 150.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        float grow = 0.9F + 0.1F * eased;

        int centerX = width / 2;
        int centerY = height / 2;
        int baseRadius = (int) Math.max(84, Math.min(118, Math.min(width, height) * 0.20D));
        int radius = Math.round(baseRadius * grow);
        int thickness = Math.round(35 * grow);

        selected = hitTest(mouseX, mouseY, centerX, centerY, radius, thickness);

        // фон-свечение и внешние кольца
        UiDraw.circle(context, centerX, centerY, radius + thickness / 2 + 26, 0x1A000000);
        UiDraw.circle(context, centerX, centerY, radius + thickness / 2 + 14, 0x26000000);
        ring(context, centerX, centerY, radius + thickness / 2 + 8, 0x33E0D8FF);
        ring(context, centerX, centerY, radius - thickness / 2 - 6, 0x40FFFFFF);

        double step = 360.0D / ITEMS.size();
        for (int index = 0; index < ITEMS.size(); index++) {
            Item item = ITEMS.get(index);
            double centerAngle = -90.0D + index * step;
            double start = centerAngle - step / 2 + GAP_DEGREES;
            double end = centerAngle + step / 2 - GAP_DEGREES;
            boolean hovered = index == selected;
            int accent = item.section == Section.CHECK ? ACCENT_CHECK : ACCENT_UTIL;

            if (hovered) {
                arc(context, centerX, centerY, radius, start, end, thickness + 8, UiDraw.withAlpha(accent, 90));
            }
            arc(context, centerX, centerY, radius, start, end, hovered ? thickness + 2 : thickness,
                    hovered ? accent : (item.section == Section.CHECK ? CHECK_BG : UTIL_BG));

            double radians = Math.toRadians(centerAngle);
            Text label = Fonts.t(item.label).formatted(hovered ? Formatting.BOLD : Formatting.RESET);
            int labelX = centerX + (int) Math.round(Math.cos(radians) * radius);
            int labelY = centerY + (int) Math.round(Math.sin(radians) * radius);
            context.drawTextWithShadow(textRenderer, label,
                    labelX - textRenderer.getWidth(label) / 2, labelY - 4,
                    hovered ? 0xFFFFFFFF : 0xFFD9DBE4);
        }

        // центральная панель
        UiDraw.circle(context, centerX, centerY, 56, 0x55101018);
        UiDraw.circle(context, centerX, centerY, 53, 0xF00E0F17);
        ring(context, centerX, centerY, 53, 0x668B5CF6);

        Text title = Fonts.t("WHEEL").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, title,
                centerX - textRenderer.getWidth(title) / 2, centerY - 32, 0xFF8A8E9C);

        Text nick = targetNick == null
                ? Fonts.t("нет цели").formatted(Formatting.DARK_GRAY)
                : Fonts.t(targetNick).formatted(Formatting.WHITE, Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, nick,
                centerX - textRenderer.getWidth(nick) / 2, centerY - 18, 0xFFFFFFFF);

        Text action;
        if (selected >= 0) {
            Item item = ITEMS.get(selected);
            String command = resolve(item);
            if (command == null) {
                action = Fonts.t("нужен игрок под прицелом").formatted(Formatting.RED);
            } else {
                action = Fonts.t("/" + command)
                        .formatted(item.section == Section.CHECK ? Formatting.LIGHT_PURPLE : Formatting.AQUA);
            }
        } else {
            action = Fonts.t("наведи на сектор").formatted(Formatting.DARK_GRAY);
        }
        context.drawTextWithShadow(textRenderer, action,
                centerX - textRenderer.getWidth(action) / 2, centerY + 2, 0xFFFFFFFF);

        Text keyHint = Fonts.t("отпусти " + keyName(wheelKey) + " — выполнить").formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, keyHint,
                centerX - textRenderer.getWidth(keyHint) / 2, centerY + 22, TEXT_MUTED);

        drawLegend(context, centerX);
    }

    private void drawLegend(DrawContext context, int centerX) {
        int y = height - 32;
        Text check = Fonts.t("● Проверка");
        Text utility = Fonts.t("● Удобство");
        int gap = 22;
        int total = textRenderer.getWidth(check) + gap + textRenderer.getWidth(utility);
        int x = centerX - total / 2;
        context.drawTextWithShadow(textRenderer, check, x, y, ACCENT_CHECK);
        context.drawTextWithShadow(textRenderer, utility,
                x + textRenderer.getWidth(check) + gap, y, ACCENT_UTIL);
        Text footer = Fonts.t("ЛКМ — выполнить · Esc — отмена").formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, footer,
                centerX - textRenderer.getWidth(footer) / 2, y + 14, TEXT_MUTED);
    }

    private static void ring(DrawContext context, int centerX, int centerY, int radius, int color) {
        for (int angle = 0; angle < 360; angle += 2) {
            double radians = Math.toRadians(angle);
            int x = centerX + (int) Math.round(Math.cos(radians) * radius);
            int y = centerY + (int) Math.round(Math.sin(radians) * radius);
            UiDraw.circle(context, x, y, 1, color);
        }
    }

    private static void arc(DrawContext context, int centerX, int centerY, int radius,
                            double startAngle, double endAngle, int thickness, int color) {
        double step = 1.6D;
        int dot = thickness / 2;
        for (double angle = startAngle; angle <= endAngle; angle += step) {
            double radians = Math.toRadians(angle);
            int x = centerX + (int) Math.round(Math.cos(radians) * radius);
            int y = centerY + (int) Math.round(Math.sin(radians) * radius);
            UiDraw.circle(context, x, y, dot, color);
        }
    }

    private int hitTest(double mouseX, double mouseY, int centerX, int centerY, int radius, int thickness) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < radius - thickness / 2.0D - 6 || distance > radius + thickness / 2.0D + 6) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double step = 360.0D / ITEMS.size();
        int best = -1;
        double bestDelta = step / 2.0D;
        for (int index = 0; index < ITEMS.size(); index++) {
            double centerAngle = -90.0D + index * step;
            double delta = Math.abs(normalize(angle - centerAngle));
            if (delta < bestDelta) {
                bestDelta = delta;
                best = index;
            }
        }
        return best;
    }

    private static double normalize(double angle) {
        double result = angle % 360.0D;
        if (result > 180.0D) {
            result -= 360.0D;
        } else if (result < -180.0D) {
            result += 360.0D;
        }
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            execute(selected);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == wheelKey) {
            execute(selected);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void execute(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (index >= 0 && index < ITEMS.size() && client.player != null) {
            Item item = ITEMS.get(index);
            String command = resolve(item);
            if (command == null) {
                client.inGameHud.getChatHud().addMessage(Text.literal(
                                "[Wheel] Наведи прицел на игрока, чтобы выполнить /"
                                        + item.command.replace("%NICK%", "<ник>"))
                        .formatted(Formatting.RED));
            } else {
                client.player.networkHandler.sendChatCommand(command);
                client.inGameHud.getChatHud().addMessage(
                        Text.literal("[Wheel] /" + command).formatted(Formatting.DARK_GRAY));
            }
        }
        close();
    }

    /** Подставляет ник цели; null — если команде нужен игрок, а цели нет. */
    private String resolve(Item item) {
        if (!item.needsTarget) {
            return item.command;
        }
        if (targetNick == null || targetNick.isBlank()) {
            return null;
        }
        return item.command.replace("%NICK%", targetNick);
    }

    private static String keyName(int keyCode) {
        try {
            return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(keyCode)
                    .getLocalizedText().getString().toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            return "клавишу";
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private enum Section {
        CHECK, UTILITY
    }

    private record Item(String label, String command, Section section, boolean needsTarget) {
    }
}
