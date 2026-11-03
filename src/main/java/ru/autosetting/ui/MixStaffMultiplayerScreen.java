package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.IasCompat;
import ru.autosetting.util.MenuBackdrop;
import ru.autosetting.util.ServerIcons;
import ru.autosetting.util.UiDraw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Меню «Сетевая игра» в стиле MixerGrief: плитки серверов с аватарами,
 * перемещаемая карточка выбранного сервера и контекстное меню.
 * <p>
 * Карточка маленькая (280×106), её положение хранится в конфиге
 * ({@code cardX/cardY}) и меняется перетаскиванием.
 */
public final class MixStaffMultiplayerScreen extends Screen {
    private static final int ACCENT = 0xFFFF8A00;
    private static final int ACCENT_LIGHT = 0xFFFFC94D;
    private static final int TEXT_MAIN = 0xFFF4F4F8;
    private static final int TEXT_MUTED = 0xFF7E828F;
    private static final int DELETE_RED = 0xFFFF5C5C;
    private static final int MARGIN = 24;
    private static final int MAX_TILES = 8;
    private static final int TILE_GAP = 12;
    private static final int TILE_GEAR = 18;
    private static final int MENU_WIDTH = 154;
    private static final int MENU_ROW = 28;
    private static final int MENU_PAD = 6;
    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 106;
    private static final int PLAY_WIDTH = 100;
    private static final int DOTS_WIDTH = 30;
    private static final int BUTTON_HEIGHT = 24;
    private static final int[] TILE_COLORS = {
            0xFFFF8A00, 0xFFEA580C, 0xFFF59E0B, 0xFFB45309, 0xFFCA8A04, 0xFF7C2D12
    };

    private final MenuBackdrop backdrop = new MenuBackdrop();
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private final List<Tile> tiles = new ArrayList<>();
    private final ModConfig config = ModConfig.get();
    private ServerList serverList;
    private boolean pinged;
    private int selectedIndex = -1;
    private int hoverIndex = -1;
    private int menuIndex = -1;
    private int tilePageStart;
    private int topGearX;
    private int topPersonX;
    private int topGearY = 26;
    private boolean draggingCard;
    private double dragOffsetX;
    private double dragOffsetY;

    public MixStaffMultiplayerScreen() {
        super(Text.literal("Сетевая игра"));
    }

    @Override
    protected void init() {
        if (serverList == null) {
            serverList = new ServerList(client);
            serverList.loadFile();
            selectedIndex = serverList.size() > 0 ? 0 : -1;
        }
        if (!pinged) {
            pingServers();
            pinged = true;
        }
        menuIndex = -1;
        rebuildTiles();
    }

    private void pingServers() {
        for (int index = 0; index < serverList.size(); index++) {
            ServerInfo info = serverList.get(index);
            if (info == null || info.address == null || info.address.isBlank()) {
                continue;
            }
            try {
                pinger.add(info, () -> {
                }, () -> {
                });
            } catch (Exception ignored) {
                // сервер недоступен — оставляем иконку-заглушку
            }
        }
    }

    private int tileSize() {
        int available = width - MARGIN * 2 - (MAX_TILES - 1) * TILE_GAP;
        return Math.max(60, Math.min(92, available / MAX_TILES));
    }

    private int tileY() {
        return 66;
    }

    private void rebuildTiles() {
        tiles.clear();
        int size = tileSize();
        int y = tileY();
        int pageSize = MAX_TILES - 1;
        tilePageStart = selectedIndex < 0 ? 0 : selectedIndex / pageSize * pageSize;
        int count = Math.min(serverList.size() - tilePageStart, pageSize);
        for (int index = 0; index < count; index++) {
            int serverIndex = tilePageStart + index;
            tiles.add(new Tile(MARGIN + index * (size + TILE_GAP), y, size, size, serverIndex));
        }
        if (tilePageStart + count >= serverList.size() && count < MAX_TILES) {
            tiles.add(new Tile(MARGIN + count * (size + TILE_GAP), y, size, size, -1));
        }
    }

    private boolean hasCard() {
        int index = cardIndex();
        return index >= 0 && index < serverList.size();
    }

    private int cardIndex() {
        if (menuIndex >= 0 && menuIndex < serverList.size()) {
            return menuIndex;
        }
        if (hoverIndex >= 0 && hoverIndex < serverList.size()) {
            return hoverIndex;
        }
        if (selectedIndex >= 0 && selectedIndex < serverList.size()) {
            return selectedIndex;
        }
        return -1;
    }

    private int cardX() {
        return Math.round(config.cardX * Math.max(0, width - CARD_WIDTH));
    }

    private int cardY() {
        return Math.round(config.cardY * Math.max(0, height - CARD_HEIGHT));
    }

    private int playX() {
        return cardX() + 78;
    }

    private int buttonY() {
        return cardY() + CARD_HEIGHT - 32;
    }

    private int dotsX() {
        return playX() + PLAY_WIDTH + 8;
    }

    private int menuX() {
        int preferred = cardX() + CARD_WIDTH - MENU_WIDTH;
        return Math.max(MARGIN, Math.min(width - MARGIN - MENU_WIDTH, preferred));
    }

    private int menuY() {
        int y = cardY() + CARD_HEIGHT + 6;
        int menuHeight = MENU_PAD * 2 + MENU_ROW * 2;
        if (y + menuHeight > height - MARGIN) {
            y = cardY() - menuHeight - 6;
        }
        return Math.max(MARGIN, Math.min(height - MARGIN - menuHeight, y));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        backdrop.render(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        MenuBackdrop.drawClock(context, textRenderer, width);
        drawTopGear(context, mouseX, mouseY);

        hoverIndex = -1;
        for (Tile tile : tiles) {
            boolean hovered = MenuBackdrop.hovered(mouseX, mouseY, tile.x, tile.y, tile.width, tile.height);
            if (hovered && tile.index >= 0) {
                hoverIndex = tile.index;
            }
            drawTile(context, tile, hovered, mouseX, mouseY);
        }

        drawCard(context, mouseX, mouseY);
        drawMenu(context, mouseX, mouseY);
    }

    private void drawTopGear(DrawContext context, int mouseX, int mouseY) {
        Text clock = Fonts.t(MenuBackdrop.clockText());
        topGearX = width - MARGIN - textRenderer.getWidth(clock) - 22;
        topPersonX = topGearX - 28;
        boolean personHovered = MenuBackdrop.hovered(mouseX, mouseY,
                topPersonX - 11, topGearY - 11, 22, 22);
        boolean hovered = MenuBackdrop.hovered(mouseX, mouseY, topGearX - 11, topGearY - 11, 22, 22);
        if (personHovered) {
            UiDraw.circle(context, topPersonX, topGearY, 12, 0x3365E8F4);
        }
        UiDraw.glassPlayerIcon(context, topPersonX, topGearY, 15,
                personHovered ? 0xFFB7F4FF : 0xFFD5D7E0);
        if (hovered) {
            UiDraw.circle(context, topGearX, topGearY, 12, 0x33FFC94D);
        }
        UiDraw.gear(context, topGearX, topGearY, 7,
                hovered ? 0xFFFFE2B0 : 0xFFD5D7E0, 0xFF0B0C12);
    }

    private void drawTile(DrawContext context, Tile tile, boolean hovered, int mouseX, int mouseY) {
        int x = tile.x;
        int y = tile.y;
        int size = tile.width;
        boolean selected = tile.index == selectedIndex;
        if (hovered || selected) {
            UiDraw.roundRect(context, x - 2, y - 2, size + 4, size + 4, 18, 0x33FFC94D);
        }
        UiDraw.roundRect(context, x, y, size, size, 16,
                selected ? 0xF21A1B26 : 0xE612131C);
        UiDraw.roundRect(context, x, y, size, 1, 0, 0x22FFFFFF);

        if (tile.index < 0) {
            int plus = size / 2;
            UiDraw.roundRect(context, x + plus - 12, y + plus - 10, 24, 4, 2,
                    hovered ? 0xFFFFE2B0 : 0xFFB9BCC7);
            UiDraw.roundRect(context, x + plus - 2, y + plus - 20, 4, 24, 2,
                    hovered ? 0xFFFFE2B0 : 0xFFB9BCC7);
            Text add = Fonts.t("Добавить");
            context.drawTextWithShadow(textRenderer, add,
                    x + (size - textRenderer.getWidth(add)) / 2, y + size - 16, TEXT_MUTED);
            return;
        }

        ServerInfo info = serverList.get(tile.index);
        String name = serverName(info);
        int avatar = Math.max(30, size - 22);
        int avatarX = x + (size - avatar) / 2;
        int avatarY = y + 6;
        if (ServerIcons.hasIcon(info)) {
            ServerIcons.draw(context, info, avatarX, avatarY, avatar);
        } else {
            int color = TILE_COLORS[Math.floorMod(name.hashCode(), TILE_COLORS.length)];
            UiDraw.roundRect(context, avatarX, avatarY, avatar, avatar, Math.max(8, avatar / 4),
                    (color & 0x00FFFFFF) | 0xE6000000);
            Text code = Fonts.t(initials(name)).formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, code,
                    avatarX + (avatar - textRenderer.getWidth(code)) / 2,
                    avatarY + avatar / 2 - 5, 0xFFFFFFFF);
        }

        Text label = Fonts.t(MenuBackdrop.fit(textRenderer, name, size - 8));
        context.drawTextWithShadow(textRenderer, label,
                x + (size - textRenderer.getWidth(label)) / 2, y + size - 15,
                hovered || selected ? 0xFFFFFFFF : 0xFFD9DBE4);

        int gearX = x + size - TILE_GEAR - 2;
        int gearY = y + 2;
        boolean gearHovered = MenuBackdrop.hovered(mouseX, mouseY, gearX, gearY, TILE_GEAR, TILE_GEAR);
        UiDraw.circle(context, gearX + TILE_GEAR / 2, gearY + TILE_GEAR / 2, 9,
                gearHovered || menuIndex == tile.index ? 0xEE0B0C12 : 0x990B0C12);
        UiDraw.gear(context, gearX + TILE_GEAR / 2, gearY + TILE_GEAR / 2, 4,
                gearHovered || menuIndex == tile.index ? 0xFFFFE2B0 : 0xFFFFFFFF, 0xFF0B0C12);
    }

    private void drawCard(DrawContext context, int mouseX, int mouseY) {
        int index = cardIndex();
        int x = cardX();
        int y = cardY();

        if (index < 0) {
            UiDraw.roundRect(context, x, y, CARD_WIDTH, CARD_HEIGHT, 14, 0xE60C0D14);
            Text empty = Fonts.t("Серверы пока не добавлены").formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, empty, x + 16, y + 20, TEXT_MAIN);
            context.drawTextWithShadow(textRenderer, Fonts.t("Нажми «+» на плитке выше"),
                    x + 16, y + 38, TEXT_MUTED);
            return;
        }

        ServerInfo info = serverList.get(index);
        boolean hovered = MenuBackdrop.hovered(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
        UiDraw.roundRect(context, x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, 15,
                hovered || draggingCard ? 0x44FFC94D : 0x22FFC94D);
        UiDraw.roundRect(context, x, y, CARD_WIDTH, CARD_HEIGHT, 14, 0xF20C0D14);
        UiDraw.roundRect(context, x + 16, y + 1, CARD_WIDTH - 32, 1, 0, 0x2EFFFFFF);

        int avatarSize = 54;
        if (ServerIcons.hasIcon(info)) {
            ServerIcons.draw(context, info, x + 14, y + 14, avatarSize);
        } else {
            String name = serverName(info);
            int color = TILE_COLORS[Math.floorMod(name.hashCode(), TILE_COLORS.length)];
            UiDraw.roundRect(context, x + 14, y + 14, avatarSize, avatarSize, 13,
                    (color & 0x00FFFFFF) | 0xF2000000);
            Text code = Fonts.t(initials(name)).formatted(Formatting.BOLD);
            context.drawTextWithShadow(textRenderer, code,
                    x + 14 + (avatarSize - textRenderer.getWidth(code)) / 2, y + 34, 0xFFFFFFFF);
        }

        int textX = x + 78;
        int textRight = x + CARD_WIDTH - 14;
        Text name = Fonts.t(MenuBackdrop.fit(textRenderer, serverName(info), textRight - textX))
                .formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, name, textX, y + 12, TEXT_MAIN);

        Text address = Fonts.t(MenuBackdrop.fit(textRenderer,
                info.address == null ? "адрес не указан" : info.address, textRight - textX))
                .formatted(Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, address, textX, y + 28, TEXT_MUTED);

        // онлайн и пинг
        int statusY = y + 46;
        int statusColor = info.players == null ? 0xFF7E828F
                : info.ping > 0 ? 0xFF34D399 : 0xFFFFC94D;
        UiDraw.circle(context, textX + 3, statusY + 4, 3, statusColor);
        Text online = Fonts.t(onlineText(info));
        context.drawTextWithShadow(textRenderer, online, textX + 10, statusY, 0xFFD5D7E0);

        long ping = info.ping;
        Text pingText = Fonts.t(ping > 0 ? ping + " ms" : "— ms");
        int pingColor = ping <= 0 ? 0xFF7E828F : ping < 80 ? 0xFF34D399
                : ping < 160 ? 0xFFFFC94D : 0xFFFF5C5C;
        context.drawTextWithShadow(textRenderer, pingText,
                textRight - textRenderer.getWidth(pingText), statusY, pingColor);

        // кнопки: «Играть» и «…»
        int by = buttonY();
        boolean playHovered = MenuBackdrop.hovered(mouseX, mouseY, playX(), by, PLAY_WIDTH, BUTTON_HEIGHT);
        UiDraw.roundRect(context, playX(), by, PLAY_WIDTH, BUTTON_HEIGHT, 12,
                playHovered ? 0xFFFFA22E : ACCENT);
        Text play = Fonts.t("Играть").formatted(Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, play,
                playX() + (PLAY_WIDTH - textRenderer.getWidth(play)) / 2, by + 7, 0xFF1A1206);

        boolean dotsHovered = MenuBackdrop.hovered(mouseX, mouseY, dotsX(), by, DOTS_WIDTH, BUTTON_HEIGHT);
        UiDraw.roundRect(context, dotsX(), by, DOTS_WIDTH, BUTTON_HEIGHT, 12,
                dotsHovered || menuIndex >= 0 ? 0xFF26283A : 0xFF1B1D28);
        for (int dot = -1; dot <= 1; dot++) {
            UiDraw.circle(context, dotsX() + DOTS_WIDTH / 2 + dot * 5, by + BUTTON_HEIGHT / 2, 1,
                    dotsHovered || menuIndex >= 0 ? 0xFFFFE2B0 : 0xFFB9BCC7);
        }
    }

    private void drawMenu(DrawContext context, int mouseX, int mouseY) {
        if (menuIndex < 0 || menuIndex >= serverList.size()) {
            return;
        }
        int x = menuX();
        int y = menuY();
        int menuHeight = MENU_PAD * 2 + MENU_ROW * 2;
        UiDraw.roundRect(context, x - 1, y - 1, MENU_WIDTH + 2, menuHeight + 2, 12, 0x44FFC94D);
        UiDraw.roundRect(context, x, y, MENU_WIDTH, menuHeight, 11, 0xF2101118);

        int editY = y + MENU_PAD;
        int deleteY = editY + MENU_ROW;
        boolean editHovered = MenuBackdrop.hovered(mouseX, mouseY, x + 4, editY, MENU_WIDTH - 8, MENU_ROW);
        boolean deleteHovered = MenuBackdrop.hovered(mouseX, mouseY, x + 4, deleteY,
                MENU_WIDTH - 8, MENU_ROW);
        if (editHovered) {
            UiDraw.roundRect(context, x + 4, editY, MENU_WIDTH - 8, MENU_ROW, 7, 0x1FFFFFFF);
        }
        if (deleteHovered) {
            UiDraw.roundRect(context, x + 4, deleteY, MENU_WIDTH - 8, MENU_ROW, 7, 0x33FF5C5C);
        }
        context.drawTextWithShadow(textRenderer, Fonts.t("Редактировать"),
                x + 16, editY + 10, editHovered ? 0xFFFFFFFF : 0xFFEDEEF5);
        context.drawTextWithShadow(textRenderer, Fonts.t("Удалить"),
                x + 16, deleteY + 10, DELETE_RED);
        context.fill(x + 10, deleteY - 1, x + MENU_WIDTH - 10, deleteY, 0x26FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuIndex >= 0) {
            if (button == 0) {
                int x = menuX();
                int y = menuY();
                if (MenuBackdrop.hovered(mouseX, mouseY, x + 4, y + MENU_PAD,
                        MENU_WIDTH - 8, MENU_ROW)) {
                    int index = menuIndex;
                    menuIndex = -1;
                    openEditServer(index);
                    return true;
                }
                if (MenuBackdrop.hovered(mouseX, mouseY, x + 4, y + MENU_PAD + MENU_ROW,
                        MENU_WIDTH - 8, MENU_ROW)) {
                    int index = menuIndex;
                    menuIndex = -1;
                    deleteServer(index);
                    return true;
                }
            }
            menuIndex = -1;
            return true;
        }

        if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY, topGearX - 11, topGearY - 11, 22, 22)) {
            client.setScreen(new SettingScreen());
            return true;
        }
        if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY,
                topPersonX - 11, topGearY - 11, 22, 22)) {
            IasCompat.openAccountMenu(client, this);
            return true;
        }

        // карточка лежит поверх плиток — обрабатываем её первой
        int index = cardIndex();
        if (index >= 0 && MenuBackdrop.hovered(mouseX, mouseY, cardX(), cardY(), CARD_WIDTH, CARD_HEIGHT)) {
            if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY, playX(), buttonY(),
                    PLAY_WIDTH, BUTTON_HEIGHT)) {
                connect(index);
                return true;
            }
            if (button == 0 && MenuBackdrop.hovered(mouseX, mouseY, dotsX(), buttonY(),
                    DOTS_WIDTH, BUTTON_HEIGHT)) {
                menuIndex = index;
                return true;
            }
            if (button == 0) {
                draggingCard = true;
                dragOffsetX = mouseX - cardX();
                dragOffsetY = mouseY - cardY();
                return true;
            }
            return true;
        }

        if (button == 0) {
            for (Tile tile : tiles) {
                if (tile.index >= 0 && MenuBackdrop.hovered(mouseX, mouseY,
                        tile.x + tile.width - TILE_GEAR - 2, tile.y + 2, TILE_GEAR, TILE_GEAR)) {
                    menuIndex = tile.index;
                    selectedIndex = tile.index;
                    return true;
                }
            }
            for (Tile tile : tiles) {
                if (MenuBackdrop.hovered(mouseX, mouseY, tile.x, tile.y, tile.width, tile.height)) {
                    if (tile.index < 0) {
                        openAddServer();
                    } else {
                        selectedIndex = tile.index;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingCard) {
            int x = (int) Math.round(mouseX - dragOffsetX);
            int y = (int) Math.round(mouseY - dragOffsetY);
            x = Math.max(0, Math.min(width - CARD_WIDTH, x));
            y = Math.max(0, Math.min(height - CARD_HEIGHT, y));
            config.cardX = width <= CARD_WIDTH ? 0.0F : (float) x / (width - CARD_WIDTH);
            config.cardY = height <= CARD_HEIGHT ? 0.0F : (float) y / (height - CARD_HEIGHT);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingCard) {
            draggingCard = false;
            config.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menuIndex >= 0) {
                menuIndex = -1;
                return true;
            }
            close();
            return true;
        }
        if (menuIndex >= 0) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            selectAdjacentServer(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            selectAdjacentServer(1);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && selectedIndex >= 0 && selectedIndex < serverList.size()) {
            connect(selectedIndex);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectAdjacentServer(int offset) {
        if (serverList.size() == 0) {
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= serverList.size()) {
            selectedIndex = offset > 0 ? 0 : serverList.size() - 1;
        } else {
            selectedIndex = Math.floorMod(selectedIndex + offset, serverList.size());
        }
        hoverIndex = -1;
        rebuildTiles();
    }

    private void connect(int index) {
        ServerInfo info = serverList.get(index);
        if (info == null || info.address == null || info.address.isBlank()) {
            return;
        }
        ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
    }

    private void openAddServer() {
        ServerInfo fresh = new ServerInfo("", "", ServerInfo.ServerType.OTHER);
        client.setScreen(new MixStaffServerScreen(serverList, -1, fresh));
    }

    private void openEditServer(int index) {
        ServerInfo info = serverList.get(index);
        if (info == null) {
            return;
        }
        client.setScreen(new MixStaffServerScreen(serverList, index, info));
    }

    private void deleteServer(int index) {
        ServerInfo info = serverList.get(index);
        if (info != null) {
            serverList.remove(info);
            serverList.saveFile();
        }
        if (selectedIndex >= serverList.size()) {
            selectedIndex = serverList.size() - 1;
        }
        rebuildTiles();
    }

    private static String serverName(ServerInfo info) {
        if (info == null) {
            return "Сервер";
        }
        if (info.name != null && !info.name.isBlank()) {
            return info.name;
        }
        return info.address == null || info.address.isBlank() ? "Сервер" : info.address;
    }

    private static String initials(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] parts = clean.split("[\\s\\-_]+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase(Locale.ROOT);
    }

    private static String onlineText(ServerInfo info) {
        if (info == null || info.players == null) {
            return "онлайн: —";
        }
        return "онлайн: " + info.players.online() + " / " + info.players.max();
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
    }

    @Override
    public void removed() {
        pinger.cancel();
        ServerIcons.clear();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record Tile(int x, int y, int width, int height, int index) {
    }
}
