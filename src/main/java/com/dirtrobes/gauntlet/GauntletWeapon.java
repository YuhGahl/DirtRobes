package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * L'arme rangée dans le gantelet : une vraie épée (avec ses enchantements et son usure) ou une arme de module.
 * Quand elle est sortie, elle porte une marque « liée au gantelet » qui permet de la ramener si elle quitte la main.
 */
public final class GauntletWeapon {
    private GauntletWeapon() {}

    private static final String BOUND = "dirtrobes_gauntlet_bound";

    public static ItemStack get(ItemStack gauntlet) {
        return gauntlet.getOrDefault(ModRegistry.GAUNTLET_WEAPON.get(), ItemContainerContents.EMPTY).copyOne();
    }

    public static void set(ItemStack gauntlet, ItemStack weapon) {
        if (weapon.isEmpty()) gauntlet.remove(ModRegistry.GAUNTLET_WEAPON.get());
        else gauntlet.set(ModRegistry.GAUNTLET_WEAPON.get(), ItemContainerContents.fromItems(List.of(unmark(weapon.copyWithCount(1)))));
    }

    public static boolean isBound(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(BOUND);
    }

    public static ItemStack mark(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(BOUND, true));
        return stack;
    }

    public static ItemStack unmark(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            tag.remove(BOUND);
            if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }
}
