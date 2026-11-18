package com.dirtrobes.client.hud;

import com.dirtrobes.DirtRobes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Position du bandeau d'équipe, enregistrée dans config/dirtrobes-client.json.
 * x et y vont de 0 (bord gauche / haut) à 1 (bord droit / bas) : le bandeau reste dans l'écran
 * quelle que soit la taille de la fenêtre. Par défaut : à droite, centré verticalement.
 */
public final class HudLayout {
    private HudLayout() {}

    public static final float DEFAULT_X = 1.0f;
    public static final float DEFAULT_Y = 0.5f;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static float teamX = DEFAULT_X;
    private static float teamY = DEFAULT_Y;
    private static boolean loaded;

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("dirtrobes-client.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f)) {
                JsonObject json = GSON.fromJson(r, JsonObject.class);
                if (json.has("team_hud_x")) teamX = Mth.clamp(json.get("team_hud_x").getAsFloat(), 0f, 1f);
                if (json.has("team_hud_y")) teamY = Mth.clamp(json.get("team_hud_y").getAsFloat(), 0f, 1f);
            }
        } catch (Exception e) {
            DirtRobes.LOGGER.warn("Impossible de lire config/dirtrobes-client.json", e);
        }
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("team_hud_x", teamX);
            json.addProperty("team_hud_y", teamY);
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(json, w);
            }
        } catch (Exception e) {
            DirtRobes.LOGGER.warn("Impossible d'enregistrer config/dirtrobes-client.json", e);
        }
    }

    public static float teamX() {
        load();
        return teamX;
    }

    public static float teamY() {
        load();
        return teamY;
    }

    public static void setTeam(float x, float y) {
        load();
        teamX = Mth.clamp(x, 0f, 1f);
        teamY = Mth.clamp(y, 0f, 1f);
    }

    public static void reset() {
        setTeam(DEFAULT_X, DEFAULT_Y);
    }
}
