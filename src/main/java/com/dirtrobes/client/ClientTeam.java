package com.dirtrobes.client;

import com.dirtrobes.client.screen.PrizmodScreen;
import com.dirtrobes.client.screen.SpectrobeSummaryScreen;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Équipe du joueur côté client (reçue du serveur) + mannequins pour afficher les modèles. */
public final class ClientTeam {
    private ClientTeam() {}

    private static List<TeamSummary> members = new ArrayList<>();
    private static int selected;
    private static final Map<String, SpectrobeEntity> PREVIEWS = new HashMap<>();

    public static List<TeamSummary> members() {
        return members;
    }

    public static int selected() {
        return members.isEmpty() ? 0 : Math.min(selected, members.size() - 1);
    }

    public static void select(int delta) {
        if (members.isEmpty()) return;
        selected = Math.floorMod(selected() + delta, members.size());
    }

    private static com.dirtrobes.combat.Support support = com.dirtrobes.combat.Support.NONE;

    public static com.dirtrobes.combat.Support support() {
        return support;
    }

    public static void onSync(List<TeamSummary> list, boolean open, com.dirtrobes.combat.Support newSupport) {
        members = new ArrayList<>(list);
        support = newSupport;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PrizmodScreen screen) {
            screen.refresh();
        } else if (mc.screen instanceof SpectrobeSummaryScreen summary) {
            summary.refresh();
        } else if (open) {
            mc.setScreen(new PrizmodScreen());
        }
    }

    /** Mannequin (non ajouté au monde) pour dessiner le modèle d'une espèce dans l'interface. */
    @Nullable
    public static SpectrobeEntity preview(String speciesId, int property, int stage) {
        return preview(speciesId, property, stage, 0);
    }

    @Nullable
    public static SpectrobeEntity preview(String speciesId, int property, int stage, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || speciesId.isEmpty()) return null;
        String key = speciesId + "|" + property + "|" + stage + "|" + color;
        SpectrobeEntity e = PREVIEWS.get(key);
        if (e == null || e.level() != mc.level) {
            e = SpectrobeEntity.preview(mc.level, speciesId, property, stage, color);
            PREVIEWS.put(key, e);
        }
        return e;
    }

    /** Fait vivre les mannequins (animations d'attente). */
    public static void tickPreviews() {
        for (SpectrobeEntity e : PREVIEWS.values()) e.tickCount++;
    }
}
