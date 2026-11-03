package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.MenuBackdrop;
import ru.autosetting.util.UiDraw;

/**
 * Добавление и редактирование сервера в стиле меню «Сетевая игра»:
 * звёздный фон, скруглённые поля и кнопки в жёлто-оранжевой гамме.
 */
public final class MixStaffServerScreen extends Screen {
    private static final int ACCENT = 0xFFFF8A00;
    private static final int ACCENT_LIGHT = 0xFFFFC94D;
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF8A8E9A;
    private static final int MARGIN = 24;
    private static final int FIELD_HEIGHT = 34;
    private static final int FIELD_GAP = 62;
    private static final int BUTTON_HEIGHT = 32;

    private final MenuBackdrop backdrop = new MenuBackdrop();
    private final ServerList serverList;
    private final ServerInfo info;
    private final int index;

    private TextFieldWidget nameField;
    private TextFieldWidget addressField;
    private int formX;
    private int formY;
    private int formWidth;
    private int saveX;
    private int cancelX;
    private int buttonsY;

    public MixStaffServerScreen(ServerList serverList, int index, ServerInfo info) {
        super(Text.literal(index < 0 ? "Добавить сервер" : "Редактировать сервер"));
        this.serverList = serverList;
        this.index = index;
        this.info = info;
    }

    @Override
    protected void init() {
        formWidth = Math.min(360, Math.max(220, width - 80));
        formX = width / 2 - formWidth / 2;
        formY = Math.max(72, height / 2 - 92);

        nameField = new TextFieldWidget(textRenderer, formX + 10, formY + 7, formWidth - 20, 20,
                Text.literal("Название сервера"));
        nameField.setDrawsBackground(false);
        nameField.setMaxLength(128);
        nameField.setEditableColor(TEXT_MAIN);
        nameField.setUneditableColor(TEXT_MUTED);
        nameField.setText(info.name == null ? "" : info.name);

        addressField = new TextFieldWidget(textRenderer, formX + 10, formY + FIELD_GAP + 7,
                formWidth - 20, 20, Text.literal("Адрес сервера"));
        addressField.setDrawsBackground(false);
        addressField.setMaxLength(128);
        addressField.setEditableColor(TEXT_MAIN);
        addressField.setUneditableColor(TEXT_MUTED);
        addressField.setText(info.address == null ? "" : info.address);

        addDrawableChild(nameField);
        addDrawableChild(addressField);

        if (info.name == null || info.name.isBlank()) {
            nameField.setFocused(true);
        } else {
            addressField.setFocused(true);
        }

        int gap = 12;
        int buttonWidth = (formWidth - gap) / 2;
        saveX = formX;
        cancelX = formX + buttonWidth + gap;
        buttonsY = formY + FIELD_GAP + FIELD_HEIGHT + 34;
    }

    private boolean canSave() {
        if (nameField == null || addressField == null) {
            return false;
        }
        return !nameField.getText().trim().isEmpty()
                && ServerAddress.isValid(addressField.getText().trim());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        backdrop.render(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        MenuBackdrop.drawClock(context, textRenderer, width);

        boolean backHovered = MenuBackdrop.hovered(mouseX, mouseY, MARGIN - 6, 14, 96, 24);
        context.drawTextWithShadow(textRenderer,
                Fonts.t("‹ Назад").formatted(backHovered ? Formatting.BOLD : Formatting.RESET),
                MARGIN, 22, backHovered ? 0xFFFFFFFF : 0xFFD5D7E0);

        Text title = Fonts.t(index < 0 ? "Добавить сервер" : "Редактировать сервер")
                .formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, title,
                width / 2 - textRenderer.getWidth(title) / 2, formY - 52, 0xFFFFE2B0);
        Text subtitle = Fonts.t(index < 0
                ? "Введите название и адрес сервера"
                : "Измените название или адрес сервера");
        context.drawTextWithShadow(textRenderer, subtitle,
                width / 2 - textRenderer.getWidth(subtitle) / 2, formY - 34, TEXT_MUTED);

        drawField(context, "Название", formY, nameField, mouseX, mouseY);
        drawField(context, "Адрес", formY + FIELD_GAP, addressField, mouseX, mouseY);

        String address = addressField.getText().trim();
        if (!address.isEmpty() && !ServerAddress.isValid(address)) {
            Text error = Fonts.t("Некорректный адрес сервера");
            context.drawTextWithShadow(textRenderer, error, formX,
                    formY + FIELD_GAP + FIELD_HEIGHT + 10, 0xFFFF5C5C);
        }

        drawButtons(context, mouseX, mouseY);

        Text hint = Fonts.t("Enter — сохранить · Tab — следующее поле · Esc — назад")
                .formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, hint, MARGIN, height - 18, 0xFF5C6070);
    }

    private void drawField(DrawContext context, String label, int y, TextFieldWidget field,
                           int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, Fonts.t(label).formatted(Formatting.GRAY),
                formX, y - 13, TEXT_MUTED);
        if (field.isFocused()) {
            UiDraw.roundRect(context, formX - 1, y - 1, formWidth + 2, FIELD_HEIGHT + 2, 11,
                    0x55FFC94D);
        }
        UiDraw.roundRect(context, formX, y, formWidth, FIELD_HEIGHT, 10,
                field.isFocused() ? 0xE61A1410 : 0xCC0E0F16);
        if (!field.isFocused()) {
            UiDraw.roundRect(context, formX, y, formWidth, 1, 0, 0x1AFFFFFF);
        }
        field.render(context, mouseX, mouseY, 0.0F);
    }

    private void drawButtons(DrawContext context, int mouseX, int mouseY) {
        boolean valid = canSave();
        int buttonWidth = (formWidth - 12) / 2;
        boolean saveHovered = valid && MenuBackdrop.hovered(mouseX, mouseY, saveX, buttonsY,
                buttonWidth, BUTTON_HEIGHT);
        boolean cancelHovered = MenuBackdrop.hovered(mouseX, mouseY, cancelX, buttonsY,
                buttonWidth, BUTTON_HEIGHT);

        UiDraw.roundRect(context, saveX, buttonsY, buttonWidth, BUTTON_HEIGHT, 16,
                valid ? (saveHovered ? 0xFFFFA22E : ACCENT) : 0x2EFFFFFF);
        Text save = Fonts.t(index < 0 ? "Добавить" : "Сохранить").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, save,
                saveX + (buttonWidth - textRenderer.getWidth(save)) / 2, buttonsY + 11,
                valid ? 0xFF1A1206 : TEXT_MUTED);

        UiDraw.roundRect(context, cancelX, buttonsY, buttonWidth, BUTTON_HEIGHT, 16,
                cancelHovered ? 0xCC241E16 : 0xCC14151F);
        if (cancelHovered) {
            UiDraw.roundRect(context, cancelX, buttonsY, buttonWidth, 1, 0, 0x33FFC94D);
        }
        Text cancel = Fonts.t("Отмена");
        context.drawTextWithShadow(textRenderer, cancel,
                cancelX + (buttonWidth - textRenderer.getWidth(cancel)) / 2, buttonsY + 11,
                cancelHovered ? 0xFFFFFFFF : 0xFFD5D7E0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (MenuBackdrop.hovered(mouseX, mouseY, MARGIN - 6, 14, 96, 24)) {
                close();
                return true;
            }
            int buttonWidth = (formWidth - 12) / 2;
            if (canSave() && MenuBackdrop.hovered(mouseX, mouseY, saveX, buttonsY,
                    buttonWidth, BUTTON_HEIGHT)) {
                save();
                return true;
            }
            if (MenuBackdrop.hovered(mouseX, mouseY, cancelX, buttonsY,
                    buttonWidth, BUTTON_HEIGHT)) {
                close();
                return true;
            }
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled && button == 0) {
            if (MenuBackdrop.hovered(mouseX, mouseY, formX, formY, formWidth, FIELD_HEIGHT)) {
                nameField.setFocused(true);
                addressField.setFocused(false);
                return true;
            }
            if (MenuBackdrop.hovered(mouseX, mouseY, formX, formY + FIELD_GAP,
                    formWidth, FIELD_HEIGHT)) {
                addressField.setFocused(true);
                nameField.setFocused(false);
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (nameField.isFocused()) {
                nameField.setFocused(false);
                addressField.setFocused(true);
            } else {
                addressField.setFocused(false);
                nameField.setFocused(true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (!canSave()) {
            return;
        }
        info.name = nameField.getText().trim();
        info.address = addressField.getText().trim();
        if (index < 0) {
            serverList.add(info, false);
        }
        serverList.saveFile();
        client.setScreen(new MixStaffMultiplayerScreen());
    }

    @Override
    public void close() {
        client.setScreen(new MixStaffMultiplayerScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
