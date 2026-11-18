package com.dirtrobes.combat;

import com.dirtrobes.entity.SpectrobeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Attaque combo (M13) : les 2 Spectrobes dehors frappent ensemble la cible, et l'onde de choc touche
 * aussi les ennemis autour d'elle. Coûte {@link #COST} points de jauge CH. Deux Spectrobes de la même
 * propriété sont « en résonance » : dégâts ×1,3.
 */
public final class ComboAttack {
    private ComboAttack() {}

    public static final int COST = 40;
    public static final float MULTIPLIER = 3.0f;
    public static final float RESONANCE = 1.3f;
    public static final double SPLASH_RADIUS = 3.5;

    /** @return true si le combo est parti. */
    public static boolean trigger(ServerPlayer player, @Nullable LivingEntity looked) {
        List<SpectrobeEntity> fighters = player.level().getEntitiesOfClass(SpectrobeEntity.class, player.getBoundingBox().inflate(24),
                s -> s.isOwnedBy(player) && s.canFight() && !s.isOrderedToSit());
        if (fighters.size() < 2) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.combo_need_two"), true);
            return false;
        }
        if (ChGauge.get(player) < COST) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.ch_low", ChGauge.get(player), COST), true);
            return false;
        }
        LivingEntity target = looked;
        if (target == null || !target.isAlive()) {
            target = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16),
                            e -> e instanceof Enemy && e.isAlive())
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.special_none"), true);
            return false;
        }
        SpectrobeEntity a = fighters.get(0), b = fighters.get(1);
        boolean resonance = a.getProperty() == b.getProperty();
        float mult = MULTIPLIER * (resonance ? RESONANCE : 1f);
        boolean started = a.startComboStrike(target, mult) | b.startComboStrike(target, mult);
        if (!started) return false;
        ChGauge.trySpend(player, COST);
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 1, 0, 0, 0, 0);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable(resonance ? "message.dirtrobes.combo_resonance" : "message.dirtrobes.combo",
                a.getDisplayName(), b.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Onde de choc autour de la cible du combo. */
    public static void splash(SpectrobeEntity source, LivingEntity center, float damage) {
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(SPLASH_RADIUS),
                e -> e != center && e instanceof Enemy && e.isAlive())) {
            e.hurt(source.damageSources().mobAttack(source), damage * 0.5f);
        }
    }
}
