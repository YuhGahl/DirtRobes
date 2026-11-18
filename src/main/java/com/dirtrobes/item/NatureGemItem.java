package com.dirtrobes.item;

import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.species.Nature;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Gemme de nature : donnée à son Spectrobe, elle remplace sa nature par celle de la gemme. */
public class NatureGemItem extends Item {
    private final Nature nature;

    public NatureGemItem(Nature nature, Properties props) {
        super(props);
        this.nature = nature;
    }

    public Nature nature() {
        return nature;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof SpectrobeEntity spectrobe && spectrobe.isOwnedBy(player)) {
            if (!player.level().isClientSide && spectrobe.feedNatureGem(this, player) && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Component name = Component.translatable(nature.translationKey());
        if (nature.raised == null) {
            tooltip.add(Component.translatable("tooltip.dirtrobes.nature_gem_neutral", name).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.dirtrobes.nature_gem", name,
                    Component.translatable("stat.dirtrobes." + nature.raised.id()),
                    Component.translatable("stat.dirtrobes." + nature.lowered.id())).withStyle(ChatFormatting.AQUA));
        }
    }
}
