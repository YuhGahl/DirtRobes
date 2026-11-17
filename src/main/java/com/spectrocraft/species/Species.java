package com.spectrocraft.species;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;

/**
 * Une espèce de Spectrobe, lue depuis data/&lt;namespace&gt;/spectrobes/species/&lt;id&gt;.json.
 * Les stats de base suivent le modèle Pokémon (PV, ATQ, DÉF).
 */
public record Species(
        ResourceLocation id,
        String name,
        Property property,
        Stage stage,
        String family,
        @Nullable ResourceLocation evolvesInto,
        int evolutionLevel,
        int searchRange,
        int baseHp,
        int baseAttack,
        int baseDefense,
        float scale,
        boolean fromFossil
) {
    public static Species fromJson(ResourceLocation id, JsonObject json) {
        JsonObject stats = GsonHelper.getAsJsonObject(json, "base_stats", new JsonObject());
        Stage stage = Stage.byId(GsonHelper.getAsString(json, "stage", "child"));
        String evo = GsonHelper.getAsString(json, "evolves_into", "");
        return new Species(
                id,
                GsonHelper.getAsString(json, "name", id.getPath()),
                Property.byId(GsonHelper.getAsString(json, "property", "prisma")),
                stage,
                GsonHelper.getAsString(json, "family", id.getPath()),
                evo.isEmpty() ? null : ResourceLocation.parse(evo),
                GsonHelper.getAsInt(json, "evolution_level", stage == Stage.CHILD ? 10 : 30),
                GsonHelper.getAsInt(json, "search_range", 3),
                GsonHelper.getAsInt(stats, "hp", 50),
                GsonHelper.getAsInt(stats, "attack", 50),
                GsonHelper.getAsInt(stats, "defense", 50),
                GsonHelper.getAsFloat(json, "scale", stage.scale()),
                GsonHelper.getAsBoolean(json, "from_fossil", stage == Stage.CHILD)
        );
    }

    public String translationKey() {
        return "species." + id.getNamespace() + "." + id.getPath();
    }
}
