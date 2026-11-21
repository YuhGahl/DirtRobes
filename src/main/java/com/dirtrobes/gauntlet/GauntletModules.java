package com.dirtrobes.gauntlet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * État du gantelet (l'arme elle-même est rangée à part, dans le composant « gauntlet_weapon »).
 * @param extractor module d'extraction (permet d'extraire les fossiles de leur pierre)
 * @param prizmod le Prizmod (le prisme) est inséré : c'est le cœur du gantelet, sans lui ni arme ni extraction
 * @param drawn l'arme est sortie (dans la main du joueur)
 */
public record GauntletModules(boolean extractor, boolean prizmod, boolean drawn) {
    public static final GauntletModules NONE = new GauntletModules(false, false, false);

    public static final Codec<GauntletModules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("extractor", false).forGetter(GauntletModules::extractor),
            Codec.BOOL.optionalFieldOf("prizmod", false).forGetter(GauntletModules::prizmod),
            Codec.BOOL.optionalFieldOf("drawn", false).forGetter(GauntletModules::drawn)
    ).apply(i, GauntletModules::new));

    public static final StreamCodec<ByteBuf, GauntletModules> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GauntletModules::extractor, ByteBufCodecs.BOOL, GauntletModules::prizmod,
            ByteBufCodecs.BOOL, GauntletModules::drawn, GauntletModules::new);

    public GauntletModules withExtractor() {
        return new GauntletModules(true, prizmod, drawn);
    }

    public GauntletModules withPrizmod(boolean value) {
        return new GauntletModules(extractor, value, drawn);
    }

    public GauntletModules withDrawn(boolean value) {
        return new GauntletModules(extractor, prizmod, value);
    }
}
