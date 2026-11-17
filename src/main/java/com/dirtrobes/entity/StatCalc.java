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

    public static double toArmor(int defStat) {
        return Math.min(20.0, defStat * 0.08);
    }
}
