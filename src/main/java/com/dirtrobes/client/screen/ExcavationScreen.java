package com.dirtrobes.client.screen;

import com.dirtrobes.excavation.ExcavationGame;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.species.Property;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini-jeu de fouille : dégager le fossile de sa gangue de roche avant qu'il s'éveille.
 * Tout est dessiné avec des rectangles (pas de texture).
 */
public class ExcavationScreen extends Screen {
    private static final int CELL = 12;
    private static final int GRID_W = ExcavationGame.WIDTH * CELL;
    private static final int GRID_H = ExcavationGame.HEIGHT * CELL;
    private static final int[] ROCK = {0xFF3A2F27, 0xFF8E7A62, 0xFF6E5C49, 0xFF4F4135};
    private static final int BONE = 0xFFEDE3C8;
    private static final int BONE_SHADE = 0xFFC9BB98;

    private final BlockPos incubatorPos;
    private final ExcavationGame game;
    private final Component fossilName;
    private final long noiseSeed;
    private final List<Debris> debris = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    private ExcavationGame.Tool tool = ExcavationGame.Tool.DRILL;
    private Button drillButton;
    private Button chiselButton;
    private Button doneButton;
    private boolean mouseDown;
    private int cooldown;
    private int shake;
    private int flash;
    private boolean resultSent;
    private int left;
    private int top;

    public ExcavationScreen(BlockPos incubatorPos, Property property, Component fossilName) {
        super(Component.translatable("gui.dirtrobes.excavation.title"));
        this.incubatorPos = incubatorPos;
        this.fossilName = fossilName;
        this.game = new ExcavationGame(property, RandomSource.create());
        this.noiseSeed = random.nextLong();
    }

    @Override
    protected void init() {
        left = (width - GRID_W) / 2;
        top = (height - GRID_H) / 2 + 8;
        int by = top + GRID_H + 8;
        drillButton = addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.excavation.drill"), b -> tool = ExcavationGame.Tool.DRILL)
                .bounds(left, by, 92, 20).build());
        chiselButton = addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.excavation.chisel"), b -> tool = ExcavationGame.Tool.CHISEL)
                .bounds(left + GRID_W - 92, by, 92, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.excavation.done"), b -> onClose())
                .bounds(width / 2 - 50, top + GRID_H / 2 + 22, 100, 20).build());
        doneButton.visible = game.isOver();
        updateButtons();
    }

    private void updateButtons() {
        drillButton.active = tool != ExcavationGame.Tool.DRILL && !game.isOver();
        chiselButton.active = tool != ExcavationGame.Tool.CHISEL && !game.isOver();
        doneButton.visible = game.isOver();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ logique

    @Override
    public void tick() {
        super.tick();
        boolean wasOver = game.isOver();
        game.tick();
        if (!wasOver && game.isOver()) onGameOver();
        if (cooldown > 0) cooldown--;
        if (shake > 0) shake--;
        if (flash > 0) flash--;
        if (mouseDown && cooldown <= 0 && minecraft != null) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            strike(mx, my);
        }
        debris.removeIf(Debris::tick);
        updateButtons();
    }

    private void strike(double mx, double my) {
        if (game.isOver()) return;
        int cx = Mth.floor((mx - left) / CELL);
        int cy = Mth.floor((my - top) / CELL);
        if (!ExcavationGame.inside(cx, cy)) return;
        int before = game.depth(cx, cy);
        ExcavationGame.HitResult hit = game.use(tool, cx, cy);
        cooldown = tool == ExcavationGame.Tool.DRILL ? 5 : 3;
        if (hit.layersRemoved() > 0) {
            spawnDebris(cx, cy, hit.layersRemoved(), ROCK[Math.max(1, before)]);
            play(tool == ExcavationGame.Tool.DRILL ? SoundEvents.STONE_BREAK : SoundEvents.STONE_HIT, 0.9f + random.nextFloat() * 0.3f);
            if (tool == ExcavationGame.Tool.DRILL) shake = 3;
        }
        if (hit.damage() > 0) {
            flash = 6;
            spawnDebris(cx, cy, 3, BONE);
            play(SoundEvents.BONE_BLOCK_BREAK, 1.4f);
        }
        if (game.isOver()) onGameOver();
    }

    private void onGameOver() {
        if (resultSent) return;
        resultSent = true;
        mouseDown = false;
        PacketDistributor.sendToServer(new ModNetwork.ExcavationResultPayload(incubatorPos, game.finalIntegrity()));
        switch (game.state()) {
            case SUCCESS -> play(SoundEvents.PLAYER_LEVELUP, 1.2f);
            case TIMEOUT -> play(SoundEvents.ANVIL_LAND, 1.4f);
            case BROKEN -> play(SoundEvents.DECORATED_POT_SHATTER, 0.8f);
            default -> {
            }
        }
        updateButtons();
    }

    private void play(SoundEvent sound, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.5f));
    }

    private void spawnDebris(int cx, int cy, int amount, int color) {
        for (int i = 0; i < amount * 4; i++) {
            debris.add(new Debris(left + cx * CELL + CELL / 2f, top + cy * CELL + CELL / 2f,
                    (random.nextFloat() - 0.5f) * 3f, -random.nextFloat() * 2.5f, color, 10 + random.nextInt(8)));
        }
    }

    // ------------------------------------------------------------------ entrées

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && !game.isOver() && insideGrid(mx, my)) {
            mouseDown = true;
            strike(mx, my);
            return true;
        }
        if (button == 1 && !game.isOver()) {
            tool = tool == ExcavationGame.Tool.DRILL ? ExcavationGame.Tool.CHISEL : ExcavationGame.Tool.DRILL;
            updateButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) mouseDown = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_1) {
            tool = ExcavationGame.Tool.DRILL;
            updateButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2) {
            tool = ExcavationGame.Tool.CHISEL;
            updateButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean insideGrid(double mx, double my) {
        return mx >= left && my >= top && mx < left + GRID_W && my < top + GRID_H;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int ox = shake > 0 ? random.nextInt(3) - 1 : 0;
        int oy = shake > 0 ? random.nextInt(3) - 1 : 0;
        int gx = left + ox, gy = top + oy;
        int accent = 0xFF000000 | game.property().color();

        // Cadre
        g.fill(gx - 6, gy - 30, gx + GRID_W + 6, gy + GRID_H + 4, 0xFF1B1611);
        g.renderOutline(gx - 6, gy - 30, GRID_W + 12, GRID_H + 34, accent);

        // En-tête : titre, intégrité, temps, avancement
        g.drawCenteredString(font, Component.translatable("gui.dirtrobes.excavation.header", fossilName), width / 2, gy - 26, 0xFFFFFFFF);
        drawBar(g, gx, gy - 14, GRID_W / 2 - 4, game.integrity() / 100f, integrityColor(), Component.translatable("gui.dirtrobes.excavation.integrity", game.integrity()));
        float time = game.ticksLeft() / (float) ExcavationGame.TIME_LIMIT;
        drawBar(g, gx + GRID_W / 2 + 4, gy - 14, GRID_W / 2 - 4, time, time < 0.25f ? 0xFFE0533D : 0xFF6FB7E8,
                Component.translatable("gui.dirtrobes.excavation.time", Mth.ceil(game.ticksLeft() / 20f)));

        // Grille
        for (int x = 0; x < ExcavationGame.WIDTH; x++) {
            for (int y = 0; y < ExcavationGame.HEIGHT; y++) {
                drawCell(g, gx + x * CELL, gy + y * CELL, x, y, accent);
            }
        }

        // Curseur d'outil
        if (!game.isOver() && insideGrid(mouseX, mouseY)) {
            int cx = (mouseX - left) / CELL, cy = (mouseY - top) / CELL;
            int hover = 0x88FFFFFF;
            if (tool == ExcavationGame.Tool.DRILL) {
                int[][] area = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : area) {
                    if (ExcavationGame.inside(cx + d[0], cy + d[1])) {
                        g.renderOutline(gx + (cx + d[0]) * CELL, gy + (cy + d[1]) * CELL, CELL, CELL, hover);
                    }
                }
            } else {
                g.renderOutline(gx + cx * CELL, gy + cy * CELL, CELL, CELL, 0xFFFFFFFF);
            }
        }

        // Débris
        for (Debris d : debris) {
            int px = Math.round(d.x), py = Math.round(d.y);
            g.fill(px, py, px + 2, py + 2, d.color);
        }

        // Éclair rouge quand le fossile est abîmé
        if (flash > 0) {
            int alpha = flash * 18;
            g.fill(gx, gy, gx + GRID_W, gy + GRID_H, (alpha << 24) | 0xD0302A);
        }

        // Aide / outil courant
        Component hint = Component.translatable(tool == ExcavationGame.Tool.DRILL ? "gui.dirtrobes.excavation.drill_hint" : "gui.dirtrobes.excavation.chisel_hint");
        g.drawCenteredString(font, hint, width / 2, top + GRID_H + 34, 0xFFBFB6A8);
        g.drawCenteredString(font, Component.translatable("gui.dirtrobes.excavation.exposed", Math.round(game.exposed() * 100)),
                width / 2, top + GRID_H + 14, 0xFFFFFFFF);

        // Résultat
        if (game.isOver()) {
            g.fill(left - 6, top + GRID_H / 2 - 22, left + GRID_W + 6, top + GRID_H / 2 + 46, 0xDD000000);
            Component title;
            Component detail;
            switch (game.state()) {
                case SUCCESS -> {
                    title = Component.translatable("gui.dirtrobes.excavation.success");
                    detail = Component.translatable("gui.dirtrobes.excavation.result", game.integrity());
                }
                case TIMEOUT -> {
                    title = Component.translatable("gui.dirtrobes.excavation.timeout");
                    detail = Component.translatable("gui.dirtrobes.excavation.result", game.integrity());
                }
                default -> {
                    title = Component.translatable("gui.dirtrobes.excavation.broken");
                    detail = Component.translatable("gui.dirtrobes.excavation.broken_detail");
                }
            }
            g.drawCenteredString(font, title, width / 2, top + GRID_H / 2 - 14, game.state() == ExcavationGame.State.BROKEN ? 0xFFFF6B5A : accent);
            g.drawCenteredString(font, detail, width / 2, top + GRID_H / 2 + 2, 0xFFFFFFFF);
            doneButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private int integrityColor() {
        int i = game.integrity();
        return i >= 90 ? 0xFF6BD36B : i >= 60 ? 0xFFE8C84A : 0xFFE0533D;
    }

    private void drawBar(GuiGraphics g, int x, int y, int w, float fraction, int color, Component label) {
        g.fill(x, y, x + w, y + 10, 0xFF0E0B08);
        g.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * Mth.clamp(fraction, 0f, 1f)), y + 9, color);
        g.drawCenteredString(font, label, x + w / 2, y + 1, 0xFFFFFFFF);
    }

    private void drawCell(GuiGraphics g, int px, int py, int x, int y, int accent) {
        int depth = game.depth(x, y);
        boolean fossil = game.isFossil(x, y);
        long h = hash(x, y);
        if (depth == 0) {
            if (fossil) {
                g.fill(px, py, px + CELL, py + CELL, BONE);
                // ombrage + liseré de la couleur de la propriété sur les bords du fossile
                if (!isFossilAt(x, y + 1)) g.fill(px, py + CELL - 2, px + CELL, py + CELL, BONE_SHADE);
                if (!isFossilAt(x + 1, y)) g.fill(px + CELL - 2, py, px + CELL, py + CELL, BONE_SHADE);
                if (!isFossilAt(x, y - 1)) g.fill(px, py, px + CELL, py + 1, accent);
                if (!isFossilAt(x - 1, y)) g.fill(px, py, px + 1, py + CELL, accent);
                if ((h & 7) == 0) g.fill(px + 3 + (int) (h >> 4 & 3), py + 4, px + 5 + (int) (h >> 4 & 3), py + 6, BONE_SHADE);
            } else {
                g.fill(px, py, px + CELL, py + CELL, ROCK[0]);
                if ((h & 3) == 0) g.fill(px + (int) (h >> 3 & 7), py + (int) (h >> 6 & 7), px + (int) (h >> 3 & 7) + 2, py + (int) (h >> 6 & 7) + 2, 0xFF2C231D);
            }
            return;
        }
        int base = ROCK[Math.min(depth, 3)];
        g.fill(px, py, px + CELL, py + CELL, base);
        // relief : bord clair en haut à gauche, sombre en bas à droite
        g.fill(px, py, px + CELL, py + 1, lighten(base, 18));
        g.fill(px, py, px + 1, py + CELL, lighten(base, 12));
        g.fill(px, py + CELL - 1, px + CELL, py + CELL, lighten(base, -22));
        g.fill(px + CELL - 1, py, px + CELL, py + CELL, lighten(base, -16));
        // grain
        for (int i = 0; i < 3; i++) {
            long k = h >> (i * 9);
            int gx = px + 2 + (int) (k & 7), gy = py + 2 + (int) (k >> 3 & 7);
            g.fill(gx, gy, gx + 1, gy + 1, lighten(base, (k & 64) != 0 ? 14 : -14));
        }
        // Sous une seule couche, le fossile se devine par des fissures claires
        if (fossil && depth == 1) {
            g.fill(px + 3, py + 5, px + 9, py + 6, 0xFFB8A888);
            g.fill(px + 5, py + 3, px + 6, py + 9, 0xFFB8A888);
        }
    }

    private boolean isFossilAt(int x, int y) {
        return ExcavationGame.inside(x, y) && game.isFossil(x, y);
    }

    private long hash(int x, int y) {
        long h = noiseSeed ^ (x * 73856093L) ^ (y * 19349663L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }

    private static int lighten(int argb, int amount) {
        int r = Mth.clamp((argb >> 16 & 255) + amount, 0, 255);
        int gg = Mth.clamp((argb >> 8 & 255) + amount, 0, 255);
        int b = Mth.clamp((argb & 255) + amount, 0, 255);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private static final class Debris {
        float x, y, vx, vy;
        final int color;
        int life;

        Debris(float x, float y, float vx, float vy, int color, int life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.life = life;
        }

        /** @return true quand le débris doit disparaître */
        boolean tick() {
            x += vx;
            y += vy;
            vy += 0.35f;
            vx *= 0.92f;
            return --life <= 0;
        }
    }
}
