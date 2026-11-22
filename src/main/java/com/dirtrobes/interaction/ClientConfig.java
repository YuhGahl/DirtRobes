package com.dirtrobes.interaction;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Réglages côté client (config/dirtrobes-client.toml). */
public final class ClientConfig {
    private ClientConfig() {}

    public static final ModConfigSpec SPEC;
    /** Shift + clic droit sur son Spectrobe : roue d'interaction (true) ou ancien « Reste ici / Suis-moi » direct (false). */
    public static final ModConfigSpec.BooleanValue INTERACTION_WHEEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        INTERACTION_WHEEL = b.comment("Shift + clic droit sur son Spectrobe ouvre la roue d'interaction.",
                        "false : l'ancien comportement (Reste ici / Suis-moi directement).")
                .define("interactionWheel", true);
        SPEC = b.build();
    }

    public static boolean wheelEnabled() {
        try {
            return INTERACTION_WHEEL.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
