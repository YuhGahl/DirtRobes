package com.spectrocraft.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Animation au format Bedrock (.animation.json, Blockbench). */
public final class BedrockAnimation {
    public enum Loop { ONCE, LOOP, HOLD }

    public final String name;
    public final float length;
    public final Loop loop;
    private final Map<String, BoneChannels> bones = new HashMap<>();

    private BedrockAnimation(String name, float length, Loop loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    private static final class BoneChannels {
        Channel rotation, position, scale;
    }

    private static final class Keyframe {
        final float time;
        final MoLang.Expr[] pre, post;

        Keyframe(float time, MoLang.Expr[] pre, MoLang.Expr[] post) {
            this.time = time;
            this.pre = pre;
            this.post = post;
        }
    }

    private static final class Channel {
        final List<Keyframe> keys = new ArrayList<>();

        double[] eval(MoLang.Context ctx, float t) {
            if (keys.size() == 1) return evalVec(keys.get(0).post, ctx);
            Keyframe first = keys.get(0);
            if (t <= first.time) return evalVec(first.pre, ctx);
            for (int i = 0; i < keys.size() - 1; i++) {
                Keyframe a = keys.get(i), b = keys.get(i + 1);
                if (t >= a.time && t <= b.time) {
                    double k = b.time > a.time ? (t - a.time) / (b.time - a.time) : 1;
                    double[] va = evalVec(a.post, ctx), vb = evalVec(b.pre, ctx);
                    return new double[]{va[0] + (vb[0] - va[0]) * k, va[1] + (vb[1] - va[1]) * k, va[2] + (vb[2] - va[2]) * k};
                }
            }
            return evalVec(keys.get(keys.size() - 1).post, ctx);
        }

        float lastTime() {
            return keys.isEmpty() ? 0 : keys.get(keys.size() - 1).time;
        }
    }

    private static double[] evalVec(MoLang.Expr[] e, MoLang.Context ctx) {
        return new double[]{e[0].eval(ctx), e[1].eval(ctx), e[2].eval(ctx)};
    }

    /** Lit toutes les animations d'un fichier : clé = nom complet (ex. « animation.embrix.ground_idle »). */
    public static Map<String, BedrockAnimation> parseFile(JsonObject root) {
        Map<String, BedrockAnimation> out = new HashMap<>();
        JsonObject anims = GsonHelper.getAsJsonObject(root, "animations", new JsonObject());
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            Loop loop = Loop.ONCE;
            if (a.has("loop")) {
                JsonElement l = a.get("loop");
                if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isBoolean()) loop = l.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
                else if ("hold_on_last_frame".equals(l.getAsString())) loop = Loop.HOLD;
            }
            float declared = GsonHelper.getAsFloat(a, "animation_length", -1f);
            Map<String, BoneChannels> parsed = new HashMap<>();
            float maxKey = 0;
            JsonObject bones = GsonHelper.getAsJsonObject(a, "bones", new JsonObject());
            for (Map.Entry<String, JsonElement> b : bones.entrySet()) {
                JsonObject bj = b.getValue().getAsJsonObject();
                BoneChannels ch = new BoneChannels();
                ch.rotation = channel(bj.get("rotation"));
                ch.position = channel(bj.get("position"));
                ch.scale = channel(bj.get("scale"));
                for (Channel c : new Channel[]{ch.rotation, ch.position, ch.scale}) if (c != null) maxKey = Math.max(maxKey, c.lastTime());
                parsed.put(b.getKey(), ch);
            }
            BedrockAnimation anim = new BedrockAnimation(e.getKey(), declared > 0 ? declared : maxKey, loop);
            anim.bones.putAll(parsed);
            out.put(e.getKey(), anim);
        }
        return out;
    }

    private static Channel channel(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        Channel c = new Channel();
        if (el.isJsonObject()) {
            List<Keyframe> keys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(k.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                JsonElement v = k.getValue();
                MoLang.Expr[] pre, post;
                if (v.isJsonObject()) {
                    JsonObject vo = v.getAsJsonObject();
                    post = vec3(vo.has("post") ? vo.get("post") : vo.get("pre"));
                    pre = vec3(vo.has("pre") ? vo.get("pre") : vo.get("post"));
                } else {
                    pre = post = vec3(v);
                }
                keys.add(new Keyframe(time, pre, post));
            }
            keys.sort((a, b) -> Float.compare(a.time, b.time));
            c.keys.addAll(keys);
        } else {
            MoLang.Expr[] v = vec3(el);
            c.keys.add(new Keyframe(0, v, v));
        }
        return c.keys.isEmpty() ? null : c;
    }

    private static MoLang.Expr[] vec3(JsonElement el) {
        MoLang.Expr[] r = {MoLang.constant(0), MoLang.constant(0), MoLang.constant(0)};
        if (el == null) return r;
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = expr(a.get(i));
        } else if (el.isJsonPrimitive()) {
            MoLang.Expr e = expr(el);
            r[0] = r[1] = r[2] = e;
        }
        return r;
    }

    private static MoLang.Expr expr(JsonElement el) {
        JsonPrimitive p = el.getAsJsonPrimitive();
        return p.isNumber() ? MoLang.constant(p.getAsDouble()) : MoLang.parse(p.getAsString());
    }

    /** Temps local de l'animation selon son mode de boucle. */
    public float localTime(float seconds) {
        if (length <= 0) return seconds;
        return switch (loop) {
            case LOOP -> seconds % length;
            case ONCE, HOLD -> Math.min(seconds, length);
        };
    }

    public boolean finished(float seconds) {
        return loop == Loop.ONCE && length > 0 && seconds >= length;
    }

    /**
     * Ajoute cette animation à la pose (rotations et positions additionnées, échelles multipliées),
     * en convertissant vers l'espace Java (X inversé).
     */
    public void apply(Pose pose, MoLang.Context ctx, float seconds, float weight) {
        float t = localTime(seconds);
        ctx.animTime = t;
        for (Map.Entry<String, BoneChannels> e : bones.entrySet()) {
            Pose.BoneTransform bt = pose.get(e.getKey());
            BoneChannels ch = e.getValue();
            if (ch.rotation != null) {
                double[] r = ch.rotation.eval(ctx, t);
                bt.rx += (float) Math.toRadians(-r[0]) * weight;
                bt.ry += (float) Math.toRadians(-r[1]) * weight;
                bt.rz += (float) Math.toRadians(r[2]) * weight;
            }
            if (ch.position != null) {
                double[] p = ch.position.eval(ctx, t);
                bt.px += (float) -p[0] * weight;
                bt.py += (float) p[1] * weight;
                bt.pz += (float) p[2] * weight;
            }
            if (ch.scale != null) {
                double[] s = ch.scale.eval(ctx, t);
                bt.sx *= (float) (1 + (s[0] - 1) * weight);
                bt.sy *= (float) (1 + (s[1] - 1) * weight);
                bt.sz *= (float) (1 + (s[2] - 1) * weight);
            }
        }
    }
}
