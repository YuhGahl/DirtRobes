package com.dirtrobes.client.screen;

import com.dirtrobes.client.ClientTeam;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.item.MineralItem;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.species.Nature;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Stage;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Fiche d'un Spectrobe : grand modèle 3D à gauche (on le fait tourner en le faisant glisser),
 * onglets à droite : Infos, Stats (radar à 4 branches + EV), Évolution.
 */
public class SpectrobeSummaryScreen extends Screen {
    private enum Tab { INFO, STATS, EVOLUTION }

    private static final int W = 320;
    private static final int H = 190;
    private static final int MODEL_W = 120;

    @Nullable
    private final Screen parent;
    private int slot;
    private Tab tab = Tab.INFO;
    private float yaw = 25f;
    private boolean dragging;
    private int left;
    private int top;

    public SpectrobeSummaryScreen(@Nullable Screen parent, int slot) {
        super(Component.translatable("gui.dirtrobes.summary.title"));
        this.parent = parent;
        this.slot = slot;
    }

    @Nullable
    private TeamSummary member() {
        return slot >= 0 && slot < ClientTeam.members().size() ? ClientTeam.members().get(slot) : null;
    }

    public void refresh() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int tx = left + MODEL_W + 8;
        int tw = (W - MODEL_W - 16) / 3;
        addTab(Tab.INFO, "gui.dirtrobes.summary.tab.info", tx, tw);
        addTab(Tab.STATS, "gui.dirtrobes.summary.tab.stats", tx + tw, tw);
        addTab(Tab.EVOLUTION, "gui.dirtrobes.summary.tab.evolution", tx + 2 * tw, tw);
        int n = ClientTeam.members().size();
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> switchTo(slot - 1)).bounds(left + 4, top + H - 22, 18, 18).build()).active = n > 1;
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> switchTo(slot + 1)).bounds(left + MODEL_W - 22, top + H - 22, 18, 18).build()).active = n > 1;
        TeamSummary m = member();
        if (m != null) {
            Button toggle = addRenderableWidget(Button.builder(Component.translatable(m.out() ? "gui.dirtrobes.prizmod.recall" : "gui.dirtrobes.prizmod.send"),
                    b -> PacketDistributor.sendToServer(new ModNetwork.TeamActionPayload(slot))).bounds(left + 26, top + H - 22, MODEL_W - 52, 18).build());
            long out = ClientTeam.members().stream().filter(TeamSummary::out).count();
            toggle.active = m.out() ? m.found() : out < PrizmodTeam.MAX_OUT;
        }
    }

    private void addTab(Tab t, String key, int x, int w) {
        Button b = addRenderableWidget(Button.builder(Component.translatable(key), btn -> {
            tab = t;
            rebuildWidgets();
        }).bounds(x, top + 6, w - 2, 16).build());
        b.active = tab != t;
    }

    private void switchTo(int newSlot) {
        int n = ClientTeam.members().size();
        if (n == 0) return;
        slot = Math.floorMod(newSlot, n);
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rotation du modèle

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && mx >= left && mx < left + MODEL_W && my >= top && my < top + H - 26) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 2.5f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void tick() {
        if (!dragging) yaw += 0.6f;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        TeamSummary m = member();
        int accent = m == null ? 0xFF5FD3F3 : 0xFF000000 | Property.byOrdinal(m.property()).color();
        GuiDraw.panel(g, left, top, W, H, accent);
        // Fond du modèle : dégradé de la couleur de la propriété
        int rgb = accent & 0xFFFFFF;
        g.fillGradient(left + 2, top + 2, left + MODEL_W, top + H - 26, GuiDraw.withAlpha(rgb, 0x70), GuiDraw.withAlpha(rgb, 0x10));
        g.fill(left + MODEL_W, top + 2, left + MODEL_W + 1, top + H - 2, GuiDraw.withAlpha(rgb, 0x90));
        // Socle sous le modèle
        int cx = left + MODEL_W / 2;
        g.fill(cx - 30, top + H - 44, cx + 30, top + H - 41, GuiDraw.withAlpha(rgb, 0x80));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        TeamSummary m = member();
        if (m == null) {
            g.drawCenteredString(font, Component.translatable("gui.dirtrobes.prizmod.empty"), width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        Property p = Property.byOrdinal(m.property());
        int accent = 0xFF000000 | p.color();

        // ---- Colonne de gauche : nom, niveau, modèle
        g.drawString(font, Component.translatable(m.nameKey()).withStyle(ChatFormatting.BOLD), left + 6, top + 6, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("message.dirtrobes.card.level", m.level()), left + 6, top + 17, 0xFFD8E2EC, false);
        com.dirtrobes.species.Rarity rarity = com.dirtrobes.species.Rarity.byOrdinal(m.rarity());
        if (rarity != com.dirtrobes.species.Rarity.COMMON) {
            Component rl = rarity.displayName();
            g.drawString(font, rl, left + MODEL_W - 6 - font.width(rl), top + 17, 0xFFFFFFFF, true);
        }
        SpectrobeEntity preview = ClientTeam.preview(m.speciesId(), m.property(), m.stage(), m.color());
        if (preview != null) {
            float scale = m.stage() == Stage.CHILD.ordinal() ? 52f : m.stage() == Stage.ADULT.ordinal() ? 42f : 34f;
            g.enableScissor(left + 2, top + 28, left + MODEL_W, top + H - 26);
            GuiDraw.entity(g, preview, left + MODEL_W / 2f, top + H - 44, scale, yaw, -8f);
            g.disableScissor();
        }
        // État
        Component state = !m.found() ? Component.translatable("gui.dirtrobes.prizmod.far")
                : m.knockedOut() ? Component.translatable("gui.dirtrobes.prizmod.ko")
                : Component.translatable(m.out() ? "gui.dirtrobes.prizmod.out" : "gui.dirtrobes.prizmod.stored");
        g.drawString(font, state, left + MODEL_W - 4 - font.width(state), top + 6, m.knockedOut() ? 0xFFFF8A7A : 0xFF7FE0FF, false);

        // ---- Colonne de droite
        int x = left + MODEL_W + 10, y = top + 28;
        switch (tab) {
            case INFO -> renderInfo(g, m, p, x, y);
            case STATS -> renderStats(g, m, accent, x, y);
            case EVOLUTION -> renderEvolution(g, m, x, y);
        }
    }

    private void row(GuiGraphics g, int x, int y, String labelKey, Component value) {
        g.drawString(font, Component.translatable(labelKey), x, y, 0xFF8FA6B8, false);
        int maxW = left + W - 6 - (x + 62);
        var lines = font.split(value, maxW);
        if (!lines.isEmpty()) g.drawString(font, lines.get(0), x + 62, y, 0xFFFFFFFF, false);
    }

    private void renderInfo(GuiGraphics g, TeamSummary m, Property p, int x, int y) {
        row(g, x, y, "gui.dirtrobes.summary.species", Component.translatable(m.nameKey()));
        row(g, x, y + 12, "gui.dirtrobes.summary.property", Component.translatable(p.translationKey()).withStyle(s -> s.withColor(p.color())));
        row(g, x, y + 24, "gui.dirtrobes.summary.stage", Component.translatable(Stage.byOrdinal(m.stage()).translationKey()));
        row(g, x, y + 36, "gui.dirtrobes.summary.nature", natureText(Nature.values()[m.nature()]));
        row(g, x, y + 48, "gui.dirtrobes.summary.quality", Component.literal(m.quality() + " %"));
        // PV
        float hpFrac = m.maxHealth() <= 0 ? 0 : m.health() / m.maxHealth();
        g.drawString(font, Component.translatable("stat.dirtrobes.health"), x, y + 64, 0xFF8FA6B8, false);
        GuiDraw.bar(g, x + 62, y + 65, 100, 6, hpFrac, GuiDraw.hpColor(hpFrac, m.knockedOut()), 0xFF0D1117);
        g.drawString(font, Component.literal(Mth.ceil(hpFrac * m.hp()) + " / " + m.hp()), x + 62, y + 74, 0xFFB8C4D0, false);
        // XP
        int span = Math.max(1, m.xpNextLevel() - m.xpLevelStart());
        float xpFrac = m.level() >= StatCalc.MAX_LEVEL ? 1f : (m.xp() - m.xpLevelStart()) / (float) span;
        g.drawString(font, Component.literal("XP"), x, y + 92, 0xFF8FA6B8, false);
        GuiDraw.bar(g, x + 62, y + 93, 100, 6, xpFrac, 0xFF5FB4F3, 0xFF0D1117);
        g.drawString(font, Component.translatable("gui.dirtrobes.summary.xp_next", Math.max(0, m.xpNextLevel() - m.xp())), x + 62, y + 102, 0xFFB8C4D0, false);
    }

    private static String pct(float f) {
        return "+" + Math.round(f * 100) + "%";
    }

    private MutableComponent natureText(Nature n) {
        MutableComponent c = Component.translatable(n.translationKey());
        if (n.raised == null) return c.append(Component.literal(" (")).append(Component.translatable("gui.dirtrobes.summary.neutral")).append(")");
        return c.append(Component.literal(" ▲")).append(Component.translatable("stat.dirtrobes." + n.raised.id())).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" ▼").append(Component.translatable("stat.dirtrobes." + n.lowered.id())).withStyle(ChatFormatting.GRAY));
    }

    private void renderStats(GuiGraphics g, TeamSummary m, int accent, int x, int y) {
        Nature n = Nature.values()[m.nature()];
        // ordre des branches : Nord, Est, Sud, Ouest
        int[] stats = {m.hp(), m.atk(), m.spd(), m.def()};
        int[] evs = {m.evHp(), m.evAtk(), m.evSpd(), m.evDef()};
        MineralItem.Stat[] order = {MineralItem.Stat.HEALTH, MineralItem.Stat.POWER, MineralItem.Stat.SPEED, MineralItem.Stat.DEFENCE};
        int areaW = W - MODEL_W - 20;
        int max = 1;
        for (int st : stats) max = Math.max(max, st);
        float ref = max * 1.15f;
        float[] values = new float[4];
        float[] evValues = new float[4];
        for (int i = 0; i < 4; i++) {
            values[i] = stats[i] / ref;
            evValues[i] = evs[i] / (float) StatCalc.MAX_EV_PER_STAT;
        }
        // Radar centré : PV au nord, ATQ à l'est, VIT au sud, DÉF à l'ouest
        float cx = x + areaW / 2f, cy = y + 40, r = 28;
        GuiDraw.radarGrid(g, cx, cy, r, 4, 0x553A4A5C);
        GuiDraw.radar(g, cx, cy, r, values, GuiDraw.withAlpha(accent, 0x90), accent);
        GuiDraw.radar(g, cx, cy, r, evValues, 0x40FFFFFF, 0xC0FFFFFF);
        for (int i = 0; i < 4; i++) {
            MineralItem.Stat st = order[i];
            int color = st == n.raised ? 0xFFFF8A7A : st == n.lowered ? 0xFF7FB2FF : 0xFFD8E2EC;
            Component lbl = Component.translatable("stat.dirtrobes." + st.id()).append(" " + stats[i]);
            int w = font.width(lbl);
            int lx, ly;
            switch (i) {
                case 0 -> { lx = Math.round(cx - w / 2f); ly = Math.round(cy - r - 11); }
                case 1 -> { lx = Math.round(cx + r + 5); ly = Math.round(cy - 4); }
                case 2 -> { lx = Math.round(cx - w / 2f); ly = Math.round(cy + r + 3); }
                default -> { lx = Math.round(cx - r - 5 - w); ly = Math.round(cy - 4); }
            }
            g.drawString(font, lbl, lx, ly, color, true);
        }
        // EV : 2 colonnes × 2 lignes
        int ey = y + 84;
        g.drawString(font, Component.translatable("gui.dirtrobes.summary.ev", m.totalEv(), StatCalc.MAX_EV_TOTAL), x, ey, 0xFF8FA6B8, false);
        int colW = (areaW - 8) / 2;
        for (int i = 0; i < 4; i++) {
            int ex = x + (i % 2) * (colW + 8), yy = ey + 11 + (i / 2) * 17;
            g.drawString(font, Component.translatable("stat.dirtrobes." + order[i].id()).append(" " + evs[i]), ex, yy, 0xFFD8E2EC, false);
            GuiDraw.bar(g, ex, yy + 10, colW, 3, evs[i] / (float) StatCalc.MAX_EV_PER_STAT, 0xFFE8E8F0, 0xFF0D1117);
        }
        // Soutien (sur 2 lignes au maximum, dans le cadre)
        com.dirtrobes.combat.Support sup = ClientTeam.support();
        Component supText = !m.out() ? Component.translatable("gui.dirtrobes.summary.support_giver")
                : sup.isEmpty() ? Component.translatable("gui.dirtrobes.summary.support_none")
                : Component.translatable("gui.dirtrobes.summary.support", pct(sup.atk()), pct(sup.def()), pct(sup.spd()), pct(sup.hp()), pct(sup.ch()));
        int sy = ey + 48;
        for (var line : font.split(supText, areaW)) {
            if (sy > top + H - 12) break;
            g.drawString(font, line, x, sy, 0xFFB58CFF, false);
            sy += 10;
        }
    }

    private void renderEvolution(GuiGraphics g, TeamSummary m, int x, int y) {
        if (m.evoSpeciesId().isEmpty()) {
            g.drawString(font, Component.translatable("gui.dirtrobes.summary.final_form"), x, y + 4, 0xFFD8E2EC, false);
            return;
        }
        Component next = Component.translatable(m.evoNameKey()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, Component.translatable("gui.dirtrobes.summary.evolves_into", next), x, y + 4, 0xFFFFFFFF, false);
        boolean ready = m.level() >= m.evoLevel();
        g.drawString(font, Component.translatable("gui.dirtrobes.summary.evolves_at", m.evoLevel()), x, y + 16,
                ready ? 0xFF7FE08A : 0xFFB8C4D0, false);
        float frac = Mth.clamp(m.level() / (float) Math.max(1, m.evoLevel()), 0f, 1f);
        GuiDraw.bar(g, x, y + 28, 170, 5, frac, 0xFFB58CFF, 0xFF0D1117);
        int nextStage = Math.min(m.stage() + 1, Stage.values().length - 1);
        SpectrobeEntity preview = ClientTeam.preview(m.evoSpeciesId(), m.property(), nextStage, m.color());
        if (preview != null) {
            int bx = x + 40, by = y + 132;
            g.fill(bx - 34, by - 2, bx + 34, by + 1, 0x60FFFFFF);
            g.enableScissor(x, y + 36, x + 180, y + 136);
            // silhouette tant que le niveau n'est pas atteint : modèle assombri par-dessus
            GuiDraw.entity(g, preview, bx, by, 30f, -yaw, -6f);
            g.disableScissor();
            if (!ready) g.fill(x, y + 36, x + 82, y + 134, 0x99000000);
            g.drawString(font, Component.translatable(ready ? "gui.dirtrobes.summary.evo_ready" : "gui.dirtrobes.summary.evo_locked"),
                    x + 90, y + 80, ready ? 0xFF7FE08A : 0xFF8FA6B8, false);
        }
    }
}
