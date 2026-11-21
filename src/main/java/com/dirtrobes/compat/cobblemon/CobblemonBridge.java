package com.dirtrobes.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.entity.StatCalc;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.species.Species;
import com.dirtrobes.species.SpeciesManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pont DirtRobes ↔ Cobblemon. Seule classe du mod qui touche à Cobblemon : elle n'est chargée que si
 * Cobblemon est installé.
 *
 * <p>Un Spectrobe envoyé vers Cobblemon devient un Pokémon de l'espèce {@code dirtrobes:<id>} (fichiers
 * data/dirtrobes/species, lus uniquement par Cobblemon) au même niveau. Sa sauvegarde DirtRobes voyage dans
 * les données persistantes du Pokémon ; au retour, elle reprend le niveau et l'espèce atteints dans Cobblemon
 * (évolutions comprises), et garde les données Cobblemon (IV, EV, talent, attaques, nature) pour le prochain
 * voyage.</p>
 */
public final class CobblemonBridge {
    private CobblemonBridge() {}

    /** Sauvegarde DirtRobes rangée dans le Pokémon. */
    static final String RECORD_KEY = "DirtRobesRecord";
    /** Données Cobblemon rangées dans la sauvegarde DirtRobes, pour retrouver IV/EV/attaques au prochain voyage. */
    static final String COBBLEMON_KEY = "CobblemonData";

    @Nullable
    static com.cobblemon.mod.common.pokemon.Species cobblemonSpecies(ResourceLocation dirtrobesId) {
        return PokemonSpecies.getByIdentifier(dirtrobesId);
    }

    /** Fabrique le Pokémon correspondant à une sauvegarde de Spectrobe. Null si l'espèce n'existe pas côté Cobblemon. */
    @Nullable
    static Pokemon toPokemon(RegistryAccess registries, CompoundTag record) {
        ResourceLocation id = ResourceLocation.tryParse(record.getString("Species"));
        if (id == null || SpeciesManager.get(id) == null) return null;
        com.cobblemon.mod.common.pokemon.Species species = cobblemonSpecies(id);
        if (species == null) return null;
        int level = Math.max(1, Math.min(StatCalc.MAX_LEVEL, record.getInt("SpectrobeLevel")));

        Pokemon pokemon;
        if (record.contains(COBBLEMON_KEY)) {
            // déjà venu dans Cobblemon : on retrouve ses IV, EV, talent, attaques…
            pokemon = new Pokemon().loadFromNBT(registries, record.getCompound(COBBLEMON_KEY));
            if (pokemon.getSpecies() != species) pokemon.setSpecies(species);
            if (pokemon.getLevel() != level) pokemon.setLevel(level);
        } else {
            pokemon = new Pokemon();
            pokemon.setSpecies(species);
            pokemon.setLevel(level);
            pokemon.initialize(); // IV, talent, nature, attaques apprises à ce niveau
        }
        pokemon.heal();
        CompoundTag copy = record.copy();
        copy.remove(COBBLEMON_KEY);
        pokemon.getPersistentData().put(RECORD_KEY, copy);
        return pokemon;
    }

    /** Sauvegarde de Spectrobe correspondant à un Pokémon d'une espèce DirtRobes. Null si ce n'en est pas un. */
    @Nullable
    static CompoundTag toRecord(ServerLevel level, @Nullable ServerPlayer owner, Pokemon pokemon) {
        ResourceLocation id = pokemon.getSpecies().getResourceIdentifier();
        if (!DirtRobes.MODID.equals(id.getNamespace())) return null;
        Species species = SpeciesManager.get(id);
        if (species == null) return null;
        int lvl = Math.max(1, Math.min(StatCalc.MAX_LEVEL, pokemon.getLevel()));

        CompoundTag record;
        CompoundTag stored = pokemon.getPersistentData().getCompound(RECORD_KEY);
        if (!stored.isEmpty() && stored.hasUUID("UUID")) {
            record = stored.copy();
        } else {
            // Pokémon jamais passé par DirtRobes (par ex. /pokegive) : nouveau Spectrobe
            SpectrobeEntity fresh = ModRegistry.SPECTROBE.get().create(level);
            if (fresh == null) return null;
            fresh.setSpecies(species, true);
            fresh.setSpectrobeLevel(lvl);
            if (owner != null) fresh.tame(owner);
            record = new CompoundTag();
            fresh.saveWithoutId(record);
            fresh.discard();
        }
        int oldLevel = record.getInt("SpectrobeLevel");
        record.putString("Species", id.toString());
        record.putInt("SpectrobeLevel", lvl);
        if (lvl != oldLevel) record.putInt("Xp", StatCalc.xpForLevel(lvl));
        record.putFloat("Health", 10_000f); // ramené aux PV max au chargement
        record.putInt("KoTicks", 0);
        // données Cobblemon sans la sauvegarde DirtRobes elle-même (pas d'imbrication d'un voyage à l'autre)
        CompoundTag inside = pokemon.getPersistentData().getCompound(RECORD_KEY);
        pokemon.getPersistentData().remove(RECORD_KEY);
        CompoundTag data = pokemon.saveToNBT(level.registryAccess(), new CompoundTag());
        if (!inside.isEmpty()) pokemon.getPersistentData().put(RECORD_KEY, inside);
        record.put(COBBLEMON_KEY, data);
        return record;
    }

    /** Envoie une sauvegarde dans l'équipe Cobblemon du joueur (ou son PC Cobblemon si l'équipe est pleine). */
    public static boolean send(ServerPlayer player, CompoundTag record) {
        Pokemon pokemon = toPokemon(player.registryAccess(), record);
        if (pokemon == null) return false;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        return party.add(pokemon);
    }

    /** Retire de l'équipe Cobblemon tous les Pokémon qui sont des Spectrobes et renvoie leurs sauvegardes (au plus {@code max}). */
    public static List<CompoundTag> takeBack(ServerPlayer player, int max) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<Pokemon> candidates = new ArrayList<>();
        for (Pokemon p : party) {
            if (p != null && DirtRobes.MODID.equals(p.getSpecies().getResourceIdentifier().getNamespace())) candidates.add(p);
        }
        List<CompoundTag> out = new ArrayList<>();
        for (Pokemon p : candidates) {
            if (out.size() >= max) break;
            PokemonEntity entity = p.getEntity();
            if (entity != null && entity.isBattling()) continue; // pas pendant un combat
            CompoundTag record = toRecord(player.serverLevel(), player, p);
            if (record == null) continue;
            if (entity != null) p.recall();
            if (party.remove(p)) out.add(record);
        }
        return out;
    }

    /** Nombre de Spectrobes dans l'équipe Cobblemon du joueur. */
    public static int countInParty(ServerPlayer player) {
        int n = 0;
        for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (p != null && DirtRobes.MODID.equals(p.getSpecies().getResourceIdentifier().getNamespace())) n++;
        }
        return n;
    }

    /**
     * Vérification (commande /dirtrobes cobblemon check, utilisée par la CI) : chaque espèce DirtRobes existe
     * côté Cobblemon, et un aller-retour Spectrobe → Pokémon → Spectrobe garde espèce et niveau.
     */
    public static String selfCheck(MinecraftServer server) {
        int total = 0, found = 0;
        List<String> missing = new ArrayList<>();
        for (Species s : SpeciesManager.all()) {
            total++;
            if (cobblemonSpecies(s.id()) != null) found++;
            else if (missing.size() < 10) missing.add(s.id().getPath());
        }
        String roundTrip = "non testé";
        Species first = SpeciesManager.get(DirtRobes.id("komainu"));
        if (first != null && cobblemonSpecies(first.id()) != null) {
            ServerLevel level = server.overworld();
            SpectrobeEntity e = ModRegistry.SPECTROBE.get().create(level);
            if (e != null) {
                e.setSpecies(first, true);
                e.setSpectrobeLevel(23);
                CompoundTag record = new CompoundTag();
                e.saveWithoutId(record);
                e.discard();
                Pokemon p = toPokemon(level.registryAccess(), record);
                CompoundTag back = p == null ? null : toRecord(level, null, p);
                boolean ok = p != null && p.getLevel() == 23 && back != null
                        && back.getString("Species").equals(first.id().toString()) && back.getInt("SpectrobeLevel") == 23
                        && back.getUUID("UUID").equals(record.getUUID("UUID"));
                // second voyage : les données Cobblemon (IV…) sont conservées
                if (ok) {
                    Pokemon again = toPokemon(level.registryAccess(), back);
                    ok = again != null && again.getUuid().equals(p.getUuid());
                }
                roundTrip = ok ? "OK" : "ÉCHEC";
            }
        }
        String msg = "Intégration Cobblemon : " + found + "/" + total + " espèces, aller-retour " + roundTrip
                + (missing.isEmpty() ? "" : " (manquantes : " + String.join(", ", missing) + ")");
        if (found != total || !"OK".equals(roundTrip)) DirtRobes.LOGGER.error(msg);
        else DirtRobes.LOGGER.info(msg);
        return msg;
    }
}
