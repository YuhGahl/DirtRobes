package com.dirtrobes.species;

import java.util.Locale;

/** Stades d'un Spectrobe. Seuls les adultes, évolués et secrets combattent. */
public enum Stage {
    CHILD(0.8f, false),
    ADULT(1.0f, true),
    EVOLVED(1.25f, true),
    SECRET(1.35f, true),
    SPECIAL(1.35f, true);

    private final float scale;
    private final boolean fights;

    Stage(float scale, boolean fights) {
        this.scale = scale;
        this.fights = fights;
    }

    public float scale() {
        return scale;
    }

    public boolean fights() {
        return fights;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "stage.dirtrobes." + id();
    }

    public static Stage byId(String id) {
        for (Stage s : values()) if (s.id().equalsIgnoreCase(id)) return s;
        return CHILD;
    }

    public static Stage byOrdinal(int ordinal) {
        Stage[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : CHILD;
    }
}
