package com.dirtrobes.team;

import com.dirtrobes.entity.SpectrobeEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Ce que l'écran du Prizmod affiche pour un membre de l'équipe. */
public record TeamSummary(String nameKey, int level, int property, int stage, float health, float maxHealth,
                          boolean knockedOut, int quality, boolean out, boolean found) {

    public static TeamSummary of(SpectrobeEntity s, boolean out, boolean found) {
        String key = s.getSpecies() != null ? s.getSpecies().translationKey() : "entity.dirtrobes.spectrobe";
        return new TeamSummary(key, s.getSpectrobeLevel(), s.getProperty().ordinal(), s.getStage().ordinal(),
                s.getHealth(), s.getMaxHealth(), s.isKnockedOut(), s.getQuality(), out, found);
    }

    public static TeamSummary missing() {
        return new TeamSummary("entity.dirtrobes.spectrobe", 1, 0, 0, 0, 1, false, 100, true, false);
    }

    public static final StreamCodec<FriendlyByteBuf, TeamSummary> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeUtf(s.nameKey);
                buf.writeVarInt(s.level);
                buf.writeVarInt(s.property);
                buf.writeVarInt(s.stage);
                buf.writeFloat(s.health);
                buf.writeFloat(s.maxHealth);
                buf.writeBoolean(s.knockedOut);
                buf.writeVarInt(s.quality);
                buf.writeBoolean(s.out);
                buf.writeBoolean(s.found);
            },
            buf -> new TeamSummary(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
}
