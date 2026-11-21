package com.dirtrobes.client.gauntlet;

import com.dirtrobes.gauntlet.GauntletActions;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * La lame déployée du gantelet : l'arme rangée dedans (épée, Lame, Poing, Canon) est dessinée dans la main droite,
 * en vue à la première et à la troisième personne, sans jamais sortir dans l'inventaire.
 */
public final class GauntletBladeRenderer {
    private GauntletBladeRenderer() {}

    /** Première personne (main vide). */
    public static void onRenderArm(RenderArmEvent event) {
        if (event.getArm() != HumanoidArm.RIGHT) return;
        AbstractClientPlayer player = event.getPlayer();
        ItemStack weapon = GauntletActions.activeWeapon(player);
        if (weapon.isEmpty()) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        ModelPart arm = renderer.getModel().rightArm;
        float xr = arm.xRot, yr = arm.yRot, zr = arm.zRot;
        arm.xRot = 0f;
        arm.yRot = 0f;
        arm.zRot = 0.1f;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        arm.translateAndRotate(pose);
        renderInHand(player, weapon, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
        arm.xRot = xr;
        arm.yRot = yr;
        arm.zRot = zr;
    }

    /** Taille de la lame (1 = taille d'un objet posé). */
    private static final float SCALE = 0.8f;

    /**
     * La lame part du cristal (face extérieure de l'avant-bras) et longe le bras vers la main, comme une lame de
     * poignet. La pose doit être celle du bras droit (épaule en haut, main à y = 10/16).
     */
    static void renderInHand(AbstractClientPlayer player, ItemStack weapon, PoseStack pose, MultiBufferSource buffers, int light) {
        // garde de l'épée (texel 4,11 de la texture) placée sur le cristal ; la pointe vers la main et au-delà
        float guard = 0.309f * SCALE;
        pose.translate(-3.4f / 16f, 7f / 16f + guard, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(90f));   // plat de la lame tourné vers l'extérieur du bras
        pose.mulPose(Axis.ZP.rotationDegrees(45f));   // diagonale de l'icône alignée sur l'axe du bras
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(weapon, ItemDisplayContext.NONE, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
    }

    /** Troisième personne : couche ajoutée aux modèles de joueur. */
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
        }
    }

    /** Clic droit main vide avec le Canon déployé : tir. */
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !GauntletActions.activeWeapon(player).is(ModRegistry.GAUNTLET_CANNON.get())) return;
        PacketDistributor.sendToServer(new ModNetwork.GauntletPayload(3));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack weapon = GauntletActions.activeWeapon(player);
            if (weapon.isEmpty()) return;
            pose.pushPose();
            getParentModel().rightArm.translateAndRotate(pose);
            renderInHand(player, weapon, pose, buffers, light);
            pose.popPose();
        }
    }
}
