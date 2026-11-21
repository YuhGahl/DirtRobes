package com.dirtrobes.client.screen;

import com.dirtrobes.lab.LabNetwork;
import com.dirtrobes.lab.Pen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Réglage de l'enclos d'un terminal : boutons − / + pour la taille, la position (par rapport au terminal)
 * et le point de spawn (par rapport au coin de l'enclos). Maj + clic = pas de 5. Le panneau est à gauche
 * et le monde reste visible : le contour vert de l'enclos et le point de spawn jaune s'y dessinent en direct.
 */
public class PenScreen extends Screen {
    private static final int W = 196;
    private static final int ROW = 15;
    private static final int H = 262;

    /** Enclos en cours de réglage (lu par le rendu du contour). */
    @Nullable
    private static Pen editing;
    @Nullable
    private static BlockPos editingTerminal;

    private final Screen parent;
    private final BlockPos terminal;
    private boolean enabled;
    // taille, position du coin par rapport au terminal, spawn par rapport au coin
    private int sx, sy, sz, ox, oy, oz, px, py, pz;
    private int left;
    private int top;
    private int panelH = 270;

    public PenScreen(Screen parent, BlockPos terminal, @Nullable Pen pen) {
        super(Component.translatable("gui.dirtrobes.pen.title"));
        this.parent = parent;
        this.terminal = terminal;
        load(pen);
    }

    private void load(@Nullable Pen pen) {
        enabled = pen != null;
        Pen p = pen != null ? pen : Pen.defaultFor(terminal);
        sx = p.sizeX();
        sy = p.sizeY();
        sz = p.sizeZ();
        ox = p.min().getX() - terminal.getX();
        oy = p.min().getY() - terminal.getY();
        oz = p.min().getZ() - terminal.getZ();
        px = p.spawn().getX() - p.min().getX();
        py = p.spawn().getY() - p.min().getY();
        pz = p.spawn().getZ() - p.min().getZ();
        updatePreview();
    }

    /** Le serveur a validé (et peut-être corrigé) l'enclos. */
    public void onServerPen(@Nullable Pen pen) {
        load(pen);
        rebuildWidgets();
    }

    private Pen current() {
        return Pen.fromRelative(terminal, ox, oy, oz, sx, sy, sz, px, py, pz);
    }

    private void updatePreview() {
        editing = enabled ? current() : null;
        editingTerminal = terminal;
    }

    @Nullable
    public static Pen editingPen() {
        return editing;
    }

    private void send() {
        Pen p = current();
        // on renvoie les valeurs corrigées (taille et spawn ramenés dans les limites)
        sx = p.sizeX();
        sy = p.sizeY();
        sz = p.sizeZ();
        px = p.spawn().getX() - p.min().getX();
        py = p.spawn().getY() - p.min().getY();
        pz = p.spawn().getZ() - p.min().getZ();
        updatePreview();
        PacketDistributor.sendToServer(new LabNetwork.PenEditPayload(terminal, enabled, ox, oy, oz, sx, sy, sz, px, py, pz));
    }

    @Override
    protected void init() {
        left = 8;
        top = Math.max(4, (height - 300) / 2);
        int y = top + 32;
        addRenderableWidget(Button.builder(Component.translatable(enabled ? "gui.dirtrobes.pen.disable" : "gui.dirtrobes.pen.enable"), b -> {
            enabled = !enabled;
            send();
            rebuildWidgets();
        }).bounds(left + 8, y, W - 16, 18).build());
        y += 26;
        y = section(y, "gui.dirtrobes.pen.size");
        y = row(y, "X", () -> sx, v -> sx = v);
        y = row(y, "Y", () -> sy, v -> sy = v);
        y = row(y, "Z", () -> sz, v -> sz = v);
        y = section(y + 2, "gui.dirtrobes.pen.offset");
        y = row(y, "X", () -> ox, v -> ox = v);
        y = row(y, "Y", () -> oy, v -> oy = v);
        y = row(y, "Z", () -> oz, v -> oz = v);
        y = section(y + 2, "gui.dirtrobes.pen.spawn");
        y = row(y, "X", () -> px, v -> px = v);
        y = row(y, "Y", () -> py, v -> py = v);
        y = row(y, "Z", () -> pz, v -> pz = v);
        addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.pen.spawn_here"), b -> spawnHere())
                .bounds(left + 8, y + 2, W - 16, 18).build()).active = enabled;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 8, y + 22, W - 16, 18).build());
        panelH = y + 26 + 18 + 8 - top;
    }

    private int section(int y, String key) {
        int yy = y;
        addRenderableOnly((g, mx, my, pt) -> g.drawString(font, Component.translatable(key), left + 8, yy + 2, 0xFF8FA6B8, false));
        return y + 11;
    }

    private interface Getter {
        int get();
    }

    private interface Setter {
        void set(int v);
    }

    private int row(int y, String axis, Getter get, Setter set) {
        int bx = left + W - 8 - 20;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            set.set(get.get() - (hasShiftDown() ? 5 : 1));
            send();
        }).bounds(left + 70, y, 20, 13).build()).active = enabled;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.set(get.get() + (hasShiftDown() ? 5 : 1));
            send();
        }).bounds(bx, y, 20, 13).build()).active = enabled;
        int yy = y;
        addRenderableOnly((g, mx, my, pt) -> {
            g.drawString(font, axis, left + 20, yy + 3, 0xFFD8E2EC, false);
            String v = String.valueOf(get.get());
            g.drawCenteredString(font, v, (left + 90 + bx) / 2, yy + 3, enabled ? 0xFFFFFFFF : 0xFF6F8294);
        });
        return y + ROW;
    }

    /** Met le point de spawn là où se trouve le joueur, s'il est dans l'enclos. */
    private void spawnHere() {
        if (minecraft == null || minecraft.player == null) return;
        Pen p = current();
        BlockPos at = minecraft.player.blockPosition();
        if (!p.containsBlock(at)) {
            minecraft.player.displayClientMessage(Component.translatable("gui.dirtrobes.pen.not_inside"), true);
            return;
        }
        px = at.getX() - p.min().getX();
        py = at.getY() - p.min().getY();
        pz = at.getZ() - p.min().getZ();
        send();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // pas de flou : on veut voir l'enclos dans le monde
        GuiDraw.panel(g, left, top, W, panelH, 0xFF7FE08A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 8, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("gui.dirtrobes.pen.hint"), left + 8, top + 19, 0xFF6F8294, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ se déplacer pendant le réglage

    /** Touches de déplacement transmises au jeu pendant que l'écran est ouvert (avancer, reculer, côtés, sauter, s'accroupir, sprinter). */
    private boolean movementKey(int keyCode, int scanCode, boolean down) {
        if (minecraft == null) return false;
        var o = minecraft.options;
        for (net.minecraft.client.KeyMapping k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight,
                o.keyJump, o.keyShift, o.keySprint}) {
            if (k.matches(keyCode, scanCode)) {
                k.setDown(down);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && movementKey(keyCode, scanCode, true)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (movementKey(keyCode, scanCode, false)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        editing = null;
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        editing = null;
        net.minecraft.client.KeyMapping.releaseAll();
        super.removed();
    }
}
