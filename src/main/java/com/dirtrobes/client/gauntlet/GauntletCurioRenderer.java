package com.dirtrobes.client.gauntlet;

import com.dirtrobes.DirtRobes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.EntityModel;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Affiche le gantelet sur le bras droit quand il est porté dans l'emplacement Curios (classe chargée seulement avec Curios). */
public class GauntletCurioRenderer implements ICurioRenderer {
    public static final ResourceLocation TEXTURE = DirtRobes.id("textures/models/gauntlet_arm.png");

    public static void register() {
        CuriosRendererRegistry.register(com.dirtrobes.registry.ModRegistry.GAUNTLET.get(), GauntletCurioRenderer::new);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GauntletCurioRenderer::onRenderArm);
    }

    /** Première personne : le gantelet sur le bras droit visible à l'écran. */
    private static void onRenderArm(net.neoforged.neoforge.client.event.RenderArmEvent event) {
        if (event.getArm() != net.minecraft.world.entity.HumanoidArm.RIGHT) return;
        AbstractClientPlayer player = event.getPlayer();
        boolean worn = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(com.dirtrobes.registry.ModRegistry.GAUNTLET.get()))
                .map(r -> r.slotContext().visible()).orElse(false);
        if (!worn) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer)) return;
        PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
        ModelPart arm = model.rightArm;
        float xr = arm.xRot, yr = arm.yRot, zr = arm.zRot;
        arm.xRot = 0f;
        arm.yRot = 0f;
        arm.zRot = 0.1f;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        arm.translateAndRotate(pose);
        boolean slimArms = "slim".equals(player.getSkin().model().id());
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        GauntletArmModel.get(slimArms).render(pose, vc, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
        arm.xRot = xr;
        arm.yRot = yr;
        arm.zRot = zr;
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack pose,
                                                                         RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                                                                         float limbSwing, float limbSwingAmount, float partialTicks,
                                                                         float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> humanoid)) return;
        boolean slimArms = slotContext.entity() instanceof AbstractClientPlayer p && "slim".equals(p.getSkin().model().id());
        pose.pushPose();
        humanoid.rightArm.translateAndRotate(pose);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        GauntletArmModel.get(slimArms).render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
