package com.dirtrobes.registry;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlock;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.item.FossilItem;
import com.dirtrobes.item.GemItem;
import com.dirtrobes.krawl.KrawlEntity;
import com.dirtrobes.krawl.VortexBlock;
import com.dirtrobes.krawl.VortexBlockEntity;
import com.dirtrobes.item.MineralItem;
import com.dirtrobes.item.PrizmodItem;
import com.dirtrobes.menu.IncubatorMenu;
import com.dirtrobes.species.Property;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
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

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DirtRobes.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DirtRobes.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, DirtRobes.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DirtRobes.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DirtRobes.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DirtRobes.MODID);

    // ---------- Entité unique (l'espèce est une donnée synchronisée, comme Cobblemon) ----------
    public static final DeferredHolder<EntityType<?>, EntityType<SpectrobeEntity>> SPECTROBE = ENTITIES.register("spectrobe",
            () -> EntityType.Builder.of(SpectrobeEntity::new, MobCategory.CREATURE)
                    .sized(0.7f, 0.9f)
                    .clientTrackingRange(10)
                    .build("spectrobe"));

    public static final DeferredHolder<EntityType<?>, EntityType<KrawlEntity>> KRAWL = ENTITIES.register("krawl",
            () -> EntityType.Builder.of(KrawlEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.9f)
                    .clientTrackingRange(10)
                    .build("krawl"));

    // ---------- Blocs ----------
    public static final DeferredBlock<Block> FOSSIL_STONE = BLOCKS.registerSimpleBlock("fossil_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> DEEPSLATE_FOSSIL_STONE = BLOCKS.registerSimpleBlock("deepslate_fossil_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> MINERAL_DEPOSIT = BLOCKS.registerSimpleBlock("mineral_deposit",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> DEEPSLATE_MINERAL_DEPOSIT = BLOCKS.registerSimpleBlock("deepslate_mineral_deposit",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<IncubatorBlock> INCUBATOR = BLOCKS.registerBlock("incubator", IncubatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(IncubatorBlock.ACTIVE) ? 12 : 3).noOcclusion());

    public static final DeferredBlock<VortexBlock> VORTEX = BLOCKS.registerBlock("krawl_vortex", VortexBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f, 6.0f).sound(SoundType.SCULK)
                    .lightLevel(state -> 7).noOcclusion().noCollission());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VortexBlockEntity>> VORTEX_BE = BLOCK_ENTITIES.register("krawl_vortex",
            () -> BlockEntityType.Builder.of(VortexBlockEntity::new, VORTEX.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR_BE = BLOCK_ENTITIES.register("incubator",
            () -> BlockEntityType.Builder.of(IncubatorBlockEntity::new, INCUBATOR.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<IncubatorMenu>> INCUBATOR_MENU = MENUS.register("incubator",
            () -> IMenuTypeExtension.create(IncubatorMenu::new));

    // ---------- Objets ----------
    public static final DeferredItem<BlockItem> FOSSIL_STONE_ITEM = ITEMS.registerSimpleBlockItem("fossil_stone", FOSSIL_STONE);
    public static final DeferredItem<BlockItem> DEEPSLATE_FOSSIL_STONE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_fossil_stone", DEEPSLATE_FOSSIL_STONE);
    public static final DeferredItem<GemItem> RUBY = ITEMS.registerItem("ruby",
            props -> new GemItem(MineralItem.Stat.POWER, true, props), new Item.Properties());
    public static final DeferredItem<GemItem> SAPPHIRE = ITEMS.registerItem("sapphire",
            props -> new GemItem(MineralItem.Stat.DEFENCE, true, props), new Item.Properties());
    public static final DeferredItem<GemItem> EMERALD = ITEMS.registerItem("emerald",
            props -> new GemItem(MineralItem.Stat.HEALTH, true, props), new Item.Properties());
    public static final DeferredItem<GemItem> TOPAZ = ITEMS.registerItem("topaz",
            props -> new GemItem(MineralItem.Stat.SPEED, true, props), new Item.Properties());
    public static final DeferredItem<GemItem> ONYX = ITEMS.registerItem("onyx",
            props -> new GemItem(MineralItem.Stat.POWER, false, props), new Item.Properties());
    public static final DeferredItem<GemItem> OPAL = ITEMS.registerItem("opal",
            props -> new GemItem(MineralItem.Stat.DEFENCE, false, props), new Item.Properties());
    public static final DeferredItem<GemItem> PEARL = ITEMS.registerItem("pearl",
            props -> new GemItem(MineralItem.Stat.HEALTH, false, props), new Item.Properties());
    public static final DeferredItem<GemItem> GRAPHITE = ITEMS.registerItem("graphite",
            props -> new GemItem(MineralItem.Stat.SPEED, false, props), new Item.Properties());
    public static final List<DeferredItem<GemItem>> GEMS = List.of(RUBY, SAPPHIRE, EMERALD, TOPAZ, ONYX, OPAL, PEARL, GRAPHITE);

    public static final DeferredItem<BlockItem> MINERAL_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem("mineral_deposit", MINERAL_DEPOSIT);
    public static final DeferredItem<BlockItem> DEEPSLATE_MINERAL_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem("deepslate_mineral_deposit", DEEPSLATE_MINERAL_DEPOSIT);
    public static final DeferredItem<BlockItem> INCUBATOR_ITEM = ITEMS.registerSimpleBlockItem("incubator", INCUBATOR);
    public static final DeferredItem<BlockItem> VORTEX_ITEM = ITEMS.registerSimpleBlockItem("krawl_vortex", VORTEX);
    public static final DeferredItem<DeferredSpawnEggItem> KRAWL_SPAWN_EGG = ITEMS.registerItem("krawl_spawn_egg",
            props -> new DeferredSpawnEggItem(KRAWL, 0x241A30, 0x9B5CFF, props), new Item.Properties());

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
            .title(Component.translatable("itemGroup.dirtrobes"))
            .icon(() -> PRIZMOD.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(PRIZMOD.get());
                output.accept(INCUBATOR_ITEM.get());
                output.accept(FOSSIL_STONE_ITEM.get());
                output.accept(DEEPSLATE_FOSSIL_STONE_ITEM.get());
                output.accept(MINERAL_DEPOSIT_ITEM.get());
                output.accept(DEEPSLATE_MINERAL_DEPOSIT_ITEM.get());
                output.accept(VORTEX_ITEM.get());
                GEMS.forEach(g -> output.accept(g.get()));
                output.accept(KRAWL_SPAWN_EGG.get());
                FOSSILS.values().forEach(f -> output.accept(f.get()));
                MINERALS.forEach(m -> output.accept(m.get()));
            }).build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}
