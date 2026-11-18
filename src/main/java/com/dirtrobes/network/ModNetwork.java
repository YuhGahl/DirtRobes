package com.dirtrobes.network;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.block.IncubatorBlockEntity;
import com.dirtrobes.combat.ChGauge;
import com.dirtrobes.team.PrizmodTeam;
import com.dirtrobes.team.TeamSummary;
import net.minecraft.network.FriendlyByteBuf;
import com.dirtrobes.entity.SpectrobeEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public final class ModNetwork {
    private ModNetwork() {}

    /** Touche « attaque spéciale » : envoyée par le client, traitée par le serveur. */
    public record SpecialAttackPayload() implements CustomPacketPayload {
        public static final Type<SpecialAttackPayload> TYPE = new Type<>(DirtRobes.id("special_attack"));
        public static final StreamCodec<ByteBuf, SpecialAttackPayload> STREAM_CODEC = StreamCodec.unit(new SpecialAttackPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Fin du mini-jeu de fouille : intégrité du fossile dégagé (0 = brisé). */
    public record ExcavationResultPayload(BlockPos pos, int integrity) implements CustomPacketPayload {
        public static final Type<ExcavationResultPayload> TYPE = new Type<>(DirtRobes.id("excavation_result"));
        public static final StreamCodec<ByteBuf, ExcavationResultPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ExcavationResultPayload::pos,
                ByteBufCodecs.VAR_INT, ExcavationResultPayload::integrity,
                ExcavationResultPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Valeur de la jauge CH envoyée au client pour l'affichage. */
    public record ChSyncPayload(int ch) implements CustomPacketPayload {
        public static final Type<ChSyncPayload> TYPE = new Type<>(DirtRobes.id("ch_sync"));
        public static final StreamCodec<ByteBuf, ChSyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChSyncPayload::ch, ChSyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Équipe du Prizmod envoyée au client (openScreen = ouvrir l'écran). */
    public record TeamSyncPayload(List<TeamSummary> members, boolean openScreen) implements CustomPacketPayload {
        public static final Type<TeamSyncPayload> TYPE = new Type<>(DirtRobes.id("team_sync"));
        public static final StreamCodec<FriendlyByteBuf, TeamSyncPayload> STREAM_CODEC = StreamCodec.composite(
                TeamSummary.STREAM_CODEC.apply(ByteBufCodecs.list(PrizmodTeam.MAX)), TeamSyncPayload::members,
                ByteBufCodecs.BOOL, TeamSyncPayload::openScreen,
                TeamSyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Le joueur clique sur « Sortir » / « Ranger » pour le membre n°slot. */
    public record TeamActionPayload(int slot) implements CustomPacketPayload {
        public static final Type<TeamActionPayload> TYPE = new Type<>(DirtRobes.id("team_action"));
        public static final StreamCodec<ByteBuf, TeamActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TeamActionPayload::slot, TeamActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Dernière valeur reçue du serveur (côté client). */
    public static volatile int clientCh;

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4")
                .playToClient(ChSyncPayload.TYPE, ChSyncPayload.STREAM_CODEC, (payload, context) -> clientCh = payload.ch())
                .playToClient(TeamSyncPayload.TYPE, TeamSyncPayload.STREAM_CODEC, ModNetwork::handleTeamSync)
                .playToServer(TeamActionPayload.TYPE, TeamActionPayload.STREAM_CODEC, ModNetwork::handleTeamAction)
                .playToServer(SpecialAttackPayload.TYPE, SpecialAttackPayload.STREAM_CODEC, ModNetwork::handleSpecialAttack)
                .playToServer(ExcavationResultPayload.TYPE, ExcavationResultPayload.STREAM_CODEC, ModNetwork::handleExcavation);
    }

    private static void handleTeamSync(TeamSyncPayload payload, IPayloadContext context) {
        // Classe client chargée seulement quand ce code s'exécute (donc jamais sur un serveur dédié).
        context.enqueueWork(() -> com.dirtrobes.client.ClientTeam.onSync(payload.members(), payload.openScreen()));
    }

    private static void handleTeamAction(TeamActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PrizmodTeam.toggle(player, payload.slot());
                PrizmodTeam.sync(player, false);
            }
        });
    }

    private static void handleExcavation(ExcavationResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) return;
            if (player.level().getBlockEntity(pos) instanceof IncubatorBlockEntity incubator) {
                incubator.finishExcavation(player, Math.max(0, Math.min(100, payload.integrity())));
            }
        });
    }

    private static void handleSpecialAttack(SpecialAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) triggerSpecialAttack(player, lookedAtEntity(player, 24.0));
        });
    }

    /**
     * Attaque spéciale de tous les Spectrobes du joueur à portée. Coûte {@link ChGauge#SPECIAL_COST} points
     * de jauge CH (une fois par appui). Renvoie le nombre de Spectrobes qui ont attaqué.
     */
    public static int triggerSpecialAttack(ServerPlayer player, LivingEntity looked) {
        if (ChGauge.get(player) < ChGauge.SPECIAL_COST) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.ch_low", ChGauge.get(player), ChGauge.SPECIAL_COST), true);
            return 0;
        }
        List<SpectrobeEntity> mine = player.level().getEntitiesOfClass(SpectrobeEntity.class, player.getBoundingBox().inflate(24),
                s -> s.isOwnedBy(player));
        int count = 0;
        for (SpectrobeEntity s : mine) {
            if (s.startSpecialAttack(looked)) count++;
        }
        if (count == 0) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.special_none"), true);
        } else {
            ChGauge.trySpend(player, ChGauge.SPECIAL_COST);
        }
        return count;
    }

    private static LivingEntity lookedAtEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f).scale(range);
        AABB box = player.getBoundingBox().expandTowards(look).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look), box,
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof SpectrobeEntity s && s.isOwnedBy(player)), range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
