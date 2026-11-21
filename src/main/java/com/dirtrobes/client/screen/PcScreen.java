package com.dirtrobes.client.screen;

import com.dirtrobes.client.ClientTeam;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.lab.LabNetwork;
import com.dirtrobes.lab.LabTerminalBlockEntity;
import com.dirtrobes.species.Nature;
import com.dirtrobes.species.Property;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Écran du PC du laboratoire : l'équipe à gauche (6 cases), une boîte de 30 cases au centre, et à droite
 * les détails du Spectrobe survolé. Clic sur un Spectrobe pour le prendre, clic sur une case pour le poser
 * (ou l'échanger). Tout ce qui est rangé dans les boîtes se promène dans l'enclos, s'il y en a un.
 */
public class PcScreen extends Screen {
    private static final int CELL = 30;
    private static final int COLS = 6;
    private static final int ROWS = 5;
    private static final int W = 360;
    private static final int H = 206;

    @Nullable
    private static LabNetwork.PcSyncPayload data;

    private int left;
    private int top;
    /** Spectrobe tenu : zone (0 équipe / 1 boîte) et index, ou -1. */
    private int heldArea = -1;
    private int heldIndex = -1;

    public PcScreen() {
        super(Component.translatable("gui.dirtrobes.pc.title"));
    }

    public static void onSync(LabNetwork.PcSyncPayload payload) {
        data = payload;
        ClientTeam.onSync(payload.team(), false, ClientTeam.support());
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PenScreen pen) {
            pen.onServerPen(payload.pen());
        } else if (mc.screen instanceof PcScreen screen) {
            screen.rebuildWidgets();
        } else if (payload.open()) {
            mc.setScreen(new PcScreen());
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (data == null) return;
        int gx = gridX();
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> changeBox(-1)).bounds(gx, top + 6, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> changeBox(1)).bounds(gx + COLS * CELL - 18, top + 6, 18, 14).build());
        if (data.kind() == LabNetwork.KIND_TERMINAL) {
            addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.pc.pen_button"), b -> {
                if (minecraft != null && data != null) minecraft.setScreen(new PenScreen(this, data.pos(), data.pen()));
            }).bounds(left + W - 78, top + 6, 70, 14).build());
        }
    }

    private void changeBox(int delta) {
        if (data == null) return;
        heldArea = -1;
        PacketDistributor.sendToServer(new LabNetwork.PcBoxPayload(data.kind(), data.pos(), data.box() + delta));
    }

    private int teamX() {
        return left + 8;
    }

    private int gridX() {
        return left + 8 + 2 * CELL + 14;
    }

    private int gridY() {
        return top + 26;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ cases

    @Nullable
    private TeamSummary at(int area, int index) {
        if (data == null) return null;
        List<TeamSummary> list = area == LabTerminalBlockEntity.AREA_TEAM ? data.team() : data.boxSlots();
        int i = area == LabTerminalBlockEntity.AREA_TEAM ? index : index - data.box() * LabTerminalBlockEntity.BOX_SIZE;
        if (i < 0 || i >= list.size()) return null;
        TeamSummary s = list.get(i);
        return s.speciesId().isEmpty() ? null : s;
    }

    /** {zone, index} sous la souris, ou null. */
    @Nullable
    private int[] cellAt(double mx, double my) {
        if (data == null) return null;
        int tx = teamX(), ty = gridY();
        for (int i = 0; i < PrizmodTeam.MAX; i++) {
            int x = tx + (i % 2) * CELL, y = ty + (i / 2) * CELL;
            if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2) return new int[]{LabTerminalBlockEntity.AREA_TEAM, i};
        }
        int gx = gridX(), gy = gridY();
        if (mx >= gx && my >= gy && mx < gx + COLS * CELL && my < gy + ROWS * CELL) {
            int c = (int) ((mx - gx) / CELL), r = (int) ((my - gy) / CELL);
            return new int[]{LabTerminalBlockEntity.AREA_BOX, data.box() * LabTerminalBlockEntity.BOX_SIZE + r * COLS + c};
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int[] cell = cellAt(mx, my);
        if (cell == null || data == null) {
            heldArea = -1;
            return false;
        }
        if (heldArea < 0) {
            if (at(cell[0], cell[1]) != null) {
                heldArea = cell[0];
                heldIndex = cell[1];
            }
        } else {
            PacketDistributor.sendToServer(new LabNetwork.PcMovePayload(data.kind(), data.pos(), heldArea, heldIndex, cell[0], cell[1], data.box()));
            heldArea = -1;
        }
        return true;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiDraw.panel(g, left, top, W, H, 0xFF5FD3F3);
        if (data == null) return;
        // colonne équipe
        int tx = teamX(), ty = gridY();
        g.fill(tx - 3, ty - 3, tx + 2 * CELL + 1, ty + 3 * CELL + 1, 0xFF0F151C);
        // grille de la boîte
        int gx = gridX(), gy = gridY();
        g.fill(gx - 3, gy - 3, gx + COLS * CELL + 1, gy + ROWS * CELL + 1, 0xFF0F151C);
        for (int i = 0; i < PrizmodTeam.MAX; i++) cellBackground(g, tx + (i % 2) * CELL, ty + (i / 2) * CELL, LabTerminalBlockEntity.AREA_TEAM, i);
        for (int i = 0; i < LabTerminalBlockEntity.BOX_SIZE; i++) {
            cellBackground(g, gx + (i % COLS) * CELL, gy + (i / COLS) * CELL, LabTerminalBlockEntity.AREA_BOX,
                    data.box() * LabTerminalBlockEntity.BOX_SIZE + i);
        }
    }

    private void cellBackground(GuiGraphics g, int x, int y, int area, int index) {
        TeamSummary m = at(area, index);
        boolean held = area == heldArea && index == heldIndex;
        int base = m == null ? 0xFF1C2530 : 0xFF243241;
        g.fill(x, y, x + CELL - 2, y + CELL - 2, base);
        if (m != null) {
            int accent = 0xFF000000 | Property.byOrdinal(m.property()).color();
            g.fill(x, y + CELL - 4, x + CELL - 2, y + CELL - 2, accent);
        }
        if (held) g.renderOutline(x - 1, y - 1, CELL, CELL, 0xFFFFE066);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (data == null) return;
        g.drawString(font, Component.translatable(data.kind() == LabNetwork.KIND_PC ? "gui.dirtrobes.pc.title_pc" : "gui.dirtrobes.pc.title"),
                left + 8, top + 8, 0xFFFFFFFF, true);
        g.drawCenteredString(font, Component.translatable("gui.dirtrobes.pc.box", data.box() + 1, data.boxes()),
                gridX() + COLS * CELL / 2, top + 9, 0xFFD8E2EC);
        g.drawString(font, Component.translatable("gui.dirtrobes.pc.team"), teamX(), gridY() + 3 * CELL + 6, 0xFF8FA6B8, false);

        int tx = teamX(), ty = gridY();
        for (int i = 0; i < PrizmodTeam.MAX; i++) cellModel(g, tx + (i % 2) * CELL, ty + (i / 2) * CELL, LabTerminalBlockEntity.AREA_TEAM, i);
        int gx = gridX(), gy = gridY();
        for (int i = 0; i < LabTerminalBlockEntity.BOX_SIZE; i++) {
            cellModel(g, gx + (i % COLS) * CELL, gy + (i / COLS) * CELL, LabTerminalBlockEntity.AREA_BOX,
                    data.box() * LabTerminalBlockEntity.BOX_SIZE + i);
        }
        // survol de la souris
        int[] cell = cellAt(mouseX, mouseY);
        if (cell != null) {
            int x, y;
            if (cell[0] == LabTerminalBlockEntity.AREA_TEAM) {
                x = tx + (cell[1] % 2) * CELL;
                y = ty + (cell[1] / 2) * CELL;
            } else {
                int i = cell[1] - data.box() * LabTerminalBlockEntity.BOX_SIZE;
                x = gx + (i % COLS) * CELL;
                y = gy + (i / COLS) * CELL;
            }
            g.renderOutline(x - 1, y - 1, CELL, CELL, heldArea >= 0 ? 0xFF7FE08A : 0xAAFFFFFF);
        }
        renderDetails(g, cell != null ? at(cell[0], cell[1]) : heldArea >= 0 ? at(heldArea, heldIndex) : null);

        // bas de l'écran : état de l'enclos et aide
        Component pen = data.kind() == LabNetwork.KIND_PC ? Component.translatable("gui.dirtrobes.pc.pc_info", data.stored())
                : data.hasPen() ? Component.translatable("gui.dirtrobes.pc.pen_on", data.stored())
                : Component.translatable("gui.dirtrobes.pc.pen_off");
        if (minecraft != null && minecraft.screen instanceof PenScreen) return;
        g.drawString(font, pen, gridX(), top + H - 26, data.kind() == LabNetwork.KIND_PC || data.hasPen() ? 0xFF7FE08A : 0xFFE8C84A, false);
        g.drawString(font, Component.translatable(heldArea >= 0 ? "gui.dirtrobes.pc.hint_place" : "gui.dirtrobes.pc.hint_pick"),
                gridX(), top + H - 14, 0xFF8FA6B8, false);
    }

    private void cellModel(GuiGraphics g, int x, int y, int area, int index) {
        TeamSummary m = at(area, index);
        if (m == null) return;
        SpectrobeEntity preview = ClientTeam.preview(m.speciesId(), m.property(), m.stage(), m.color());
        if (preview != null) {
            g.enableScissor(x, y, x + CELL - 2, y + CELL - 4);
            GuiDraw.entity(g, preview, x + (CELL - 2) / 2f, y + CELL - 6, m.stage() == 0 ? 15f : 11f, 30f, -5f);
            g.disableScissor();
        }
        if (area == heldArea && index == heldIndex) g.fill(x, y, x + CELL - 2, y + CELL - 2, 0x55FFE066);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawString(font, String.valueOf(m.level()), x + 1, y + 1, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private void renderDetails(GuiGraphics g, @Nullable TeamSummary m) {
        int x = gridX() + COLS * CELL + 12, y = gridY();
        int w = left + W - 8 - x;
        g.fill(x - 4, y - 3, x + w, y + ROWS * CELL + 1, 0xFF0F151C);
        if (m == null) {
            g.drawString(font, Component.translatable("gui.dirtrobes.pc.details_empty"), x, y + 4, 0xFF5C6B7C, false);
            return;
        }
        Property p = Property.byOrdinal(m.property());
        SpectrobeEntity preview = ClientTeam.preview(m.speciesId(), m.property(), m.stage(), m.color());
        if (preview != null) {
            g.enableScissor(x - 4, y, x + w, y + 64);
            GuiDraw.entity(g, preview, x + w / 2f - 2, y + 60, m.stage() == 0 ? 34f : 26f,
                    (Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount) * 2f, -6f);
            g.disableScissor();
        }
        int ty = y + 68;
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(m.nameKey()).getString(), w), x, ty, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("message.dirtrobes.card.level", m.level()), x, ty + 11, 0xFFD8E2EC, false);
        g.drawString(font, Component.translatable(p.translationKey()).withStyle(s -> s.withColor(p.color())), x, ty + 22, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable(Nature.values()[m.nature()].translationKey()).withStyle(ChatFormatting.GRAY), x, ty + 33, 0xFFFFFFFF, false);
        float frac = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0f, 1f);
        GuiDraw.bar(g, x, ty + 46, w - 4, 4, frac, GuiDraw.hpColor(frac, m.knockedOut()), 0xFF1C2530);
        g.drawString(font, Component.literal("PV " + m.hp() + " · ATQ " + m.atk()), x, ty + 54, 0xFFB8C4D0, false);
        g.drawString(font, Component.literal("DÉF " + m.def() + " · VIT " + m.spd()), x, ty + 64, 0xFFB8C4D0, false);
    }

    @Override
    public void removed() {
        heldArea = -1;
        super.removed();
    }

    @Nullable
    public static LabNetwork.PcSyncPayload data() {
        return data;
    }

    @Nullable
    public static BlockPos openPos() {
        return data == null ? null : data.pos();
    }
}
