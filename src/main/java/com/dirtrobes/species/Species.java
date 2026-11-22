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
        boolean flying,
        Rarity rarity,
        @Nullable ResourceLocation rareEvolution,
        float rareEvolutionChance,
        float hitboxWidth,
        float hitboxHeight,
        float seatHeight
) {
    /** Copie avec un autre mode de déplacement (tests, commandes). */
    public Species withFlying(boolean value) {
        return new Species(id, name, property, stage, family, evolvesInto, evolutionLevel, searchRange,
                baseHp, baseAttack, baseDefense, baseSpeed, scale, fromFossil, value, rarity, rareEvolution, rareEvolutionChance,
                hitboxWidth, hitboxHeight, seatHeight);
    }

    public static Species fromJson(ResourceLocation id, JsonObject json) {
        JsonObject stats = GsonHelper.getAsJsonObject(json, "base_stats", new JsonObject());
        Stage stage = Stage.byId(GsonHelper.getAsString(json, "stage", "child"));
        String evo = GsonHelper.getAsString(json, "evolves_into", "");
        int hp = GsonHelper.getAsInt(stats, "hp", 50);
        int atk = GsonHelper.getAsInt(stats, "attack", 50);
        int def = GsonHelper.getAsInt(stats, "defense", 50);
        boolean secret = stage == Stage.SECRET || stage == Stage.SPECIAL;
        Rarity rarity = Rarity.byId(GsonHelper.getAsString(json, "rarity", secret ? "secret" : "common"));
        // stats brutes de la liste des Spectrobes (document de conception), sans aucun bonus ajouté
        int spd = GsonHelper.getAsInt(stats, "speed", defaultSpeed(atk, def));
        ResourceLocation rareEvo = null;
        float rareChance = 0f;
        if (json.has("rare_evolution")) {
            JsonObject rare = GsonHelper.getAsJsonObject(json, "rare_evolution");
            rareEvo = ResourceLocation.parse(GsonHelper.getAsString(rare, "species"));
            rareChance = GsonHelper.getAsFloat(rare, "chance", 0.05f);
        }
        // hitbox mesurée sur le modèle (tools/models/gen_hitboxes.py), en blocs avant « scale »
        JsonObject hb = GsonHelper.getAsJsonObject(json, "hitbox", new JsonObject());
        return new Species(
                id,
                GsonHelper.getAsString(json, "name", id.getPath()),
                Property.byId(GsonHelper.getAsString(json, "property", "prisma")),
                stage,
                GsonHelper.getAsString(json, "family", id.getPath()),
                evo.isEmpty() ? null : ResourceLocation.parse(evo),
                GsonHelper.getAsInt(json, "evolution_level", stage == Stage.CHILD ? 10 : 30),
                GsonHelper.getAsInt(json, "search_range", 3),
                hp, atk, def, spd,
                GsonHelper.getAsFloat(json, "scale", stage.scale()),
                GsonHelper.getAsBoolean(json, "from_fossil", stage == Stage.CHILD || secret),
                GsonHelper.getAsBoolean(json, "flying", false),
                rarity,
                rareEvo,
                rareChance,
                GsonHelper.getAsFloat(hb, "width", 0.7f),
                GsonHelper.getAsFloat(hb, "height", 0.9f),
                // hauteur du dos (selle), mesurée sur le modèle ; 0 = haut de la hitbox
                GsonHelper.getAsFloat(hb, "seat", 0f)
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
