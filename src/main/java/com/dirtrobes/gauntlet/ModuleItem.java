package com.dirtrobes.gauntlet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Module du gantelet : se pose sur le gantelet à la table de forge (module + gantelet + minéral). */
public class ModuleItem extends Item {
    private final String module;

    public ModuleItem(String module, Properties props) {
        super(props);
        this.module = module;
    }

    public String module() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.module." + module).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.module_how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
