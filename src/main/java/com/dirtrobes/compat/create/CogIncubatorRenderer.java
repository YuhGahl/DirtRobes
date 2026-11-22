package com.dirtrobes.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Engrenage de l'incubateur à engrenage, qui tourne à la vitesse de Create. Même décalage que les engrenages de
 * Create (22,5° une case sur deux) pour que les dents s'emboîtent avec les voisins.
 */
public class CogIncubatorRenderer implements BlockEntityRenderer<CogIncubatorBlockEntity> {
    public CogIncubatorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CogIncubatorBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockPos pos = be.getBlockPos();
        float time = be.getLevel().getGameTime() % 72000 + partialTick;
        float offset = Math.floorMod(pos.getX() + pos.getZ(), 2) == 0 ? 22.5f : 0f;
        float angle = (time * be.getSpeed() * 3f / 10f + offset) % 360f;
        BakedModel cog = AllPartialModels.SHAFTLESS_COGWHEEL.get();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(angle));
        ms.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ms.last(), buffers.getBuffer(RenderType.solid()),
                be.getBlockState(), cog, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.solid());
        ms.popPose();
    }
}
