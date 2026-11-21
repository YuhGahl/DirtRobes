package com.dirtrobes.gauntlet;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Tir d'énergie du canon du gantelet : vole droit, 5 dégâts. */
public class GauntletShot extends ThrowableItemProjectile {
    public static final float DAMAGE = 5.0f;

    public GauntletShot(EntityType<? extends GauntletShot> type, Level level) {
        super(type, level);
    }

    public GauntletShot(Level level, LivingEntity owner) {
        super(ModRegistry.GAUNTLET_SHOT.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModRegistry.GAUNTLET_SHOT_ITEM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 60) discard();
        else if (level().isClientSide) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(damageSources().thrown(this, getOwner()), DAMAGE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.2);
            discard();
        }
    }
}
