package com.dirtrobes.krawl;

import com.dirtrobes.DirtRobes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Charge les Krawl depuis les datapacks (dossier spectrobes/krawl). */
public class KrawlManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static Map<ResourceLocation, KrawlSpecies> krawl = Collections.emptyMap();

    public KrawlManager() {
        super(GSON, "spectrobes/krawl");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, KrawlSpecies> loaded = new LinkedHashMap<>();
        entries.forEach((id, json) -> {
            try {
                loaded.put(id, KrawlSpecies.fromJson(id, json.getAsJsonObject()));
            } catch (Exception e) {
                DirtRobes.LOGGER.error("Krawl invalide : {}", id, e);
            }
        });
        krawl = loaded;
        DirtRobes.LOGGER.info("{} espèces de Krawl chargées", loaded.size());
    }

    @Nullable
    public static KrawlSpecies get(@Nullable ResourceLocation id) {
        return id == null ? null : krawl.get(id);
    }

    @Nullable
    public static KrawlSpecies byName(String name) {
        for (KrawlSpecies k : krawl.values()) {
            if (k.name().equalsIgnoreCase(name) || k.id().getPath().equalsIgnoreCase(name)) return k;
        }
        return null;
    }

    public static Collection<KrawlSpecies> all() {
        return krawl.values();
    }

    /** Tire un Krawl au hasard, pondéré par « weight ». */
    @Nullable
    public static KrawlSpecies random(RandomSource random) {
        int total = 0;
        for (KrawlSpecies k : krawl.values()) total += Math.max(0, k.weight());
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (KrawlSpecies k : krawl.values()) {
            roll -= Math.max(0, k.weight());
            if (roll < 0) return k;
        }
        return null;
    }
}
