package com.dirtrobes.compat.create;

import com.dirtrobes.block.IncubatorBlock;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.registry.ModRegistry;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Intégration Create (classe chargée seulement si Create est installé) : un engrenage inséré dans l'incubateur
 * (clic droit avec un engrenage) en fait un incubateur à engrenage. Engrené avec un autre engrenage, il tourne
 * et accélère l'éveil selon sa vitesse de rotation.
 */
public final class CreateCompat {
    private CreateCompat() {}

    /** Charge de contrainte par tr/min (comme une machine Create moyenne). */
    public static final double STRESS_IMPACT = 4.0;

    public static DeferredBlock<CogIncubatorBlock> COG_INCUBATOR;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CogIncubatorBlockEntity>> COG_INCUBATOR_BE;

    public static void register(IEventBus modBus) {
        COG_INCUBATOR = ModRegistry.BLOCKS.registerBlock("incubator_cog", CogIncubatorBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noOcclusion()
                        .lightLevel(s -> s.getValue(IncubatorBlock.ACTIVE) ? 7 : 0));
        COG_INCUBATOR_BE = ModRegistry.BLOCK_ENTITIES.register("incubator_cog",
                () -> BlockEntityType.Builder.<CogIncubatorBlockEntity>of((pos, state) -> new CogIncubatorBlockEntity(COG_INCUBATOR_BE.get(), pos, state), COG_INCUBATOR.get()).build(null));
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> BlockStressValues.IMPACTS.register(COG_INCUBATOR.get(), () -> STRESS_IMPACT)));
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COG_INCUBATOR_BE.get(), (be, side) -> be.incubator().energy()));
    }

    public static void fillTab(CreativeModeTab.Output output) {
    }

    /** Insère l'engrenage : l'incubateur garde son fossile, sa progression et son énergie. */
    public static void insertCog(Level level, BlockPos pos, Player player, ItemStack cog) {
        IncubatorBlockEntity old = IncubatorBlockEntity.at(level, pos);
        if (old == null) return;
        BlockState state = level.getBlockState(pos);
        CompoundTag data = old.writeData(level.registryAccess());
        old.inventory().clearContent(); // sinon l'incubateur retiré lâcherait son fossile
        boolean active = state.hasProperty(IncubatorBlock.ACTIVE) && state.getValue(IncubatorBlock.ACTIVE);
        level.setBlock(pos, COG_INCUBATOR.get().defaultBlockState().setValue(IncubatorBlock.ACTIVE, active), 3);
        if (level.getBlockEntity(pos) instanceof CogIncubatorBlockEntity be) {
            be.incubator().readData(data, level.registryAccess());
            be.setChanged();
        }
        if (!player.getAbilities().instabuild) cog.shrink(1);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.dirtrobes.cog_inserted"), true);
    }
}
