package com.dirtrobes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.dirtrobes.DirtRobes;
import com.dirtrobes.client.hud.ChGaugeHud;
import com.dirtrobes.client.hud.TeamHud;
import com.dirtrobes.client.screen.SpectrobeSummaryScreen;
import com.dirtrobes.client.model.ModelRepository;
import com.dirtrobes.client.render.BedrockEntityRenderer;
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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = DirtRobes.MODID, dist = Dist.CLIENT)
public class DirtRobesClient {
    public static final KeyMapping SPECIAL_ATTACK = new KeyMapping("key.dirtrobes.special_attack",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.dirtrobes");
    public static final KeyMapping TEAM_UP = new KeyMapping("key.dirtrobes.team_up",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, "key.categories.dirtrobes");
    public static final KeyMapping TEAM_DOWN = new KeyMapping("key.dirtrobes.team_down",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "key.categories.dirtrobes");
    public static final KeyMapping TEAM_TOGGLE = new KeyMapping("key.dirtrobes.team_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.dirtrobes");
    public static final KeyMapping TEAM_SUMMARY = new KeyMapping("key.dirtrobes.team_summary",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.dirtrobes");

    public DirtRobesClient(IEventBus modBus) {
        modBus.addListener(DirtRobesClient::onRenderers);
        modBus.addListener(DirtRobesClient::onReloadListeners);
        modBus.addListener(DirtRobesClient::onKeys);
        modBus.addListener(DirtRobesClient::onMenuScreens);
        modBus.addListener(DirtRobesClient::onGuiLayers);
        NeoForge.EVENT_BUS.addListener(DirtRobesClient::onClientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.SPECTROBE.get(), BedrockEntityRenderer::new);
        event.registerEntityRenderer(ModRegistry.KRAWL.get(), BedrockEntityRenderer::new);
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ModelRepository());
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, DirtRobes.id("ch_gauge"), ChGaugeHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, DirtRobes.id("team"), TeamHud::render);
    }

    private static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.INCUBATOR_MENU.get(), IncubatorScreen::new);
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(SPECIAL_ATTACK);
        event.register(TEAM_UP);
        event.register(TEAM_DOWN);
        event.register(TEAM_TOGGLE);
        event.register(TEAM_SUMMARY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientTeam.tickPreviews();
        while (SPECIAL_ATTACK.consumeClick()) {
            PacketDistributor.sendToServer(new ModNetwork.SpecialAttackPayload());
        }
        while (TEAM_UP.consumeClick()) ClientTeam.select(-1);
        while (TEAM_DOWN.consumeClick()) ClientTeam.select(1);
        while (TEAM_TOGGLE.consumeClick()) {
            if (!ClientTeam.members().isEmpty()) PacketDistributor.sendToServer(new ModNetwork.TeamActionPayload(ClientTeam.selected()));
        }
        while (TEAM_SUMMARY.consumeClick()) {
            if (!ClientTeam.members().isEmpty()) mc.setScreen(new SpectrobeSummaryScreen(null, ClientTeam.selected()));
        }
    }
}
