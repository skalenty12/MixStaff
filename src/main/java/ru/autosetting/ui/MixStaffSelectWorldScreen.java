package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.IasCompat;
import ru.autosetting.util.MenuBackdrop;
import ru.autosetting.util.UiDraw;

public final class MixStaffSelectWorldScreen extends SelectWorldScreen {
    private static final int MARGIN = 24;
    private static final int TOP_GEAR_Y = 26;

    private final MenuBackdrop backdrop = new MenuBackdrop();
    private int topGearX;
    private int topPersonX;

    public MixStaffSelectWorldScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        backdrop.render(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MenuBackdrop.drawClock(context, textRenderer, width);
        drawTopActions(context, mouseX, mouseY);
    }

    private void drawTopActions(DrawContext context, int mouseX, int mouseY) {
        Text clock = Fonts.t(MenuBackdrop.clockText());
        topGearX = width - MARGIN - textRenderer.getWidth(clock) - 22;
        topPersonX = topGearX - 28;

        boolean personHovered = MenuBackdrop.hovered(mouseX, mouseY,
                topPersonX - 11, TOP_GEAR_Y - 11, 22, 22);
        boolean gearHovered = MenuBackdrop.hovered(mouseX, mouseY,
                topGearX - 11, TOP_GEAR_Y - 11, 22, 22);
        if (personHovered) {
            UiDraw.circle(context, topPersonX, TOP_GEAR_Y, 12, 0x3365E8F4);
        }
        UiDraw.glassPlayerIcon(context, topPersonX, TOP_GEAR_Y, 15,
                personHovered ? 0xFFB7F4FF : 0xFFD5D7E0);
        if (gearHovered) {
            UiDraw.circle(context, topGearX, TOP_GEAR_Y, 12, 0x33FFC94D);
        }
        UiDraw.gear(context, topGearX, TOP_GEAR_Y, 7,
                gearHovered ? 0xFFFFE2B0 : 0xFFD5D7E0, 0xFF0B0C12);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY,
                topGearX - 11, TOP_GEAR_Y - 11, 22, 22)) {
            client.setScreen(new SettingScreen());
            return true;
        }
        if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY,
                topPersonX - 11, TOP_GEAR_Y - 11, 22, 22)) {
            IasCompat.openAccountMenu(client, this);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
