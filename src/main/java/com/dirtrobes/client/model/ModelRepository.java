package com.dirtrobes.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dirtrobes.DirtRobes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Charge les modèles, animations, posers et resolvers depuis les resource packs :
 * assets/&lt;ns&gt;/bedrock/spectrobes/{models,animations,posers,resolvers}/...
 * Les modèles, animations et posers des Spectrobes sont rangés dans assets/dirtrobes/bedrock/pokemon/… : le même
 * fichier sert à DirtRobes et à Cobblemon (qui ne lit que ce dossier). Seul l'espace « dirtrobes » y est lu ici.
 * Un resource pack peut donc ajouter ou remplacer le modèle d'un Spectrobe sans toucher au code.
 */
public class ModelRepository extends SimplePreparableReloadListener<ModelRepository.Data> {
    private static final String ROOT = "bedrock/spectrobes/";
    /** Dossier partagé avec Cobblemon. */
    private static final String SHARED = "bedrock/pokemon/";
    private static Data current = new Data();

    public static final class Data {
        final Map<ResourceLocation, BedrockModel> models = new HashMap<>();
        final Map<String, BedrockAnimation> animations = new HashMap<>();
        final Map<ResourceLocation, Poser> posers = new HashMap<>();
        final Map<String, Resolver> resolvers = new HashMap<>();
    }

    @Override
    protected Data prepare(ResourceManager manager, ProfilerFiller profiler) {
        Data data = new Data();
        for (String root : new String[]{ROOT, SHARED}) {
            boolean shared = root.equals(SHARED);
            load(manager, root, shared, "models", ".geo.json", (id, json) -> data.models.put(id, BedrockModel.parse(json)));
            load(manager, root, shared, "animations", ".animation.json", (id, json) -> data.animations.putAll(BedrockAnimation.parseFile(json)));
            load(manager, root, shared, "posers", ".json", (id, json) -> data.posers.put(id, Poser.parse(json)));
        }
        load(manager, ROOT, false, "resolvers", ".json", (id, json) -> {
            Resolver r = Resolver.parse(json, id.toString());
            data.resolvers.put(r.key(), r);
        });
        return data;
    }

    @Override
    protected void apply(Data data, ResourceManager manager, ProfilerFiller profiler) {
        current = data;
        DirtRobes.LOGGER.info("Spectrobes : {} modèles, {} animations, {} posers, {} resolvers",
                data.models.size(), data.animations.size(), data.posers.size(), data.resolvers.size());
    }

    private interface Loader {
        void accept(ResourceLocation id, JsonObject json);
    }

    private static void load(ResourceManager manager, String root, boolean onlyOurs, String folder, String suffix, Loader loader) {
        Map<ResourceLocation, Resource> found = manager.listResources(root + folder, rl -> rl.getPath().endsWith(suffix)
                && (!onlyOurs || rl.getNamespace().equals(DirtRobes.MODID)));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - suffix.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
            try (Reader reader = e.getValue().openAsReader()) {
                loader.accept(id, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception ex) {
                DirtRobes.LOGGER.error("Fichier Bedrock invalide : {}", file, ex);
            }
        }
    }

    @Nullable
    public static BedrockModel model(ResourceLocation id) {
        return current.models.get(id);
    }

    @Nullable
    public static BedrockAnimation animation(String group, String name) {
        return current.animations.get("animation." + group + "." + name);
    }

    @Nullable
    public static Poser poser(ResourceLocation id) {
        return current.posers.get(id);
    }

    /** Resolver de l'espèce, sinon celui par défaut du stade (« default:child », « default:adult »…). */
    @Nullable
    public static Resolver resolver(@Nullable ResourceLocation species, String stage) {
        if (species != null) {
            Resolver r = current.resolvers.get(species.toString());
            if (r != null) return r;
        }
        Resolver r = current.resolvers.get("default:" + stage);
        return r != null ? r : current.resolvers.get("default:adult");
    }
}
