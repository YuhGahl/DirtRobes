package com.dirtrobes.item;

import com.dirtrobes.entity.SpectrobeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Prizmod (0.0.1-alpha) :
 * - clic droit sur un de ses Spectrobes : affiche sa fiche ;
 * - sneak + clic droit dessus : assis / suit ;
 * - clic droit dans le vide : rappelle tous ses Spectrobes (64 blocs).
 */
public class PrizmodItem extends Item {
    public PrizmodItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SpectrobeEntity spectrobe)) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            if (!spectrobe.isOwnedBy(player)) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.not_yours"), true);
            } else if (player.isShiftKeyDown()) {
                spectrobe.toggleSit(player);
            } else {
                spectrobe.describeTo(player);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            List<SpectrobeEntity> mine = level.getEntitiesOfClass(SpectrobeEntity.class, player.getBoundingBox().inflate(64),
                    s -> s.isOwnedBy(player) && !s.isKnockedOut());
            for (SpectrobeEntity s : mine) {
                s.setOrderedToSit(false);
                s.setInSittingPose(false);
                s.teleportTo(player.getX(), player.getY(), player.getZ());
            }
            player.displayClientMessage(Component.translatable("message.dirtrobes.recall", mine.size()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.prizmod.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.prizmod.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dirtrobes.prizmod.3").withStyle(ChatFormatting.GRAY));
    }
}
