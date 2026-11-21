package com.dirtrobes.compat.create;

import com.dirtrobes.block.IncubatorBooster;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** +1 vitesse d'éveil par tranche de 32 tr/min (256 tr/min = 9 fois plus vite), rien en surcharge. */
public class KineticAcceleratorBlockEntity extends KineticBlockEntity implements IncubatorBooster {
    public static final float RPM_PER_BONUS = 32f;

    public KineticAcceleratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float incubatorBonus() {
        if (isOverStressed()) return 0f;
        return Math.abs(getSpeed()) / RPM_PER_BONUS;
    }
}
