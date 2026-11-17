package com.dirtrobes.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Promenade aléatoire qui ne choisit jamais un point collé au dresseur (sinon le Spectrobe
 * reste bloqué contre le joueur en essayant de l'atteindre) et qui abandonne au bout de 6 s.
 */
public class SpectrobeStrollGoal extends WaterAvoidingRandomStrollGoal {
    private static final double OWNER_CLEARANCE = 3.0;
    private static final int MAX_TICKS = 120;

    private final SpectrobeEntity spectrobe;
    private int runningTicks;

    public SpectrobeStrollGoal(SpectrobeEntity mob, double speed) {
        super(mob, speed);
        this.spectrobe = mob;
        setInterval(60);
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        LivingEntity owner = spectrobe.getOwner();
        for (int i = 0; i < 5; i++) {
            Vec3 pos = super.getPosition();
            if (pos == null) return null;
            if (owner == null || pos.distanceToSqr(owner.position()) > OWNER_CLEARANCE * OWNER_CLEARANCE) return pos;
        }
        return null;
    }

    @Override
    public void start() {
        super.start();
        runningTicks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (++runningTicks > MAX_TICKS) return false;
        LivingEntity owner = spectrobe.getOwner();
        if (owner != null && spectrobe.distanceToSqr(owner) < 2.0 * 2.0) return false;
        return super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        mob.getNavigation().stop();
    }
}
