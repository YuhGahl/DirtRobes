package com.spectrocraft;

import com.mojang.logging.LogUtils;
import com.spectrocraft.event.CommonEvents;
import com.spectrocraft.network.ModNetwork;
import com.spectrocraft.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * SpectroCraft : mod fan inspiré de Spectrobes.
 * Les espèces sont décrites en JSON (datapack), les modèles et animations au format Bedrock
 * (resource pack), comme dans Cobblemon.
 */
@Mod(SpectroCraft.MODID)
public class SpectroCraft {
    public static final String MODID = "spectrocraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpectroCraft(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(CommonEvents::onAttributes);
        modBus.addListener(ModNetwork::register);
        CommonEvents.registerGameEvents(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
