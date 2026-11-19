package com.dirtrobes.species;

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
        int baseSpeed,
        float scale,
        boolean fromFossil,
        boolean flying
) {
    /** Copie avec un autre mode de déplacement (tests, commandes). */
    public Species withFlying(boolean value) {
        return new Species(id, name, property, stage, family, evolvesInto, evolutionLevel, searchRange,
                baseHp, baseAttack, baseDefense, baseSpeed, scale, fromFossil, value);
    }

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
                GsonHelper.getAsInt(stats, "speed", defaultSpeed(GsonHelper.getAsInt(stats, "attack", 50), GsonHelper.getAsInt(stats, "defense", 50))),
                GsonHelper.getAsFloat(json, "scale", stage.scale()),
                GsonHelper.getAsBoolean(json, "from_fossil", stage == Stage.CHILD),
                GsonHelper.getAsBoolean(json, "flying", false)
        );
    }

    /** Vitesse par défaut si la fiche n'en donne pas : les attaquants sont rapides, les blindés lents. */
    public static int defaultSpeed(int attack, int defense) {
        return Math.max(20, Math.round((attack + Math.max(20, 140 - defense)) / 2f));
    }

    public String translationKey() {
        return "species." + id.getNamespace() + "." + id.getPath();
    }
}
