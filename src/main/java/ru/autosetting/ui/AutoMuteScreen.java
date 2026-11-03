package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.feature.AutoMuteManager;
import ru.autosetting.feature.AutoSpecManager;
import ru.autosetting.feature.ChatRule;
import ru.autosetting.feature.MuteAlertManager;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.UiDraw;

/**
 * Форма AutoMute: ник + причина (с выбором пункта правил 2.1–2.7), затем скриншот и /push.
 */
public final class AutoMuteScreen extends Screen {
    private final Screen parent;
    private final String presetNick;
    private final String presetReason;
    private ChatRule rule;

    private TextFieldWidget nickField;
    private TextFieldWidget reasonField;
    private ButtonWidget submitButton;
    private ButtonWidget ruleButton;

    public AutoMuteScreen() {
        this(null);
    }

    public AutoMuteScreen(Screen parent) {
        this(parent, null, null);
    }

    public AutoMuteScreen(Screen parent, String presetNick, ChatRule presetRule) {
        this(parent, presetNick, presetRule, null);
    }

    public AutoMuteScreen(Screen parent, String presetNick, ChatRule presetRule, String presetReason) {
        super(Text.literal("AutoMute"));
        this.parent = parent;
        this.presetNick = presetNick;
        this.rule = presetRule;
        this.presetReason = presetReason;
    }

    @Override
    protected void init() {
        int centerX = width / 2;

        nickField = new TextFieldWidget(textRenderer, centerX - 150, 68, 300, 20, Text.literal("Ник игрока"));
        nickField.setMaxLength(16);
        nickField.setPlaceholder(Text.literal("Ник игрока").formatted(Formatting.DARK_GRAY));
        String prefill = presetNick;
        if (prefill == null) {
            prefill = AutoSpecManager.getLastSender();
        }
        if (prefill == null) {
            prefill = AutoSpecManager.getLastRequester();
        }
        if (prefill != null) {
            nickField.setText(prefill);
        }

        reasonField = new TextFieldWidget(textRenderer, centerX - 150, 102, 300, 20, Text.literal("Причина"));
        reasonField.setMaxLength(140);
        reasonField.setPlaceholder(Text.literal("Причина мута").formatted(Formatting.DARK_GRAY));
        String reason = presetReason == null ? "" : presetReason.trim();
        if (reason.isBlank() && prefill != null) {
            reason = MuteAlertManager.reasonFor(prefill);
        }
        if (reason.isBlank() && rule != null) {
            reason = rule.reasonPrefix();
        }
        if (reason.isBlank()) {
            reason = AutoSpecManager.getLastMessage();
        }
        reasonField.setText(reason);

        addDrawableChild(nickField);
        addDrawableChild(reasonField);

        ruleButton = ButtonWidget.builder(ruleLabel(), button -> {
            rule = nextRule(rule);
            button.setMessage(ruleLabel());
            if (reasonField.getText().isBlank() || rule != null && AutoSpecManager.getLastMessage().equals(reasonField.getText())) {
                reasonField.setText(rule == null ? "" : rule.reasonPrefix());
            }
        }).dimensions(centerX - 150, 126, 300, 20).build();
        addDrawableChild(ruleButton);

        submitButton = ButtonWidget.builder(
                        Text.literal("Скриншот + /push").formatted(Formatting.GREEN), button -> submit())
                .dimensions(centerX - 150, 168, 300, 20).build();
        addDrawableChild(submitButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), button -> close())
                .dimensions(centerX - 150, 194, 300, 20).build());
    }

    private Text ruleLabel() {
        if (rule == null) {
            return Text.literal("Пункт правил: не выбран — нажми, чтобы выбрать")
                    .formatted(Formatting.YELLOW);
        }
        return Text.literal("Пункт " + rule.label()).formatted(Formatting.AQUA);
    }

    private static ChatRule nextRule(ChatRule current) {
        ChatRule[] all = ChatRule.values();
        if (current == null) {
            return all[0];
        }
        return all[(current.ordinal() + 1) % all.length];
    }

    private void submit() {
        if (nickField == null || reasonField == null || submitButton == null) {
            return;
        }
        String nick = nickField.getText().trim();
        if (!nick.matches("[A-Za-z0-9_]{2,16}")) {
            submitButton.setMessage(Text.literal("Введи корректный ник").formatted(Formatting.RED));
            return;
        }
        String reason = reasonField.getText().trim();
        if (reason.isBlank() && rule != null) {
            reason = rule.reasonPrefix();
        }
        if (client.player == null || client.getNetworkHandler() == null) {
            submitButton.setMessage(Text.literal("Нет подключения к серверу").formatted(Formatting.RED));
            return;
        }
        AutoMuteManager.requestMute(nick, reason);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && nickField != null && reasonField != null
                && (nickField.isFocused() || reasonField.isFocused())) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int left = centerX - 168;
        int top = 26;
        int panelWidth = 336;
        int panelHeight = 200;
        UiDraw.roundRect(context, left, top, panelWidth, panelHeight, 12, 0xE60C0D14);
        UiDraw.roundRect(context, left, top, 4, panelHeight, 3, 0xFF8B5CF6);
        UiDraw.roundRect(context, left + 12, top + 8, panelWidth - 24, 1, 0, 0x22FFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        context.drawTextWithShadow(textRenderer,
                Fonts.t("MixerGrief · AutoMute").formatted(Formatting.BOLD),
                centerX - 150, 34, 0xFFF4F4F8);
        context.drawTextWithShadow(textRenderer,
                Fonts.t("Скриншот в .minecraft/mutes, затем /push ник").formatted(Formatting.GRAY),
                centerX - 150, 46, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,
                Fonts.t("Ник игрока").formatted(Formatting.WHITE), centerX - 150, 58, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,
                Fonts.t("Причина мута").formatted(Formatting.WHITE), centerX - 150, 92, 0xFFFFFFFF);

        Text punishment = Fonts.t(rule == null
                        ? "Пункт правил не выбран — мут можно выдать и без него"
                        : "Наказание по правилам: " + rule.punishment())
                .formatted(rule == null ? Formatting.GRAY : Formatting.GOLD);
        context.drawTextWithShadow(textRenderer, punishment, centerX - 150, 150, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
