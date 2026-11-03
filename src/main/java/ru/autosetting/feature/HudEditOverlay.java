package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.TargetUtil;
import ru.autosetting.util.UiDraw;

/** Перетаскивание и масштабирование всех HUD-панелей в редакторе и чате. */
public final class HudEditOverlay {
    private static final int TARGET_BASE_WIDTH = 340;
    private static final int TARGET_BASE_HEIGHT = 58;
    private static final int[] PREVIEW_COLORS = {
            0xFF34D399, 0xFFFF8A00, 0xFF8B5CF6, 0xFFFF5C5C, 0xFFFF8A00, 0xFF8EDFF5
    };
    private static final String[] PREVIEW_TITLES = {
            "", "", "AutoSpeck", "AutoMute", "AnyDesk", "MixerGrief"
    };

    private static Element dragging;
    private static double offsetX;
    private static double offsetY;

    private HudEditOverlay() {
    }

    public static void tick(MinecraftClient client) {
        if (!isEditing(client)
                || client.player == null) {
            if (dragging != null) {
                finish();
            }
            return;
        }
    }

    public static boolean isEditing(MinecraftClient client) {
        return client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                || client.currentScreen instanceof ru.autosetting.ui.HudEditorScreen;
    }

    public static boolean mouseDragged(MinecraftClient client, double mouseX, double mouseY, int button) {
        if (dragging == null || button != 0) {
            return false;
        }
        int[] rect = rect(client, dragging);
        int x = clamp((int) Math.round(mouseX - offsetX), 0, Math.max(0, width(client) - rect[2]));
        int y = clamp((int) Math.round(mouseY - offsetY), 0, Math.max(0, height(client) - rect[3]));
        setPosition(client, dragging, x, y, rect[2], rect[3]);
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (dragging == null || button != 0) {
            return false;
        }
        finish();
        return true;
    }

    public static boolean mouseClicked(MinecraftClient client, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Element element : Element.values()) {
            int[] rect = rect(client, element);
            if (inside(mouseX, mouseY, rect)) {
                dragging = element;
                offsetX = mouseX - rect[0];
                offsetY = mouseY - rect[1];
                return true;
            }
        }
        return false;
    }

    public static boolean mouseScrolled(MinecraftClient client, double mouseX, double mouseY, double amount) {
        for (Element element : Element.values()) {
            int[] rect = rect(client, element);
            if (!inside(mouseX, mouseY, rect)) {
                continue;
            }
            ModConfig config = ModConfig.get();
            float delta = (float) amount * (element == Element.MUSIC ? 0.1F : 0.05F);
            switch (element) {
                case REPORTS -> config.reportsScale = clamp(config.reportsScale + delta, 0.7F, 1.6F);
                case TARGET -> config.targetScale = clamp(config.targetScale + delta, 0.6F, 2.2F);
                case SPEC -> config.specHudScale = clamp(config.specHudScale + delta, 0.6F, 2.0F);
                case MUTE -> config.muteHudScale = clamp(config.muteHudScale + delta, 0.6F, 2.0F);
                case ANYDESK -> config.anydeskHudScale = clamp(config.anydeskHudScale + delta, 0.6F, 2.0F);
                case MUSIC -> config.musicIslandScale = clamp(config.musicIslandScale + delta, 0.6F, 2.0F);
            }
            config.save();
            return true;
        }
        return false;
    }

    public static void render(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
        if (client.player == null) {
            return;
        }
        for (Element element : Element.values()) {
            int[] rect = rect(client, element);
            boolean hovered = inside(mouseX, mouseY, rect);
            if (element == Element.REPORTS) {
                if (!ModConfig.get().hudHints || !ModConfig.get().reportsHud) {
                    preview(context, client, element, rect, hovered || dragging == element);
                } else if (hovered || dragging == element) {
                    outline(context, rect, PREVIEW_COLORS[element.ordinal()]);
                }
            } else if (element == Element.TARGET) {
                if (!ModConfig.get().hudHints || !ModConfig.get().targetHud
                        || TargetUtil.findTarget(client) == null) {
                    preview(context, client, element, rect, hovered || dragging == element);
                } else if (hovered || dragging == element) {
                    outline(context, rect, PREVIEW_COLORS[element.ordinal()]);
                }
            } else if (element == Element.MUSIC) {
                if (!ModConfig.get().musicIsland || client.options.hudHidden) {
                    preview(context, client, element, rect, hovered || dragging == element);
                } else if (hovered || dragging == element) {
                    outline(context, rect, PREVIEW_COLORS[element.ordinal()]);
                }
            } else {
                preview(context, client, element, rect, hovered || dragging == element);
            }
        }

        Text hint = Fonts.t("ЛКМ — двигать · колесо — масштаб · отпусти кнопку — сохранить")
                .formatted(Formatting.GRAY);
        int hintX = context.getScaledWindowWidth() / 2 - client.textRenderer.getWidth(hint) / 2;
        UiDraw.roundRect(context, hintX - 10, 46, client.textRenderer.getWidth(hint) + 20,
                18, 9, 0xCC0C0D14);
        context.drawTextWithShadow(client.textRenderer, hint, hintX, 52, 0xFFD5D7E0);
    }

    private static void preview(DrawContext context, MinecraftClient client, Element element,
                                int[] rect, boolean active) {
        int color = PREVIEW_COLORS[element.ordinal()];
        int background = active ? 0xD90C0D14 : 0x990C0D14;
        UiDraw.roundRect(context, rect[0], rect[1], rect[2], rect[3], 8, background);
        UiDraw.roundRect(context, rect[0], rect[1], 3, rect[3], 2, color);
        if (active) {
            outline(context, rect, color);
        }
        String title = element == Element.REPORTS ? "Жалобы"
                : element == Element.TARGET ? "Target HUD" : PREVIEW_TITLES[element.ordinal()];
        context.drawTextWithShadow(client.textRenderer, Fonts.t(title).formatted(Formatting.BOLD),
                rect[0] + 10, rect[1] + 6, color);
        String size = String.format(java.util.Locale.ROOT, "x%.2f", scale(element));
        context.drawTextWithShadow(client.textRenderer, Fonts.t(size).formatted(Formatting.GRAY),
                rect[0] + rect[2] - client.textRenderer.getWidth(Fonts.t(size)) - 8,
                rect[1] + 6, 0xFFB6B9C6);
        String instruction = element == Element.ANYDESK ? "уведомление о скопированном коде"
                : element == Element.SPEC ? "запрос слежки"
                : element == Element.MUTE ? "уведомление о нарушении"
                : element == Element.TARGET ? "панель цели"
                : element == Element.MUSIC ? "музыкальный островок"
                : "пластинки из меню /reports";
        context.drawTextWithShadow(client.textRenderer, Fonts.t(instruction).formatted(Formatting.DARK_GRAY),
                rect[0] + 10, rect[1] + 20, 0xFF8A8E9C);
    }

    private static void outline(DrawContext context, int[] rect, int color) {
        UiDraw.roundRect(context, rect[0] - 3, rect[1] - 3, rect[2] + 6, rect[3] + 6, 10,
                (color & 0x00FFFFFF) | 0x44000000);
    }

    private static int[] rect(MinecraftClient client, Element element) {
        int screenWidth = width(client);
        int screenHeight = height(client);
        ModConfig config = ModConfig.get();
        if (element == Element.REPORTS) {
            return ReportsHud.placement(screenWidth, screenHeight, ReportsStore.entries().size());
        }
        if (element == Element.MUSIC) {
            return MusicIsland.editBounds(client);
        }
        if (element == Element.TARGET) {
            int panelWidth = Math.round(TARGET_BASE_WIDTH * config.targetScale);
            int panelHeight = Math.round(TARGET_BASE_HEIGHT * config.targetScale);
            int centerX = Math.round(config.targetX * screenWidth);
            int centerY = Math.round(config.targetY * screenHeight);
            return new int[]{clamp(centerX - panelWidth / 2, 0, Math.max(0, screenWidth - panelWidth)),
                    clamp(centerY - panelHeight / 2, 0, Math.max(0, screenHeight - panelHeight)),
                    panelWidth, panelHeight};
        }
        float scale = scale(element);
        int baseWidth = element == Element.SPEC ? 320 : element == Element.MUTE ? 600 : 172;
        int baseHeight = element == Element.SPEC ? 34 : element == Element.MUTE ? 42 : 44;
        float centerX = switch (element) {
            case SPEC -> config.specHudX;
            case MUTE -> config.muteHudX;
            case ANYDESK -> config.anydeskHudX;
            case MUSIC -> config.musicIslandX;
            default -> 0.5F;
        };
        float centerY = switch (element) {
            case SPEC -> config.specHudY;
            case MUTE -> config.muteHudY;
            case ANYDESK -> config.anydeskHudY;
            case MUSIC -> config.musicIslandY;
            default -> 0.5F;
        };
        int panelWidth = Math.round(baseWidth * scale);
        int panelHeight = Math.round(baseHeight * scale);
        int x = Math.round(centerX * screenWidth) - panelWidth / 2;
        int y = Math.round(centerY * screenHeight) - panelHeight / 2;
        return new int[]{clamp(x, 0, Math.max(0, screenWidth - panelWidth)),
                clamp(y, 0, Math.max(0, screenHeight - panelHeight)), panelWidth, panelHeight};
    }

    private static float scale(Element element) {
        ModConfig config = ModConfig.get();
        return switch (element) {
            case REPORTS -> config.reportsScale;
            case TARGET -> config.targetScale;
            case SPEC -> config.specHudScale;
            case MUTE -> config.muteHudScale;
            case ANYDESK -> config.anydeskHudScale;
            case MUSIC -> config.musicIslandScale * 0.5F;
        };
    }

    private static void setPosition(MinecraftClient client, Element element,
                                    int x, int y, int rectWidth, int rectHeight) {
        ModConfig config = ModConfig.get();
        int screenWidth = width(client);
        int screenHeight = height(client);
        switch (element) {
            case REPORTS -> {
                config.reportsX = screenWidth <= rectWidth ? 0.0F : (float) x / (screenWidth - rectWidth);
                config.reportsY = screenHeight <= rectHeight ? 0.0F : (float) y / (screenHeight - rectHeight);
            }
            case TARGET -> {
                config.targetX = (x + rectWidth / 2.0F) / Math.max(1, screenWidth);
                config.targetY = (y + rectHeight / 2.0F) / Math.max(1, screenHeight);
            }
            case SPEC -> {
                config.specHudX = (x + rectWidth / 2.0F) / Math.max(1, screenWidth);
                config.specHudY = (y + rectHeight / 2.0F) / Math.max(1, screenHeight);
            }
            case MUTE -> {
                config.muteHudX = (x + rectWidth / 2.0F) / Math.max(1, screenWidth);
                config.muteHudY = (y + rectHeight / 2.0F) / Math.max(1, screenHeight);
            }
            case ANYDESK -> {
                config.anydeskHudX = (x + rectWidth / 2.0F) / Math.max(1, screenWidth);
                config.anydeskHudY = (y + rectHeight / 2.0F) / Math.max(1, screenHeight);
            }
            case MUSIC -> {
                float actualScale = scale(element);
                float expandedOffset = MusicIsland.isExpanded() ? 31.0F * actualScale : 0.0F;
                config.musicIslandX = (x + rectWidth / 2.0F) / Math.max(1, screenWidth);
                config.musicIslandY = (y + rectHeight / 2.0F - expandedOffset)
                        / Math.max(1, screenHeight);
            }
        }
    }

    private static void finish() {
        dragging = null;
        ModConfig.get().save();
    }

    private static int width(MinecraftClient client) {
        return client.getWindow().getScaledWidth();
    }

    private static int height(MinecraftClient client) {
        return client.getWindow().getScaledHeight();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inside(double mouseX, double mouseY, int[] rect) {
        return UiDraw.inside(mouseX, mouseY, rect[0], rect[1], rect[2], rect[3]);
    }

    private enum Element {
        REPORTS, TARGET, SPEC, MUTE, ANYDESK, MUSIC
    }
}
