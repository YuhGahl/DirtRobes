package com.dirtrobes.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Boules de riz de Spectrobes Origins : elles soignent le joueur (30, 100, 300 PV ou tout dans le jeu ;
 * ici 4, 8, 14 PV ou toute la vie). Se mangent même rassasié.
 */
public class RiceBallItem extends Item {
    private final float heal;

    /** @param heal PV rendus, ou un nombre négatif pour toute la vie */
    public RiceBallItem(float heal, int nutrition, Properties props) {
        super(props.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0.6f).alwaysEdible().fast().build()));
        this.heal = heal;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) entity.heal(heal < 0 ? entity.getMaxHealth() : heal);
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add((heal < 0 ? Component.translatable("tooltip.dirtrobes.rice_ball_full")
                : Component.translatable("tooltip.dirtrobes.rice_ball", (int) heal)).withStyle(ChatFormatting.GRAY));
    }
}
