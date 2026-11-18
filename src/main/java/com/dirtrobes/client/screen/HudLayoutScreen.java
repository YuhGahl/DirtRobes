package com.dirtrobes.client.screen;

import com.dirtrobes.client.hud.HudLayout;
import com.dirtrobes.client.hud.TeamHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Écran pour déplacer le bandeau d'équipe à la souris (enregistré à la fermeture). */
public class HudLayoutScreen extends Screen {
    private boolean dragging;
    private double grabX;
    private double grabY;

    public HudLayoutScreen() {
        super(Component.translatable("gui.dirtrobes.hud_layout.title"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.hud_layout.reset"), b -> HudLayout.reset())
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.dirtrobes.hud_layout.hint"), width / 2, 24, 0xFFB8C4D0);
        int[] box = TeamHud.bounds(width, height);
        TeamHud.draw(g, true);
        g.renderOutline(box[0] - 3, box[1] - 3, box[2] + 6, box[3] + 6, dragging ? 0xFFFFE066 : 0xFF5FD3F3);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int[] box = TeamHud.bounds(width, height);
        if (button == 0 && mx >= box[0] - 3 && mx < box[0] + box[2] + 3 && my >= box[1] - 3 && my < box[1] + box[3] + 3) {
            dragging = true;
            grabX = mx - box[0];
            grabY = my - box[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mx, my, button, dx, dy);
        int[] box = TeamHud.bounds(width, height);
        float fx = (float) ((mx - grabX) / Math.max(1, width - box[2]));
        float fy = (float) ((my - grabY) / Math.max(1, height - box[3]));
        HudLayout.setTeam(fx, fy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void onClose() {
        HudLayout.save();
        super.onClose();
    }
}
