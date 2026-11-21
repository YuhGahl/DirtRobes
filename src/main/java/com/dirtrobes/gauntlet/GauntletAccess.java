package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Trouve le gantelet porté : avec Curios, uniquement dans son emplacement Curios (retiré = inactif) ;
 * sans Curios, dans l'inventaire.
 */
public final class GauntletAccess {
    private GauntletAccess() {}

    public static ItemStack find(Player player) {
        if (ModList.get().isLoaded("curios")) return CuriosCompat.findGauntlet(player);
        for (ItemStack s : player.getInventory().items) if (s.is(ModRegistry.GAUNTLET.get())) return s;
        for (ItemStack s : player.getInventory().offhand) if (s.is(ModRegistry.GAUNTLET.get())) return s;
        return ItemStack.EMPTY;
    }
}
