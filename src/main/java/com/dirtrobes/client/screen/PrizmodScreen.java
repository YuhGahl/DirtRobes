package com.dirtrobes.client.screen;

import com.dirtrobes.client.ClientTeam;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Stage;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Écran d'équipe du Prizmod : 6 emplacements, sortir / ranger chaque Spectrobe. Dessiné sans texture. */
public class PrizmodScreen extends Screen {
    private static final int CARD_W = 150;
    private static final int CARD_H = 44;
    private static final int GAP = 6;

    private final List<Button> buttons = new ArrayList<>();
    private int left;
    private int top;

    public PrizmodScreen() {
        super(Component.translatable("gui.dirtrobes.prizmod.title"));
    }

    public void refresh() {
        rebuildWidgets();
    }

    private static List<TeamSummary> members() {
        return ClientTeam.members();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < members().size(); i++) {
            int x = cardX(i), y = cardY(i);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H && minecraft != null) {
                minecraft.setScreen(new SpectrobeSummaryScreen(this, i));
                return true;
            }
        }
        return false;
    }

    @Override
    protected void init() {
        int w = CARD_W * 2 + GAP;
        int h = CARD_H * 3 + GAP * 2;
        left = (width - w) / 2;
        top = (height - h) / 2 + 6;
        buttons.clear();
        for (int i = 0; i < PrizmodTeam.MAX; i++) {
            if (i >= members().size()) continue;
            TeamSummary m = members().get(i);
            int x = cardX(i), y = cardY(i);
            final int slot = i;
            Component label = Component.translatable(m.out() ? "gui.dirtrobes.prizmod.recall" : "gui.dirtrobes.prizmod.send");
            Button b = addRenderableWidget(Button.builder(label, btn -> PacketDistributor.sendToServer(new ModNetwork.TeamActionPayload(slot)))
                    .bounds(x + CARD_W - 50, y + CARD_H - 18, 46, 14).build());
            b.active = m.out() ? m.found() : outCount() < PrizmodTeam.MAX_OUT;
            buttons.add(b);
        }
    }

    private int cardX(int i) {
        return left + (i % 2) * (CARD_W + GAP);
    }

    private int cardY(int i) {
        return top + (i / 2) * (CARD_H + GAP);
    }

    private static int outCount() {
        return (int) members().stream().filter(TeamSummary::out).count();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int w = CARD_W * 2 + GAP;
        int h = CARD_H * 3 + GAP * 2;
        g.fill(left - 8, top - 26, left + w + 8, top + h + 20, 0xF0141A22);
        g.renderOutline(left - 8, top - 26, w + 16, h + 46, 0xFF5FD3F3);
        for (int i = 0; i < PrizmodTeam.MAX; i++) {
            int x = cardX(i), y = cardY(i);
            if (i >= members().size()) {
                g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF1C232C);
                g.renderOutline(x, y, CARD_W, CARD_H, 0xFF2C3846);
                continue;
            }
            TeamSummary m = members().get(i);
            int color = 0xFF000000 | Property.byOrdinal(m.property()).color();
            g.fill(x, y, x + CARD_W, y + CARD_H, m.out() ? 0xFF243241 : 0xFF1E2731);
            g.fill(x, y, x + 4, y + CARD_H, color);
            g.renderOutline(x, y, CARD_W, CARD_H, m.out() ? color : 0xFF3A4757);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top - 20, 0xFFFFFFFF);
        for (int i = 0; i < PrizmodTeam.MAX; i++) {
            int x = cardX(i), y = cardY(i);
            if (i >= members().size()) {
                g.drawCenteredString(font, Component.translatable("gui.dirtrobes.prizmod.empty"), x + CARD_W / 2, y + CARD_H / 2 - 4, 0xFF5C6B7C);
                continue;
            }
            TeamSummary m = members().get(i);
            Property p = Property.byOrdinal(m.property());
            SpectrobeEntity preview = ClientTeam.preview(m.speciesId(), m.property(), m.stage());
            if (preview != null) {
                g.enableScissor(x + 5, y + 2, x + 37, y + CARD_H - 2);
                GuiDraw.entity(g, preview, x + 21, y + CARD_H - 6, m.stage() == 0 ? 20f : 15f, 30f, -5f);
                g.disableScissor();
            }
            g.drawString(font, Component.translatable(m.nameKey()), x + 40, y + 5, 0xFFFFFFFF, true);
            Component sub = Component.translatable("message.dirtrobes.card.level", m.level()).append(" · ")
                    .append(Component.translatable(p.translationKey())).append(" · ")
                    .append(Component.translatable(Stage.byOrdinal(m.stage()).translationKey()));
            g.drawString(font, Component.translatable("message.dirtrobes.card.level", m.level()), x + 40, y + 16, 0xFFB8C4D0, false);
            // Barre de PV
            int bx = x + 40, by = y + CARD_H - 12, bw = CARD_W - 96;
            float frac = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0f, 1f);
            g.fill(bx, by, bx + bw, by + 5, 0xFF0D1117);
            int hpColor = m.knockedOut() ? 0xFF7A7A7A : frac > 0.5f ? 0xFF5BD46A : frac > 0.2f ? 0xFFE8C84A : 0xFFE0533D;
            g.fill(bx, by, bx + Math.round(bw * frac), by + 5, hpColor);
            // État
            Component state = !m.found() ? Component.translatable("gui.dirtrobes.prizmod.far")
                    : m.knockedOut() ? Component.translatable("gui.dirtrobes.prizmod.ko")
                    : Component.translatable(m.out() ? "gui.dirtrobes.prizmod.out" : "gui.dirtrobes.prizmod.stored");
            int stateColor = !m.found() ? 0xFFE0533D : m.knockedOut() ? 0xFFFF8A7A : m.out() ? 0xFF7FE0FF : 0xFF8FA6B8;
            g.drawString(font, state, x + CARD_W - 4 - font.width(state), y + 5, stateColor, false);
        }
        g.drawCenteredString(font, Component.translatable("gui.dirtrobes.prizmod.hint", outCount(), PrizmodTeam.MAX_OUT),
                width / 2, top + CARD_H * 3 + GAP * 2 + 6, 0xFF8FA6B8);
    }
}
