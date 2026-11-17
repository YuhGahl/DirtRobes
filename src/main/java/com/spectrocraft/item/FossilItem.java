package com.spectrocraft.item;

import com.spectrocraft.species.Property;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(property.translationKey()).withStyle(s -> s.withColor(property.color())));
        tooltip.add(Component.translatable("tooltip.spectrocraft.fossil").withStyle(ChatFormatting.GRAY));
    }
}
