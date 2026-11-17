package com.spectrocraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spectrocraft.SpectroCraft;
import com.spectrocraft.client.model.ModelRepository;
import com.spectrocraft.client.render.SpectrobeRenderer;
import com.spectrocraft.network.ModNetwork;
import com.spectrocraft.registry.ModRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = SpectroCraft.MODID, dist = Dist.CLIENT)
public class SpectroCraftClient {
    public static final KeyMapping SPECIAL_ATTACK = new KeyMapping("key.spectrocraft.special_attack",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.spectrocraft");

    public SpectroCraftClient(IEventBus modBus) {
        modBus.addListener(SpectroCraftClient::onRenderers);
        modBus.addListener(SpectroCraftClient::onReloadListeners);
        modBus.addListener(SpectroCraftClient::onKeys);
        NeoForge.EVENT_BUS.addListener(SpectroCraftClient::onClientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.SPECTROBE.get(), SpectrobeRenderer::new);
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ModelRepository());
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
