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
        if (spectrobe.isFlyer()) {
            // vol : un point en l'air ou au sol, à portée d'ailes
            for (int i = 0; i < 10; i++) {
                Vec3 look = spectrobe.getViewVector(0f);
                Vec3 pos = net.minecraft.world.entity.ai.util.HoverRandomPos.getPos(mob, 8, 5, look.x, look.z, (float) (Math.PI / 2), 3, 1);
                if (pos == null) pos = net.minecraft.world.entity.ai.util.AirAndWaterRandomPos.getPos(mob, 8, 4, -2, look.x, look.z, Math.PI / 2);
                if (pos == null) continue;
                if (spectrobe.isPenResident() && spectrobe.pen() != null && !spectrobe.pen().contains(pos)) continue;
                LivingEntity o = spectrobe.getOwner();
                if (!spectrobe.isPenResident() && o != null && pos.distanceToSqr(o.position()) < OWNER_CLEARANCE * OWNER_CLEARANCE) continue;
                return pos;
            }
            return null;
        }
        if (spectrobe.isPenResident() && spectrobe.pen() != null) {
            // Points à pied depuis là où il est, et dans l'enclos : jamais de téléportation dans une grotte.
            for (int i = 0; i < 10; i++) {
                Vec3 pos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(mob, 8, 3);
                if (pos != null && spectrobe.pen().contains(pos)) return pos;
            }
            return null;
        }
        LivingEntity owner = spectrobe.getOwner();
        for (int i = 0; i < 5; i++) {
            Vec3 pos = super.getPosition();
            if (pos == null) return null;
            if (owner == null || segmentDistance(mob.position(), pos, owner.position()) > OWNER_CLEARANCE) return pos;
        }
        return null;
    }

    /** Distance (horizontale) entre le point p et le segment [a, b]. */
    private static double segmentDistance(Vec3 a, Vec3 b, Vec3 p) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len = dx * dx + dz * dz;
        double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.z - a.z) * dz) / len));
        double cx = a.x + t * dx - p.x, cz = a.z + t * dz - p.z;
        return Math.sqrt(cx * cx + cz * cz);
    }

    @Override
    public void start() {
        super.start();
        runningTicks = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (++runningTicks > MAX_TICKS) return false;
        if (spectrobe.isPenResident()) return super.canContinueToUse();
        LivingEntity owner = spectrobe.getOwner();
        if (owner != null && spectrobe.distanceToSqr(owner) < 2.5 * 2.5) return false;
        return super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        mob.getNavigation().stop();
    }
}
