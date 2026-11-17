package com.dirtrobes.krawl;

import com.dirtrobes.species.Property;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Une espèce de Krawl, lue depuis data/&lt;namespace&gt;/spectrobes/krawl/&lt;id&gt;.json.
 * <ul>
 *   <li>behaviour : « melee » (corps à corps) ou « spore » (soigne les Krawl proches et en fait apparaître)</li>
 *   <li>min_level / max_level : niveau tiré à l'apparition</li>
 *   <li>weight : chance relative d'apparaître (0 = jamais naturellement)</li>
 * </ul>
 */
public record KrawlSpecies(
        ResourceLocation id,
        String name,
        Property property,
        String behaviour,
        int baseHp,
        int baseAttack,
        int baseDefense,
        int minLevel,
        int maxLevel,
        float scale,
        float speed,
        int weight
) {
    public static KrawlSpecies fromJson(ResourceLocation id, JsonObject json) {
        JsonObject stats = GsonHelper.getAsJsonObject(json, "base_stats", new JsonObject());
        int min = GsonHelper.getAsInt(json, "min_level", 3);
        return new KrawlSpecies(
                id,
                GsonHelper.getAsString(json, "name", id.getPath()),
                Property.byId(GsonHelper.getAsString(json, "property", "eclipse")),
                GsonHelper.getAsString(json, "behaviour", "melee"),
                GsonHelper.getAsInt(stats, "hp", 50),
                GsonHelper.getAsInt(stats, "attack", 50),
                GsonHelper.getAsInt(stats, "defense", 50),
                min,
                Math.max(min, GsonHelper.getAsInt(json, "max_level", min + 10)),
                GsonHelper.getAsFloat(json, "scale", 1.0f),
                GsonHelper.getAsFloat(json, "speed", 0.25f),
                GsonHelper.getAsInt(json, "weight", 10)
        );
    }

    public String translationKey() {
        return "krawl." + id.getNamespace() + "." + id.getPath();
    }

    public boolean isSpore() {
        return "spore".equals(behaviour);
    }
}
