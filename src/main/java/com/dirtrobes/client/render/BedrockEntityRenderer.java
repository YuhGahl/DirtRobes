package com.dirtrobes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.dirtrobes.DirtRobes;
import com.dirtrobes.client.model.BedrockAnimation;
import com.dirtrobes.client.model.BedrockModel;
import com.dirtrobes.client.model.ModelRepository;
import com.dirtrobes.client.model.MoLang;
import com.dirtrobes.client.model.Pose;
import com.dirtrobes.client.model.Poser;
import com.dirtrobes.client.model.Resolver;
import com.dirtrobes.entity.BedrockPosable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rendu maison (inspiré de Cobblemon) : modèle Bedrock + poser + animations Bedrock, sans GeckoLib.
 * Sert aux Spectrobes comme aux Krawl.
 */
public class BedrockEntityRenderer<T extends LivingEntity & BedrockPosable> extends EntityRenderer<T> {
    private static final ResourceLocation MISSING = DirtRobes.id("textures/spectrobes/placeholder.png");

    public BedrockEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.45f;
    }

    /** État d'animation propre à chaque Spectrobe (clignements, cris, recul). */
    private static final class AnimState {
        float quirkStart = -100f;
        float nextQuirk = 2f;
        float cryStart = -100f;
        int seenCryTick = -1000;
        float recoilStart = -100f;
        float faintStart = -1f;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        Resolver r = ModelRepository.resolver(entity.getSpeciesId(), entity.resolverStage());
        return r == null ? MISSING : r.texture();
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Resolver resolver = ModelRepository.resolver(entity.getSpeciesId(), entity.resolverStage());
        BedrockModel model = resolver == null ? null : ModelRepository.model(resolver.model());
        if (model != null) {
            Poser poser = ModelRepository.poser(resolver.poser());
            Pose pose = computePose(entity, poser, partialTick);
            poseStack.pushPose();
            float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
            float scale = entity.getScale() / 16f;
            poseStack.scale(scale, scale, scale);

            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);
            int variant = entity.colorVariant();
            ResourceLocation tex = resolver.variantTexture(variant);
            int color = resolver.tint() ? tint(chromaShift(entity.tintColor(), tex == null ? variant : 0), 0.45f)
                    : (tex == null ? chromaFallback(variant) : 0xFFFFFFFF);
            VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(tex != null ? tex : resolver.texture()));
            for (BedrockModel.Bone bone : model.roots) renderBone(bone, pose, poseStack, base, packedLight, overlay, color);
            ResourceLocation glowTex = resolver.variantEmissive(variant) != null ? resolver.variantEmissive(variant) : resolver.emissive();
            if (glowTex != null) {
                int glow = resolver.tint() ? (0xFF000000 | chromaShift(entity.tintColor(), tex == null ? variant : 0)) : 0xFFFFFFFF;
                VertexConsumer emissive = buffers.getBuffer(RenderType.eyes(glowTex));
                for (BedrockModel.Bone bone : model.roots) renderBone(bone, pose, poseStack, emissive, LightTexture.FULL_BRIGHT, overlay, glow);
            }
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        // Étiquette « Nom Niv. X » quand on vise la créature (comme Cobblemon)
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.crosshairPickEntity == entity && !entity.isInvisible() && entity.nameTag() != null && !mc.options.hideGui) {
            renderNameTag(entity, entity.nameTag(), poseStack, buffers, packedLight, partialTick);
        }
    }

    /** Mélange la couleur de la propriété avec du blanc (amount = part de blanc). */
    /** Couleur Chroma de secours sur un modèle provisoire teinté : décale la teinte de 120° ou 240°. */
    private static int chromaShift(int rgb, int variant) {
        if (variant <= 0) return rgb;
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return java.awt.Color.HSBtoRGB((hsb[0] + variant / 3f) % 1f, hsb[1], hsb[2]) & 0xFFFFFF;
    }

    /** Couleur Chroma de secours sur une vraie texture sans variante : légère teinte dorée ou bleutée. */
    private static int chromaFallback(int variant) {
        return switch (variant) {
            case 1 -> 0xFFFFD98A;
            case 2 -> 0xFFA9C4FF;
            default -> 0xFFFFFFFF;
        };
    }

    private static int tint(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void renderBone(BedrockModel.Bone bone, Pose pose, PoseStack stack, VertexConsumer vc, int light, int overlay, int color) {
        stack.pushPose();
        Pose.BoneTransform bt = pose.peek(bone.name);
        float rx = bone.rotX, ry = bone.rotY, rz = bone.rotZ;
        if (bt != null) {
            stack.translate(bt.px, bt.py, bt.pz);
            rx += bt.rx;
            ry += bt.ry;
            rz += bt.rz;
        }
        stack.translate(bone.pivotX, bone.pivotY, bone.pivotZ);
        if (rz != 0) stack.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0) stack.mulPose(Axis.YP.rotation(ry));
        if (rx != 0) stack.mulPose(Axis.XP.rotation(rx));
        if (bt != null && (bt.sx != 1 || bt.sy != 1 || bt.sz != 1)) {
            stack.scale(Math.max(bt.sx, 0.001f), Math.max(bt.sy, 0.001f), Math.max(bt.sz, 0.001f));
        }
        stack.translate(-bone.pivotX, -bone.pivotY, -bone.pivotZ);

        PoseStack.Pose last = stack.last();
        for (BedrockModel.Quad q : bone.quads) {
            for (int k = 0; k < 4; k++) {
                vc.addVertex(last, q.x[k], q.y[k], q.z[k])
                        .setColor(color)
                        .setUv(q.u[k], q.v[k])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(last, q.nx, q.ny, q.nz);
            }
        }
        for (BedrockModel.Bone child : bone.children) renderBone(child, pose, stack, vc, light, overlay, color);
        stack.popPose();
    }

    // =====================================================================
    // Calcul de la pose (poser + animations)
    // =====================================================================

    private Pose computePose(T entity, Poser poser, float pt) {
        Pose pose = new Pose();
        if (poser == null) return pose;
        AnimState state = entity.clientAnimState() instanceof AnimState s ? s : new AnimState();
        entity.setClientAnimState(state);

        float now = (entity.tickCount + pt) / 20f;
        MoLang.Context ctx = new MoLang.Context();
        ctx.lifeTime = now;

        boolean down = entity.isDown();
        if (down) {
            if (state.faintStart < 0) state.faintStart = now;
            Poser.Instruction faint = poser.oneShots.get("faint");
            if (faint != null) {
                Poser.PoseDef stand = poser.select(Poser.PoseType.STAND, false);
                if (stand != null) runAll(stand.animations, entity, pose, ctx, now, pt, true);
                run(faint, entity, pose, ctx, now - state.faintStart, pt);
                return pose;
            }
        } else {
            state.faintStart = -1f;
        }

        Poser.PoseType type;
        if (entity.isPoseSleeping()) type = Poser.PoseType.SLEEP;
        else if (entity.isFlyingNow()) type = entity.getDeltaMovement().horizontalDistanceSqr() > 0.0004 ? Poser.PoseType.FLY : Poser.PoseType.HOVER;
        else if (entity.walkAnimation.speed(pt) > 0.05f) type = Poser.PoseType.WALK;
        else type = Poser.PoseType.STAND;

        Poser.PoseDef def = poser.select(type, entity.isInBattle());
        if (def != null) {
            runAll(def.animations, entity, pose, ctx, now, pt, false);
            // Quirks (clignement…) déclenchés au hasard toutes les 3 à 7 secondes.
            if (!def.quirks.isEmpty()) {
                if (now >= state.nextQuirk) {
                    state.quirkStart = now;
                    state.nextQuirk = now + 3f + entity.getRandom().nextFloat() * 4f;
                }
                for (Poser.Instruction q : def.quirks) run(q, entity, pose, ctx, now - state.quirkStart, pt);
            }
        }

        // Animations ponctuelles : cri / attaque spéciale, recul quand il est touché.
        if (entity.lastCryTick() != state.seenCryTick) {
            state.seenCryTick = entity.lastCryTick();
            state.cryStart = now;
        }
        if (entity.hurtTime == entity.hurtDuration && entity.hurtTime > 0) state.recoilStart = now;
        playOneShot(poser, "cry", entity, pose, ctx, now - state.cryStart, pt);
        playOneShot(poser, "recoil", entity, pose, ctx, now - state.recoilStart, pt);
        return pose;
    }

    private void playOneShot(Poser poser, String key, T entity, Pose pose, MoLang.Context ctx, float elapsed, float pt) {
        Poser.Instruction ins = poser.oneShots.get(key);
        if (ins == null || elapsed < 0 || elapsed > 5f) return;
        BedrockAnimation anim = ModelRepository.animation(ins.arg(0, ""), ins.arg(1, ""));
        if (anim != null && !(anim.length > 0 && elapsed > anim.length)) anim.apply(pose, ctx, elapsed, 1f);
    }

    private void runAll(java.util.List<Poser.Instruction> list, T entity, Pose pose, MoLang.Context ctx, float now, float pt,
                        boolean skipWalk) {
        for (Poser.Instruction ins : list) {
            if (skipWalk && ins.function().equals("quadruped_walk")) continue;
            run(ins, entity, pose, ctx, now, pt);
        }
    }

    private void run(Poser.Instruction ins, T entity, Pose pose, MoLang.Context ctx, float time, float pt) {
        switch (ins.function()) {
            case "bedrock", "bedrock_quirk", "bedrock_stateful", "bedrock_primary" -> {
                BedrockAnimation anim = ModelRepository.animation(ins.arg(0, ""), ins.arg(1, ""));
                if (anim != null && time >= 0 && !anim.finished(time)) anim.apply(pose, ctx, time, 1f);
            }
            case "look" -> {
                Pose.BoneTransform head = pose.get(ins.arg(0, "head"));
                float bodyYaw = Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot);
                float headYaw = Mth.rotLerp(pt, entity.yHeadRotO, entity.yHeadRot);
                float yaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -60f, 60f);
                float pitch = Mth.clamp(entity.getViewXRot(pt), -45f, 45f);
                head.ry += -yaw * Mth.DEG_TO_RAD;
                head.rx += -pitch * Mth.DEG_TO_RAD;
            }
            case "quadruped_walk" -> {
                float period = Math.max(0.1f, ins.floatArg(0, 1f));
                float amplitude = ins.floatArg(1, 1f);
                float position = entity.walkAnimation.position(pt);
                float speed = Math.min(1f, entity.walkAnimation.speed(pt));
                float swing = Mth.cos(position * 0.6662f / period) * amplitude * speed;
                pose.get(ins.arg(2, "front_leg_left")).rx += swing;
                pose.get(ins.arg(3, "front_leg_right")).rx -= swing;
                pose.get(ins.arg(4, "back_leg_left")).rx -= swing;
                pose.get(ins.arg(5, "back_leg_right")).rx += swing;
            }
            default -> {
                // instruction inconnue : ignorée
            }
        }
    }
}
