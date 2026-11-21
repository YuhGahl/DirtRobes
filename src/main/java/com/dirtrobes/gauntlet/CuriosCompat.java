package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Lien avec Curios (classe chargée seulement si Curios est installé). */
final class CuriosCompat {
    private CuriosCompat() {}

    static ItemStack findGauntlet(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(s -> s.is(ModRegistry.GAUNTLET.get())))
                .map(r -> r.stack())
                .orElse(ItemStack.EMPTY);
    }
}
