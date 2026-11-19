package com.dirtrobes.client;

import com.dirtrobes.lab.Pen;
import com.dirtrobes.lab.PenMarkerItem;
import com.dirtrobes.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Affiche le contour de l'enclos (et des coins choisis) quand on tient le Délimiteur d'enclos. */
public final class PenOutlineRenderer {
    private PenOutlineRenderer() {}

    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem().is(ModRegistry.PEN_MARKER.get()) ? mc.player.getMainHandItem()
                : mc.player.getOffhandItem().is(ModRegistry.PEN_MARKER.get()) ? mc.player.getOffhandItem() : ItemStack.EMPTY;
        if (stack.isEmpty()) return;
        CompoundTag tag = PenMarkerItem.data(stack);
        BlockPos a = PenMarkerItem.pos(tag, "A"), b = PenMarkerItem.pos(tag, "B");
        if (a == null && b == null) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (a != null && b != null) {
            Pen pen = Pen.of(a, b);
            boolean ok = pen.isValidSize();
            LevelRenderer.renderLineBox(pose, lines, pen.box(), ok ? 0.4f : 1f, ok ? 1f : 0.3f, ok ? 0.6f : 0.3f, 1f);
        }
        if (a != null) LevelRenderer.renderLineBox(pose, lines, new AABB(a).inflate(0.02), 1f, 0.85f, 0.2f, 1f);
        if (b != null) LevelRenderer.renderLineBox(pose, lines, new AABB(b).inflate(0.02), 1f, 0.85f, 0.2f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
