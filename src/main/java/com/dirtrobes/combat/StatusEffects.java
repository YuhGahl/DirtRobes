package com.dirtrobes.combat;

import com.dirtrobes.species.Property;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Altérations d'état de Spectrobes Origins (table SickTable du disque : Brûlure, Gel, Poison, Étourdissement,
 * Lenteur, Sommeil), données selon la propriété de l'attaquant.
 */
public final class StatusEffects {
    private StatusEffects() {}

    public static final float NORMAL_CHANCE = 0.10f;
    public static final float SPECIAL_CHANCE = 0.40f;

    public static void apply(Property property, LivingEntity target, boolean strong) {
        int t = strong ? 2 : 1;
        switch (property) {
            // Feu → Brûlure
            case CORONA -> target.igniteForSeconds(2f * t);
            // Eau → Gel
            case FLASH -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30 * t, 3));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20 * t));
            }
            // Plante → Poison
            case AURORA -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 50 * t, 0));
            // Terre → Lenteur
            case ECLIPSE -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 * t, 1));
            // Ciel → Étourdissement
            case HALO -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15 * t, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15 * t, 1));
            }
            default -> {
            }
        }
    }
}
