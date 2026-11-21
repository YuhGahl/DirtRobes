package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Trouve le gantelet du joueur : d'abord dans l'emplacement Curios, sinon dans les mains ou l'inventaire. */
public final class GauntletAccess {
    private GauntletAccess() {}

    public static ItemStack find(Player player) {
        if (ModList.get().isLoaded("curios")) {
            ItemStack curio = CuriosCompat.findGauntlet(player);
            if (!curio.isEmpty()) return curio;
        }
        for (ItemStack s : player.getInventory().items) if (s.is(ModRegistry.GAUNTLET.get())) return s;
        for (ItemStack s : player.getInventory().offhand) if (s.is(ModRegistry.GAUNTLET.get())) return s;
        return ItemStack.EMPTY;
    }
}
