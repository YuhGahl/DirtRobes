package com.dirtrobes.compat.create;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Accélérateur cinétique : reçoit un axe Create sur ses deux faces, comme un arbre de transmission. */
public class KineticAcceleratorBlock extends RotatedPillarKineticBlock implements IBE<KineticAcceleratorBlockEntity> {
    public KineticAcceleratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<KineticAcceleratorBlockEntity> getBlockEntityClass() {
        return KineticAcceleratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KineticAcceleratorBlockEntity> getBlockEntityType() {
        return CreateCompat.ACCELERATOR_BE.get();
    }
}
