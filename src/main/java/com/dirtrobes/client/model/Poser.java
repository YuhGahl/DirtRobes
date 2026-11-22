package com.dirtrobes.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Poser façon Cobblemon : quelles animations jouer selon la situation (repos, marche, combat, sommeil),
 * plus les animations ponctuelles (cri, recul, K.O.) et les « quirks » aléatoires (clignement).
 * Les instructions s'écrivent comme dans Cobblemon : q.bedrock('groupe', 'anim'), q.look('head'),
 * q.quadruped_walk(période, amplitude, 'patte_av_g', 'patte_av_d', 'patte_ar_g', 'patte_ar_d').
 */
public final class Poser {
    public enum PoseType { STAND, WALK, SLEEP, FLY, HOVER, SHOULDER, NONE }

    public static final class PoseDef {
        public final String name;
        public final List<PoseType> types = new ArrayList<>();
        public Boolean battle; // null = peu importe
        public final List<Instruction> animations = new ArrayList<>();
        public final List<Instruction> quirks = new ArrayList<>();

        PoseDef(String name) {
            this.name = name;
        }
    }

    /** Une instruction : nom de fonction + arguments texte. */
    public record Instruction(String function, List<String> args) {
        public String arg(int i, String def) {
            return i < args.size() ? args.get(i) : def;
        }

        public float floatArg(int i, float def) {
            try {
                return Float.parseFloat(arg(i, String.valueOf(def)));
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }

    public String rootBone = "";
    public final Map<String, PoseDef> poses = new LinkedHashMap<>();
    public final Map<String, Instruction> oneShots = new LinkedHashMap<>();

    private static final Pattern CALL = Pattern.compile("^(?:q\\.|query\\.)?([a-z_]+)\\s*\\((.*)\\)\\s*$");

    public static Instruction instruction(String text) {
        Matcher m = CALL.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return new Instruction(text.trim(), List.of());
        List<String> args = new ArrayList<>();
        String inner = m.group(2);
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : inner.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                args.add(clean(cur.toString()));
                cur.setLength(0);
            } else cur.append(c);
        }
        if (!cur.toString().isBlank()) args.add(clean(cur.toString()));
        return new Instruction(m.group(1), args);
    }

    private static String clean(String s) {
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) s = s.substring(1, s.length() - 1);
        return s;
    }

    public static Poser parse(JsonObject json) {
        Poser p = new Poser();
        p.rootBone = GsonHelper.getAsString(json, "rootBone", "");
        JsonObject oneShots = GsonHelper.getAsJsonObject(json, "animations", new JsonObject());
        for (Map.Entry<String, JsonElement> e : oneShots.entrySet()) p.oneShots.put(e.getKey(), instruction(e.getValue().getAsString()));
        JsonObject poses = GsonHelper.getAsJsonObject(json, "poses", new JsonObject());
        for (Map.Entry<String, JsonElement> e : poses.entrySet()) {
            JsonObject pj = e.getValue().getAsJsonObject();
            PoseDef def = new PoseDef(e.getKey());
            for (JsonElement t : GsonHelper.getAsJsonArray(pj, "poseTypes", new JsonArray())) {
                try {
                    String name = t.getAsString().toUpperCase(Locale.ROOT);
                    // Cobblemon distingue l'épaule gauche et droite ; ici une seule pose
                    if (name.startsWith("SHOULDER")) name = "SHOULDER";
                    PoseType pt = PoseType.valueOf(name);
                    if (!def.types.contains(pt)) def.types.add(pt);
                } catch (IllegalArgumentException ignored) {
                    // types Cobblemon non gérés (PORTRAIT, PROFILE, FLOAT, SWIM…)
                }
            }
            if (pj.has("isBattle")) def.battle = pj.get("isBattle").getAsBoolean();
            for (JsonElement a : GsonHelper.getAsJsonArray(pj, "animations", new JsonArray())) def.animations.add(instruction(a.getAsString()));
            for (JsonElement a : GsonHelper.getAsJsonArray(pj, "quirks", new JsonArray())) def.quirks.add(instruction(a.getAsString()));
            p.poses.put(e.getKey(), def);
        }
        return p;
    }

    /** Choisit la pose : même type et même état de combat d'abord, puis même type, puis la première. */
    public PoseDef select(PoseType type, boolean battle) {
        PoseDef fallback = null;
        for (PoseDef d : poses.values()) {
            if (d.types.contains(type)) {
                if (d.battle == null || d.battle == battle) return d;
                if (fallback == null) fallback = d;
            }
        }
        if (fallback != null) return fallback;
        if (type == PoseType.WALK || type == PoseType.SHOULDER) return select(PoseType.STAND, battle);
        if (type == PoseType.FLY) return select(PoseType.HOVER, battle);
        if (type == PoseType.HOVER) return select(PoseType.WALK, battle);
        return poses.isEmpty() ? null : poses.values().iterator().next();
    }
}
