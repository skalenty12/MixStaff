package ru.autosetting;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.autosetting.config.ModConfig;
import ru.autosetting.feature.AnydeskCopy;
import ru.autosetting.feature.AntiAfk;
import ru.autosetting.feature.AutoMuteManager;
import ru.autosetting.feature.AutoProofManager;
import ru.autosetting.feature.AutoSpecManager;
import ru.autosetting.feature.ChatRule;
import ru.autosetting.feature.DiscordRpc;
import ru.autosetting.feature.DynamicIslandFeature;
import ru.autosetting.feature.Freelook;
import ru.autosetting.feature.Fullbright;
import ru.autosetting.feature.HudEditOverlay;
import ru.autosetting.feature.MediaSessionBridge;
import ru.autosetting.feature.ReportsScanner;
import ru.autosetting.feature.ReportsStore;
import ru.autosetting.feature.Valute;
import ru.autosetting.ui.AutoMuteScreen;
import ru.autosetting.ui.HudEditorScreen;
import ru.autosetting.ui.SettingScreen;
import ru.autosetting.ui.TemplatesScreen;
import ru.autosetting.ui.WheelScreen;
import ru.autosetting.util.HudRenderer;

import java.util.Locale;

public final class AutoSettingClient implements ClientModInitializer {
    public static final String MOD_ID = "autosetting";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean pendingSettingsScreen;
    private static boolean pendingMuteScreen;
    private static boolean pendingHudEditor;
    private static boolean pendingTemplatesScreen;
    private static boolean guiKeyWasDown;
    private static boolean wheelKeyWasDown;
    private static String pendingMuteNick;
    private static String pendingMuteReason;
    private static ChatRule pendingMuteRule;

    private static KeyBinding muteKey;
    private static KeyBinding specKey;
    private static KeyBinding freelookKey;
    private static KeyBinding templatesKey;

    @Override
    public void onInitializeClient() {
        ModConfig.get();

        AutoSpecManager.init();
        AutoMuteManager.init();
        AutoProofManager.init();
        AnydeskCopy.init();
        HudRenderer.init();
        DynamicIslandFeature.init();
        if (ModConfig.get().discordRpc) {
            DiscordRpc.init();
        }

        // ChatScreen#keyPressed calls sendMessage(...) first and setScreen(null) right after it,
        // so opening the screen inside this callback would be immediately undone.
        // Remember the request and open the screen at the end of the tick instead.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String trimmed = message.trim();
            String command = trimmed.toLowerCase(Locale.ROOT);
            if (command.equals(".setting") || command.equals(".settings")) {
                pendingSettingsScreen = true;
                return false;
            }
            if (command.equals(".mute") || command.startsWith(".mute ")) {
                queueMuteScreen(trimmed.substring(1));
                return false;
            }
            if (command.equals(".hud") || command.equals(".hud editor")) {
                pendingHudEditor = true;
                return false;
            }
            if (command.equals(".templates") || command.equals(".tpl")) {
                pendingTemplatesScreen = true;
                return false;
            }
            if (command.equals(".report") || command.startsWith(".report ")) {
                handleReportCommand(trimmed);
                return false;
            }
            if (command.equals(".music") || command.startsWith(".music ")) {
                handleMusicCommand(trimmed);
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.MODIFY_CHAT.register(Valute::expand);
        ClientSendMessageEvents.MODIFY_COMMAND.register(Valute::expand);

        // /reports на сервере: очищаем панель и собираем жалобы из ответа сервера.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String rawCommand = command == null ? "" : command.trim();
            String body = rawCommand.toLowerCase(Locale.ROOT);
            if (body.startsWith("/")) {
                body = body.substring(1).trim();
            }
            if (body.equals("mute") || body.startsWith("mute ")) {
                queueMuteScreen(rawCommand.startsWith("/") ? rawCommand.substring(1).trim() : rawCommand);
                return false;
            }
            if (body.equals("reports") || body.startsWith("reports ")) {
                ReportsStore.beginCollect();
                ReportsScanner.arm();
            }
            return true;
        });

        muteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosetting.automute",
                InputUtil.Type.KEYSYM,
                ModConfig.get().muteKeyCode,
                "category.autosetting"
        ));

        specKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosetting.spec",
                InputUtil.Type.KEYSYM,
                ModConfig.get().specKeyCode,
                "category.autosetting"
        ));

        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosetting.freelook",
                InputUtil.Type.KEYSYM,
                ModConfig.get().freelookKey,
                "category.autosetting"
        ));

        templatesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosetting.templates",
                InputUtil.Type.KEYSYM,
                ModConfig.get().templatesKey,
                "category.autosetting"
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Freelook.stop();
            Fullbright.reset();
            AntiAfk.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Freelook.stop();
            Fullbright.reset();
            AnydeskCopy.reset();
            AntiAfk.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DynamicIslandFeature.tick(client);
            AntiAfk.tick(client);
            if (pendingSettingsScreen) {
                pendingSettingsScreen = false;
                if (client.player != null) {
                    client.setScreen(new SettingScreen());
                }
            }
            if (pendingMuteScreen) {
                pendingMuteScreen = false;
                if (client.player != null) {
                    client.setScreen(new AutoMuteScreen(null, pendingMuteNick, pendingMuteRule, pendingMuteReason));
                }
                pendingMuteNick = null;
                pendingMuteRule = null;
                pendingMuteReason = null;
            }
            if (pendingHudEditor) {
                pendingHudEditor = false;
                if (client.player != null) {
                    client.setScreen(new HudEditorScreen());
                }
            }
            if (pendingTemplatesScreen) {
                pendingTemplatesScreen = false;
                if (client.player != null) {
                    client.setScreen(new TemplatesScreen());
                }
            }

            handleGuiKey(client);
            handleWheelKey(client);
            handleFreelook(client);
            handleTemplatesKey(client);

            while (muteKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    if (ModConfig.get().autoMute) {
                        client.setScreen(new AutoMuteScreen());
                    } else {
                        client.inGameHud.getChatHud().addMessage(Text.literal(
                                        "[AutoMute] Выключен — включи в .setting")
                                .formatted(Formatting.GRAY));
                    }
                }
            }
            while (specKey.wasPressed()) {
                AutoSpecManager.specLastRequester();
            }
            AutoMuteManager.tick();
            Fullbright.tick(client);
            ReportsStore.tick();
            ReportsScanner.tick(client);
            HudEditOverlay.tick(client);
        });
    }

    private static void queueMuteScreen(String command) {
        String[] parts = command.trim().split("\\s+", 4);
        pendingMuteNick = parts.length >= 2 ? parts[1] : null;
        pendingMuteRule = parts.length >= 3 ? ChatRule.byClause(parts[2]) : null;
        pendingMuteReason = parts.length >= 4 ? parts[3]
                : parts.length >= 3 && pendingMuteRule == null ? parts[2] : null;
        pendingMuteScreen = true;
    }

    /** .music next | prev | play | pause — управление системным плеером. */
    private static void handleMusicCommand(String message) {
        String body = message.substring(".music".length()).trim().toLowerCase(Locale.ROOT);
        String command = switch (body) {
            case "next", "n", "след" -> "NEXT";
            case "prev", "p", "назад" -> "PREV";
            default -> "PLAY";
        };
        MediaSessionBridge.send(command);
        MinecraftClient client = MinecraftClient.getInstance();
        client.inGameHud.getChatHud().addMessage(Text.literal(
                        "[Music] " + command).formatted(Formatting.GRAY));
    }

    /**
     * .report Ник причина — добавляет запись в reports-list.
     * .report clear — очищает список.
     */
    private static void handleReportCommand(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        String body = message.substring(".report".length()).trim();
        if (body.isEmpty()) {
            client.inGameHud.getChatHud().addMessage(Text.literal(
                            "[Reports] .report Ник причина | .report clear | /reports")
                    .formatted(Formatting.GRAY));
            return;
        }
        if (body.equalsIgnoreCase("clear") || body.equalsIgnoreCase("clear all")) {
            ReportsStore.clear();
            client.inGameHud.getChatHud().addMessage(Text.literal(
                            "[Reports] Список очищен").formatted(Formatting.GRAY));
            return;
        }
        String[] parts = body.split("\\s+", 2);
        String nick = parts[0];
        String reason = parts.length > 1 ? parts[1] : "";
        ReportsStore.add(nick, reason);
        client.inGameHud.getChatHud().addMessage(Text.literal(
                        "[Reports] Добавлен: " + nick).formatted(Formatting.GRAY));
    }

    private static void handleFreelook(MinecraftClient client) {
        if (freelookKey == null) {
            return;
        }
        if (ModConfig.get().freelook && client.currentScreen == null
                && client.player != null && freelookKey.isPressed()) {
            Freelook.start(client);
        } else {
            Freelook.stop();
        }
    }

    private static void handleTemplatesKey(MinecraftClient client) {
        if (templatesKey == null) {
            return;
        }
        while (templatesKey.wasPressed()) {
            if (ModConfig.get().templates && client.currentScreen == null && client.player != null) {
                client.setScreen(new TemplatesScreen());
            }
        }
    }

    private static void handleGuiKey(MinecraftClient client) {
        int keyCode = ModConfig.get().clickGuiKey;
        if (keyCode <= 0 || client.getWindow() == null) {
            guiKeyWasDown = false;
            return;
        }
        boolean down = InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode);
        boolean pressed = down && !guiKeyWasDown;
        guiKeyWasDown = down;
        if (pressed && client.currentScreen == null && client.player != null) {
            client.setScreen(new SettingScreen());
        }
    }

    private static void handleWheelKey(MinecraftClient client) {
        int keyCode = ModConfig.get().wheelKey;
        if (keyCode <= 0 || client.getWindow() == null) {
            wheelKeyWasDown = false;
            return;
        }
        boolean down = InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode);
        boolean pressed = down && !wheelKeyWasDown;
        wheelKeyWasDown = down;
        if (pressed && client.currentScreen == null && client.player != null && client.world != null) {
            WheelScreen.open(client);
        }
    }

    public static void rebindMuteKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.muteKeyCode = keyCode;
        config.save();
        if (muteKey != null) {
            muteKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updatePressedStates();
        }
    }

    public static void rebindSpecKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.specKeyCode = keyCode;
        config.save();
        if (specKey != null) {
            specKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updatePressedStates();
        }
    }

    public static void rebindFreelookKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.freelookKey = keyCode;
        config.save();
        if (freelookKey != null) {
            freelookKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updatePressedStates();
        }
    }

    public static void rebindTemplatesKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.templatesKey = keyCode;
        config.save();
        if (templatesKey != null) {
            templatesKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updatePressedStates();
        }
    }

    public static void rebindGuiKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.clickGuiKey = keyCode;
        config.save();
    }

    public static String muteKeyName() {
        return muteKey == null ? "M" : muteKey.getBoundKeyLocalizedText().getString();
    }

    public static String specKeyName() {
        return specKey == null ? "J" : specKey.getBoundKeyLocalizedText().getString();
    }

    public static String freelookKeyName() {
        return freelookKey == null ? "Z" : freelookKey.getBoundKeyLocalizedText().getString();
    }

    public static String templatesKeyName() {
        return templatesKey == null ? "H" : templatesKey.getBoundKeyLocalizedText().getString();
    }

    public static void rebindWheelKey(int keyCode) {
        ModConfig config = ModConfig.get();
        config.wheelKey = keyCode;
        config.save();
    }

    public static String wheelKeyName() {
        int keyCode = ModConfig.get().wheelKey;
        try {
            return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
        } catch (Exception exception) {
            return "Alt";
        }
    }
}
