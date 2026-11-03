package ru.autosetting.feature;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.PlayerNames;
import ru.autosetting.util.UiDraw;

/**
 * Автокопирование AnyDesk: если в чате появляются 9–10 цифр,
 * код сразу попадает в буфер обмена, а над прицелом на пару секунд
 * появляется плашка «AnyDesk».
 */
public final class AnydeskCopy {
    private static final int ACCENT = 0xFFFF8A00;
    private static final long SHOW_MS = 4_500L;
    private static final int WIDTH = 172;
    private static final int HEIGHT = 44;

    private static String code;
    private static long until;

    private AnydeskCopy() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, parameters, timestamp) -> onChat(message.getString()));
    }

    public static void onChat(String plain) {
        if (!ModConfig.get().autoCopyAnydesk || plain == null || plain.isBlank()) {
            return;
        }
        String found = PlayerNames.findAnydesk(plain);
        if (found == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.keyboard == null) {
            return;
        }
        client.keyboard.setClipboard(found);
        code = found;
        until = System.currentTimeMillis() + SHOW_MS;
        client.inGameHud.getChatHud().addMessage(Text.literal(
                        "[AnyDesk] " + found + " скопирован")
                .formatted(Formatting.GRAY));
    }

    public static void renderHud(DrawContext context, MinecraftClient client) {
        if (code == null) {
            return;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            code = null;
            return;
        }
        float fade = Math.min(1.0F, remaining / 700.0F);
        int alpha = (int) (255 * fade);

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int x = screenWidth / 2 - WIDTH / 2;
        int y = screenHeight / 2 + 26;
        ModConfig config = ModConfig.get();
        int baseCenterX = screenWidth / 2;
        int baseCenterY = y + HEIGHT / 2;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(Math.round(config.anydeskHudX * screenWidth),
                Math.round(config.anydeskHudY * screenHeight), 0.0F);
        matrices.scale(config.anydeskHudScale, config.anydeskHudScale, 1.0F);
        matrices.translate(-baseCenterX, -baseCenterY, 0.0F);

        UiDraw.glassPanel(context, x, y, WIDTH, HEIGHT, 12, ACCENT, alpha);

        Text title = Fonts.t("AnyDesk").formatted(Formatting.BOLD);
        context.drawTextWithShadow(client.textRenderer, title, x + 12, y + 8,
                UiDraw.withAlpha(ACCENT, alpha));

        Text value = Fonts.t(code).formatted(Formatting.BOLD);
        context.drawTextWithShadow(client.textRenderer, value, x + 12, y + 23,
                UiDraw.withAlpha(0xFFFFFF, alpha));

        UiDraw.circle(context, x + WIDTH - 17, y + 21, 8, UiDraw.withAlpha(ACCENT, alpha / 3));
        UiDraw.circle(context, x + WIDTH - 17, y + 21, 6, UiDraw.withAlpha(ACCENT, alpha));
        // галочка «скопировано»
        check(context, x + WIDTH - 17, y + 21, UiDraw.withAlpha(0x1A1206, alpha));
        matrices.pop();
    }

    private static void check(DrawContext context, int centerX, int centerY, int color) {
        int thickness = 2;
        for (int step = 0; step < 5; step++) {
            context.fill(centerX - 3 + step, centerY + step, centerX - 1 + step, centerY + step + thickness, color);
        }
        for (int step = 0; step < 6; step++) {
            context.fill(centerX + 1 + step, centerY + 1 - step,
                    centerX + 3 + step, centerY + 4 - step, color);
        }
    }

    public static void reset() {
        code = null;
        until = 0L;
    }
}
