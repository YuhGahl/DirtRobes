package com.dirtrobes.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lie une espèce à son modèle, sa texture et son poser (assets/&lt;ns&gt;/bedrock/spectrobes/resolvers/*.json).
 * <ul>
 *   <li>« tint » : la texture est teintée de la couleur de la propriété (utile pour les modèles provisoires) ;</li>
 *   <li>« variant_textures » / « variant_emissives » : textures des couleurs Chroma 2 et Chroma 3 (dans cet ordre).
 *   Sans elles, une teinte de secours est appliquée.</li>
 *   <li>« scale » : agrandit ou réduit le modèle à l'affichage (1 par défaut), sans toucher à la hitbox.</li>
 * </ul>
 */
public record Resolver(String key, ResourceLocation model, ResourceLocation texture, @Nullable ResourceLocation emissive,
                       ResourceLocation poser, boolean tint, List<ResourceLocation> variantTextures, List<ResourceLocation> variantEmissives,
                       float scale) {
    public static Resolver parse(JsonObject json, String fallbackKey) {
        String emissive = GsonHelper.getAsString(json, "emissive", "");
        return new Resolver(
                GsonHelper.getAsString(json, "species", fallbackKey),
                ResourceLocation.parse(GsonHelper.getAsString(json, "model")),
                ResourceLocation.parse(GsonHelper.getAsString(json, "texture")),
                emissive.isEmpty() ? null : ResourceLocation.parse(emissive),
                ResourceLocation.parse(GsonHelper.getAsString(json, "poser")),
                GsonHelper.getAsBoolean(json, "tint", false),
                list(json, "variant_textures"),
                list(json, "variant_emissives"),
                GsonHelper.getAsFloat(json, "scale", 1f));
    }

    private static List<ResourceLocation> list(JsonObject json, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (json.has(key)) {
            JsonArray arr = GsonHelper.getAsJsonArray(json, key);
            arr.forEach(e -> out.add(ResourceLocation.parse(e.getAsString())));
        }
        return out;
    }

    /** Texture pour une couleur (0 = normale, 1 et 2 = couleurs spéciales), ou null si le resolver n'en a pas. */
    @Nullable
    public ResourceLocation variantTexture(int color) {
        return color > 0 && color <= variantTextures.size() ? variantTextures.get(color - 1) : null;
    }

    @Nullable
    public ResourceLocation variantEmissive(int color) {
        return color > 0 && color <= variantEmissives.size() ? variantEmissives.get(color - 1) : null;
    }
}
