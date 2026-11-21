package com.dirtrobes.entity;

/**
 * Stats calculées avec les formules de Pokémon (https://www.pokepedia.fr/Calcul_des_statistiques) :
 *   PV          = floor((2 × base + IV + floor(EV / 4)) × niveau / 100) + niveau + 10
 *   autres stats = floor((floor((2 × base + IV + floor(EV / 4)) × niveau / 100) + 5) × nature)
 * Les IV (0 à 31) sont fixés à l'éveil selon l'intégrité du fossile dégagé. Les stats sont ensuite
 * converties en attributs Minecraft.
 */
public final class StatCalc {
    private StatCalc() {}

    public static final int MAX_LEVEL = 100;
    public static final int MAX_EV_PER_STAT = 252;
    public static final int MAX_EV_TOTAL = 510;
    public static final int MAX_IV = 31;
    /** Ordre des IV (comme Pokémon / Cobblemon) : PV, ATQ, DÉF, Atq. Spé, Déf. Spé, VIT. */
    public static final int IV_HP = 0, IV_ATK = 1, IV_DEF = 2, IV_SPATK = 3, IV_SPDEF = 4, IV_SPD = 5;
    /** Écart aléatoire autour de l'IV moyen donné par l'intégrité. */
    public static final int IV_SPREAD = 3;

    public static int hp(int base, int iv, int ev, int level) {
        return (2 * base + iv + ev / 4) * level / 100 + level + 10;
    }

    public static int other(int base, int iv, int ev, int level) {
        return (2 * base + iv + ev / 4) * level / 100 + 5;
    }

    /**
     * IV moyen (0 à 31) selon l'intégrité du fossile dégagé :
     * 95 % d'intégrité ou plus → 31 partout (fossile parfait), 10 % ou moins → 0, linéaire entre les deux.
     */
    public static int ivForIntegrity(int integrity) {
        if (integrity >= 95) return MAX_IV;
        if (integrity <= 10) return 0;
        return Math.round((integrity - 10) * MAX_IV / 85f);
    }

    /** Tire les 6 IV d'un Spectrobe éveillé : l'IV moyen de l'intégrité, ± {@link #IV_SPREAD} (31 partout si fossile parfait). */
    public static int[] rollIvs(int integrity, net.minecraft.util.RandomSource random) {
        int mean = ivForIntegrity(integrity);
        int[] ivs = new int[6];
        for (int i = 0; i < 6; i++) {
            ivs[i] = mean >= MAX_IV ? MAX_IV : Math.max(0, Math.min(MAX_IV, mean + random.nextInt(2 * IV_SPREAD + 1) - IV_SPREAD));
        }
        return ivs;
    }

    public static int[] perfectIvs() {
        return new int[]{MAX_IV, MAX_IV, MAX_IV, MAX_IV, MAX_IV, MAX_IV};
    }

    /** Anciens Spectrobes (qualité 50 à 100 %) : IV équivalents. */
    public static int ivFromLegacyQuality(int quality) {
        return Math.max(0, Math.min(MAX_IV, Math.round((quality - 50) * MAX_IV / 50f)));
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
