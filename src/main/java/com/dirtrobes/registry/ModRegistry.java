package com.dirtrobes.registry;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlock;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.item.FossilItem;
import com.dirtrobes.item.ChromaItem;
import com.dirtrobes.lab.LabTerminalBlock;
import com.dirtrobes.lab.LabTerminalBlockEntity;
import com.dirtrobes.item.CombatItem;
import com.dirtrobes.item.GemItem;
import com.dirtrobes.item.NatureGemItem;
import com.dirtrobes.species.Nature;
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
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DirtRobes.MODID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, DirtRobes.MODID);
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

    public static final DeferredHolder<EntityType<?>, EntityType<com.dirtrobes.gauntlet.GauntletShot>> GAUNTLET_SHOT = ENTITIES.register("gauntlet_shot",
            () -> EntityType.Builder.<com.dirtrobes.gauntlet.GauntletShot>of(com.dirtrobes.gauntlet.GauntletShot::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(4).updateInterval(10).build("gauntlet_shot"));

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<com.dirtrobes.gauntlet.GauntletModules>> GAUNTLET_MODULES =
            DATA_COMPONENTS.registerComponentType("gauntlet_modules", b -> b.persistent(com.dirtrobes.gauntlet.GauntletModules.CODEC)
                    .networkSynchronized(com.dirtrobes.gauntlet.GauntletModules.STREAM_CODEC));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> GAUNTLET_WEAPON =
            DATA_COMPONENTS.registerComponentType("gauntlet_weapon", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC).cacheEncoding());
    /** Espèce contenue dans un fossile (tirée à l'extraction). */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>> FOSSIL_SPECIES =
            DATA_COMPONENTS.registerComponentType("fossil_species", b -> b.persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));
    /** Rareté du fossile (visible par le client : infobulle, difficulté de la fouille). */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> FOSSIL_RARITY =
            DATA_COMPONENTS.registerComponentType("fossil_rarity", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    /** Arme à rendre au joueur quand il prend le gantelet à la table de forge (temporaire). */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> GAUNTLET_RETURN =
            DATA_COMPONENTS.registerComponentType("gauntlet_return", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, com.dirtrobes.gauntlet.GauntletModuleRecipe.Serializer> GAUNTLET_MODULE_SERIALIZER =
            RECIPE_SERIALIZERS.register("gauntlet_module", com.dirtrobes.gauntlet.GauntletModuleRecipe.Serializer::new);

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

    public static final DeferredBlock<com.dirtrobes.storage.SpectrobePcBlock> SPECTROBE_PC = BLOCKS.registerBlock("spectrobe_pc",
            com.dirtrobes.storage.SpectrobePcBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 5));
    public static final DeferredBlock<LabTerminalBlock> LAB_TERMINAL = BLOCKS.registerBlock("lab_terminal", LabTerminalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(state -> 6).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabTerminalBlockEntity>> LAB_TERMINAL_BE = BLOCK_ENTITIES.register("lab_terminal",
            () -> BlockEntityType.Builder.of(LabTerminalBlockEntity::new, LAB_TERMINAL.get()).build(null));

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
    public static final DeferredItem<GemItem> PERIDOT = ITEMS.registerItem("peridot",
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
    public static final List<DeferredItem<GemItem>> GEMS = List.of(RUBY, SAPPHIRE, PERIDOT, TOPAZ, ONYX, OPAL, PEARL, GRAPHITE);

    public static final DeferredItem<NatureGemItem> SELENITE = ITEMS.registerItem("selenite",
            props -> new NatureGemItem(Nature.BALANCED, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> MOISSANITE = ITEMS.registerItem("moissanite",
            props -> new NatureGemItem(Nature.STEADY, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> PLATINUM = ITEMS.registerItem("platinum",
            props -> new NatureGemItem(Nature.COMPOSED, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> ZIRCON = ITEMS.registerItem("zircon",
            props -> new NatureGemItem(Nature.CONSTANT, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> JADE = ITEMS.registerItem("jade",
            props -> new NatureGemItem(Nature.PEACEFUL, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> AMBER = ITEMS.registerItem("amber",
            props -> new NatureGemItem(Nature.ENDURING, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> RHODONITE = ITEMS.registerItem("rhodonite",
            props -> new NatureGemItem(Nature.PLACID, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> GARNET = ITEMS.registerItem("garnet",
            props -> new NatureGemItem(Nature.RECKLESS, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> SPINAL = ITEMS.registerItem("spinal",
            props -> new NatureGemItem(Nature.FIERY, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> AGATE = ITEMS.registerItem("agate",
            props -> new NatureGemItem(Nature.HULKING, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> COBALT = ITEMS.registerItem("cobalt",
            props -> new NatureGemItem(Nature.WARY, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> SODALITE = ITEMS.registerItem("sodalite",
            props -> new NatureGemItem(Nature.RESERVED, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> AZURITE = ITEMS.registerItem("azurite",
            props -> new NatureGemItem(Nature.UNSHAKABLE, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> FLUORITE = ITEMS.registerItem("fluorite",
            props -> new NatureGemItem(Nature.JITTERY, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> SYLVITE = ITEMS.registerItem("sylvite",
            props -> new NatureGemItem(Nature.NIMBLE, props), new Item.Properties());
    public static final DeferredItem<NatureGemItem> CITRINE = ITEMS.registerItem("citrine",
            props -> new NatureGemItem(Nature.LIVELY, props), new Item.Properties());
    public static final List<DeferredItem<NatureGemItem>> NATURE_GEMS = List.of(SELENITE, MOISSANITE, PLATINUM, ZIRCON, JADE, AMBER, RHODONITE, GARNET, SPINAL, AGATE, COBALT, SODALITE, AZURITE, FLUORITE, SYLVITE, CITRINE);

    public static final DeferredItem<ChromaItem> CHROMA_1 = ITEMS.registerItem("chroma_1", props -> new ChromaItem(0, props), new Item.Properties());
    public static final DeferredItem<ChromaItem> CHROMA_2 = ITEMS.registerItem("chroma_2", props -> new ChromaItem(1, props), new Item.Properties());
    public static final DeferredItem<ChromaItem> CHROMA_3 = ITEMS.registerItem("chroma_3", props -> new ChromaItem(2, props), new Item.Properties());

    public static final DeferredItem<CombatItem> SERUM = ITEMS.registerItem("serum",
            props -> new CombatItem(CombatItem.Kind.SERUM, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<CombatItem> REVIVE = ITEMS.registerItem("revive",
            props -> new CombatItem(CombatItem.Kind.REVIVE, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<CombatItem> CH_RECHARGE = ITEMS.registerItem("ch_recharge",
            props -> new CombatItem(CombatItem.Kind.CH_RECHARGE, props), new Item.Properties().stacksTo(16));
    public static final DeferredItem<CombatItem> ANTIDOTE = ITEMS.registerItem("antidote",
            props -> new CombatItem(CombatItem.Kind.ANTIDOTE, props), new Item.Properties().stacksTo(16));
    public static final List<DeferredItem<CombatItem>> COMBAT_ITEMS = List.of(SERUM, REVIVE, CH_RECHARGE, ANTIDOTE);

    public static final DeferredItem<com.dirtrobes.gauntlet.GauntletItem> GAUNTLET = ITEMS.registerItem("gauntlet",
            com.dirtrobes.gauntlet.GauntletItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<com.dirtrobes.gauntlet.GauntletWeaponItem> GAUNTLET_BLADE = ITEMS.registerItem("gauntlet_blade",
            p -> new com.dirtrobes.gauntlet.GauntletWeaponItem("blade", p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.gauntlet.GauntletWeaponItem> GAUNTLET_CANNON = ITEMS.registerItem("gauntlet_cannon",
            p -> new com.dirtrobes.gauntlet.GauntletWeaponItem("cannon", p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.gauntlet.GauntletWeaponItem> GAUNTLET_FIST = ITEMS.registerItem("gauntlet_fist",
            p -> new com.dirtrobes.gauntlet.GauntletWeaponItem("fist", p), new Item.Properties());
    public static final DeferredItem<Item> GAUNTLET_SHOT_ITEM = ITEMS.registerSimpleItem("gauntlet_shot");
    public static final DeferredItem<com.dirtrobes.gauntlet.ModuleItem> MODULE_BLADE = ITEMS.registerItem("module_blade",
            p -> new com.dirtrobes.gauntlet.ModuleItem("blade", p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.gauntlet.ModuleItem> MODULE_CANNON = ITEMS.registerItem("module_cannon",
            p -> new com.dirtrobes.gauntlet.ModuleItem("cannon", p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.gauntlet.ModuleItem> MODULE_FIST = ITEMS.registerItem("module_fist",
            p -> new com.dirtrobes.gauntlet.ModuleItem("fist", p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.gauntlet.ModuleItem> MODULE_EXTRACTOR = ITEMS.registerItem("module_extractor",
            p -> new com.dirtrobes.gauntlet.ModuleItem("extractor", p), new Item.Properties());

    public static final DeferredItem<BlockItem> MINERAL_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem("mineral_deposit", MINERAL_DEPOSIT);
    public static final DeferredItem<BlockItem> DEEPSLATE_MINERAL_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem("deepslate_mineral_deposit", DEEPSLATE_MINERAL_DEPOSIT);
    public static final DeferredItem<BlockItem> SPECTROBE_PC_ITEM = ITEMS.registerSimpleBlockItem("spectrobe_pc", SPECTROBE_PC);
    public static final DeferredItem<BlockItem> LAB_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem("lab_terminal", LAB_TERMINAL);
    public static final DeferredItem<BlockItem> INCUBATOR_ITEM = ITEMS.registerSimpleBlockItem("incubator", INCUBATOR);
    public static final DeferredItem<BlockItem> VORTEX_ITEM = ITEMS.registerSimpleBlockItem("krawl_vortex", VORTEX);
    public static final DeferredItem<DeferredSpawnEggItem> KRAWL_SPAWN_EGG = ITEMS.registerItem("krawl_spawn_egg",
            props -> new DeferredSpawnEggItem(KRAWL, 0x241A30, 0x9B5CFF, props), new Item.Properties());

    public static final DeferredItem<PrizmodItem> PRIZMOD = ITEMS.registerItem("prizmod", PrizmodItem::new, new Item.Properties().stacksTo(1));

    /** Un fossile par propriété (Prisma n'a pas de fossile). */
    public static final Map<Property, DeferredItem<FossilItem>> FOSSILS = new EnumMap<>(Property.class);
    /** Minéraux façon Spectrobes : Power (ATQ), Defence (DÉF), Health (PV), de C à A+. */
    public static final List<DeferredItem<MineralItem>> MINERALS = new ArrayList<>();
    /** Minéraux de Spectrobes Origins (EXP, stats, évolution). */
    public static final java.util.Map<com.dirtrobes.item.OriginsMineralItem.Kind, DeferredItem<com.dirtrobes.item.OriginsMineralItem>> ORIGINS_MINERALS =
            new java.util.EnumMap<>(com.dirtrobes.item.OriginsMineralItem.Kind.class);

    static {
        for (var kind : com.dirtrobes.item.OriginsMineralItem.Kind.values()) {
            String name = kind.xp > 0 ? kind.id() + "_exp_mineral" : kind.id() + "_mineral";
            ORIGINS_MINERALS.put(kind, ITEMS.registerItem(name, props -> new com.dirtrobes.item.OriginsMineralItem(kind, props), new Item.Properties()));
        }
    }

    public static final DeferredItem<com.dirtrobes.item.RiceBallItem> RICE_BALL = ITEMS.registerItem("rice_ball",
            p -> new com.dirtrobes.item.RiceBallItem(4f, 3, p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.item.RiceBallItem> MEGA_RICE_BALL = ITEMS.registerItem("mega_rice_ball",
            p -> new com.dirtrobes.item.RiceBallItem(8f, 4, p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.item.RiceBallItem> GIGA_RICE_BALL = ITEMS.registerItem("giga_rice_ball",
            p -> new com.dirtrobes.item.RiceBallItem(14f, 6, p), new Item.Properties());
    public static final DeferredItem<com.dirtrobes.item.RiceBallItem> NEO_RICE_BALL = ITEMS.registerItem("neo_rice_ball",
            p -> new com.dirtrobes.item.RiceBallItem(-1f, 8, p), new Item.Properties());

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
                if (net.neoforged.fml.ModList.get().isLoaded("create")) com.dirtrobes.compat.create.CreateCompat.fillTab(output);
                output.accept(FOSSIL_STONE_ITEM.get());
                output.accept(DEEPSLATE_FOSSIL_STONE_ITEM.get());
                output.accept(MINERAL_DEPOSIT_ITEM.get());
                output.accept(DEEPSLATE_MINERAL_DEPOSIT_ITEM.get());
                output.accept(VORTEX_ITEM.get());
                output.accept(GAUNTLET.get());
                output.accept(MODULE_BLADE.get());
                output.accept(MODULE_CANNON.get());
                output.accept(MODULE_FIST.get());
                output.accept(MODULE_EXTRACTOR.get());
                output.accept(SPECTROBE_PC_ITEM.get());
                output.accept(LAB_TERMINAL_ITEM.get());
                GEMS.forEach(g -> output.accept(g.get()));
                NATURE_GEMS.forEach(g -> output.accept(g.get()));
                COMBAT_ITEMS.forEach(i -> output.accept(i.get()));
                ORIGINS_MINERALS.values().forEach(i -> output.accept(i.get()));
                output.accept(RICE_BALL.get());
                output.accept(MEGA_RICE_BALL.get());
                output.accept(GIGA_RICE_BALL.get());
                output.accept(NEO_RICE_BALL.get());
                output.accept(CHROMA_1.get());
                output.accept(CHROMA_2.get());
                output.accept(CHROMA_3.get());
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
        DATA_COMPONENTS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
    }
}
