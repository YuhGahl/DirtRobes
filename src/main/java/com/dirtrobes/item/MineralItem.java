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
 * Minéral à donner à son Spectrobe : de l'expérience et des EV dans une stat.
 * Les valeurs sont celles de l'onglet Minéraux du document de conception.
 */
public class MineralItem extends Item {
    public enum Stat {
        POWER, DEFENCE, HEALTH, SPEED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Grade {
        C(4, 30), B(10, 80), A(20, 200), A_PLUS(40, 500);

        public final int ev;
        public final int xp;

        Grade(int ev, int xp) {
            this.ev = ev;
            this.xp = xp;
        }

        public String id() {
            return this == A_PLUS ? "a_plus" : name().toLowerCase(Locale.ROOT);
        }
    }

    private final Stat stat;
    private final Grade grade;

    public MineralItem(Stat stat, Grade grade, Properties props) {
        super(props);
        this.stat = stat;
        this.grade = grade;
    }

    public Stat stat() {
        return stat;
    }

    public Grade grade() {
        return grade;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof SpectrobeEntity spectrobe && spectrobe.isOwnedBy(player)) {
            if (!player.level().isClientSide) {
                spectrobe.feedMineral(this, player);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dirtrobes.mineral", grade.xp, grade.ev,
                Component.translatable("stat.dirtrobes." + stat.id())).withStyle(ChatFormatting.GRAY));
    }
}
