package com.dirtrobes.item;

import com.dirtrobes.entity.SpectrobeEntity;
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

/** Minéral Chroma (comme dans le premier jeu) : Chroma 1 rend la couleur normale, Chroma 2 et 3 donnent les couleurs spéciales. */
public class ChromaItem extends Item {
    /** 0, 1 ou 2. */
    private final int color;

    public ChromaItem(int color, Properties props) {
        super(props);
        this.color = color;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof SpectrobeEntity spectrobe && spectrobe.isOwnedBy(player) && !spectrobe.isPenResident()) {
            if (!player.level().isClientSide && spectrobe.feedChroma(color, player) && !player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.chroma", Component.translatable("chroma.dirtrobes." + color))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
