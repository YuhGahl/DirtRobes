package com.spectrocraft.network;

import com.spectrocraft.SpectroCraft;
import com.spectrocraft.entity.SpectrobeEntity;
import io.netty.buffer.ByteBuf;
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
        public static final Type<SpecialAttackPayload> TYPE = new Type<>(SpectroCraft.id("special_attack"));
        public static final StreamCodec<ByteBuf, SpecialAttackPayload> STREAM_CODEC = StreamCodec.unit(new SpecialAttackPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SpecialAttackPayload.TYPE, SpecialAttackPayload.STREAM_CODEC, ModNetwork::handleSpecialAttack);
    }

    private static void handleSpecialAttack(SpecialAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            LivingEntity looked = lookedAtEntity(player, 24.0);
            List<SpectrobeEntity> mine = player.level().getEntitiesOfClass(SpectrobeEntity.class, player.getBoundingBox().inflate(24),
                    s -> s.isOwnedBy(player));
            int count = 0;
            for (SpectrobeEntity s : mine) {
                if (s.startSpecialAttack(looked)) count++;
            }
            if (count == 0) {
                player.displayClientMessage(Component.translatable("message.spectrocraft.special_none"), true);
            }
        });
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
