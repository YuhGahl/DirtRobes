package com.dirtrobes.krawl;

import com.dirtrobes.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fait apparaître des Krawl à intervalles réguliers tant qu'un joueur est à moins de 16 blocs. */
public class VortexBlockEntity extends BlockEntity {
    public static final int RANGE = 16;
    public static final int MAX_NEARBY = 4;
    public static final int FIRST_DELAY = 40;

    private int cooldown = FIRST_DELAY;

    public VortexBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.VORTEX_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VortexBlockEntity be) {
        if (!(level instanceof ServerLevel server) || --be.cooldown > 0) return;
        be.cooldown = 200 + server.random.nextInt(200);
        Vec3 center = Vec3.atCenterOf(pos);
        Player player = server.getNearestPlayer(center.x, center.y, center.z, RANGE, p -> !p.isSpectator());
        if (player == null) {
            be.cooldown = 40;
            return;
        }
        int nearby = server.getEntitiesOfClass(KrawlEntity.class, new AABB(pos).inflate(RANGE)).size();
        if (nearby >= MAX_NEARBY) return;
        KrawlSpecies species = KrawlManager.random(server.random);
        if (species == null) return;
        Vec3 spawn = center.add(server.random.nextInt(3) - 1, -0.5, server.random.nextInt(3) - 1);
        KrawlEntity krawl = KrawlEntity.create(server, species, Mth.nextInt(server.random, species.minLevel(), species.maxLevel()), spawn);
        krawl.setTarget(player);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, spawn.x, spawn.y + 0.5, spawn.z, 40, 0.4, 0.6, 0.4, 0.1);
        server.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.6f);
    }
}
