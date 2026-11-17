package com.spectrocraft.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle au format Bedrock (.geo.json, Blockbench « Bedrock Entity »), converti dans l'espace Java
 * de Minecraft comme le fait GeckoLib : X des positions et des pivots inversé, rotations X et Y inversées.
 */
public final class BedrockModel {
    public final List<Bone> roots = new ArrayList<>();
    public final Map<String, Bone> bones = new LinkedHashMap<>();
    public final int textureWidth;
    public final int textureHeight;

    private BedrockModel(int tw, int th) {
        this.textureWidth = tw;
        this.textureHeight = th;
    }

    public static final class Bone {
        public final String name;
        public final float pivotX, pivotY, pivotZ;
        public final float rotX, rotY, rotZ; // radians, espace Java
        public final List<Bone> children = new ArrayList<>();
        public final List<Quad> quads = new ArrayList<>();

        Bone(String name, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.pivotX = px;
            this.pivotY = py;
            this.pivotZ = pz;
            this.rotX = rx;
            this.rotY = ry;
            this.rotZ = rz;
        }
    }

    /** Une face : 4 sommets (en pixels), UV normalisés, normale. */
    public static final class Quad {
        public final float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        public final float nx, ny, nz;

        Quad(float nx, float ny, float nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    public static BedrockModel parse(JsonObject root) {
        JsonArray geos = GsonHelper.getAsJsonArray(root, "minecraft:geometry");
        JsonObject geo = geos.get(0).getAsJsonObject();
        JsonObject desc = GsonHelper.getAsJsonObject(geo, "description", new JsonObject());
        BedrockModel model = new BedrockModel(GsonHelper.getAsInt(desc, "texture_width", 64), GsonHelper.getAsInt(desc, "texture_height", 64));
        Map<String, String> parents = new HashMap<>();
        for (JsonElement el : GsonHelper.getAsJsonArray(geo, "bones", new JsonArray())) {
            JsonObject b = el.getAsJsonObject();
            String name = GsonHelper.getAsString(b, "name");
            float[] pivot = vec(b, "pivot");
            float[] rot = vec(b, "rotation");
            Bone bone = new Bone(name, -pivot[0], pivot[1], pivot[2],
                    (float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2]));
            if (b.has("cubes")) {
                for (JsonElement ce : b.getAsJsonArray("cubes")) addCube(model, bone, ce.getAsJsonObject());
            }
            model.bones.put(name, bone);
            if (b.has("parent")) parents.put(name, GsonHelper.getAsString(b, "parent"));
        }
        for (Bone bone : model.bones.values()) {
            Bone parent = model.bones.get(parents.get(bone.name));
            if (parent != null) parent.children.add(bone);
            else model.roots.add(bone);
        }
        return model;
    }

    private static float[] vec(JsonObject o, String key) {
        float[] r = new float[3];
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsFloat();
        }
        return r;
    }

    private static void addCube(BedrockModel model, Bone bone, JsonObject c) {
        float[] o = vec(c, "origin");
        float[] s = vec(c, "size");
        float inf = GsonHelper.getAsFloat(c, "inflate", 0f);
        boolean mirror = GsonHelper.getAsBoolean(c, "mirror", false);
        // Espace Java : X inversé.
        float x0 = -(o[0] + s[0]) - inf, x1 = -o[0] + inf;
        float y0 = o[1] - inf, y1 = o[1] + s[1] + inf;
        float z0 = o[2] - inf, z1 = o[2] + s[2] + inf;
        if (!c.has("rotation")) {
            addFaces(model, bone, c, s, mirror, x0, x1, y0, y1, z0, z1, null);
        } else {
            // Rotation propre au cube : on la cuit dans les sommets autour de son pivot.
            float[] piv = vec(c, "pivot");
            float[] rot = vec(c, "rotation");
            float[] pivotJ = {-piv[0], piv[1], piv[2]};
            float[] rotJ = {(float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2])};
            addFaces(model, bone, c, s, mirror, x0, x1, y0, y1, z0, z1, new float[][]{pivotJ, rotJ});
        }
    }

    private static void addFaces(BedrockModel model, Bone bone, JsonObject c, float[] s, boolean mirror,
                                 float x0, float x1, float y0, float y1, float z0, float z1, float[][] cubeRot) {
        int w = (int) Math.ceil(s[0]), h = (int) Math.ceil(s[1]), d = (int) Math.ceil(s[2]);
        boolean boxUv = !c.has("uv") || c.get("uv").isJsonArray();
        float u = 0, v = 0;
        if (boxUv && c.has("uv")) {
            JsonArray uv = c.getAsJsonArray("uv");
            u = uv.get(0).getAsFloat();
            v = uv.get(1).getAsFloat();
        }
        JsonObject faceUv = boxUv ? null : c.getAsJsonObject("uv");
        // nom, normale, « droite » de la texture, « haut » de la texture, rectangle box-UV
        face(model, bone, faceUv, "north", 0, 0, -1, -1, 0, 0, 0, 1, 0, u + d, v + d, w, h, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
        face(model, bone, faceUv, "south", 0, 0, 1, 1, 0, 0, 0, 1, 0, u + 2 * d + w, v + d, w, h, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
        face(model, bone, faceUv, "west", 1, 0, 0, 0, 0, -1, 0, 1, 0, u, v + d, d, h, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
        face(model, bone, faceUv, "east", -1, 0, 0, 0, 0, 1, 0, 1, 0, u + d + w, v + d, d, h, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
        face(model, bone, faceUv, "up", 0, 1, 0, -1, 0, 0, 0, 0, 1, u + d, v, w, d, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
        face(model, bone, faceUv, "down", 0, -1, 0, -1, 0, 0, 0, 0, -1, u + d + w, v, w, d, x0, x1, y0, y1, z0, z1, mirror, cubeRot);
    }

    private static void face(BedrockModel model, Bone bone, JsonObject faceUv, String name,
                             float nx, float ny, float nz, float rx, float ry, float rz, float tx, float ty, float tz,
                             float bu, float bv, float bw, float bh,
                             float x0, float x1, float y0, float y1, float z0, float z1, boolean mirror, float[][] cubeRot) {
        float fu, fv, fw, fh;
        if (faceUv != null) {
            if (!faceUv.has(name)) return;
            JsonObject f = faceUv.getAsJsonObject(name);
            JsonArray uv = GsonHelper.getAsJsonArray(f, "uv");
            JsonArray size = GsonHelper.getAsJsonArray(f, "uv_size", null);
            fu = uv.get(0).getAsFloat();
            fv = uv.get(1).getAsFloat();
            fw = size == null ? 0 : size.get(0).getAsFloat();
            fh = size == null ? 0 : size.get(1).getAsFloat();
        } else {
            fu = bu;
            fv = bv;
            fw = bw;
            fh = bh;
        }
        // Centre de la face et demi-dimensions le long de « droite » et « haut ».
        float cx = (x0 + x1) / 2 + nx * (x1 - x0) / 2;
        float cy = (y0 + y1) / 2 + ny * (y1 - y0) / 2;
        float cz = (z0 + z1) / 2 + nz * (z1 - z0) / 2;
        float halfR = (Math.abs(rx) * (x1 - x0) + Math.abs(ry) * (y1 - y0) + Math.abs(rz) * (z1 - z0)) / 2;
        float halfT = (Math.abs(tx) * (x1 - x0) + Math.abs(ty) * (y1 - y0) + Math.abs(tz) * (z1 - z0)) / 2;
        if (halfR <= 0 || halfT <= 0) return; // face sans surface (cube plat vu de côté)
        Quad q = new Quad(nx, ny, nz);
        float[][] corners = {
                {-halfR, halfT}, {-halfR, -halfT}, {halfR, -halfT}, {halfR, halfT}
        };
        float u0 = fu / model.textureWidth, u1 = (fu + fw) / model.textureWidth;
        float v0 = fv / model.textureHeight, v1 = (fv + fh) / model.textureHeight;
        if (mirror) {
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        float[][] uvs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int k = 0; k < 4; k++) {
            float px = cx + rx * corners[k][0] + tx * corners[k][1];
            float py = cy + ry * corners[k][0] + ty * corners[k][1];
            float pz = cz + rz * corners[k][0] + tz * corners[k][1];
            if (cubeRot != null) {
                float[] p = rotateAround(new float[]{px, py, pz}, cubeRot[0], cubeRot[1]);
                px = p[0];
                py = p[1];
                pz = p[2];
            }
            q.x[k] = px;
            q.y[k] = py;
            q.z[k] = pz;
            q.u[k] = uvs[k][0];
            q.v[k] = uvs[k][1];
        }
        bone.quads.add(q);
    }

    /** Rotation Z puis Y puis X (même ordre que le rendu des os) autour d'un pivot. */
    private static float[] rotateAround(float[] p, float[] pivot, float[] rot) {
        double x = p[0] - pivot[0], y = p[1] - pivot[1], z = p[2] - pivot[2];
        double cx = Math.cos(rot[0]), sx = Math.sin(rot[0]);
        double y1 = y * cx - z * sx, z1 = y * sx + z * cx;
        y = y1;
        z = z1;
        double cy = Math.cos(rot[1]), sy = Math.sin(rot[1]);
        double x2 = x * cy + z * sy, z2 = -x * sy + z * cy;
        x = x2;
        z = z2;
        double cz = Math.cos(rot[2]), sz = Math.sin(rot[2]);
        double x3 = x * cz - y * sz, y3 = x * sz + y * cz;
        return new float[]{(float) (x3 + pivot[0]), (float) (y3 + pivot[1]), (float) (z + pivot[2])};
    }
}
