package com.dirtrobes.client.screen;

import com.dirtrobes.item.FossilItem;
import com.dirtrobes.menu.IncubatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Écran de l'incubateur, dessiné sans texture (rectangles), dans l'esprit d'un appareil de labo. */
public class IncubatorScreen extends AbstractContainerScreen<IncubatorMenu> {
    private Button digButton;

    public IncubatorScreen(IncubatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        digButton = addRenderableWidget(Button.builder(Component.translatable("gui.dirtrobes.incubator.dig"), b -> startExcavation())
                .bounds(leftPos + 104, topPos + 29, 64, 20).build());
        updateButton();
    }

    private void startExcavation() {
        ItemStack stack = menu.fossil();
        if (minecraft == null || !(stack.getItem() instanceof FossilItem fossil) || menu.isIncubating()) return;
        minecraft.setScreen(new ExcavationScreen(menu.pos(), fossil.property(), FossilItem.rarity(stack), stack.getHoverName()));
    }

    private void updateButton() {
        if (digButton != null) digButton.active = !menu.isIncubating() && menu.fossil().getItem() instanceof FossilItem;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // Cadre
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1E2A33);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFB9C4CC);
        g.fill(x + 4, y + 14, x + imageWidth - 4, y + 70, 0xFF8C9AA5);
        // Emplacements
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF37414A);
            g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }
        // Halo autour du fossile
        int fx = x + IncubatorMenu.FOSSIL_X - 3, fy = y + IncubatorMenu.FOSSIL_Y - 3;
        g.renderOutline(fx, fy, 22, 22, menu.isIncubating() ? 0xFF7FE0FF : 0xFF4E5C66);
        // Barre d'éveil
        int bx = x + 10, by = y + 60, bw = imageWidth - 20;
        g.fill(bx, by, bx + bw, by + 8, 0xFF2B343B);
        if (menu.isIncubating()) {
            int filled = Math.round((bw - 2) * menu.progress());
            g.fill(bx + 1, by + 1, bx + 1 + filled, by + 7, 0xFF5FD3F3);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Component status;
        if (menu.isIncubating()) {
            status = Component.translatable("gui.dirtrobes.incubator.awakening", Math.round(menu.progress() * 100),
                    com.dirtrobes.entity.StatCalc.ivForIntegrity(menu.integrity()));
        } else if (menu.fossil().isEmpty()) {
            status = Component.translatable("gui.dirtrobes.incubator.insert");
        } else {
            status = Component.translatable("gui.dirtrobes.incubator.ready");
        }
        g.drawString(font, status, 10, 51, 0xFF20303A, false);
    }
}
