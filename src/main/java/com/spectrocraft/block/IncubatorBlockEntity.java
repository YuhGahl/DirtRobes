package com.spectrocraft.block;

import com.spectrocraft.entity.SpectrobeEntity;
import com.spectrocraft.registry.ModRegistry;
import com.spectrocraft.species.Property;
import com.spectrocraft.species.Species;
import com.spectrocraft.species.SpeciesManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class IncubatorBlockEntity extends BlockEntity {
    public static final int DURATION = 400; // 20 secondes

    @Nullable
    private Property property;
    @Nullable
    private UUID owner;
    private int progress;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.INCUBATOR_BE.get(), pos, state);
    }

    public boolean isBusy() {
        return property != null;
    }

    public void start(Property property, Player player) {
        this.property = property;
        this.owner = player.getUUID();
        this.progress = 0;
        if (level != null) {
            level.setBlock(worldPosition, getBlockState().setValue(IncubatorBlock.ACTIVE, true), 3);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (be.property == null || !(level instanceof ServerLevel server)) return;
        be.progress++;
        if (be.progress % 10 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.01);
        }
        if (be.progress >= DURATION) be.hatch(server);
        be.setChanged();
    }

    private void hatch(ServerLevel server) {
        Player player = owner == null ? null : server.getPlayerByUUID(owner);
        Species species = property == null ? null : SpeciesManager.randomFossilSpecies(property, server.random);
        if (species != null) {
            SpectrobeEntity.create(server, species, 1, player, Vec3.atBottomCenterOf(worldPosition.above()));
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    40, 0.4, 0.5, 0.4, 0.25);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.spectrocraft.hatched", Component.translatable(species.translationKey()))
                        .withStyle(ChatFormatting.AQUA));
            }
        } else if (player != null) {
            player.sendSystemMessage(Component.translatable("message.spectrocraft.hatch_failed"));
        }
        property = null;
        owner = null;
        progress = 0;
        server.setBlock(worldPosition, getBlockState().setValue(IncubatorBlock.ACTIVE, false), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (property != null) tag.putString("Property", property.id());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        property = tag.contains("Property") ? Property.byId(tag.getString("Property")) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        progress = tag.getInt("Progress");
    }
}
