package com.dirtrobes.species;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Rareté d'une lignée de Spectrobes : un fossile plus long à dégager et une espèce plus rare à l'extraction.
 * Elle ne modifie pas les stats : celles des fichiers d'espèces sont déjà les valeurs finales du document de conception.
 */
public enum Rarity {
    COMMON(0xFFFFFF, 70, 0.42f, 0.58f, 75),
    RARE(0x55AAFF, 22, 0.32f, 0.50f, 90),
    EPIC(0xC070FF, 7, 0.22f, 0.42f, 105),
    SECRET(0xFFC040, 1, 0.12f, 0.32f, 120);

    public final int color;
    /** Poids du palier quand on tire l'espèce d'un fossile. */
    public final int weight;
    /** Seuils du bruit de la roche au mini-jeu de fouille : plus bas = plus de couches dures. */
    public final float depth2;
    public final float depth3;
    /** Temps du mini-jeu, en secondes. */
    public final int excavationSeconds;

    Rarity(int color, int weight, float depth2, float depth3, int excavationSeconds) {
        this.color = color;
        this.weight = weight;
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

}
