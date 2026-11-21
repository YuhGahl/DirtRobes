package com.dirtrobes.species;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Rareté d'une lignée de Spectrobes. Elle ne donne pas de bonus en pourcentage : elle rend la répartition des stats
 * plus tranchée (une part des deux stats les plus faibles passe aux deux plus fortes, 200 au maximum par stat),
 * rend le fossile plus long à dégager et l'espèce plus rare à l'extraction.
 */
public enum Rarity {
    COMMON(0xFFFFFF, 70, 0.00f, 0.42f, 0.58f, 75),
    RARE(0x55AAFF, 22, 0.10f, 0.32f, 0.50f, 90),
    EPIC(0xC070FF, 7, 0.20f, 0.22f, 0.42f, 105),
    SECRET(0xFFC040, 1, 0.30f, 0.12f, 0.32f, 120);

    public static final int STAT_CAP = 200;

    public final int color;
    /** Poids du palier quand on tire l'espèce d'un fossile. */
    public final int weight;
    /** Part prise à chacune des deux stats les plus faibles. */
    public final float shift;
    /** Seuils du bruit de la roche au mini-jeu de fouille : plus bas = plus de couches dures. */
    public final float depth2;
    public final float depth3;
    /** Temps du mini-jeu, en secondes. */
    public final int excavationSeconds;

    Rarity(int color, int weight, float shift, float depth2, float depth3, int excavationSeconds) {
        this.color = color;
        this.weight = weight;
        this.shift = shift;
        this.depth2 = depth2;
        this.depth3 = depth3;
        this.excavationSeconds = excavationSeconds;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("rarity.dirtrobes." + id()).withStyle(s -> s.withColor(color));
    }

    public static Rarity byId(String id) {
        for (Rarity r : values()) if (r.id().equalsIgnoreCase(id)) return r;
        return COMMON;
    }

    public static Rarity byOrdinal(int i) {
        Rarity[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /**
     * Répartition des stats (ordre PV, ATQ, DÉF, VIT) : chacune des deux stats les plus faibles cède {@link #shift}
     * de sa valeur ; les deux tiers vont à la plus forte, le reste à la deuxième. Total inchangé, 200 au maximum.
     * À égalité, l'ordre PV, ATQ, DÉF, VIT départage.
     */
    public int[] redistribute(int[] stats) {
        int[] out = stats.clone();
        if (shift <= 0f) return out;
        Integer[] order = {0, 1, 2, 3};
        java.util.Arrays.sort(order, (a, b) -> stats[b] != stats[a] ? Integer.compare(stats[b], stats[a]) : Integer.compare(a, b));
        int pool = 0;
        for (int k = 2; k < 4; k++) {
            int take = Math.round(stats[order[k]] * shift);
            out[order[k]] -= take;
            pool += take;
        }
        int toFirst = Math.round(pool * 2f / 3f);
        out[order[0]] += toFirst;
        out[order[1]] += pool - toFirst;
        // plafond : l'excédent descend vers la stat suivante
        for (int k = 0; k < 3; k++) {
            int over = out[order[k]] - STAT_CAP;
            if (over > 0) {
                out[order[k]] = STAT_CAP;
                out[order[k + 1]] += over;
            }
        }
        for (int k = 0; k < 4; k++) out[k] = Math.max(1, Math.min(STAT_CAP, out[k]));
        return out;
    }
}
