package ru.autosetting.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.autosetting.util.Fonts;
import ru.autosetting.util.UiDraw;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Original tap-to-fly arcade minigame with MixerGrief's neon-glass styling. */
public final class SkylineDashScreen extends Screen {
    private static final int SKY = 0xFF090D1B;
    private static final int CYAN = 0xFF65E8F4;
    private static final int VIOLET = 0xFF9A78FF;
    private static final double GRAVITY = 0.34D;
    private static final double FLAP = -5.2D;
    private static final double GATE_SPEED = 2.8D;
    private static final int GATE_WIDTH = 38;
    private static final int GAP_HEIGHT = 122;

    private final List<Gate> gates = new ArrayList<>();
    private double gliderY;
    private double velocity;
    private double gateOffset;
    private int ticks;
    private int score;
    private int bestScore;
    private boolean started;
    private boolean gameOver;

    public SkylineDashScreen() {
        super(Text.literal("Skyline Dash"));
    }

    @Override
    protected void init() {
        if (gliderY == 0.0D) {
            gliderY = height * 0.48D;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!started || gameOver) {
            return;
        }
        ticks++;
        velocity = Math.min(6.5D, velocity + GRAVITY);
        gliderY += velocity;

        if (ticks % 86 == 0) {
            int margin = Math.max(55, GAP_HEIGHT / 2 + 12);
            int minY = margin;
            int maxY = Math.max(minY + 1, height - margin);
            int gapCenter = minY + Math.floorMod(ticks * 37, maxY - minY);
            gates.add(new Gate(width + 24.0D, gapCenter));
        }

        double gliderX = width * 0.28D;
        Iterator<Gate> iterator = gates.iterator();
        while (iterator.hasNext()) {
            Gate gate = iterator.next();
            gate.x -= GATE_SPEED;
            if (!gate.scored && gate.x + GATE_WIDTH < gliderX) {
                gate.scored = true;
                score++;
                bestScore = Math.max(bestScore, score);
            }
            if (gate.x + GATE_WIDTH < 0.0D) {
                iterator.remove();
                continue;
            }
            boolean overlapsX = gliderX + 9 > gate.x && gliderX - 9 < gate.x + GATE_WIDTH;
            if (overlapsX && (gliderY - 8 < gate.gapCenter - GAP_HEIGHT / 2.0D
                    || gliderY + 8 > gate.gapCenter + GAP_HEIGHT / 2.0D)) {
                gameOver = true;
            }
        }

        if (gliderY < 10 || gliderY > height - 12) {
            gameOver = true;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SKY);
        context.fill(0, height / 3, width, height, 0xFF10172A);
        context.fill(0, height * 2 / 3, width, height, 0xFF15172B);

        for (int index = 0; index < 38; index++) {
            int starX = Math.floorMod(index * 127 + ticks * (index % 3 + 1), Math.max(1, width));
            int starY = Math.floorMod(index * 71 + 19, Math.max(1, height));
            context.fill(starX, starY, starX + 2, starY + 2,
                    index % 4 == 0 ? 0xAA65E8F4 : 0x667C8CB8);
        }
        for (int index = 0; index < 12; index++) {
            int buildingWidth = 22 + index % 3 * 9;
            int buildingHeight = 24 + Math.floorMod(index * 43, Math.max(25, height / 3));
            int x = index * Math.max(1, width / 10) - Math.floorMod(ticks, 40);
            int y = height - buildingHeight;
            UiDraw.roundRect(context, x, y, buildingWidth, buildingHeight, 4,
                    index % 2 == 0 ? 0x55395B83 : 0x554C407B);
            for (int windowY = y + 7; windowY < height - 5; windowY += 12) {
                context.fill(x + 6, windowY, x + 9, windowY + 3, 0x6678DDE8);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        renderGates(context);
        renderGlider(context);

        Text scoreText = Fonts.t(Integer.toString(score));
        context.drawTextWithShadow(textRenderer, scoreText,
                width / 2 - textRenderer.getWidth(scoreText) / 2, 22, 0xFFF4F8FF);
        Text bestText = Fonts.t("BEST  " + bestScore);
        context.drawTextWithShadow(textRenderer, bestText,
                width - textRenderer.getWidth(bestText) - 16, 14, 0xFFA8B4CE);

        if (!started) {
            drawCard(context, "SKYLINE DASH", "Пробел или клик — взлететь",
                    "Пройди через энергетические ворота");
        } else if (gameOver) {
            drawCard(context, "ПОЛЁТ ЗАВЕРШЁН", "Счёт: " + score + "   ·   Рекорд: " + bestScore,
                    "Клик или пробел — начать заново");
        } else {
            Text hint = Fonts.t("ПРОБЕЛ / КЛИК — импульс   ·   Esc — выход");
            context.drawTextWithShadow(textRenderer, hint,
                    width / 2 - textRenderer.getWidth(hint) / 2, height - 22, 0xFFA8B4CE);
        }
    }

    private void renderGates(DrawContext context) {
        for (Gate gate : gates) {
            int x = (int) Math.round(gate.x);
            int gapTop = (int) Math.round(gate.gapCenter - GAP_HEIGHT / 2.0D);
            int gapBottom = (int) Math.round(gate.gapCenter + GAP_HEIGHT / 2.0D);
            renderGateSegment(context, x, 0, gapTop, false);
            renderGateSegment(context, x, gapBottom, height, true);
        }
    }

    private static void renderGateSegment(DrawContext context, int x, int top, int bottom, boolean lower) {
        int segmentHeight = bottom - top;
        if (segmentHeight <= 0) {
            return;
        }
        UiDraw.roundRect(context, x - 3, top, GATE_WIDTH + 6, segmentHeight, 8, 0x3365E8F4);
        UiDraw.roundRect(context, x, top, GATE_WIDTH, segmentHeight, 7, 0xCC17263A);
        UiDraw.roundRect(context, x + 2, top + 2, 3, Math.max(1, segmentHeight - 4), 1, 0xAA65E8F4);
        UiDraw.roundRect(context, x + GATE_WIDTH - 5, top + 2, 2,
                Math.max(1, segmentHeight - 4), 1, 0x668FD5FF);
        int edgeY = lower ? top : bottom - 8;
        UiDraw.roundRect(context, x - 5, edgeY, GATE_WIDTH + 10, 8, 4, VIOLET);
        UiDraw.roundRect(context, x + 3, edgeY + 2, GATE_WIDTH - 6, 1, 1, 0xCCFFFFFF);
    }

    private void renderGlider(DrawContext context) {
        int x = (int) (width * 0.28D);
        int y = (int) Math.round(gliderY);
        UiDraw.glassIconStroke(context, x - 17, y + 3, x - 5, y + 1, 3, 0x6685D8FF);
        UiDraw.circle(context, x, y, 13, 0x4465E8F4);
        UiDraw.circle(context, x, y, 9, 0xFF5366A8);
        UiDraw.circle(context, x - 2, y - 3, 4, 0xFF93F1F4);
        UiDraw.circle(context, x + 5, y - 1, 2, 0xFFFFFFFF);
        context.fill(x + 8, y - 1, x + 14, y + 2, 0xFFFFC96B);
        UiDraw.glassIconStroke(context, x - 2, y + 3,
                x - 10, y + (velocity < 0 ? 10 : -5), 3, 0xFFB7A5FF);
    }

    private void drawCard(DrawContext context, String title, String subtitle, String instruction) {
        int cardWidth = Math.min(340, width - 32);
        int cardX = (width - cardWidth) / 2;
        int cardY = height / 2 - 48;
        UiDraw.glassPanel(context, cardX, cardY, cardWidth, 96, 16, CYAN);
        drawCentered(context, title, cardY + 17, 0xFFF4F8FF);
        drawCentered(context, subtitle, cardY + 42, 0xFFBFEFF6);
        drawCentered(context, instruction, cardY + 62, 0xFFA8B4CE);
    }

    private void drawCentered(DrawContext context, String value, int y, int color) {
        Text text = Fonts.t(value);
        context.drawTextWithShadow(textRenderer, text,
                width / 2 - textRenderer.getWidth(text) / 2, y, color);
    }

    private void flap() {
        if (gameOver) {
            reset();
        }
        started = true;
        velocity = FLAP;
    }

    private void reset() {
        gates.clear();
        score = 0;
        ticks = 0;
        velocity = 0.0D;
        gliderY = height * 0.48D;
        started = false;
        gameOver = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            flap();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_UP) {
            flap();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class Gate {
        private double x;
        private final int gapCenter;
        private boolean scored;

        private Gate(double x, int gapCenter) {
            this.x = x;
            this.gapCenter = gapCenter;
        }
    }
}
