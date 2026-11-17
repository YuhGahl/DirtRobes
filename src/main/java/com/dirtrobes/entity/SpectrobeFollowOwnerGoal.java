package com.dirtrobes.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.EnumSet;

/**
 * Suivi du dresseur, réglé comme Cobblemon plutôt que comme le loup vanilla :
 * le Spectrobe ne se met en route qu'au-delà de {@link #START_DISTANCE} blocs, s'arrête dès
 * qu'il est revenu à {@link #STOP_DISTANCE} blocs, marche tranquillement et n'accélère que
 * s'il est vraiment distancé. Il ne se téléporte qu'au-delà de {@link #TELEPORT_DISTANCE} blocs.
 */
public class SpectrobeFollowOwnerGoal extends Goal {
    public static final double START_DISTANCE = 6.0;
    public static final double STOP_DISTANCE = 3.5;
    public static final double CATCH_UP_DISTANCE = 12.0;
    public static final double TELEPORT_DISTANCE = 24.0;
    private static final double WALK_SPEED = 0.8;
    private static final double CATCH_UP_SPEED = 1.15;

    private final SpectrobeEntity mob;
    private LivingEntity owner;
    private int recalcTicks;

    public SpectrobeFollowOwnerGoal(SpectrobeEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean blocked() {
        return mob.isOrderedToSit() || mob.isPassenger() || mob.isLeashed() || mob.isKnockedOut();
    }

    @Override
    public boolean canUse() {
        LivingEntity o = mob.getOwner();
        if (o == null || o.isSpectator() || blocked()) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive() && mob.canFight()) return false;
        if (mob.distanceToSqr(o) < START_DISTANCE * START_DISTANCE) return false;
        this.owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || owner.isSpectator() || blocked()) return false;
        if (mob.getTarget() != null && mob.getTarget().isAlive() && mob.canFight()) return false;
        return mob.distanceToSqr(owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start() {
        recalcTicks = 0;
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 10.0f, mob.getMaxHeadXRot());
        if (--recalcTicks > 0) return;
        recalcTicks = adjustedTickDelay(10);
        double dist = mob.distanceTo(owner);
        if (dist >= TELEPORT_DISTANCE && teleportNearOwner()) return;
        mob.getNavigation().moveTo(owner, dist >= CATCH_UP_DISTANCE ? CATCH_UP_SPEED : WALK_SPEED);
    }

    private boolean teleportNearOwner() {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 12; i++) {
            int dx = mob.getRandom().nextIntBetweenInclusive(-4, 4);
            int dz = mob.getRandom().nextIntBetweenInclusive(-4, 4);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            int dy = mob.getRandom().nextIntBetweenInclusive(-1, 1);
            BlockPos pos = base.offset(dx, dy, dz);
            if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) != PathType.WALKABLE) continue;
            if (!mob.level().noCollision(mob, mob.getBoundingBox().move(pos.getCenter().subtract(mob.position()).add(0, -0.5, 0)))) continue;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, mob.getYRot(), mob.getXRot());
            mob.getNavigation().stop();
            return true;
        }
        return false;
    }
}
