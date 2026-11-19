package com.dirtrobes.client;

import com.dirtrobes.client.screen.PenScreen;
import com.dirtrobes.lab.Pen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Pendant le réglage d'un enclos : contour vert de la zone et colonne jaune au point de spawn. */
public final class PenOutlineRenderer {
    private PenOutlineRenderer() {}

    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Pen pen = PenScreen.editingPen();
        if (pen == null) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, lines, pen.box(), 0.4f, 1f, 0.6f, 1f);
        AABB spawn = new AABB(pen.spawn()).deflate(0.25, 0, 0.25).setMaxY(pen.spawn().getY() + 2.0);
        LevelRenderer.renderLineBox(pose, lines, spawn, 1f, 0.85f, 0.2f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
