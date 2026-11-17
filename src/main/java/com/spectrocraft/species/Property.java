package com.spectrocraft.species;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Locale;

/**
 * Les 6 propriétés du mod (les 5 types de Spectrobes: Origins renommés, plus Prisma).
 * Cycle : Corona > Aurora > Eclipse > Halo > Flash > Corona. Prisma est neutre.
 */
public enum Property {
    CORONA(0xF08A3C, "aurora", "flash"),
    AURORA(0x6BC56B, "eclipse", "corona"),
    FLASH(0x4A9BE8, "corona", "halo"),
    ECLIPSE(0xB08A5A, "halo", "aurora"),
    HALO(0xF2D64B, "flash", "eclipse"),
    PRISMA(0xD9D9E6, "", "");

    public static final float STRONG = 1.5f;
    public static final float WEAK = 0.67f;

    private final int color;
    private final String strongAgainst;
    private final String weakAgainst;

    Property(int color, String strongAgainst, String weakAgainst) {
        this.color = color;
        this.strongAgainst = strongAgainst;
        this.weakAgainst = weakAgainst;
    }

    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "property.spectrocraft." + id();
    }

    public static Property byId(String id) {
        for (Property p : values()) if (p.id().equalsIgnoreCase(id)) return p;
        return PRISMA;
    }

    public static Property byOrdinal(int ordinal) {
        Property[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : PRISMA;
    }

    /** Multiplicateur de dégâts quand une propriété attaque l'autre. */
    public float multiplierAgainst(Property defender) {
        if (defender == null) return 1f;
        if (defender.id().equals(strongAgainst)) return STRONG;
        if (defender.id().equals(weakAgainst)) return WEAK;
        return 1f;
    }

    /** Particule utilisée pour l'attaque spéciale. */
    public ParticleOptions particle() {
        return switch (this) {
            case CORONA -> ParticleTypes.FLAME;
            case AURORA -> ParticleTypes.HAPPY_VILLAGER;
            case FLASH -> ParticleTypes.SPLASH;
            case ECLIPSE -> ParticleTypes.SMOKE;
            case HALO -> ParticleTypes.ELECTRIC_SPARK;
            case PRISMA -> ParticleTypes.END_ROD;
        };
    }
}
