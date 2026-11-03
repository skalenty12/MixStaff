package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.AutoSettingClient;
import ru.autosetting.config.ModConfig;
import ru.autosetting.feature.DiscordRpc;
import ru.autosetting.feature.DynamicIslandFeature;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.Logo;
import ru.autosetting.util.MenuBackdrop;
import ru.autosetting.util.UiDraw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Главное меню MixerGrief — плавающее окно: его можно перетаскивать за шапку
 * и менять масштаб кнопками «−/+» или колесом мыши. Положение и масштаб
 * хранятся в конфиге.
 */
public final class SettingScreen extends Screen {
    private static final int ACCENT = 0xFF8B5CF6;
    private static final int ROW_BG = 0xFF15161F;
    private static final int ROW_HOVER = 0xFF1C1E2B;
    private static final int ROW_BORDER = 0xFF23252F;
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF6E7280;
    private static final int OFF = 0xFF2A2C38;
    private static final String[] TABS = {"Visuals", "HUD", "Utilities"};

    private static final int BASE_WIDTH = 720;
    private static final int BASE_HEIGHT = 470;
    private static final int HEADER = 48;
    private static final int MARGIN = 24;
    private static final int ROW_GAP = 10;
    private static final int ROW_HEIGHT = 44;
    private static final float MIN_SCALE = 0.6F;
    private static final float MAX_SCALE = 1.6F;

    private final ModConfig config = ModConfig.get();
    private final MenuBackdrop backdrop = new MenuBackdrop();
    private final List<Row> rows = new ArrayList<>();
    private final List<int[]> tabRects = new ArrayList<>();
    private int tab = 0;
    private BindTarget awaiting;
    private int rowHeight = ROW_HEIGHT;

    private float scale = 1.0F;
    private int windowWidth = BASE_WIDTH;
    private int windowHeight = BASE_HEIGHT;
    private int originX;
    private int originY;

    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private final int[] minusRect = new int[4];
    private final int[] plusRect = new int[4];
    private final int[] closeRect = new int[4];
    private final int[] headerDragRect = new int[4];

    private enum BindTarget {
        MENU, WHEEL, MUTE, SPEC, FREELOOK, TEMPLATES
    }

    public SettingScreen() {
        super(Text.literal("MixerGrief"));
    }

    @Override
    protected void init() {
        relayout();
        rebuildRows();
    }

    /** Пересчитывает размеры и положение окна из конфига. */
    private void relayout() {
        float requested = Math.max(MIN_SCALE, Math.min(MAX_SCALE, config.menuScale));
        float fitWidth = (width - 16) / (float) BASE_WIDTH;
        float fitHeight = (height - 16) / (float) BASE_HEIGHT;
        scale = Math.max(0.45F, Math.min(requested, Math.min(fitWidth, fitHeight)));
        windowWidth = Math.round(BASE_WIDTH * scale);
        windowHeight = Math.round(BASE_HEIGHT * scale);
        originX = Math.round(config.menuX * Math.max(0, width - windowWidth));
        originY = Math.round(config.menuY * Math.max(0, height - windowHeight));
    }

    private double localX(double mouseX) {
        return (mouseX - originX) / scale;
    }

    private double localY(double mouseY) {
        return (mouseY - originY) / scale;
    }

    private void rebuildRows() {
        rows.clear();
        switch (tab) {
            case 0 -> {
                addToggle("Target HUD", "Привилегия, ник, HP и экипировка над прицелом",
                        () -> config.targetHud, value -> config.targetHud = value);
                addAction("Target Size", "Размер панели Target",
                        () -> "x" + String.format(java.util.Locale.ROOT, "%.2f", config.targetScale),
                        () -> {
                            config.targetScale = nextTargetScale(config.targetScale);
                            config.save();
                        });
                addToggle("Big-Head", "Увеличение головы игрока",
                        () -> config.bigHead, value -> config.bigHead = value);
                addAction("Big-Head Size", "Размер головы",
                        () -> "x" + config.bigHeadScale, () -> {
                            config.bigHeadScale = config.bigHeadScale == 2 ? 4 : config.bigHeadScale == 4 ? 8 : 2;
                            config.save();
                        });
                addToggle("Fullbright", "Ночное видение без изменения настроек игры",
                        () -> config.fullbright, value -> config.fullbright = value);
                addToggle("Freelook", "Удерживай бинд для свободного обзора от третьего лица",
                        () -> config.freelook, value -> config.freelook = value);
                addToggle("AutoSpeck", "Уведомления о запросе слежки",
                        () -> config.autoSpeck, value -> config.autoSpeck = value);
            }
            case 1 -> {
                addToggle("Target HUD", "Привилегия, ник, HP и экипировка над прицелом",
                        () -> config.targetHud, value -> config.targetHud = value);
                addToggle("Reports HUD", "Плашка reports-list на экране",
                        () -> config.reportsHud, value -> config.reportsHud = value);
                addAction("Reports Size", "Размер плашки жалоб",
                        () -> "x" + String.format(java.util.Locale.ROOT, "%.2f", config.reportsScale),
                        () -> {
                            config.reportsScale = nextReportScale(config.reportsScale);
                            config.save();
                        });
                addToggle("Music Island", "Трек из системного плеера и текст песни",
                        () -> config.musicIsland, DynamicIslandFeature::setEnabled);
                addAction("Редактор HUD", "Перемещение и масштаб всех HUD-плашек",
                        () -> "открыть", () -> client.setScreen(new HudEditorScreen()));
                addToggle("HUD подсказки", "Плашки о спеке и муте на экране",
                        () -> config.hudHints, value -> config.hudHints = value);
                addToggle("ChatTime", "Время перед сообщениями чата",
                        () -> config.chatTime, value -> config.chatTime = value);
                addToggle("Sounds", "Звонок при спеке и муте",
                        () -> config.sounds, value -> config.sounds = value);
            }
            case 2 -> {
                addToggle("Discord RPC", "Статус MixerGrief, ник, сервер и время",
                        () -> config.discordRpc, value -> {
                            config.discordRpc = value;
                            if (value) {
                                DiscordRpc.init();
                            }
                        });
                addToggle("Auto-муты", "Поиск нарушений правил 2.1–2.7",
                        () -> config.autoMuteDetect, value -> config.autoMuteDetect = value);
                addToggle("AutoMute", "Скриншот в mutes и форма /push",
                        () -> config.autoMute, value -> config.autoMute = value);
                addToggle("AutoProof", "Скриншот в proofs при /push",
                        () -> config.autoProof, value -> config.autoProof = value);
                addToggle("Auto copy AnyDesk", "Копировать 9–10 цифр из чата и показывать плашку",
                        () -> config.autoCopyAnydesk, value -> config.autoCopyAnydesk = value);
                addToggle("Anti-AFK", "Отключиться после 5 минут бездействия, чтобы избежать варна",
                        () -> config.antiAfk, value -> config.antiAfk = value);
                addAction("Templates", "Три готовых сообщения проверки",
                        () -> "открыть", () -> client.setScreen(new TemplatesScreen()));
                addAction("Skyline Dash", "Мини-игра MixerGrief",
                        () -> "играть", () -> client.setScreen(new SkylineDashScreen()));
                addAction("Кнопка меню", "Клавиша открытия этого меню",
                        () -> keyName(config.clickGuiKey), () -> awaiting = BindTarget.MENU);
                addAction("Клавиша Wheel", "Колесо действий на игрока",
                        AutoSettingClient::wheelKeyName, () -> awaiting = BindTarget.WHEEL);
                addAction("Клавиша MUTE", "Быстрый мут в игре",
                        AutoSettingClient::muteKeyName, () -> awaiting = BindTarget.MUTE);
                addAction("Клавиша SPEC", "/spec последнему запросившему",
                        AutoSettingClient::specKeyName, () -> awaiting = BindTarget.SPEC);
                addAction("Клавиша Freelook", "Удержание — свободный обзор",
                        AutoSettingClient::freelookKeyName, () -> awaiting = BindTarget.FREELOOK);
                addAction("Клавиша Templates", "Открыть шар с шаблонами проверки",
                        AutoSettingClient::templatesKeyName, () -> awaiting = BindTarget.TEMPLATES);
                addAction("Открыть AutoMute", "Форма мута со скриншотом",
                        () -> "", () -> client.setScreen(new AutoMuteScreen(this)));
            }
        }
        layoutRows();
    }

    private static float nextTargetScale(float current) {
        float[] scales = {0.75F, 1.0F, 1.25F, 1.5F, 2.0F};
        for (int index = 0; index < scales.length; index++) {
            if (Math.abs(scales[index] - current) < 0.01F) {
                return scales[(index + 1) % scales.length];
            }
        }
        return 1.0F;
    }

    private static float nextReportScale(float current) {
        float[] scales = {0.75F, 1.0F, 1.25F, 1.5F};
        for (int index = 0; index < scales.length; index++) {
            if (Math.abs(scales[index] - current) < 0.01F) {
                return scales[(index + 1) % scales.length];
            }
        }
        return 1.0F;
    }

    private void addToggle(String title, String subtitle, BooleanSupplier state, Consumer<Boolean> setter) {
        rows.add(new Row(title, subtitle, true, state, setter, null, null));
    }

    private void addAction(String title, String subtitle, Supplier<String> value, Runnable action) {
        rows.add(new Row(title, subtitle, false, null, null, action, value));
    }

    private void layoutRows() {
        int contentWidth = BASE_WIDTH - MARGIN * 2;
        boolean twoColumns = contentWidth >= 480;
        int columnWidth = twoColumns ? (contentWidth - ROW_GAP) / 2 : contentWidth;
        int startY = HEADER + 12;
        rowHeight = ROW_HEIGHT;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int column = twoColumns ? index % 2 : 0;
            int line = twoColumns ? index / 2 : index;
            row.x = MARGIN + column * (columnWidth + ROW_GAP);
            row.y = startY + line * (rowHeight + ROW_GAP);
            row.width = columnWidth;
            row.height = rowHeight;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        backdrop.render(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        double lx = localX(mouseX);
        double ly = localY(mouseY);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0.0F);
        matrices.scale(scale, scale, 1.0F);

        drawWindow(context, lx, ly);
        renderHeader(context, lx, ly);
        renderRows(context, lx, ly);
        renderFooter(context);
        if (awaiting != null) {
            renderBindOverlay(context);
        }

        matrices.pop();
    }

    private void drawWindow(DrawContext context, double mouseX, double mouseY) {
        UiDraw.roundRect(context, -2, -2, BASE_WIDTH + 4, BASE_HEIGHT + 4, 18, 0x338B5CF6);
        UiDraw.roundRect(context, 0, 0, BASE_WIDTH, BASE_HEIGHT, 16, 0xF20C0D14);
        UiDraw.roundRect(context, 20, 1, BASE_WIDTH - 40, 1, 0, 0x2EFFFFFF);
        UiDraw.roundRect(context, 20, HEADER - 1, BASE_WIDTH - 40, 1, 0, 0x1FFFFFFF);

        // полоска-акцент сверху, как у плавающего окна
        UiDraw.roundRect(context, 20, 0, 90, 2, 1, ACCENT);
    }

    private void renderHeader(DrawContext context, double mouseX, double mouseY) {
        int top = 12;
        Logo.draw(context, MARGIN, top, 24);

        context.drawTextWithShadow(textRenderer, Fonts.t("MixerGrief").formatted(Formatting.BOLD),
                MARGIN + 32, top + 1, TEXT_MAIN);
        context.drawTextWithShadow(textRenderer, Fonts.t("by Scally_Milano").formatted(Formatting.DARK_GRAY),
                MARGIN + 32, top + 13, TEXT_MUTED);

        tabRects.clear();
        int tabX = MARGIN + 152;
        for (int index = 0; index < TABS.length; index++) {
            Text label = Fonts.t(TABS[index]);
            int tabWidth = textRenderer.getWidth(label) + 14;
            int tabY = top + 2;
            boolean selected = tab == index;
            boolean hovered = UiDraw.inside(mouseX, mouseY, tabX, tabY - 4, tabWidth, 22);
            context.drawTextWithShadow(textRenderer, label, tabX, tabY + 3,
                    selected ? TEXT_MAIN : hovered ? 0xFFBBBEC9 : TEXT_MUTED);
            if (selected) {
                context.fill(tabX, tabY + 18, tabX + tabWidth - 6, tabY + 20, ACCENT);
            }
            tabRects.add(new int[]{tabX, tabY - 4, tabWidth, 22});
            tabX += tabWidth;
        }

        // управление масштабом и закрытие
        int centerY = 24;
        minusRect[0] = BASE_WIDTH - 164;
        minusRect[1] = centerY - 10;
        minusRect[2] = 20;
        minusRect[3] = 20;
        plusRect[0] = BASE_WIDTH - 72;
        plusRect[1] = centerY - 10;
        plusRect[2] = 20;
        plusRect[3] = 20;
        closeRect[0] = BASE_WIDTH - 34;
        closeRect[1] = centerY - 10;
        closeRect[2] = 20;
        closeRect[3] = 20;
        headerDragRect[0] = 0;
        headerDragRect[1] = 0;
        headerDragRect[2] = BASE_WIDTH;
        headerDragRect[3] = HEADER;

        drawScaleButton(context, minusRect, mouseX, mouseY, false);
        drawScaleButton(context, plusRect, mouseX, mouseY, true);

        Text percent = Fonts.t(Math.round(scale * 100) + "%");
        context.drawTextWithShadow(textRenderer, percent,
                BASE_WIDTH - 118 - textRenderer.getWidth(percent) / 2, centerY - 4, TEXT_MUTED);

        boolean closeHovered = UiDraw.inside(mouseX, mouseY, closeRect[0], closeRect[1],
                closeRect[2], closeRect[3]);
        if (closeHovered) {
            UiDraw.roundRect(context, closeRect[0], closeRect[1], closeRect[2], closeRect[3], 6,
                    0x33FF5C5C);
        }
        int closeColor = closeHovered ? 0xFFFF8A8A : 0xFF8A8E9A;
        context.fill(closeRect[0] + 6, closeRect[1] + 6, closeRect[0] + 8, closeRect[1] + 8, closeColor);
        context.fill(closeRect[0] + 12, closeRect[1] + 6, closeRect[0] + 14, closeRect[1] + 8, closeColor);
        context.fill(closeRect[0] + 6, closeRect[1] + 12, closeRect[0] + 8, closeRect[1] + 14, closeColor);
        context.fill(closeRect[0] + 12, closeRect[1] + 12, closeRect[0] + 14, closeRect[1] + 14, closeColor);

        // подсказка по перетаскиванию — в правой части шапки
        Text hint = Fonts.t("перетащи за шапку").formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, hint,
                BASE_WIDTH - 178 - textRenderer.getWidth(hint), centerY + 14, 0xFF4C5060);
    }

    private void drawScaleButton(DrawContext context, int[] rect, double mouseX, double mouseY,
                                 boolean plus) {
        boolean hovered = UiDraw.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
        UiDraw.roundRect(context, rect[0], rect[1], rect[2], rect[3], 6,
                hovered ? 0xFF26283A : 0xFF1B1D28);
        int color = hovered ? 0xFFFFE2B0 : 0xFFB9BCC7;
        int cx = rect[0] + rect[2] / 2;
        int cy = rect[1] + rect[3] / 2;
        context.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
        if (plus) {
            context.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
        }
    }

    private void renderRows(DrawContext context, double mouseX, double mouseY) {
        for (Row row : rows) {
            boolean hovered = UiDraw.inside(mouseX, mouseY, row.x, row.y, row.width, row.height);
            UiDraw.roundRect(context, row.x, row.y, row.width, row.height, 9,
                    hovered ? ROW_HOVER : ROW_BG);
            context.fill(row.x + 9, row.y, row.x + row.width - 9, row.y + 1, ROW_BORDER);

            if (hovered) {
                UiDraw.roundRect(context, row.x, row.y, 3, row.height, 2, ACCENT);
            }

            context.drawTextWithShadow(textRenderer, Fonts.t(row.title),
                    row.x + 16, row.y + 9, TEXT_MAIN);
            context.drawTextWithShadow(textRenderer, Fonts.t(row.subtitle),
                    row.x + 16, row.y + 22, TEXT_MUTED);

            int dotsX = row.x + row.width - 74;
            int dotsY = row.y + row.height / 2;
            for (int index = -1; index <= 1; index++) {
                UiDraw.circle(context, dotsX + index * 5, dotsY, 1, 0xFF4A4D5C);
            }

            if (row.toggle) {
                UiDraw.toggle(context, row.x + row.width - 50, row.y + row.height / 2 - 9,
                        34, 18, row.state.getAsBoolean(), ACCENT, OFF);
            } else {
                String value = row.value == null ? "" : row.value.get();
                Text valueText = Fonts.t(value);
                int chipWidth = Math.max(32, textRenderer.getWidth(valueText) + 14);
                int chipX = row.x + row.width - chipWidth - 12;
                UiDraw.roundRect(context, chipX, row.y + row.height / 2 - 9,
                        chipWidth, 18, 9, 0xFF23253A);
                context.drawTextWithShadow(textRenderer, valueText,
                        chipX + (chipWidth - textRenderer.getWidth(valueText)) / 2,
                        row.y + row.height / 2 - 4,
                        awaiting == null ? 0xFFB9A7FF : 0xFFFFD166);
            }
        }
    }

    private void renderFooter(DrawContext context) {
        context.drawTextWithShadow(textRenderer,
                Fonts.t("Шапка — перетащить окно · −／+ или колесо — масштаб · Esc — закрыть"),
                MARGIN, BASE_HEIGHT - 30, TEXT_MUTED);
        context.drawTextWithShadow(textRenderer,
                Fonts.t(".setting — меню · .hud — редактор HUD · /reports — жалобы · .templates — шаблоны"),
                MARGIN, BASE_HEIGHT - 18, TEXT_MUTED);
    }

    private void renderBindOverlay(DrawContext context) {
        Text text = Fonts.t("Нажми новую клавишу · Esc — отмена");
        int boxWidth = textRenderer.getWidth(text) + 28;
        int x = BASE_WIDTH / 2 - boxWidth / 2;
        int y = BASE_HEIGHT - 62;
        UiDraw.roundRect(context, x, y, boxWidth, 20, 8, 0xF0181A24);
        context.drawTextWithShadow(textRenderer, text, x + 14, y + 6, 0xFFFFD166);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (awaiting != null) {
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double lx = localX(mouseX);
        double ly = localY(mouseY);
        // клик мимо окна — не поглощаем
        if (lx < 0 || ly < 0 || lx > BASE_WIDTH || ly > BASE_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (insideRect(lx, ly, closeRect)) {
            close();
            return true;
        }
        if (insideRect(lx, ly, minusRect)) {
            changeScale(-0.1F);
            return true;
        }
        if (insideRect(lx, ly, plusRect)) {
            changeScale(0.1F);
            return true;
        }

        for (int index = 0; index < tabRects.size(); index++) {
            int[] rect = tabRects.get(index);
            if (UiDraw.inside(lx, ly, rect[0], rect[1], rect[2], rect[3])) {
                if (tab != index) {
                    tab = index;
                    rebuildRows();
                }
                return true;
            }
        }

        for (Row row : rows) {
            if (!UiDraw.inside(lx, ly, row.x, row.y, row.width, row.height)) {
                continue;
            }
            if (row.toggle) {
                row.setter.accept(!row.state.getAsBoolean());
                config.save();
            } else if (row.action != null) {
                row.action.run();
            }
            return true;
        }

        // пустая часть шапки — начало перетаскивания окна
        if (ly <= HEADER) {
            dragging = true;
            dragOffsetX = lx;
            dragOffsetY = ly;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean insideRect(double mouseX, double mouseY, int[] rect) {
        return UiDraw.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            int x = (int) Math.round(mouseX - dragOffsetX * scale);
            int y = (int) Math.round(mouseY - dragOffsetY * scale);
            originX = Math.max(0, Math.min(Math.max(0, width - windowWidth), x));
            originY = Math.max(0, Math.min(Math.max(0, height - windowHeight), y));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            config.menuX = width <= windowWidth ? 0.0F
                    : (float) originX / (width - windowWidth);
            config.menuY = height <= windowHeight ? 0.0F
                    : (float) originY / (height - windowHeight);
            config.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double lx = localX(mouseX);
        double ly = localY(mouseY);
        if (lx >= 0 && ly >= 0 && lx <= BASE_WIDTH && ly <= BASE_HEIGHT) {
            changeScale((float) verticalAmount * 0.08F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void changeScale(float delta) {
        float requested = Math.max(MIN_SCALE, Math.min(MAX_SCALE, config.menuScale + delta));
        if (Math.abs(requested - config.menuScale) < 0.001F) {
            return;
        }
        config.menuScale = requested;
        relayout();
        config.save();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaiting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                awaiting = null;
                return true;
            }
            if (keyCode > 0) {
                switch (awaiting) {
                    case MENU -> AutoSettingClient.rebindGuiKey(keyCode);
                    case WHEEL -> AutoSettingClient.rebindWheelKey(keyCode);
                    case MUTE -> AutoSettingClient.rebindMuteKey(keyCode);
                    case SPEC -> AutoSettingClient.rebindSpecKey(keyCode);
                    case FREELOOK -> AutoSettingClient.rebindFreelookKey(keyCode);
                    case TEMPLATES -> AutoSettingClient.rebindTemplatesKey(keyCode);
                }
            }
            awaiting = null;
            rebuildRows();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String keyName(int keyCode) {
        if (keyCode <= 0) {
            return "—";
        }
        try {
            return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
        } catch (Exception exception) {
            return "key " + keyCode;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class Row {
        private final String title;
        private final String subtitle;
        private final boolean toggle;
        private final BooleanSupplier state;
        private final Consumer<Boolean> setter;
        private final Runnable action;
        private final Supplier<String> value;
        private int x;
        private int y;
        private int width;
        private int height;

        private Row(String title, String subtitle, boolean toggle, BooleanSupplier state,
                    Consumer<Boolean> setter, Runnable action, Supplier<String> value) {
            this.title = title;
            this.subtitle = subtitle;
            this.toggle = toggle;
            this.state = state;
            this.setter = setter;
            this.action = action;
            this.value = value;
        }
    }
}
