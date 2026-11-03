package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.IasCompat;
import ru.autosetting.util.MenuBackdrop;
import ru.autosetting.util.UiDraw;

public final class MixStaffTitleScreen extends TitleScreen {
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF7E828F;
    private static final int BASE_WIDTH = 430;
    private static final int BASE_HEIGHT = 218;
    private static final int BUTTON_Y = 138;
    private static final int BUTTON_RADIUS = 24;
    private static final int SETTINGS_X = 55;
    private static final int MULTIPLAYER_X = 158;
    private static final int SINGLEPLAYER_X = 270;
    private static final int EXIT_X = 375;

    private final MenuBackdrop backdrop = new MenuBackdrop();
    private final ModConfig config = ModConfig.get();
    private float scale = 1.0F;
    private int windowWidth;
    private int windowHeight;
    private int originX;
    private int originY;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public MixStaffTitleScreen() {
        super(false);
    }

    @Override
    protected void init() {
        clearChildren();
        IasCompat.hideAccountText();
        relayout();
    }

    private void relayout() {
        float fit = Math.min((width - 16) / (float) BASE_WIDTH, (height - 16) / (float) BASE_HEIGHT);
        scale = Math.max(0.45F, Math.min(config.homeMenuScale, fit));
        windowWidth = Math.round(BASE_WIDTH * scale);
        windowHeight = Math.round(BASE_HEIGHT * scale);
        originX = Math.round(config.homeMenuX * Math.max(0, width - windowWidth));
        originY = Math.round(config.homeMenuY * Math.max(0, height - windowHeight));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        backdrop.render(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        relayout();

        UiDraw.roundRect(context, originX, originY, windowWidth, windowHeight, 18, 0xE60C0D14);
        UiDraw.roundRect(context, originX + 16, originY, windowWidth - 32, 1, 0, 0x30FFFFFF);
        UiDraw.roundRect(context, originX, originY + Math.round(36 * scale), windowWidth, 1, 0, 0x22FFFFFF);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0.0F);
        matrices.scale(scale, scale, 1.0F);

        Text brand = Fonts.t("MixerGrief").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, brand, 22, 12, 0xFFFFC94D);

        String nick = client.getSession() == null || client.getSession().getUsername() == null
                || client.getSession().getUsername().isBlank()
                ? "игрок" : client.getSession().getUsername();
        Text welcome = Fonts.t("Добро пожаловать, " + nick + "!")
                .formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, welcome,
                (BASE_WIDTH - textRenderer.getWidth(welcome)) / 2, 60, TEXT_MAIN);
        context.drawTextWithShadow(textRenderer, Fonts.t("Выбери режим игры").formatted(Formatting.GRAY),
                (BASE_WIDTH - textRenderer.getWidth(Fonts.t("Выбери режим игры"))) / 2, 78, TEXT_MUTED);

        boolean settingsHovered = hovered(mouseX, mouseY, SETTINGS_X - BUTTON_RADIUS - 4,
                BUTTON_Y - BUTTON_RADIUS - 4, (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2);
        boolean multiHovered = hovered(mouseX, mouseY, MULTIPLAYER_X - BUTTON_RADIUS - 4,
                BUTTON_Y - BUTTON_RADIUS - 4, (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2);
        boolean singleHovered = hovered(mouseX, mouseY, SINGLEPLAYER_X - BUTTON_RADIUS - 4,
                BUTTON_Y - BUTTON_RADIUS - 4, (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2);
        boolean exitHovered = hovered(mouseX, mouseY, EXIT_X - BUTTON_RADIUS - 4,
                BUTTON_Y - BUTTON_RADIUS - 4, (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2);

        drawCircle(context, SETTINGS_X, BUTTON_Y, BUTTON_RADIUS, settingsHovered, false);
        UiDraw.glassSettingsIcon(context, SETTINGS_X, BUTTON_Y, 9, 0xFFB7A8FF);
        drawCircle(context, MULTIPLAYER_X, BUTTON_Y, BUTTON_RADIUS, multiHovered, false);
        UiDraw.glassServerIcon(context, MULTIPLAYER_X, BUTTON_Y, 20, 0xFF9EEAFF);
        drawCircle(context, SINGLEPLAYER_X, BUTTON_Y, BUTTON_RADIUS, singleHovered, false);
        UiDraw.glassPlayerIcon(context, SINGLEPLAYER_X, BUTTON_Y, 24, 0xFFB7EFFF);
        drawCircle(context, EXIT_X, BUTTON_Y, BUTTON_RADIUS, exitHovered, false);
        drawExitIcon(context, EXIT_X, BUTTON_Y, exitHovered ? 0xFFFFC4B8 : 0xFFFF9B87);

        drawLabel(context, "Настройки", SETTINGS_X, BUTTON_Y + 34, settingsHovered);
        drawLabel(context, "Сетевая игра", MULTIPLAYER_X, BUTTON_Y + 34, multiHovered);
        drawLabel(context, "Одиночная игра", SINGLEPLAYER_X, BUTTON_Y + 34, singleHovered);
        drawLabel(context, "Выход", EXIT_X, BUTTON_Y + 34, exitHovered);

        Text hint = Fonts.t("Перетащи за шапку · колесо — масштаб").formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, hint,
                (BASE_WIDTH - textRenderer.getWidth(hint)) / 2, 198, 0xFF777B88);
        matrices.pop();
    }

    private void drawLabel(DrawContext context, String value, int centerX, int y, boolean hovered) {
        Text label = Fonts.t(value);
        context.drawTextWithShadow(textRenderer, label, centerX - textRenderer.getWidth(label) / 2, y,
                hovered ? TEXT_MAIN : TEXT_MUTED);
    }

    private void drawCircle(DrawContext context, int centerX, int centerY, int radius,
                            boolean hovered, boolean primary) {
        if (hovered) {
            UiDraw.circle(context, centerX, centerY, radius + 4, 0x338DEBFF);
        }
        UiDraw.circle(context, centerX, centerY, radius, 0x88D8F7FF);
        UiDraw.circle(context, centerX, centerY, radius - 1,
                primary ? 0xDD6E69B9 : 0xAA343D5C);
        UiDraw.circle(context, centerX, centerY, radius - 3,
                primary ? 0x884A8AA3 : 0x55475072);
        for (int angle = 210; angle <= 330; angle += 8) {
            double radians = Math.toRadians(angle);
            int x = centerX + (int) Math.round(Math.cos(radians) * (radius - 2));
            int y = centerY + (int) Math.round(Math.sin(radians) * (radius - 2));
            UiDraw.circle(context, x, y, 1, 0x99FFFFFF);
        }
    }

    private void drawExitIcon(DrawContext context, int centerX, int centerY, int color) {
        UiDraw.glassIconStroke(context, centerX - 8, centerY - 9, centerX + 1, centerY - 9, 2, color);
        UiDraw.glassIconStroke(context, centerX - 8, centerY - 9, centerX - 8, centerY + 9, 2, color);
        UiDraw.glassIconStroke(context, centerX - 8, centerY + 9, centerX + 1, centerY + 9, 2, color);
        UiDraw.glassIconStroke(context, centerX + 1, centerY - 9, centerX + 1, centerY - 4, 2, color);
        UiDraw.glassIconStroke(context, centerX + 1, centerY + 4, centerX + 1, centerY + 9, 2, color);
        UiDraw.glassIconStroke(context, centerX - 3, centerY, centerX + 9, centerY, 2, color);
        UiDraw.glassIconStroke(context, centerX + 5, centerY - 4, centerX + 9, centerY, 2, color);
        UiDraw.glassIconStroke(context, centerX + 5, centerY + 4, centerX + 9, centerY, 2, color);
    }

    private boolean hovered(double mouseX, double mouseY, int x, int y, int rectWidth, int rectHeight) {
        return MenuBackdrop.hovered((mouseX - originX) / scale, (mouseY - originY) / scale,
                x, y, rectWidth, rectHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        relayout();
        if (button == 0) {
            double localX = (mouseX - originX) / scale;
            double localY = (mouseY - originY) / scale;
            if (inside(localX, localY, 0, 0, BASE_WIDTH, 36)) {
                dragging = true;
                dragOffsetX = mouseX - originX;
                dragOffsetY = mouseY - originY;
                return true;
            }
            if (inside(localX, localY, SETTINGS_X - BUTTON_RADIUS - 4, BUTTON_Y - BUTTON_RADIUS - 4,
                    (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2)) {
                client.setScreen(new OptionsScreen(this, client.options));
                return true;
            }
            if (inside(localX, localY, MULTIPLAYER_X - BUTTON_RADIUS - 4, BUTTON_Y - BUTTON_RADIUS - 4,
                    (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2)) {
                client.setScreen(new MixStaffMultiplayerScreen());
                return true;
            }
            if (inside(localX, localY, SINGLEPLAYER_X - BUTTON_RADIUS - 4, BUTTON_Y - BUTTON_RADIUS - 4,
                    (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2)) {
                client.setScreen(new MixStaffSelectWorldScreen(this));
                return true;
            }
            if (inside(localX, localY, EXIT_X - BUTTON_RADIUS - 4, BUTTON_Y - BUTTON_RADIUS - 4,
                    (BUTTON_RADIUS + 4) * 2, (BUTTON_RADIUS + 4) * 2)) {
                client.stop();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            int x = (int) Math.round(mouseX - dragOffsetX);
            int y = (int) Math.round(mouseY - dragOffsetY);
            config.homeMenuX = width <= windowWidth ? 0.0F
                    : (float) Math.max(0, Math.min(width - windowWidth, x)) / (width - windowWidth);
            config.homeMenuY = height <= windowHeight ? 0.0F
                    : (float) Math.max(0, Math.min(height - windowHeight, y)) / (height - windowHeight);
            relayout();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            config.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        relayout();
        if (inside(mouseX, mouseY, originX, originY, windowWidth, windowHeight)) {
            config.homeMenuScale = Math.max(0.6F, Math.min(1.6F,
                    config.homeMenuScale + (float) verticalAmount * 0.05F));
            config.save();
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean inside(double x, double y, double left, double top, double rectWidth, double rectHeight) {
        return x >= left && x <= left + rectWidth && y >= top && y <= top + rectHeight;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
