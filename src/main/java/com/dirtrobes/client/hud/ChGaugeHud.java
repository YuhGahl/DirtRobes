package com.dirtrobes.client.hud;

import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.network.ModNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Jauge CH affichée à gauche de la barre d'objets, dessinée sans texture : 10 segments,
 * un repère au coût de l'attaque spéciale, et un clignotement quand elle est prête.
 * Visible seulement si le joueur a un Spectrobe à proximité ou une jauge non vide.
 */
public final class ChGaugeHud {
    private ChGaugeHud() {}

    private static float shown;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        int ch = ModNetwork.clientCh;
        boolean hasSpectrobe = !mc.player.level().getEntitiesOfClass(SpectrobeEntity.class, mc.player.getBoundingBox().inflate(32),
                s -> s.isOwnedBy(mc.player)).isEmpty();
        if (ch <= 0 && !hasSpectrobe) return;

        shown = Mth.lerp(0.25f, shown, ch);
        int segments = 10;
        int segW = 7, segH = 5, gap = 1;
        int w = segments * (segW + gap) - gap;
        int x = g.guiWidth() / 2 - 91 - w - 26;
        int y = g.guiHeight() - 16;

        g.fill(x - 2, y - 2, x + w + 2, y + segH + 2, 0xAA000000);
        boolean ready = ch >= ChGauge.SPECIAL_COST;
        float pulse = ready ? (Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true)) * 0.25f) + 1f) / 2f : 0f;
        for (int i = 0; i < segments; i++) {
            int sx = x + i * (segW + gap);
            float fill = Mth.clamp(shown / ChGauge.MAX * segments - i, 0f, 1f);
            g.fill(sx, y, sx + segW, y + segH, 0xFF2A2F3A);
            if (fill > 0) {
                int color = ready ? lerpColor(0xFF39C6F0, 0xFFB8F4FF, pulse) : 0xFF2D86B8;
                g.fill(sx, y, sx + Math.round(segW * fill), y + segH, color);
            }
        }
        // repère du coût de l'attaque spéciale
        int mark = x + Math.round(w * ChGauge.SPECIAL_COST / (float) ChGauge.MAX);
        g.fill(mark, y - 2, mark + 1, y + segH + 2, 0xFFFFD84A);
        g.drawString(mc.font, Component.literal("CH"), x - 14, y - 2, ready ? 0xFFB8F4FF : 0xFF8FA6B8, true);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bb = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }
}
