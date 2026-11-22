package com.dirtrobes.compat.create;

import com.dirtrobes.block.IncubatorBlock;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Incubateur à engrenage : un incubateur avec un petit engrenage horizontal à mi-hauteur. Il s'engrène avec les
 * engrenages posés à côté de lui (au même niveau), consomme de la contrainte et accélère l'éveil.
 * Cassé : rend l'incubateur et l'engrenage.
 */
public class CogIncubatorBlock extends KineticBlock implements ICogWheel, IBE<CogIncubatorBlockEntity> {
    public CogIncubatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(IncubatorBlock.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IncubatorBlock.ACTIVE);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        IncubatorBlockEntity incubator = IncubatorBlockEntity.at(level, pos);
        if (player instanceof ServerPlayer sp && incubator != null) sp.openMenu(incubator, buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            IncubatorBlockEntity incubator = IncubatorBlockEntity.at(level, pos);
            if (incubator != null) Containers.dropContents(level, pos, incubator.inventory());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<CogIncubatorBlockEntity> getBlockEntityClass() {
        return CogIncubatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CogIncubatorBlockEntity> getBlockEntityType() {
        return CreateCompat.COG_INCUBATOR_BE.get();
    }
}
