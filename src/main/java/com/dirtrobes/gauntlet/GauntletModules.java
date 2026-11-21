package com.dirtrobes.gauntlet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * État du gantelet.
 * @param weapon arme installée à la table de forge : "" (aucune), "blade", "cannon" ou "fist" — une seule à la fois
 * @param extractor module d'extraction (permet d'extraire les fossiles de leur pierre)
 * @param prizmod le Prizmod (le prisme) est inséré : c'est le cœur du gantelet, sans lui ni arme ni extraction
 */
public record GauntletModules(String weapon, boolean extractor, boolean prizmod) {
    public static final GauntletModules NONE = new GauntletModules("", false, false);

    public static final Codec<GauntletModules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("weapon", "").forGetter(GauntletModules::weapon),
            Codec.BOOL.optionalFieldOf("extractor", false).forGetter(GauntletModules::extractor),
            Codec.BOOL.optionalFieldOf("prizmod", false).forGetter(GauntletModules::prizmod)
    ).apply(i, GauntletModules::new));

    public static final StreamCodec<ByteBuf, GauntletModules> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GauntletModules::weapon, ByteBufCodecs.BOOL, GauntletModules::extractor,
            ByteBufCodecs.BOOL, GauntletModules::prizmod, GauntletModules::new);

    public boolean hasWeapon() {
        return !weapon.isEmpty();
    }

    public GauntletModules with(String module) {
        return "extractor".equals(module) ? new GauntletModules(weapon, true, prizmod) : new GauntletModules(module, extractor, prizmod);
    }

    public GauntletModules withPrizmod(boolean value) {
        return new GauntletModules(weapon, extractor, value);
    }
}
