package com.dirtrobes.compat.create;

import com.dirtrobes.registry.ModRegistry;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Intégration Create (classe chargée seulement si Create est installé) : l'accélérateur cinétique, un bloc à axe
 * qui, collé à un incubateur et mis en rotation, accélère l'éveil selon la vitesse de rotation.
 */
public final class CreateCompat {
    private CreateCompat() {}

    /** Charge de contrainte par tr/min (comme une machine Create moyenne). */
    public static final double STRESS_IMPACT = 4.0;

    public static DeferredBlock<KineticAcceleratorBlock> ACCELERATOR;
    public static DeferredItem<BlockItem> ACCELERATOR_ITEM;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticAcceleratorBlockEntity>> ACCELERATOR_BE;

    public static void register(IEventBus modBus) {
        ACCELERATOR = ModRegistry.BLOCKS.registerBlock("kinetic_accelerator", KineticAcceleratorBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.METAL).noOcclusion());
        ACCELERATOR_ITEM = ModRegistry.ITEMS.registerSimpleBlockItem("kinetic_accelerator", ACCELERATOR, new Item.Properties());
        ACCELERATOR_BE = ModRegistry.BLOCK_ENTITIES.register("kinetic_accelerator",
                () -> BlockEntityType.Builder.<KineticAcceleratorBlockEntity>of((pos, state) -> new KineticAcceleratorBlockEntity(ACCELERATOR_BE.get(), pos, state), ACCELERATOR.get()).build(null));
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> BlockStressValues.IMPACTS.register(ACCELERATOR.get(), () -> STRESS_IMPACT)));
    }

    public static void fillTab(CreativeModeTab.Output output) {
        if (ACCELERATOR_ITEM != null) output.accept(ACCELERATOR_ITEM.get());
    }
}
