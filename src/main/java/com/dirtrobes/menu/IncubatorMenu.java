package com.dirtrobes.menu;

import com.dirtrobes.item.FossilItem;
import com.dirtrobes.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu de l'incubateur : un emplacement pour le fossile + l'inventaire du joueur. */
public class IncubatorMenu extends AbstractContainerMenu {
    public static final int FOSSIL_X = 80;
    public static final int FOSSIL_Y = 30;
    public static final int INV_Y = 84;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** Constructeur côté client (ouvert par le réseau). */
    public IncubatorMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, new SimpleContainer(1), new SimpleContainerData(3), buf.readBlockPos());
    }

    public IncubatorMenu(int id, Inventory playerInv, Container container, ContainerData data, BlockPos pos) {
        super(ModRegistry.INCUBATOR_MENU.get(), id);
        checkContainerSize(container, 1);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.pos = pos;
        addSlot(new Slot(container, 0, FOSSIL_X, FOSSIL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FossilItem;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack fossil() {
        return container.getItem(0);
    }

    public boolean isIncubating() {
        return data.get(1) > 0;
    }

    public float progress() {
        int duration = data.get(1);
        return duration <= 0 ? 0f : Math.min(1f, data.get(0) / (float) duration);
    }

    public int integrity() {
        return data.get(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FossilItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return com.dirtrobes.block.IncubatorBlockEntity.at(player.level(), pos) != null
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
