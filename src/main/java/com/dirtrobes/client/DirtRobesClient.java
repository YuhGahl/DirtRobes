package com.dirtrobes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.dirtrobes.DirtRobes;
import com.dirtrobes.client.model.ModelRepository;
import com.dirtrobes.client.render.SpectrobeRenderer;
import com.dirtrobes.client.screen.IncubatorScreen;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.registry.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = DirtRobes.MODID, dist = Dist.CLIENT)
public class DirtRobesClient {
    public static final KeyMapping SPECIAL_ATTACK = new KeyMapping("key.dirtrobes.special_attack",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.dirtrobes");

    public DirtRobesClient(IEventBus modBus) {
        modBus.addListener(DirtRobesClient::onRenderers);
        modBus.addListener(DirtRobesClient::onReloadListeners);
        modBus.addListener(DirtRobesClient::onKeys);
        modBus.addListener(DirtRobesClient::onMenuScreens);
        NeoForge.EVENT_BUS.addListener(DirtRobesClient::onClientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.SPECTROBE.get(), SpectrobeRenderer::new);
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ModelRepository());
    }

    private static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.INCUBATOR_MENU.get(), IncubatorScreen::new);
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(SPECIAL_ATTACK);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        while (SPECIAL_ATTACK.consumeClick()) {
            PacketDistributor.sendToServer(new ModNetwork.SpecialAttackPayload());
        }
    }
}
