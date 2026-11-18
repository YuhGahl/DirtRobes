package com.dirtrobes.client.screen;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Petits outils de dessin partagés par les interfaces DirtRobes (tout est dessiné par le code, sans texture). */
public final class GuiDraw {
    private GuiDraw() {}

    /**
     * Dessine le modèle 3D d'une créature centré en (x, y = bas des pieds).
     * @param yaw rotation en degrés autour de l'axe vertical
     */
    public static void entity(GuiGraphics g, LivingEntity entity, float x, float y, float scale, float yaw, float pitch) {
        float oldBody = entity.yBodyRot, oldBodyO = entity.yBodyRotO, oldHead = entity.yHeadRot, oldHeadO = entity.yHeadRotO;
        float oldYRot = entity.getYRot(), oldXRot = entity.getXRot();
        entity.yBodyRot = entity.yBodyRotO = 180f + yaw;
        entity.yHeadRot = entity.yHeadRotO = 180f + yaw;
        entity.setYRot(180f + yaw);
        entity.setXRot(0f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
        pose.mul(camera);
        InventoryScreen.renderEntityInInventory(g, x, y, scale, new Vector3f(0, 0, 0), pose, camera, entity);
        entity.yBodyRot = oldBody;
        entity.yBodyRotO = oldBodyO;
        entity.yHeadRot = oldHead;
        entity.yHeadRotO = oldHeadO;
        entity.setYRot(oldYRot);
        entity.setXRot(oldXRot);
    }

    /** Triangle plein (via un quad dégénéré, le mode de RenderType.gui()). */
    public static void triangle(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.addVertex(m, x1, y1, 0).setColor(argb);
        vc.addVertex(m, x2, y2, 0).setColor(argb);
        vc.addVertex(m, x3, y3, 0).setColor(argb);
        vc.addVertex(m, x3, y3, 0).setColor(argb);
        g.flush();
    }

    /** Segment épais d'environ 1 px. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float nx = -dy / len * 0.5f, ny = dx / len * 0.5f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(argb);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(argb);
        vc.addVertex(m, x2 - nx, y2 - ny, 0).setColor(argb);
        vc.addVertex(m, x2 + nx, y2 + ny, 0).setColor(argb);
        g.flush();
    }

    /**
     * Polygone en étoile autour d'un centre : valeurs entre 0 et 1, axes répartis à partir du haut,
     * dans le sens des aiguilles d'une montre.
     */
    public static void radar(GuiGraphics g, float cx, float cy, float radius, float[] values, int fill, int outline) {
        int n = values.length;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / n;
            float r = radius * Mth.clamp(values[i], 0.04f, 1f);
            px[i] = cx + (float) Math.cos(a) * r;
            py[i] = cy + (float) Math.sin(a) * r;
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            // ordre des sommets choisi pour que le triangle soit toujours visible quel que soit le sens
            triangle(g, cx, cy, px[j], py[j], px[i], py[i], fill);
            triangle(g, cx, cy, px[i], py[i], px[j], py[j], fill);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            line(g, px[i], py[i], px[j], py[j], outline);
        }
    }

    /** Grille du radar (losanges concentriques + axes). */
    public static void radarGrid(GuiGraphics g, float cx, float cy, float radius, int axes, int color) {
        for (int ring = 1; ring <= 4; ring++) {
            float r = radius * ring / 4f;
            for (int i = 0; i < axes; i++) {
                double a1 = -Math.PI / 2 + i * 2 * Math.PI / axes, a2 = -Math.PI / 2 + (i + 1) * 2 * Math.PI / axes;
                line(g, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                        cx + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r, color);
            }
        }
        for (int i = 0; i < axes; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / axes;
            line(g, cx, cy, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, color);
        }
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color, int background) {
        g.fill(x, y, x + w, y + h, background);
        g.fill(x, y, x + Math.round(w * Mth.clamp(fraction, 0f, 1f)), y + h, color);
    }

    public static int hpColor(float fraction, boolean ko) {
        if (ko) return 0xFF7A7A7A;
        return fraction > 0.5f ? 0xFF5BD46A : fraction > 0.2f ? 0xFFE8C84A : 0xFFE0533D;
    }

    /** Panneau sombre avec un liseré coloré. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, 0xF0121820);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF01A222C);
        g.renderOutline(x, y, w, h, accent);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
