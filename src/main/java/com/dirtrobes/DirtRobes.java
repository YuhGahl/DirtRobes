package com.dirtrobes;

import com.mojang.logging.LogUtils;
import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.event.CommonEvents;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.gametest.DirtRobesGameTests;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

/**
 * DirtRobes : mod fan inspiré de Spectrobes.
 * Les espèces sont décrites en JSON (datapack), les modèles et animations au format Bedrock
 * (resource pack), comme dans Cobblemon.
 */
@Mod(DirtRobes.MODID)
public class DirtRobes {
    public static final String MODID = "dirtrobes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DirtRobes(IEventBus modBus, ModContainer container) {
        if (net.neoforged.fml.ModList.get().isLoaded("create")) com.dirtrobes.compat.create.CreateCompat.register(modBus);
        ModRegistry.register(modBus);
        ChGauge.ATTACHMENTS.register(modBus);
        PrizmodTeam.ATTACHMENTS.register(modBus);
        modBus.addListener(CommonEvents::onAttributes);
        modBus.addListener(CommonEvents::onSpawnPlacements);
        modBus.addListener(ModNetwork::register);
        // Énergie FE : l'incubateur accepte l'électricité (Create: New Age, et tout mod à énergie FE)
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ModRegistry.INCUBATOR_BE.get(),
                        (be, side) -> be.energy()));
        modBus.addListener(com.dirtrobes.lab.LabNetwork::register);
        // Cobblemon (optionnel) : les Spectrobes peuvent passer dans l'équipe Cobblemon et revenir
        modBus.addListener(com.dirtrobes.compat.cobblemon.CobblemonNetwork::register);
        if (net.neoforged.fml.ModList.get().isLoaded("cobblemon")) com.dirtrobes.compat.cobblemon.CobblemonCompat.register(NeoForge.EVENT_BUS);
        modBus.addListener((RegisterGameTestsEvent event) -> event.register(DirtRobesGameTests.class));
        CommonEvents.registerGameEvents(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
