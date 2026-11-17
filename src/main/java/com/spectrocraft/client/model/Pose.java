package com.spectrocraft.client.model;

import java.util.HashMap;
import java.util.Map;

/** Transformations calculées pour chaque os à une image donnée (espace Java, radians, pixels). */
public final class Pose {
    public static final class BoneTransform {
        public float rx, ry, rz;
        public float px, py, pz;
        public float sx = 1, sy = 1, sz = 1;
    }

    private final Map<String, BoneTransform> bones = new HashMap<>();

    public BoneTransform get(String bone) {
        return bones.computeIfAbsent(bone, b -> new BoneTransform());
    }

    public BoneTransform peek(String bone) {
        return bones.get(bone);
    }
}
