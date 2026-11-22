package com.dirtrobes.compat.create;

import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.block.IncubatorBooster;
import com.dirtrobes.block.IncubatorHolder;
import com.dirtrobes.registry.ModRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entité de l'incubateur à engrenage : pièce cinétique de Create qui porte un incubateur ordinaire
 * (même contenu, même progression, même énergie FE). +1 vitesse d'éveil par tranche de 32 tr/min,
 * rien en surcharge.
 */
public class CogIncubatorBlockEntity extends KineticBlockEntity implements IncubatorBooster, IncubatorHolder {
    public static final float RPM_PER_BONUS = 32f;

    private final IncubatorBlockEntity incubator;

    public CogIncubatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.incubator = new IncubatorBlockEntity(pos, ModRegistry.INCUBATOR.get().defaultBlockState());
    }

    @Override
    public IncubatorBlockEntity incubator() {
        return incubator;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        incubator.setLevel(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide) IncubatorBlockEntity.serverTick(level, worldPosition, getBlockState(), incubator);
    }

    @Override
    public float incubatorBonus() {
        if (isOverStressed()) return 0f;
        return Math.abs(getSpeed()) / RPM_PER_BONUS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("Incubator", incubator.writeData(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Incubator")) incubator.readData(tag.getCompound("Incubator"), registries);
    }
}
