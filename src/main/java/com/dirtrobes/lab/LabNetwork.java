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
    public record PcSyncPayload(BlockPos pos, int box, List<TeamSummary> team, List<TeamSummary> boxSlots,
                                @javax.annotation.Nullable Pen pen, int stored, boolean open) implements CustomPacketPayload {
        public boolean hasPen() {
            return pen != null;
        }

        public static final Type<PcSyncPayload> TYPE = new Type<>(DirtRobes.id("pc_sync"));
        public static final StreamCodec<FriendlyByteBuf, PcSyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.box);
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
                    BlockPos pos = buf.readBlockPos();
                    int box = buf.readVarInt();
                    List<TeamSummary> team = new ArrayList<>();
                    for (int i = buf.readVarInt(); i > 0; i--) team.add(TeamSummary.STREAM_CODEC.decode(buf));
                    List<TeamSummary> slots = new ArrayList<>();
                    for (int i = buf.readVarInt(); i > 0; i--) slots.add(TeamSummary.STREAM_CODEC.decode(buf));
                    Pen pen = buf.readBoolean() ? Pen.read(buf) : null;
                    return new PcSyncPayload(pos, box, team, slots, pen, buf.readVarInt(), buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Demande d'affichage d'une boîte. */
    public record PcBoxPayload(BlockPos pos, int box) implements CustomPacketPayload {
        public static final Type<PcBoxPayload> TYPE = new Type<>(DirtRobes.id("pc_box"));
        public static final StreamCodec<ByteBuf, PcBoxPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PcBoxPayload::pos, ByteBufCodecs.VAR_INT, PcBoxPayload::box, PcBoxPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Déplacement d'un Spectrobe : (zone, case) → (zone, case). Zones : 0 = équipe, 1 = boîtes (index absolu). */
    public record PcMovePayload(BlockPos pos, int fromArea, int fromIndex, int toArea, int toIndex, int box) implements CustomPacketPayload {
        public static final Type<PcMovePayload> TYPE = new Type<>(DirtRobes.id("pc_move"));
        public static final StreamCodec<ByteBuf, PcMovePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PcMovePayload::pos,
                ByteBufCodecs.VAR_INT, PcMovePayload::fromArea,
                ByteBufCodecs.VAR_INT, PcMovePayload::fromIndex,
                ByteBufCodecs.VAR_INT, PcMovePayload::toArea,
                ByteBufCodecs.VAR_INT, PcMovePayload::toIndex,
                ByteBufCodecs.VAR_INT, PcMovePayload::box,
                PcMovePayload::new);

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

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("lab2")
                .playToServer(PenEditPayload.TYPE, PenEditPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !near(player, p.pos())) return;
                    if (player.level().getBlockEntity(p.pos()) instanceof LabTerminalBlockEntity terminal) {
                        terminal.setPen(p.enabled() ? Pen.fromRelative(p.pos(), p.ox(), p.oy(), p.oz(), p.sx(), p.sy(), p.sz(), p.px(), p.py(), p.pz()) : null);
                    }
                    sendPc(player, p.pos(), 0, false);
                }))
                .playToClient(PcSyncPayload.TYPE, PcSyncPayload.STREAM_CODEC, LabNetwork::handleSync)
                .playToServer(PcBoxPayload.TYPE, PcBoxPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player && near(player, p.pos())) sendPc(player, p.pos(), p.box(), false);
                }))
                .playToServer(PcMovePayload.TYPE, PcMovePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || !near(player, p.pos())) return;
                    if (player.level().getBlockEntity(p.pos()) instanceof LabTerminalBlockEntity terminal) {
                        terminal.move(player, p.fromArea(), p.fromIndex(), p.toArea(), p.toIndex());
                    }
                    sendPc(player, p.pos(), p.box(), false);
                }));
    }

    private static void handleSync(PcSyncPayload payload, IPayloadContext context) {
        // Classe client chargée seulement quand ce code s'exécute (jamais sur un serveur dédié).
        context.enqueueWork(() -> com.dirtrobes.client.screen.PcScreen.onSync(payload));
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 8 * 8;
    }

    /** Envoie au joueur le contenu du PC (équipe + une boîte). */
    public static void sendPc(ServerPlayer player, BlockPos pos, int box, boolean open) {
        if (!(player.level().getBlockEntity(pos) instanceof LabTerminalBlockEntity terminal)) return;
        if (player.connection == null || !player.connection.hasChannel(PcSyncPayload.TYPE)) return;
        int b = Math.floorMod(box, LabTerminalBlockEntity.BOXES);
        List<TeamSummary> slots = new ArrayList<>();
        for (int i = 0; i < LabTerminalBlockEntity.BOX_SIZE; i++) {
            CompoundTag tag = terminal.get(player.getUUID(), b * LabTerminalBlockEntity.BOX_SIZE + i);
            slots.add(tag == null ? empty() : summary(player, tag));
        }
        PacketDistributor.sendToPlayer(player, new PcSyncPayload(pos, b, PrizmodTeam.summaries(player), slots,
                terminal.pen(), terminal.count(player.getUUID()), open));
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
