package com.dirtrobes.entity;

/**
 * Stats façon Pokémon, sans IV (décision M23 du document de conception).
 *   PV  = floor((2 × base + EV/4) × niveau / 100) + niveau + 10
 *   ATQ/DÉF = floor((2 × base + EV/4) × niveau / 100) + 5
 * Les stats sont ensuite converties en attributs Minecraft.
 */
public final class StatCalc {
    private StatCalc() {}

    public static final int MAX_LEVEL = 100;
    public static final int MAX_EV_PER_STAT = 252;
    public static final int MAX_EV_TOTAL = 510;

    public static int hp(int base, int ev, int level) {
        return (int) Math.floor((2.0 * base + ev / 4) * level / 100.0) + level + 10;
    }

    public static int other(int base, int ev, int level) {
        return (int) Math.floor((2.0 * base + ev / 4) * level / 100.0) + 5;
    }

    /**
     * Qualité des stats (en %) selon l'intégrité du fossile dégagé :
     * 95 % d'intégrité ou plus → 100 % des stats, 10 % ou moins → 50 %, linéaire entre les deux.
     */
    public static int qualityForIntegrity(int integrity) {
        if (integrity >= 95) return 100;
        if (integrity <= 10) return 50;
        return 50 + Math.round((integrity - 10) * 50f / 85f);
    }

    /** Applique la qualité à une stat. */
    public static int applyQuality(int stat, int quality) {
        return Math.max(1, Math.round(stat * quality / 100f));
    }

    /** Expérience totale pour atteindre ce niveau (courbe « moyenne » : n³ / 4). */
    public static int xpForLevel(int level) {
        return level <= 1 ? 0 : (level * level * level) / 4;
    }

    // ----- Conversion vers les attributs Minecraft -----
    public static double toMaxHealth(int hpStat) {
        return 6.0 + hpStat * 0.22;
    }

    public static double toAttackDamage(int atkStat) {
        return 1.0 + atkStat * 0.06;
    }

    /**
     * Vitesse de déplacement Minecraft : 0,20 + VIT × 0,0012, plafonnée à 0,42 (le loup est à 0,30).
     * Ex. VIT 40 → 0,25 ; VIT 90 → 0,31 ; VIT 150 → 0,38.
     */
    public static double toMovementSpeed(int spdStat) {
        return Math.min(0.42, 0.20 + spdStat * 0.0012);
    }

    /** Applique la nature (+10 % / −10 %) à une stat. */
    public static int applyNature(int stat, com.dirtrobes.species.Nature nature, com.dirtrobes.item.MineralItem.Stat which) {
        return Math.max(1, (int) Math.floor(stat * nature.multiplier(which)));
    }

    public static double toArmor(int defStat) {
        return Math.min(20.0, defStat * 0.08);
    }
}
