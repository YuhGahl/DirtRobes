package com.spectrocraft.species;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.spectrocraft.SpectroCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Charge les espèces depuis les datapacks (dossier spectrobes/species), comme Cobblemon. */
public class SpeciesManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static Map<ResourceLocation, Species> species = Collections.emptyMap();

    public SpeciesManager() {
        super(GSON, "spectrobes/species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Species> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                loaded.put(id, Species.fromJson(id, json.getAsJsonObject()));
            } catch (Exception e) {
                SpectroCraft.LOGGER.error("Espèce de Spectrobe invalide : {}", id, e);
            }
        });
        species = loaded;
        SpectroCraft.LOGGER.info("{} espèces de Spectrobes chargées", loaded.size());
    }

    @Nullable
    public static Species get(@Nullable ResourceLocation id) {
        return id == null ? null : species.get(id);
    }

    @Nullable
    public static Species byName(String name) {
        for (Species s : species.values()) {
            if (s.name().equalsIgnoreCase(name) || s.id().getPath().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    public static Collection<Species> all() {
        return species.values();
    }

    /** Tire au hasard une espèce enfant de cette propriété qu'on peut obtenir par fossile. */
    @Nullable
    public static Species randomFossilSpecies(Property property, RandomSource random) {
        List<Species> pool = new ArrayList<>();
        for (Species s : species.values()) {
            if (s.fromFossil() && s.property() == property && s.stage() == Stage.CHILD) pool.add(s);
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }
}
