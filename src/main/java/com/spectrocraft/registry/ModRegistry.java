package com.spectrocraft.registry;

import com.spectrocraft.SpectroCraft;
import com.spectrocraft.block.IncubatorBlock;
import com.spectrocraft.block.IncubatorBlockEntity;
import com.spectrocraft.entity.SpectrobeEntity;
import com.spectrocraft.item.FossilItem;
import com.spectrocraft.item.MineralItem;
import com.spectrocraft.item.PrizmodItem;
import com.spectrocraft.species.Property;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Tous les enregistrements du mod (entités, blocs, objets, onglet créatif). */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpectroCraft.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpectroCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SpectroCraft.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SpectroCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpectroCraft.MODID);

    // ---------- Entité unique (l'espèce est une donnée synchronisée, comme Cobblemon) ----------
    public static final DeferredHolder<EntityType<?>, EntityType<SpectrobeEntity>> SPECTROBE = ENTITIES.register("spectrobe",
            () -> EntityType.Builder.of(SpectrobeEntity::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(10)
                    .build("spectrobe"));

    // ---------- Blocs ----------
    public static final DeferredBlock<Block> FOSSIL_STONE = BLOCKS.registerSimpleBlock("fossil_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> DEEPSLATE_FOSSIL_STONE = BLOCKS.registerSimpleBlock("deepslate_fossil_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<IncubatorBlock> INCUBATOR = BLOCKS.registerBlock("incubator", IncubatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(IncubatorBlock.ACTIVE) ? 12 : 3).noOcclusion());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR_BE = BLOCK_ENTITIES.register("incubator",
            () -> BlockEntityType.Builder.of(IncubatorBlockEntity::new, INCUBATOR.get()).build(null));

    // ---------- Objets ----------
    public static final DeferredItem<BlockItem> FOSSIL_STONE_ITEM = ITEMS.registerSimpleBlockItem("fossil_stone", FOSSIL_STONE);
    public static final DeferredItem<BlockItem> DEEPSLATE_FOSSIL_STONE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_fossil_stone", DEEPSLATE_FOSSIL_STONE);
    public static final DeferredItem<BlockItem> INCUBATOR_ITEM = ITEMS.registerSimpleBlockItem("incubator", INCUBATOR);

    public static final DeferredItem<PrizmodItem> PRIZMOD = ITEMS.registerItem("prizmod", PrizmodItem::new, new Item.Properties().stacksTo(1));

    /** Un fossile par propriété (Prisma n'a pas de fossile). */
    public static final Map<Property, DeferredItem<FossilItem>> FOSSILS = new EnumMap<>(Property.class);
    /** Minéraux façon Spectrobes : Power (ATQ), Defence (DÉF), Health (PV), de C à A+. */
    public static final List<DeferredItem<MineralItem>> MINERALS = new ArrayList<>();

    static {
        for (Property p : Property.values()) {
            if (p == Property.PRISMA) continue;
            FOSSILS.put(p, ITEMS.registerItem(p.id() + "_fossil", props -> new FossilItem(p, props), new Item.Properties().stacksTo(16)));
        }
        for (MineralItem.Stat stat : MineralItem.Stat.values()) {
            for (MineralItem.Grade grade : MineralItem.Grade.values()) {
                String name = stat.id() + "_" + grade.id();
                MINERALS.add(ITEMS.registerItem(name, props -> new MineralItem(stat, grade, props), new Item.Properties()));
            }
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.spectrocraft"))
            .icon(() -> PRIZMOD.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(PRIZMOD.get());
                output.accept(INCUBATOR_ITEM.get());
                output.accept(FOSSIL_STONE_ITEM.get());
                output.accept(DEEPSLATE_FOSSIL_STONE_ITEM.get());
                FOSSILS.values().forEach(f -> output.accept(f.get()));
                MINERALS.forEach(m -> output.accept(m.get()));
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
