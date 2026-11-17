package com.dirtrobes.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Property;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public final class CommonEvents {
    private CommonEvents() {}

    /** Propriété des mobs vanilla : tags data/dirtrobes/tags/entity_type/property/&lt;propriété&gt;.json */
    private static final Map<Property, TagKey<EntityType<?>>> PROPERTY_TAGS = new EnumMap<>(Property.class);

    static {
        for (Property p : Property.values()) {
            PROPERTY_TAGS.put(p, TagKey.create(Registries.ENTITY_TYPE, DirtRobes.id("property/" + p.id())));
        }
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.SPECTROBE.get(), SpectrobeEntity.createAttributes().build());
    }

    public static void registerGameEvents(IEventBus bus) {
        bus.addListener(CommonEvents::onReload);
        bus.addListener(CommonEvents::onIncomingDamage);
        bus.addListener(CommonEvents::onDeath);
        bus.addListener(CommonEvents::onCommands);
    }

    private static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SpeciesManager());
    }

    @Nullable
    public static Property propertyOf(@Nullable Entity entity) {
        if (entity instanceof SpectrobeEntity s) return s.getProperty();
        if (entity == null) return null;
        for (Map.Entry<Property, TagKey<EntityType<?>>> e : PROPERTY_TAGS.entrySet()) {
            if (entity.getType().is(e.getValue())) return e.getKey();
        }
        return null;
    }

    /** Applique le cycle des propriétés aux dégâts (×1,5 avantage, ×0,67 désavantage). */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Property attacker = propertyOf(event.getSource().getEntity());
        Property defender = propertyOf(event.getEntity());
        if (attacker == null || defender == null) return;
        float mult = attacker.multiplierAgainst(defender);
        if (mult != 1f) event.setAmount(event.getAmount() * mult);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof SpectrobeEntity spectrobe && !spectrobe.level().isClientSide) {
            spectrobe.onKill(event.getEntity());
        }
    }

    /** /dirtrobes spawn &lt;espèce&gt; [niveau] : fait apparaître un Spectrobe apprivoisé (tests). */
    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dirtrobes")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        SpeciesManager.all().stream().map(s -> s.id().getPath()), builder))
                                .executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "species"), 5))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> spawn(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "species"), IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    private static int spawn(ServerPlayer player, String name, int level) {
        Species species = SpeciesManager.byName(name);
        if (species == null) {
            player.sendSystemMessage(Component.translatable("message.dirtrobes.unknown_species", name));
            return 0;
        }
        SpectrobeEntity.create(player.serverLevel(), species, level, player, player.position());
        return 1;
    }

    @SuppressWarnings("unused")
    private static boolean isLiving(Entity e) {
        return e instanceof LivingEntity;
    }
}
