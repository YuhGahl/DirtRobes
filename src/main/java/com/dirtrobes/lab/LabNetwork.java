package com.dirtrobes.lab;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.team.TeamSummary;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Réseau du PC du laboratoire : ouvrir / changer de boîte / déplacer un Spectrobe. */
public final class LabNetwork {
    private LabNetwork() {}

    /** Contenu affiché par l'écran du PC. Les cases vides ont un speciesId vide. */
    public record PcSyncPayload(int kind, BlockPos pos, int box, int boxes, List<TeamSummary> team, List<TeamSummary> boxSlots,
                                @javax.annotation.Nullable Pen pen, int stored, boolean open) implements CustomPacketPayload {
        public boolean hasPen() {
            return pen != null;
        }

        public static final Type<PcSyncPayload> TYPE = new Type<>(DirtRobes.id("pc_sync"));
        public static final StreamCodec<FriendlyByteBuf, PcSyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.box);
                    buf.writeVarInt(p.boxes);
                    buf.writeVarInt(p.team.size());
                    for (TeamSummary s : p.team) TeamSummary.STREAM_CODEC.encode(buf, s);
                    buf.writeVarInt(p.boxSlots.size());
                    for (TeamSummary s : p.boxSlots) TeamSummary.STREAM_CODEC.encode(buf, s);
                    buf.writeBoolean(p.pen != null);
                    if (p.pen != null) p.pen.write(buf);
                    buf.writeVarInt(p.stored);
                    buf.writeBoolean(p.open);
                },
                buf -> {
                    int kind = buf.readVarInt();
                    BlockPos pos = buf.readBlockPos();
                    int box = buf.readVarInt();
                    int boxes = buf.readVarInt();
                    List<TeamSummary> team = new ArrayList<>();
                    for (int i = buf.readVarInt(); i > 0; i--) team.add(TeamSummary.STREAM_CODEC.decode(buf));
                    List<TeamSummary> slots = new ArrayList<>();
                    for (int i = buf.readVarInt(); i > 0; i--) slots.add(TeamSummary.STREAM_CODEC.decode(buf));
                    Pen pen = buf.readBoolean() ? Pen.read(buf) : null;
                    return new PcSyncPayload(kind, pos, box, boxes, team, slots, pen, buf.readVarInt(), buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Demande d'affichage d'une boîte. */
    public record PcBoxPayload(int kind, BlockPos pos, int box) implements CustomPacketPayload {
        public static final Type<PcBoxPayload> TYPE = new Type<>(DirtRobes.id("pc_box"));
        public static final StreamCodec<ByteBuf, PcBoxPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PcBoxPayload::kind, BlockPos.STREAM_CODEC, PcBoxPayload::pos, ByteBufCodecs.VAR_INT, PcBoxPayload::box,
                PcBoxPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Déplacement d'un Spectrobe : (zone, case) → (zone, case). Zones : 0 = équipe, 1 = boîtes (index absolu). */
    public record PcMovePayload(int kind, BlockPos pos, int fromArea, int fromIndex, int toArea, int toIndex, int box) implements CustomPacketPayload {
        public static final Type<PcMovePayload> TYPE = new Type<>(DirtRobes.id("pc_move"));
        public static final StreamCodec<FriendlyByteBuf, PcMovePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeBlockPos(p.pos);
                    for (int v : new int[]{p.fromArea, p.fromIndex, p.toArea, p.toIndex, p.box}) buf.writeVarInt(v);
                },
                buf -> new PcMovePayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()));
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Envoie directement le membre n°slot de l'équipe dans le PC des Spectrobes (depuis le Prizmod). */
    public record TeamToPcPayload(int slot) implements CustomPacketPayload {
        public static final Type<TeamToPcPayload> TYPE = new Type<>(DirtRobes.id("team_to_pc"));
        public static final StreamCodec<ByteBuf, TeamToPcPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TeamToPcPayload::slot, TeamToPcPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Réglage de l'enclos depuis l'interface : activé ou non, position du coin (par rapport au terminal),
     * taille, et point de spawn (par rapport au coin).
     */
    public record PenEditPayload(BlockPos pos, boolean enabled, int ox, int oy, int oz, int sx, int sy, int sz,
                                 int px, int py, int pz) implements CustomPacketPayload {
        public static final Type<PenEditPayload> TYPE = new Type<>(DirtRobes.id("pen_edit"));
        public static final StreamCodec<FriendlyByteBuf, PenEditPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.enabled);
                    for (int v : new int[]{p.ox, p.oy, p.oz, p.sx, p.sy, p.sz, p.px, p.py, p.pz}) buf.writeVarInt(v);
                },
                buf -> new PenEditPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Relâcher définitivement un Spectrobe depuis le PC ou le terminal (le client a déjà demandé confirmation). */
    public record PcReleasePayload(int kind, BlockPos pos, int area, int index, int box) implements CustomPacketPayload {
        public static final Type<PcReleasePayload> TYPE = new Type<>(DirtRobes.id("pc_release"));
        public static final StreamCodec<FriendlyByteBuf, PcReleasePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.kind);
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.area);
                    buf.writeVarInt(p.index);
                    buf.writeVarInt(p.box);
                },
                buf -> new PcReleasePayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Relâche le Spectrobe : sa sauvegarde est supprimée (irréversible). */
    public static boolean release(ServerPlayer player, com.dirtrobes.storage.SpectrobeStorage storage, int area, int index) {
        CompoundTag record;
        if (area == com.dirtrobes.storage.StorageOps.AREA_TEAM) {
            if (PrizmodTeam.blockedByLeader(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.team_leader_block")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return false;
            }
            record = PrizmodTeam.peekRecord(player, index);
            if (record == null) return false;
            PrizmodTeam.putRecord(player, index, null);
            PrizmodTeam.sync(player, false);
        } else {
            record = storage.get(player.getUUID(), index);
            if (record == null) return false;
            storage.set(player.getUUID(), index, null);
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.released",
                net.minecraft.network.chat.Component.translatable("species." + record.getString("Species").replace(':', '.'))), true);
        return true;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("lab3")
                .playToServer(PcReleasePayload.TYPE, PcReleasePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !near(player, p.pos())) return;
                    com.dirtrobes.storage.SpectrobeStorage storage = storage(player, p.kind(), p.pos());
                    if (storage != null) release(player, storage, p.area(), p.index());
                    sendStorage(player, p.kind(), p.pos(), p.box(), false);
                }))
                .playToServer(PenEditPayload.TYPE, PenEditPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !near(player, p.pos())) return;
                    if (player.level().getBlockEntity(p.pos()) instanceof LabTerminalBlockEntity terminal) {
                        terminal.setPen(p.enabled() ? Pen.fromRelative(p.pos(), p.ox(), p.oy(), p.oz(), p.sx(), p.sy(), p.sz(), p.px(), p.py(), p.pz()) : null);
                    }
                    sendStorage(player, KIND_TERMINAL, p.pos(), 0, false);
                }))
                .playToClient(PcSyncPayload.TYPE, PcSyncPayload.STREAM_CODEC, LabNetwork::handleSync)
                .playToServer(PcBoxPayload.TYPE, PcBoxPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player && near(player, p.pos())) sendStorage(player, p.kind(), p.pos(), p.box(), false);
                }))
                .playToServer(PcMovePayload.TYPE, PcMovePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !near(player, p.pos())) return;
                    com.dirtrobes.storage.SpectrobeStorage storage = storage(player, p.kind(), p.pos());
                    if (storage != null) com.dirtrobes.storage.StorageOps.move(player, storage, p.fromArea(), p.fromIndex(), p.toArea(), p.toIndex());
                    sendStorage(player, p.kind(), p.pos(), p.box(), false);
                }))
                .playToServer(TeamToPcPayload.TYPE, TeamToPcPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) teamToPc(player, p.slot());
                }));
    }

    public static final int KIND_TERMINAL = 0;
    public static final int KIND_PC = 1;

    @javax.annotation.Nullable
    private static com.dirtrobes.storage.SpectrobeStorage storage(ServerPlayer player, int kind, BlockPos pos) {
        if (kind == KIND_PC) {
            return player.level().getBlockState(pos).is(ModRegistry.SPECTROBE_PC.get())
                    ? com.dirtrobes.storage.GlobalPc.get(player.server) : null;
        }
        return player.level().getBlockEntity(pos) instanceof LabTerminalBlockEntity t ? t : null;
    }

    /** Rangement ouvert par le joueur (PC ou terminal à portée), ou null. */
    @javax.annotation.Nullable
    public static com.dirtrobes.storage.SpectrobeStorage storageFor(ServerPlayer player, int kind, BlockPos pos) {
        return near(player, pos) ? storage(player, kind, pos) : null;
    }

    /** Envoie directement un membre de l'équipe au PC (bouton du Prizmod). */
    public static boolean teamToPc(ServerPlayer player, int slot) {
        if (PrizmodTeam.blockedByLeader(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.team_leader_block")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        CompoundTag tag = PrizmodTeam.peekRecord(player, slot);
        if (tag == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.team_not_found"), true);
            return false;
        }
        int index = com.dirtrobes.storage.GlobalPc.get(player.server).add(player.getUUID(), tag);
        PrizmodTeam.putRecord(player, slot, null);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.sent_to_pc",
                index / com.dirtrobes.storage.SpectrobeStorage.BOX_SIZE + 1), true);
        return true;
    }

    private static void handleSync(PcSyncPayload payload, IPayloadContext context) {
        // Classe client chargée seulement quand ce code s'exécute (jamais sur un serveur dédié).
        context.enqueueWork(() -> com.dirtrobes.client.screen.PcScreen.onSync(payload));
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 8 * 8;
    }

    /** Envoie au joueur le contenu d'un rangement (équipe + une boîte). */
    public static void sendStorage(ServerPlayer player, int kind, BlockPos pos, int box, boolean open) {
        com.dirtrobes.storage.SpectrobeStorage storage = storage(player, kind, pos);
        if (storage == null) return;
        if (player.connection == null || !player.connection.hasChannel(PcSyncPayload.TYPE)) return;
        int boxes = storage.boxes(player.getUUID());
        int b = Math.floorMod(box, boxes);
        List<TeamSummary> slots = new ArrayList<>();
        for (int i = 0; i < com.dirtrobes.storage.SpectrobeStorage.BOX_SIZE; i++) {
            CompoundTag tag = storage.get(player.getUUID(), b * com.dirtrobes.storage.SpectrobeStorage.BOX_SIZE + i);
            slots.add(tag == null ? empty() : summary(player, tag));
        }
        Pen pen = storage instanceof LabTerminalBlockEntity t ? t.pen() : null;
        PacketDistributor.sendToPlayer(player, new PcSyncPayload(kind, pos, b, boxes, PrizmodTeam.summaries(player), slots,
                pen, storage.count(player.getUUID()), open));
    }

    private static TeamSummary summary(ServerPlayer player, CompoundTag tag) {
        SpectrobeEntity s = ModRegistry.SPECTROBE.get().create(player.serverLevel());
        if (s == null) return empty();
        s.load(tag);
        return TeamSummary.of(s, false, true);
    }

    private static TeamSummary empty() {
        TeamSummary m = TeamSummary.missing();
        return m;
    }
}
