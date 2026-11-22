package com.dirtrobes.interaction;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.lab.LabNetwork;
import com.dirtrobes.team.PrizmodTeam;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Actions de la roue d'interaction (Shift + clic droit sur son Spectrobe). Le client choisit, le serveur vérifie et
 * applique. Un Spectrobe sur l'épaule ou monté reste « dehors » dans l'équipe du Prizmod.
 */
public final class SpectrobeActions {
    private SpectrobeActions() {}

    public static final int STAY_FOLLOW = 0;
    public static final int SHOULDER = 1;
    public static final int RIDE = 2;
    public static final int SUMMARY = 3;
    public static final int RECALL = 4;
    public static final int TO_PC = 5;
    public static final int TO_COBBLEMON = 6;

    /** Distance maximale entre le joueur et son Spectrobe pour agir. */
    public static final double MAX_DISTANCE = 8;

    public record ActionPayload(int entityId, int action) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(DirtRobes.id("spectrobe_action"));
        public static final StreamCodec<ByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ActionPayload::entityId, ByteBufCodecs.VAR_INT, ActionPayload::action, ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Le serveur demande au client d'ouvrir la fiche du membre n°slot. */
    public record OpenSummaryPayload(int slot) implements CustomPacketPayload {
        public static final Type<OpenSummaryPayload> TYPE = new Type<>(DirtRobes.id("open_summary"));
        public static final StreamCodec<ByteBuf, OpenSummaryPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenSummaryPayload::slot, OpenSummaryPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("wheel1")
                .playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level().getEntity(p.entityId()) instanceof SpectrobeEntity s) {
                        apply(player, s, p.action());
                    }
                }))
                .playToClient(OpenSummaryPayload.TYPE, OpenSummaryPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                        com.dirtrobes.client.screen.InteractionWheelScreen.openSummary(p.slot())));
    }

    public static boolean cobblemonLoaded() {
        return ModList.get().isLoaded("cobblemon");
    }

    /** Applique l'action ; false si elle est impossible (le joueur reçoit un message). */
    public static boolean apply(ServerPlayer player, SpectrobeEntity s, int action) {
        if (!s.isOwnedBy(player) || s.isPenResident() || !s.isAlive()) return false;
        if (s.distanceTo(player) > MAX_DISTANCE && !s.isPassenger() && s.getControllingPassenger() != player) return false;
        switch (action) {
            case STAY_FOLLOW -> {
                if (s.isOnShoulder()) s.jumpOffShoulder(player);
                s.toggleSit(player);
                return true;
            }
            case SHOULDER -> {
                if (s.getVehicle() == player) {
                    s.jumpOffShoulder(player);
                    return true;
                }
                if (!s.canSitOnShoulder()) return fail(player, "message.dirtrobes.wheel.too_big", s);
                if (!player.getPassengers().isEmpty()) return fail(player, "message.dirtrobes.wheel.shoulder_taken", s);
                s.setOrderedToSit(false);
                s.setInSittingPose(false);
                s.getNavigation().stop();
                s.setTarget(null);
                return s.startRiding(player, true);
            }
            case RIDE -> {
                if (player.getVehicle() == s) {
                    player.stopRiding();
                    return true;
                }
                if (!s.canBeRidden()) return fail(player, "message.dirtrobes.wheel.too_small", s);
                if (s.isKnockedOut()) return fail(player, "message.dirtrobes.wheel.knocked_out", s);
                s.setOrderedToSit(false);
                s.setInSittingPose(false);
                s.getNavigation().stop();
                s.setTarget(null);
                return player.startRiding(s);
            }
            case SUMMARY -> {
                int slot = PrizmodTeam.indexOf(player, s.getUUID());
                if (slot < 0) return fail(player, "message.dirtrobes.wheel.not_in_team", s);
                PrizmodTeam.sync(player, false);
                PacketDistributor.sendToPlayer(player, new OpenSummaryPayload(slot));
                return true;
            }
            case RECALL -> {
                int slot = PrizmodTeam.indexOf(player, s.getUUID());
                if (slot < 0) return fail(player, "message.dirtrobes.wheel.not_in_team", s);
                dismount(player, s);
                return PrizmodTeam.toggle(player, slot);
            }
            case TO_PC -> {
                int slot = PrizmodTeam.indexOf(player, s.getUUID());
                if (slot < 0) return fail(player, "message.dirtrobes.wheel.not_in_team", s);
                dismount(player, s);
                boolean ok = LabNetwork.teamToPc(player, slot);
                PrizmodTeam.sync(player, false);
                return ok;
            }
            case TO_COBBLEMON -> {
                if (!cobblemonLoaded()) return false;
                int slot = PrizmodTeam.indexOf(player, s.getUUID());
                if (slot < 0) return fail(player, "message.dirtrobes.wheel.not_in_team", s);
                if (PrizmodTeam.blockedByLeader(player)) return fail(player, "message.dirtrobes.team_leader_block", s);
                dismount(player, s);
                CompoundTag record = PrizmodTeam.peekRecord(player, slot);
                if (record == null) return fail(player, "message.dirtrobes.team_not_found", s);
                if (!com.dirtrobes.compat.cobblemon.CobblemonBridge.send(player, record)) {
                    return fail(player, "message.dirtrobes.cobblemon.send_failed", s);
                }
                PrizmodTeam.putRecord(player, slot, null);
                PrizmodTeam.sync(player, false);
                player.displayClientMessage(Component.translatable("message.dirtrobes.cobblemon.sent", s.getDisplayName())
                        .withStyle(ChatFormatting.AQUA), true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void dismount(Player player, SpectrobeEntity s) {
        if (s.getVehicle() == player) s.jumpOffShoulder(player);
        if (player.getVehicle() == s) player.stopRiding();
    }

    private static boolean fail(Player player, String key, SpectrobeEntity s) {
        player.displayClientMessage(Component.translatable(key, s.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        return false;
    }
}
