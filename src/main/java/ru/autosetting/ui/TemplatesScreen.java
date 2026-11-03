package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.UiDraw;

import java.util.ArrayList;
import java.util.List;

/**
 * Шар шаблонов проверки: три готовых сообщения, которые отправляются
 * в чат одним кликом. Открывается клавишей из настроек или командой
 * {@code .templates}.
 */
public final class TemplatesScreen extends Screen {
    private static final int ACCENT = 0xFFFF8A00;
    private static final int ACCENT_LIGHT = 0xFFFFC94D;
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF8A8E9A;

    private static final String[] TEMPLATES = {
            "Приветствую, вы вызваны на проверку, заходите в ожидание проверки "
                    + "в Дискорд Сервере MixerGrief. У вас есть 5 минут, если не успеваете "
                    + "попросите у меня дополнительное время.",
            "Приветствую, вы вызваны на проверку, я ожидаю ваш anydesk/rudesk. "
                    + "У вас есть 5 минут, если не успеваете попросите у меня дополнительное время.",
            "Пожалуйста, не дергайте мышкой и не мешайте моей работе, иначе я начну "
                    + "принимать игровые санкции к вашему аккаунту."
    };

    private final List<int[]> rects = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public TemplatesScreen() {
        super(Text.literal("Шаблоны проверки"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, Math.max(320, width - 80));
        panelX = (width - panelWidth) / 2;
        panelHeight = 74 + TEMPLATES.length * 68 + 14;
        panelY = Math.max(18, (height - panelHeight) / 2);
        rebuildRects();
    }

    private void rebuildRects() {
        rects.clear();
        int rowY = panelY + 62;
        for (int index = 0; index < TEMPLATES.length; index++) {
            rects.add(new int[]{panelX + 14, rowY, panelWidth - 28, 60});
            rowY += 68;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0070710);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        UiDraw.roundRect(context, panelX - 2, panelY - 2, panelWidth + 4, panelHeight + 4, 20,
                0x22FFC94D);
        UiDraw.roundRect(context, panelX, panelY, panelWidth, panelHeight, 18, 0xF20C0D14);
        UiDraw.roundRect(context, panelX + 20, panelY + 1, panelWidth - 40, 1, 0, 0x2EFFFFFF);
        UiDraw.circle(context, panelX + 26, panelY + 24, 4, ACCENT);

        Text title = Fonts.t("ШАБЛОНЫ ПРОВЕРКИ").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, title, panelX + 40, panelY + 15, TEXT_MAIN);
        Text hint = Fonts.t("ЛКМ или 1–3 — отправить · Esc — закрыть").formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, hint, panelX + 40, panelY + 31, TEXT_MUTED);

        for (int index = 0; index < rects.size(); index++) {
            int[] rect = rects.get(index);
            boolean hovered = UiDraw.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
            if (hovered) {
                UiDraw.roundRect(context, rect[0] - 2, rect[1] - 2, rect[2] + 4, rect[3] + 4, 12,
                        0x33FFC94D);
            }
            UiDraw.roundRect(context, rect[0], rect[1], rect[2], rect[3], 11,
                    hovered ? 0xFF1C1A14 : 0xCC15161F);
            UiDraw.roundRect(context, rect[0], rect[1] + 7, 3, rect[3] - 14, 2,
                    hovered ? ACCENT_LIGHT : ACCENT);

            UiDraw.circle(context, rect[0] + 20, rect[1] + 18, 9,
                    hovered ? ACCENT : 0xFF2A2418);
            Text number = Fonts.t(String.valueOf(index + 1)).formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, number,
                    rect[0] + 20 - textRenderer.getWidth(number) / 2, rect[1] + 14,
                    hovered ? 0xFF1A1206 : ACCENT_LIGHT);

            List<String> lines = wrap(TEMPLATES[index], rect[2] - 58, 3);
            int textY = rect[1] + 10;
            for (String line : lines) {
                context.drawTextWithShadow(textRenderer, Fonts.t(line),
                        rect[0] + 40, textY, hovered ? 0xFFFFFFFF : 0xFFD9DBE4);
                textY += 12;
            }
        }

        Text footer = Fonts.t("Шаблоны можно перебиндить в настройках MixerGrief → Utilities")
                .formatted(Formatting.DARK_GRAY);
        context.drawTextWithShadow(textRenderer, footer,
                panelX + (panelWidth - textRenderer.getWidth(footer)) / 2, panelY + panelHeight - 12,
                0xFF5C6070);
    }

    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textRenderer.getWidth(Fonts.t(candidate)) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.size() > maxLines) {
            List<String> trimmed = new ArrayList<>(lines.subList(0, maxLines));
            String last = trimmed.get(maxLines - 1);
            while (last.length() > 1 && textRenderer.getWidth(Fonts.t(last + "…")) > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            trimmed.set(maxLines - 1, last + "…");
            return trimmed;
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = 0; index < rects.size(); index++) {
                int[] rect = rects.get(index);
                if (UiDraw.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3])) {
                    send(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        int index = switch (keyCode) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 0;
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 1;
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 2;
            default -> -1;
        };
        if (index >= 0) {
            send(index);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(int index) {
        if (client == null || client.player == null || index < 0 || index >= TEMPLATES.length) {
            return;
        }
        client.player.networkHandler.sendChatMessage(TEMPLATES[index]);
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
