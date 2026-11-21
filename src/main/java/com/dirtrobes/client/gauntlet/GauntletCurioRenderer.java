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

    private ModelPart wide;
    private ModelPart slim;

    public static void register() {
        CuriosRendererRegistry.register(com.dirtrobes.registry.ModRegistry.GAUNTLET.get(), GauntletCurioRenderer::new);
    }

    private ModelPart model(boolean slimArms) {
        if (wide == null) {
            wide = Minecraft.getInstance().getEntityModels().bakeLayer(GauntletArmModel.LAYER);
            slim = Minecraft.getInstance().getEntityModels().bakeLayer(GauntletArmModel.LAYER_SLIM);
        }
        return slimArms ? slim : wide;
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
        model(slimArms).render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
