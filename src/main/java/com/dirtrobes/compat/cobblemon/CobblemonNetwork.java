package com.dirtrobes.compat.cobblemon;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.lab.LabNetwork;
import com.dirtrobes.storage.SpectrobeStorage;
import com.dirtrobes.storage.StorageOps;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * Boutons « → Cobblemon » / « ← Cobblemon » du PC des Spectrobes. Le paquet ne référence aucune classe de
 * Cobblemon : il est toujours enregistré, et ne fait rien si Cobblemon n'est pas installé.
 */
public final class CobblemonNetwork {
    private CobblemonNetwork() {}

    public static final int ACTION_SEND = 0;
    public static final int ACTION_TAKE_BACK = 1;

    public static boolean cobblemonLoaded() {
        return ModList.get().isLoaded("cobblemon");
    }

    /** action : 0 = envoyer le Spectrobe (zone, case) vers Cobblemon ; 1 = récupérer les Spectrobes de l'équipe Cobblemon. */
    public record BridgePayload(int kind, BlockPos pos, int action, int area, int index, int box) implements CustomPacketPayload {
        public static final Type<BridgePayload> TYPE = new Type<>(DirtRobes.id("cobblemon_bridge"));
        public static final StreamCodec<FriendlyByteBuf, BridgePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeBlockPos(p.pos);
                    for (int v : new int[]{p.action, p.area, p.index, p.box}) buf.writeVarInt(v);
                },
                buf -> new BridgePayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("cobblemon_bridge1").optional()
                .playToServer(BridgePayload.TYPE, BridgePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !cobblemonLoaded()) return;
                    SpectrobeStorage storage = LabNetwork.storageFor(player, p.kind(), p.pos());
                    if (storage == null) return;
                    if (p.action() == ACTION_SEND) send(player, storage, p.area(), p.index());
                    else if (p.action() == ACTION_TAKE_BACK) takeBack(player, storage);
                    LabNetwork.sendStorage(player, p.kind(), p.pos(), p.box(), false);
                }));
    }

    private static void send(ServerPlayer player, SpectrobeStorage storage, int area, int index) {
        CompoundTag record;
        if (area == StorageOps.AREA_TEAM) {
            if (PrizmodTeam.blockedByLeader(player)) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.team_leader_block").withStyle(ChatFormatting.RED), true);
                return;
            }
            record = PrizmodTeam.peekRecord(player, index);
        } else {
            record = storage.get(player.getUUID(), index);
        }
        if (record == null) return;
        if (!CobblemonBridge.send(player, record)) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.cobblemon.send_failed").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (area == StorageOps.AREA_TEAM) PrizmodTeam.putRecord(player, index, null);
        else storage.set(player.getUUID(), index, null);
        player.displayClientMessage(Component.translatable("message.dirtrobes.cobblemon.sent",
                Component.translatable("species." + record.getString("Species").replace(':', '.'))).withStyle(ChatFormatting.AQUA), true);
    }

    private static void takeBack(ServerPlayer player, SpectrobeStorage storage) {
        int free = 0;
        int capacity = storage.capacity(player.getUUID());
        for (int i = 0; i < capacity; i++) if (storage.get(player.getUUID(), i) == null) free++;
        if (CobblemonBridge.countInParty(player) == 0) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.cobblemon.none"), true);
            return;
        }
        List<CompoundTag> records = CobblemonBridge.takeBack(player, free);
        int slot = 0;
        for (CompoundTag record : records) {
            while (storage.get(player.getUUID(), slot) != null) slot++;
            storage.set(player.getUUID(), slot, record);
        }
        player.displayClientMessage(Component.translatable(records.isEmpty() ? "message.dirtrobes.cobblemon.none_back"
                : "message.dirtrobes.cobblemon.back", records.size()).withStyle(ChatFormatting.AQUA), true);
    }
}
