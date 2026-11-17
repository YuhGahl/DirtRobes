package com.dirtrobes.item;

import com.dirtrobes.entity.SpectrobeEntity;
import net.minecraft.ChatFormatting;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.server.level.ServerPlayer;
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
 * Prizmod :
 * - clic droit dans le vide : écran d'équipe (6 Spectrobes, 2 dehors) ;
 * - accroupi + clic droit dans le vide : rappelle auprès de soi les Spectrobes dehors ;
 * - clic droit sur un de ses Spectrobes : sa fiche (et l'ajoute à l'équipe s'il n'y est pas) ;
 * - accroupi + clic droit dessus : assis / suivre.
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
                if (player instanceof ServerPlayer sp && PrizmodTeam.team(sp).find(spectrobe.getUUID()) == null && PrizmodTeam.adopt(sp, spectrobe)) {
                    player.displayClientMessage(Component.translatable("message.dirtrobes.team_added", spectrobe.getDisplayName()), false);
                }
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && !player.isShiftKeyDown() && player instanceof ServerPlayer sp) {
            PrizmodTeam.sync(sp, true);
        } else if (!level.isClientSide) {
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
