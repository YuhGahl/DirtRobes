package com.spectrocraft.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spectrocraft.SpectroCraft;
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
 * Un resource pack peut donc ajouter ou remplacer le modèle d'un Spectrobe sans toucher au code.
 */
public class ModelRepository extends SimplePreparableReloadListener<ModelRepository.Data> {
    private static final String ROOT = "bedrock/spectrobes/";
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
        load(manager, "models", ".geo.json", (id, json) -> data.models.put(id, BedrockModel.parse(json)));
        load(manager, "animations", ".animation.json", (id, json) -> data.animations.putAll(BedrockAnimation.parseFile(json)));
        load(manager, "posers", ".json", (id, json) -> data.posers.put(id, Poser.parse(json)));
        load(manager, "resolvers", ".json", (id, json) -> {
            Resolver r = Resolver.parse(json, id.toString());
            data.resolvers.put(r.key(), r);
        });
        return data;
    }

    @Override
    protected void apply(Data data, ResourceManager manager, ProfilerFiller profiler) {
        current = data;
        SpectroCraft.LOGGER.info("Spectrobes : {} modèles, {} animations, {} posers, {} resolvers",
                data.models.size(), data.animations.size(), data.posers.size(), data.resolvers.size());
    }

    private interface Loader {
        void accept(ResourceLocation id, JsonObject json);
    }

    private static void load(ResourceManager manager, String folder, String suffix, Loader loader) {
        Map<ResourceLocation, Resource> found = manager.listResources(ROOT + folder, rl -> rl.getPath().endsWith(suffix));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - suffix.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
            try (Reader reader = e.getValue().openAsReader()) {
                loader.accept(id, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception ex) {
                SpectroCraft.LOGGER.error("Fichier Bedrock invalide : {}", file, ex);
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
