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

/**
 * Gemme : à donner à son Spectrobe. Les gemmes « + » ajoutent des EV dans une stat, les gemmes « − »
 * en retirent (pour corriger une répartition), comme les vitamines et les baies de Pokémon.
 */
public class GemItem extends Item {
    public static final int EV_CHANGE = 20;
    public static final int XP = 50;

    private final MineralItem.Stat stat;
    private final boolean raises;

    public GemItem(MineralItem.Stat stat, boolean raises, Properties props) {
        super(props);
        this.stat = stat;
        this.raises = raises;
    }

    public MineralItem.Stat stat() {
        return stat;
    }

    public int evChange() {
        return raises ? EV_CHANGE : -EV_CHANGE;
    }

    public int xp() {
        return XP;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof SpectrobeEntity spectrobe && spectrobe.isOwnedBy(player)) {
            if (!player.level().isClientSide) {
                spectrobe.feedGem(this, player);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(raises ? "tooltip.dirtrobes.gem_up" : "tooltip.dirtrobes.gem_down", EV_CHANGE,
                Component.translatable("stat.dirtrobes." + stat.id())).withStyle(raises ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
