package com.dirtrobes.compat.create;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Côté client de l'intégration Create (chargé seulement si Create est installé). */
public final class CreateClientCompat {
    private CreateClientCompat() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateCompat.COG_INCUBATOR_BE.get(), CogIncubatorRenderer::new);
    }
}
