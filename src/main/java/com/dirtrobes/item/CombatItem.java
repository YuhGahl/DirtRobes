package com.dirtrobes.item;

import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.Locale;

/**
 * Objets de combat (M17), à usage unique. Pour respecter « un objet par combat », utiliser l'un
 * d'eux met les quatre en recharge pendant {@link #SHARED_COOLDOWN} ticks.
 */
public class CombatItem extends Item {
    public static final int SHARED_COOLDOWN = 20 * 30;

    public enum Kind {
        /** Soigne 50 % des PV max d'un de ses Spectrobes (pas K.O.). */
        SERUM,
        /** Sort un de ses Spectrobes du K.O., à 30 % de ses PV. */
        REVIVE,
        /** +30 points de jauge CH (s'utilise dans le vide). */
        CH_RECHARGE,
        /** Retire les effets négatifs d'un de ses Spectrobes. */
        ANTIDOTE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Kind kind;

    public CombatItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Met tous les objets de combat en recharge. */
    public static void startSharedCooldown(Player player) {
        for (var item : ModRegistry.COMBAT_ITEMS) player.getCooldowns().addCooldown(item.get(), SHARED_COOLDOWN);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (kind == Kind.CH_RECHARGE || !(target instanceof SpectrobeEntity spectrobe) || !spectrobe.isOwnedBy(player) || spectrobe.isPenResident()) return InteractionResult.PASS;
        if (!player.level().isClientSide && spectrobe.useCombatItem(kind, player)) {
            startSharedCooldown(player);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind != Kind.CH_RECHARGE) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (ChGauge.get(sp) >= ChGauge.MAX) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.ch_full"), true);
                return InteractionResultHolder.fail(stack);
            }
            ChGauge.add(sp, 30);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
            startSharedCooldown(player);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes." + kind.id()).withStyle(ChatFormatting.GRAY));
    }
}
