package com.dirtrobes.client.hud;

import com.dirtrobes.client.ClientTeam;
import com.dirtrobes.client.screen.GuiDraw;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.species.Property;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Bandeau d'équipe (à droite par défaut, déplaçable avec la touche J) : une ligne par Spectrobe, avec son modèle en miniature,
 * son niveau et ses PV. Le Spectrobe sélectionné (flèches ↑/↓) est encadré ; ceux qui sont dehors sont éclairés.
 */
public final class TeamHud {
    private TeamHud() {}

    private static final int ROW_W = 92;
    private static final int ROW_H = 26;

    /** Rectangle occupé par le bandeau : {x, y, largeur, hauteur}. */
    public static int[] bounds(int screenW, int screenH) {
        int rows = Math.max(1, ClientTeam.members().isEmpty() ? 3 : ClientTeam.members().size());
        int h = rows * (ROW_H + 2) + 10;
        int x = Math.round((screenW - ROW_W - 8) * HudLayout.teamX()) + 4;
        int y = Math.round((screenH - h - 8) * HudLayout.teamY()) + 4;
        return new int[]{x, y, ROW_W, h};
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (ClientTeam.members().isEmpty()) return;
        draw(g, false);
    }

    /** Dessine le bandeau à sa position ; en mode « placement », montre des emplacements vides si l'équipe est vide. */
    public static void draw(GuiGraphics g, boolean placing) {
        Minecraft mc = Minecraft.getInstance();
        List<TeamSummary> members = ClientTeam.members();
        Font font = mc.font;
        int[] box = bounds(g.guiWidth(), g.guiHeight());
        int x = box[0];
        int y = box[1];
        if (members.isEmpty() && placing) {
            for (int i = 0; i < 3; i++) {
                int ry = y + i * (ROW_H + 2);
                g.fill(x, ry, x + ROW_W, ry + ROW_H, 0x90141A22);
                g.drawString(font, Component.translatable("gui.dirtrobes.prizmod.empty"), x + 8, ry + 9, 0xFF6F8294, false);
            }
            return;
        }
        int selected = ClientTeam.selected();
        for (int i = 0; i < members.size(); i++) {
            TeamSummary m = members.get(i);
            int ry = y + i * (ROW_H + 2);
            int accent = 0xFF000000 | Property.byOrdinal(m.property()).color();
            boolean sel = i == selected;
            g.fill(x, ry, x + ROW_W, ry + ROW_H, m.out() ? 0xC0223040 : 0x90141A22);
            g.fill(x, ry, x + 3, ry + ROW_H, m.out() ? accent : GuiDraw.withAlpha(accent, 0x60));
            if (sel) g.renderOutline(x - 1, ry - 1, ROW_W + 2, ROW_H + 2, 0xFFFFFFFF);

            SpectrobeEntity preview = ClientTeam.preview(m.speciesId(), m.property(), m.stage(), m.color());
            if (preview != null) {
                g.enableScissor(x + 3, ry + 1, x + 27, ry + ROW_H - 1);
                GuiDraw.entity(g, preview, x + 15, ry + ROW_H - 3, m.stage() == 0 ? 14f : 10f, 30f, -5f);
                g.disableScissor();
                if (!m.out()) g.fill(x + 3, ry, x + 27, ry + ROW_H, 0x66000000);
            }
            Component name = Component.translatable(m.nameKey());
            String text = font.plainSubstrByWidth(name.getString(), ROW_W - 34);
            g.drawString(font, text, x + 30, ry + 3, m.out() ? 0xFFFFFFFF : 0xFFAAB6C2, true);
            g.drawString(font, Component.translatable("message.dirtrobes.card.level", m.level()), x + 30, ry + 13, 0xFF8FA6B8, false);
            float frac = m.maxHealth() <= 0 ? 0 : m.health() / m.maxHealth();
            GuiDraw.bar(g, x + 30, ry + ROW_H - 4, ROW_W - 34, 2, frac, GuiDraw.hpColor(frac, m.knockedOut()), 0xFF0D1117);
            if (m.knockedOut()) g.drawString(font, "K.O.", x + ROW_W - 22, ry + 13, 0xFFFF8A7A, true);
        }

    }
}
