package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.PermissionUtil;
import ru.autosetting.util.TargetUtil;
import ru.autosetting.util.UiDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * Target: над прицелом показывает привилегию, ник и экипировку игрока,
 * на которого наведён прицел.
 */
public final class TargetHud {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final int ICON = 16;
    private static final int ICON_GAP = 4;

    private static String cachedNick;
    private static String cachedPrivilege = "Игрок";
    private static long cachedAt;

    private TargetHud() {
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (!ModConfig.get().targetHud || client.player == null) {
            return;
        }
        PlayerEntity target = TargetUtil.findTarget(client);
        if (target == null) {
            return;
        }

        String nick = target.getGameProfile().getName();
        String privilege = privilege(nick);
        TextRenderer textRenderer = client.textRenderer;

        float scale = Math.max(0.6F, Math.min(2.2F, ModConfig.get().targetScale));
        int health = (int) Math.ceil(Math.max(0.0F, target.getHealth()));
        Formatting hpColor = health >= 15 ? Formatting.GREEN
                : health >= 8 ? Formatting.YELLOW : Formatting.RED;

        MutableText line = Fonts.t(privilege).formatted(Formatting.GOLD)
                .append(Fonts.t("  " + nick).formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Fonts.t("  " + health + " HP").formatted(hpColor));

        List<ItemStack> stacks = new ArrayList<>(SLOTS.length);
        List<String> names = new ArrayList<>(SLOTS.length);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = target.getEquippedStack(slot);
            stacks.add(stack);
            if (!stack.isEmpty()) {
                names.add(stack.getName().getString());
            }
        }

        Text equipment = Fonts.t(buildEquipmentLine(textRenderer, names)).formatted(Formatting.GRAY);

        int iconsWidth = SLOTS.length * ICON + (SLOTS.length - 1) * ICON_GAP;
        int contentWidth = Math.max(textRenderer.getWidth(line),
                Math.max(textRenderer.getWidth(equipment), iconsWidth));
        int width = contentWidth + 24;
        int height = 58;

        ModConfig config = ModConfig.get();
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int panelCenterX = Math.round(config.targetX * screenWidth);
        int panelCenterY = Math.round(config.targetY * screenHeight);
        int x = panelCenterX - width / 2;
        int y = panelCenterY - height / 2;
        x = Math.max(4, Math.min(screenWidth - width - 4, x));
        y = Math.max(4, Math.min(screenHeight - height - 4, y));
        panelCenterX = x + width / 2;
        panelCenterY = y + height / 2;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(panelCenterX, panelCenterY, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-panelCenterX, -panelCenterY, 0.0F);

        UiDraw.glassPanel(context, x, y, width, height, 10, 0xFF8B76F6);
        context.fill(x + 12, y + 18, x + width - 10, y + 19, 0x1FFFFFFF);

        context.drawTextWithShadow(textRenderer, line, x + 12, y + 6, 0xFFFFFFFF);

        int iconX = x + 12;
        int iconY = y + 24;
        for (int index = 0; index < stacks.size(); index++) {
            int slotX = iconX + index * (ICON + ICON_GAP);
            ItemStack stack = stacks.get(index);
            if (stack.isEmpty()) {
                UiDraw.roundRect(context, slotX, iconY, ICON, ICON, 4, 0x665B6A8F);
                UiDraw.roundRect(context, slotX + 3, iconY + 2, ICON - 6, 1, 1, 0x44FFFFFF);
                UiDraw.circle(context, slotX + ICON / 2, iconY + ICON / 2, 1, 0xFF3A3D4A);
            } else {
                UiDraw.roundRect(context, slotX - 1, iconY - 1, ICON + 2, ICON + 2, 4, 0x665B6A8F);
                UiDraw.roundRect(context, slotX + 1, iconY, ICON - 2, 1, 1, 0x44FFFFFF);
                context.drawItem(stack, slotX, iconY);
            }
        }

        if (!equipment.getString().isBlank()) {
            context.drawTextWithShadow(textRenderer, equipment, x + 12, y + 44, 0xFFFFFF);
        }

        matrices.pop();
    }

    private static String buildEquipmentLine(TextRenderer textRenderer, List<String> names) {
        if (names.isEmpty()) {
            return "без экипировки";
        }
        String joined = String.join(" · ", names);
        while (textRenderer.getWidth(Fonts.t(joined)) > 280 && joined.length() > 4) {
            joined = joined.substring(0, joined.length() - 2);
        }
        if (!joined.endsWith("…") && textRenderer.getWidth(Fonts.t(joined + "…")) <= 284
                && !joined.equals(String.join(" · ", names))) {
            joined = joined + "…";
        }
        return joined;
    }

    private static String privilege(String nick) {
        long now = System.currentTimeMillis();
        if (nick.equals(cachedNick) && now - cachedAt < 2_000L) {
            return cachedPrivilege;
        }
        cachedNick = nick;
        cachedPrivilege = PermissionUtil.resolve(nick);
        cachedAt = now;
        return cachedPrivilege;
    }
}
