package com.dirtrobes.combat;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.network.ModNetwork;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Jauge CH du joueur (inspirée de Spectrobes) : elle se remplit quand ses Spectrobes frappent
 * ou encaissent des coups, et l'attaque spéciale (touche R) la consomme. Hors combat, elle
 * redescend lentement. Sauvegardée avec le joueur.
 */
public final class ChGauge {
    private ChGauge() {}

    public static final int MAX = 100;
    /** Coût d'une attaque spéciale. */
    public static final int SPECIAL_COST = 30;
    public static final int GAIN_HIT = 6;
    public static final int GAIN_HURT = 3;
    public static final int GAIN_PLAYER_HIT = 2;
    /** Ticks sans combat avant que la jauge ne redescende. */
    public static final int IDLE_TICKS = 200;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DirtRobes.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> CH = ATTACHMENTS.register("ch",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());
    /** Dernier tick de combat (non sauvegardé). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> LAST_COMBAT = ATTACHMENTS.register("last_combat",
            () -> AttachmentType.builder(() -> -100000L).build());

    public static int get(ServerPlayer player) {
        return player.getData(CH);
    }

    public static void set(ServerPlayer player, int value) {
        int v = Mth.clamp(value, 0, MAX);
        if (v != get(player)) {
            player.setData(CH, v);
            sync(player);
        }
    }

    /** Bonus de gain CH (soutien Eclipse), mis à jour chaque seconde. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> GAIN_BONUS = ATTACHMENTS.register("ch_gain_bonus",
            () -> AttachmentType.builder(() -> 0f).build());

    public static void add(ServerPlayer player, int amount) {
        player.setData(LAST_COMBAT, player.level().getGameTime());
        set(player, get(player) + Math.round(amount * (1f + player.getData(GAIN_BONUS))));
    }

    /** Retire le coût si la jauge est assez remplie. */
    public static boolean trySpend(ServerPlayer player, int cost) {
        if (get(player) < cost) return false;
        player.setData(LAST_COMBAT, player.level().getGameTime());
        set(player, get(player) - cost);
        return true;
    }

    /** Appelé toutes les secondes : la jauge perd 2 points après 10 s sans combat. */
    public static void tickDecay(ServerPlayer player) {
        if (get(player) > 0 && player.level().getGameTime() - player.getData(LAST_COMBAT) > IDLE_TICKS) {
            set(player, get(player) - 2);
        }
    }

    public static void sync(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(ModNetwork.ChSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ModNetwork.ChSyncPayload(get(player)));
        }
    }
}
