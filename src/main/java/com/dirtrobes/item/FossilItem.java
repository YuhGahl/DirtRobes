package com.dirtrobes.item;

import com.dirtrobes.species.Property;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Fossile d'une propriété : à placer dans un incubateur pour éveiller un Spectrobe enfant. */
public class FossilItem extends Item {
    private final Property property;

    public FossilItem(Property property, Properties props) {
        super(props);
        this.property = property;
    }

    public Property property() {
        return property;
    }

    /** Rareté du fossile (Commun si le fossile n'a pas encore d'espèce tirée). */
    public static com.dirtrobes.species.Rarity rarity(ItemStack stack) {
        return com.dirtrobes.species.Rarity.byOrdinal(stack.getOrDefault(com.dirtrobes.registry.ModRegistry.FOSSIL_RARITY.get(), 0));
    }

    /** Tire et fixe l'espèce du fossile (côté serveur, à l'extraction). */
    public static void assignSpecies(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (!(stack.getItem() instanceof FossilItem fossil) || stack.has(com.dirtrobes.registry.ModRegistry.FOSSIL_SPECIES.get())) return;
        com.dirtrobes.species.Species sp = com.dirtrobes.species.SpeciesManager.rollFossilSpecies(fossil.property(), random);
        if (sp == null) return;
        stack.set(com.dirtrobes.registry.ModRegistry.FOSSIL_SPECIES.get(), sp.id());
        stack.set(com.dirtrobes.registry.ModRegistry.FOSSIL_RARITY.get(), sp.rarity().ordinal());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(property.translationKey()).withStyle(s -> s.withColor(property.color())));
        if (stack.has(com.dirtrobes.registry.ModRegistry.FOSSIL_RARITY.get())) tooltip.add(rarity(stack).displayName());
    }
}
