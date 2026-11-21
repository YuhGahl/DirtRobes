package com.dirtrobes.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Demande aux clients (le joueur et ceux qui le voient) de jouer une animation Player Animator. */
public final class PlayerAnims {
    private PlayerAnims() {}

    public static final String DEPLOY = "gauntlet_deploy";
    public static final String EXTRACT = "gauntlet_extract";
    public static final String CANNON = "gauntlet_cannon";
    public static final String PRIZMOD = "prizmod_call";

    public static void play(ServerPlayer player, String name) {
        var payload = new ModNetwork.PlayAnimPayload(player.getId(), name);
        if (player.connection != null && player.connection.hasChannel(ModNetwork.PlayAnimPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }
}
