package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Gantelet : se porte dans l'emplacement « gantelet » de Curios (ou reste dans l'inventaire sans Curios).
 * Le Prizmod (le prisme) s'y insère dans l'inventaire : prendre le Prizmod et cliquer sur le gantelet ;
 * clic droit sur le gantelet pour le ressortir. Ses modules s'ajoutent à la table de forge.
 * Touche B : sortir / ranger l'arme. Touche Z : extraire un fossile. Touche P : équipe (Prizmod inséré).
 */
public class GauntletItem extends Item {
    public GauntletItem(Properties props) {
        super(props);
    }

    public static GauntletModules modules(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.GAUNTLET_MODULES.get(), GauntletModules.NONE);
    }

    /** Insérer le Prizmod (clic avec le Prizmod sur le gantelet) ou le ressortir (clic droit, curseur vide). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack gauntlet, ItemStack carried, net.minecraft.world.inventory.Slot slot,
                                            net.minecraft.world.inventory.ClickAction action, net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.entity.SlotAccess carriedAccess) {
        GauntletModules m = modules(gauntlet);
        if (!m.prizmod() && carried.is(ModRegistry.PRIZMOD.get()) && action == net.minecraft.world.inventory.ClickAction.PRIMARY) {
            carried.shrink(1);
            gauntlet.set(ModRegistry.GAUNTLET_MODULES.get(), m.withPrizmod(true));
            player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 1.4f);
            return true;
        }
        if (m.prizmod() && carried.isEmpty() && action == net.minecraft.world.inventory.ClickAction.SECONDARY) {
            carriedAccess.set(new ItemStack(ModRegistry.PRIZMOD.get()));
            gauntlet.set(ModRegistry.GAUNTLET_MODULES.get(), m.withPrizmod(false));
            player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 1.4f);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GauntletModules m = modules(stack);
        tooltip.add(Component.translatable(m.prizmod() ? "tooltip.dirtrobes.gauntlet.prizmod_on" : "tooltip.dirtrobes.gauntlet.prizmod_off")
                .withStyle(m.prizmod() ? ChatFormatting.AQUA : ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.dirtrobes.gauntlet.weapon",
                Component.translatable(m.hasWeapon() ? "module.dirtrobes." + m.weapon() : "module.dirtrobes.none")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(m.extractor() ? "tooltip.dirtrobes.gauntlet.extractor_on" : "tooltip.dirtrobes.gauntlet.extractor_off")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.gauntlet.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
