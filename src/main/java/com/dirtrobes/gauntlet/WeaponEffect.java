package com.dirtrobes.gauntlet;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Locale;

/** Effets des armes alimentées par un Spectrobe (descriptions du disque d'Origins). */
public enum WeaponEffect {
    NONE, BURN, FREEZE, POISON, STUN, SLOW, KNOCKBACK, LAUNCH, BURN_KNOCKBACK, ANTI_AIR, DOUBLE, AREA_STUN, EXPLOSIVE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Multiplicateur de dégâts selon la cible (Anti-aérien : +50 % sur une cible en l'air). */
    public float damageMultiplier(LivingEntity target) {
        if (this == ANTI_AIR && !target.onGround()) return 1.5f;
        return 1f;
    }

    public void apply(LivingEntity target, LivingEntity attacker) {
        switch (this) {
            case BURN -> target.igniteForSeconds(3f);
            case FREEZE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 40));
            }
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            case STUN -> stun(target);
            case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            case KNOCKBACK -> knockback(target, attacker, 1.2);
            case LAUNCH -> {
                target.push(0, 0.7, 0);
                target.hurtMarked = true;
            }
            case BURN_KNOCKBACK -> {
                target.igniteForSeconds(3f);
                knockback(target, attacker, 0.8);
            }
            case AREA_STUN -> {
                for (LivingEntity e : target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3),
                        e -> e != attacker && e.isAlive() && !e.isAlliedTo(attacker))) stun(e);
            }
            case EXPLOSIVE -> target.level().explode(attacker, target.getX(), target.getY() + 0.5, target.getZ(), 1.5f, Level.ExplosionInteraction.NONE);
            default -> {
            }
        }
    }

    private static void stun(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1));
    }

    private static void knockback(LivingEntity target, LivingEntity attacker, double strength) {
        target.knockback(strength, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
    }
}
