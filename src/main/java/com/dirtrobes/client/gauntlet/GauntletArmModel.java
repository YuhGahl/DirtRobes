package com.dirtrobes.client.gauntlet;

import com.dirtrobes.DirtRobes;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Modèle du gantelet porté sur le bras droit (coordonnées du bras du joueur : épaule en haut, main à y = 10).
 * Manchette autour de l'avant-bras + plaque sur le dos de la main avec un cristal. Texture 32 × 32.
 */
public final class GauntletArmModel {
    private GauntletArmModel() {}

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(DirtRobes.id("gauntlet_arm"), "main");
    public static final ModelLayerLocation LAYER_SLIM = new ModelLayerLocation(DirtRobes.id("gauntlet_arm_slim"), "main");

    public static LayerDefinition create(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float w = slim ? 3f : 4f;
        float x0 = -3f;
        root.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, 0)
                .addBox(x0, 4f, -2f, w, 6f, 4f, new CubeDeformation(0.45f)), PartPose.ZERO);
        root.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 12)
                .addBox(x0 - 0.6f, 5f, -1.5f, 1f, 4f, 3f, new CubeDeformation(0.1f)), PartPose.ZERO);
        root.addOrReplaceChild("crystal", CubeListBuilder.create().texOffs(10, 12)
                .addBox(x0 - 1.2f, 6.2f, -0.7f, 1f, 1.6f, 1.4f, new CubeDeformation(0.05f)), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static ModelPart bake(ModelPart root) {
        return root;
    }
}
