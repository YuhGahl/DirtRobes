package com.dirtrobes.team;

import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.item.MineralItem;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Ce que l'interface (bandeau d'équipe, écran du Prizmod, fiche) affiche pour un membre de l'équipe. */
public record TeamSummary(
        String speciesId, String nameKey, int level, int property, int stage,
        float health, float maxHealth, boolean knockedOut, int quality, boolean out, boolean found,
        int hp, int atk, int def, int spd,
        int evHp, int evAtk, int evDef, int evSpd,
        int nature, int xp, int xpLevelStart, int xpNextLevel,
        String evoSpeciesId, String evoNameKey, int evoLevel, int color) {

    public static TeamSummary of(SpectrobeEntity s, boolean out, boolean found) {
        Species sp = s.getSpecies();
        String key = sp != null ? sp.translationKey() : "entity.dirtrobes.spectrobe";
        Species evo = sp != null && sp.evolvesInto() != null ? SpeciesManager.get(sp.evolvesInto()) : null;
        int lvl = s.getSpectrobeLevel();
        return new TeamSummary(
                s.getSpeciesId() == null ? "" : s.getSpeciesId().toString(), key, lvl, s.getProperty().ordinal(), s.getStage().ordinal(),
                s.getHealth(), s.getMaxHealth(), s.isKnockedOut(), s.getQuality(), out, found,
                s.hpStat(), s.attackStat(), s.defenseStat(), s.speedStat(),
                s.getEv(MineralItem.Stat.HEALTH), s.getEv(MineralItem.Stat.POWER), s.getEv(MineralItem.Stat.DEFENCE), s.getEv(MineralItem.Stat.SPEED),
                s.getNature().ordinal(), s.getXp(), StatCalc.xpForLevel(lvl), StatCalc.xpForLevel(Math.min(StatCalc.MAX_LEVEL, lvl + 1)),
                evo == null ? "" : evo.id().toString(), evo == null ? "" : evo.translationKey(), sp == null ? 0 : sp.evolutionLevel(),
                s.colorVariant());
    }

    public static TeamSummary missing() {
        return new TeamSummary("", "entity.dirtrobes.spectrobe", 1, 0, 0, 0, 1, false, 100, true, false,
                1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, "", "", 0, 0);
    }

    public int totalEv() {
        return evHp + evAtk + evDef + evSpd;
    }

    public static final StreamCodec<FriendlyByteBuf, TeamSummary> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeUtf(s.speciesId);
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
                buf.writeVarInt(s.hp);
                buf.writeVarInt(s.atk);
                buf.writeVarInt(s.def);
                buf.writeVarInt(s.spd);
                buf.writeVarInt(s.evHp);
                buf.writeVarInt(s.evAtk);
                buf.writeVarInt(s.evDef);
                buf.writeVarInt(s.evSpd);
                buf.writeVarInt(s.nature);
                buf.writeVarInt(s.xp);
                buf.writeVarInt(s.xpLevelStart);
                buf.writeVarInt(s.xpNextLevel);
                buf.writeUtf(s.evoSpeciesId);
                buf.writeUtf(s.evoNameKey);
                buf.writeVarInt(s.evoLevel);
                buf.writeVarInt(s.color);
            },
            buf -> new TeamSummary(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
}
