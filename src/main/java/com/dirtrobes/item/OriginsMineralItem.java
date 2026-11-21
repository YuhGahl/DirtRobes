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
import java.util.Locale;

/**
 * Minéraux de Spectrobes Origins (valeurs lues sur le disque du jeu) : sept minéraux d'expérience de 16 à 1 024 EXP,
 * les minéraux d'Attaque, de Garde, de Vie et Super, et le minéral d'Évolution.
 */
public class OriginsMineralItem extends Item {
    public enum Kind {
        BLUE(16), GREEN(32), YELLOW(64), ORANGE(128), PINK(256), RED(512), PURPLE(1024),
        ATTACK(0), GUARD(0), LIFE(0), SUPER(0), EVOLVE(0);

        public final int xp;

        Kind(int xp) {
            this.xp = xp;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** EV donnés par les minéraux d'Attaque, de Garde et de Vie (Super : la moitié dans chacune des trois). */
    public static final int STAT_EV = 20;

    private final Kind kind;

    public OriginsMineralItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof SpectrobeEntity spectrobe) || !spectrobe.isOwnedBy(player) || spectrobe.isPenResident()) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            boolean used = spectrobe.feedOriginsMineral(kind);
            if (!used) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.cannot_evolve").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Component line = kind.xp > 0 ? Component.translatable("tooltip.dirtrobes.origins_mineral.xp", kind.xp)
                : Component.translatable("tooltip.dirtrobes.origins_mineral." + kind.id(), STAT_EV, STAT_EV / 2);
        tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
    }
}
