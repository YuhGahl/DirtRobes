package com.dirtrobes.species;

import com.dirtrobes.item.MineralItem.Stat;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Natures (à la Pokémon, noms originaux) : +10 % sur une stat, −10 % sur une autre.
 * 4 stats → 12 natures qui changent quelque chose et 4 neutres. Tirée au hasard à la naissance.
 */
public enum Nature {
    BALANCED(null, null),
    STEADY(null, null),
    COMPOSED(null, null),
    CONSTANT(null, null),

    PEACEFUL(Stat.HEALTH, Stat.POWER),
    ENDURING(Stat.HEALTH, Stat.DEFENCE),
    PLACID(Stat.HEALTH, Stat.SPEED),

    RECKLESS(Stat.POWER, Stat.HEALTH),
    FIERY(Stat.POWER, Stat.DEFENCE),
    HULKING(Stat.POWER, Stat.SPEED),

    WARY(Stat.DEFENCE, Stat.HEALTH),
    RESERVED(Stat.DEFENCE, Stat.POWER),
    UNSHAKABLE(Stat.DEFENCE, Stat.SPEED),

    JITTERY(Stat.SPEED, Stat.HEALTH),
    NIMBLE(Stat.SPEED, Stat.POWER),
    LIVELY(Stat.SPEED, Stat.DEFENCE);

    @Nullable
    public final Stat raised;
    @Nullable
    public final Stat lowered;

    Nature(@Nullable Stat raised, @Nullable Stat lowered) {
        this.raised = raised;
        this.lowered = lowered;
    }

    public float multiplier(Stat stat) {
        if (stat == raised) return 1.1f;
        if (stat == lowered) return 0.9f;
        return 1.0f;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "nature.dirtrobes." + id();
    }

    public static Nature random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }

    public static Nature byId(String id) {
        for (Nature n : values()) if (n.id().equals(id)) return n;
        return BALANCED;
    }
}
