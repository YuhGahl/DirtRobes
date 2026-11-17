package com.dirtrobes.client.model;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;

/**
 * Lie une espèce à son modèle, sa texture et son poser (assets/&lt;ns&gt;/bedrock/spectrobes/resolvers/*.json).
 * « tint » : la texture est teintée de la couleur de la propriété (utile pour les modèles provisoires).
 */
public record Resolver(String key, ResourceLocation model, ResourceLocation texture, @Nullable ResourceLocation emissive,
                       ResourceLocation poser, boolean tint) {
    public static Resolver parse(JsonObject json, String fallbackKey) {
        String emissive = GsonHelper.getAsString(json, "emissive", "");
        return new Resolver(
                GsonHelper.getAsString(json, "species", fallbackKey),
                ResourceLocation.parse(GsonHelper.getAsString(json, "model")),
                ResourceLocation.parse(GsonHelper.getAsString(json, "texture")),
                emissive.isEmpty() ? null : ResourceLocation.parse(emissive),
                ResourceLocation.parse(GsonHelper.getAsString(json, "poser")),
                GsonHelper.getAsBoolean(json, "tint", false));
    }
}
