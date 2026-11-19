package com.dirtrobes.team;

import com.dirtrobes.DirtRobes;
import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.network.ModNetwork;
import com.dirtrobes.registry.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Équipe du Prizmod : jusqu'à {@link #MAX} Spectrobes, dont {@link #MAX_OUT} dehors à la fois.
 * Les Spectrobes rangés sont sauvegardés (NBT complet) avec le joueur, et ceux qui sont dehors
 * sont rangés automatiquement quand il se déconnecte.
 */
public final class PrizmodTeam {
    private PrizmodTeam() {}

    public static final int MAX = 6;
    public static final int MAX_OUT = 2;

    /** Un membre de l'équipe : dehors (data vide) ou rangé (data = sauvegarde complète de l'entité). */
    public static final class Entry {
        public final UUID uuid;
        @Nullable
        public CompoundTag data;

        public Entry(UUID uuid, Optional<CompoundTag> data) {
            this.uuid = uuid;
            this.data = data.orElse(null);
        }

        public boolean isOut() {
            return data == null;
        }

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(e -> e.uuid),
                CompoundTag.CODEC.optionalFieldOf("data").forGetter(e -> Optional.ofNullable(e.data))
        ).apply(i, Entry::new));
    }

    public static final class Team {
        public final List<Entry> entries;

        public Team() {
            this(new ArrayList<>());
        }

        public Team(List<Entry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        public static final Codec<Team> CODEC = Entry.CODEC.listOf().xmap(Team::new, t -> t.entries);

        public int outCount() {
            return (int) entries.stream().filter(Entry::isOut).count();
        }

        @Nullable
        public Entry find(UUID uuid) {
            for (Entry e : entries) if (e.uuid.equals(uuid)) return e;
            return null;
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DirtRobes.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Team>> TEAM = ATTACHMENTS.register("prizmod_team",
            () -> AttachmentType.builder(() -> new Team()).serialize(Team.CODEC).copyOnDeath().build());

    public static Team team(ServerPlayer player) {
        return player.getData(TEAM);
    }

    private static void changed(ServerPlayer player) {
        player.setData(TEAM, team(player));
        sync(player, false);
    }

    @Nullable
    private static SpectrobeEntity findLoaded(ServerPlayer player, UUID uuid) {
        Entity e = player.serverLevel().getEntity(uuid);
        if (e == null) {
            for (ServerLevel level : player.server.getAllLevels()) {
                e = level.getEntity(uuid);
                if (e != null) break;
            }
        }
        return e instanceof SpectrobeEntity s && s.isAlive() ? s : null;
    }

    /** Ajoute un Spectrobe qui vient d'apparaître (éclosion, commande). Rangé d'office s'il y en a déjà 2 dehors. */
    public static void onObtained(ServerPlayer player, SpectrobeEntity spectrobe) {
        Team team = team(player);
        if (team.find(spectrobe.getUUID()) != null) return;
        if (team.entries.size() >= MAX) {
            player.displayClientMessage(Component.translatable("message.dirtrobes.team_full").withStyle(ChatFormatting.GOLD), false);
            return;
        }
        Entry entry = new Entry(spectrobe.getUUID(), Optional.empty());
        team.entries.add(entry);
        if (team.outCount() > MAX_OUT) {
            store(player, entry, spectrobe);
            player.displayClientMessage(Component.translatable("message.dirtrobes.team_stored_new", spectrobe.getDisplayName()), false);
        }
        changed(player);
    }

    /** Ajoute à l'équipe un Spectrobe déjà dans le monde (ancienne sauvegarde, équipe pleine à l'époque). */
    public static boolean adopt(ServerPlayer player, SpectrobeEntity spectrobe) {
        Team team = team(player);
        if (team.find(spectrobe.getUUID()) != null || team.entries.size() >= MAX || team.outCount() >= MAX_OUT) return false;
        team.entries.add(new Entry(spectrobe.getUUID(), Optional.empty()));
        changed(player);
        return true;
    }

    private static void store(ServerPlayer player, Entry entry, SpectrobeEntity spectrobe) {
        CompoundTag tag = new CompoundTag();
        spectrobe.saveWithoutId(tag);
        entry.data = tag;
        if (spectrobe.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, spectrobe.getX(), spectrobe.getY() + spectrobe.getBbHeight() / 2, spectrobe.getZ(),
                    20, 0.3, 0.4, 0.3, 0.05);
        }
        spectrobe.discard();
    }

    /** Range (si dehors) ou fait sortir (si rangé) le membre n°index. */
    public static boolean toggle(ServerPlayer player, int index) {
        Team team = team(player);
        if (index < 0 || index >= team.entries.size()) return false;
        Entry entry = team.entries.get(index);
        boolean ok;
        if (entry.isOut()) {
            if (blockedByLeader(player)) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.team_leader_block").withStyle(ChatFormatting.RED), true);
                return false;
            }
            SpectrobeEntity s = findLoaded(player, entry.uuid);
            if (s == null) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.team_not_found"), true);
                return false;
            }
            store(player, entry, s);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
            ok = true;
        } else {
            if (team.outCount() >= MAX_OUT) {
                player.displayClientMessage(Component.translatable("message.dirtrobes.team_max_out", MAX_OUT), true);
                return false;
            }
            ok = sendOut(player, entry);
        }
        changed(player);
        return ok;
    }

    private static boolean sendOut(ServerPlayer player, Entry entry) {
        SpectrobeEntity s = ModRegistry.SPECTROBE.get().create(player.serverLevel());
        if (s == null || entry.data == null) return false;
        s.load(entry.data);
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        s.moveTo(player.getX() + Math.cos(angle) * 1.5, player.getY(), player.getZ() + Math.sin(angle) * 1.5, player.getYRot(), 0f);
        s.setOrderedToSit(false);
        s.setInSittingPose(false);
        s.setDeltaMovement(0, 0, 0);
        s.fallDistance = 0;
        if (!player.serverLevel().addFreshEntity(s)) return false;
        entry.data = null;
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, s.getX(), s.getY() + s.getBbHeight() / 2, s.getZ(), 30, 0.3, 0.5, 0.3, 0.08);
        player.level().playSound(null, s.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
        player.serverLevel().broadcastEntityEvent(s, SpectrobeEntity.EVENT_CRY);
        return true;
    }

    // ----- Échanges avec le PC -----

    /** Sauvegarde du membre n°index (le range d'abord s'il est dehors). Null si impossible (trop loin, index invalide). */
    @Nullable
    public static CompoundTag peekRecord(ServerPlayer player, int index) {
        Team team = team(player);
        if (index < 0 || index >= team.entries.size()) return null;
        Entry entry = team.entries.get(index);
        if (entry.isOut()) {
            SpectrobeEntity s = findLoaded(player, entry.uuid);
            if (s == null) return null;
            store(player, entry, s);
        }
        return entry.data;
    }

    /** Remplace le membre n°index par cette sauvegarde (rangée), ou l'ajoute à la fin ; null = retirer. */
    public static void putRecord(ServerPlayer player, int index, @Nullable CompoundTag tag) {
        Team team = team(player);
        if (tag == null) {
            if (index >= 0 && index < team.entries.size()) team.entries.remove(index);
        } else {
            Entry entry = new Entry(tag.getUUID("UUID"), Optional.of(tag));
            if (index >= 0 && index < team.entries.size()) team.entries.set(index, entry);
            else team.entries.add(entry);
        }
        changed(player);
    }

    /** Applique les bonus de soutien aux Spectrobes dehors (appelé chaque seconde). */
    public static com.dirtrobes.combat.Support applySupport(ServerPlayer player) {
        com.dirtrobes.combat.Support support = com.dirtrobes.combat.Support.compute(player);
        player.setData(com.dirtrobes.combat.ChGauge.GAIN_BONUS, support.ch());
        for (Entry entry : team(player).entries) {
            if (!entry.isOut()) continue;
            SpectrobeEntity s = findLoaded(player, entry.uuid);
            if (s != null) support.applyTo(s);
        }
        return support;
    }

    /** Soigne les Spectrobes rangés (appelé régulièrement pour chaque joueur connecté). */
    public static void restStored(ServerPlayer player, int ticks) {
        Team team = team(player);
        boolean any = false;
        for (Entry entry : team.entries) {
            if (entry.isOut() || entry.data == null) continue;
            SpectrobeEntity s = ModRegistry.SPECTROBE.get().create(player.serverLevel());
            if (s == null) continue;
            s.load(entry.data);
            if (!s.isKnockedOut() && s.getHealth() >= s.getMaxHealth()) continue;
            s.restInPrizmod(ticks);
            CompoundTag tag = new CompoundTag();
            s.saveWithoutId(tag);
            entry.data = tag;
            any = true;
        }
        if (any) changed(player);
    }

    /** Un Krawl chef qui combat le joueur ou ses Spectrobes empêche de les ranger (on ne fuit pas un chef). */
    public static boolean blockedByLeader(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(com.dirtrobes.krawl.KrawlEntity.class, player.getBoundingBox().inflate(24),
                k -> k.isAlive() && k.isLeader() && k.getTarget() != null
                        && (k.getTarget() == player || k.getTarget() instanceof SpectrobeEntity s && s.isOwnedBy(player))).isEmpty();
    }

    /** À la déconnexion : range tous les Spectrobes dehors pour ne pas les perdre. */
    public static void storeAllOut(ServerPlayer player) {
        Team team = team(player);
        for (Entry entry : team.entries) {
            if (!entry.isOut()) continue;
            SpectrobeEntity s = findLoaded(player, entry.uuid);
            if (s != null) store(player, entry, s);
        }
        changed(player);
    }

    /** Un membre a vraiment disparu (mort hors K.O., /kill…) : on le retire de l'équipe. */
    public static void onSpectrobeDied(SpectrobeEntity spectrobe) {
        if (!(spectrobe.getOwner() instanceof ServerPlayer owner)) return;
        Team team = team(owner);
        if (team.entries.removeIf(e -> e.isOut() && e.uuid.equals(spectrobe.getUUID()))) {
            changed(owner);
            sync(owner, false);
        }
    }

    // ----- Résumé pour l'écran -----

    public static List<TeamSummary> summaries(ServerPlayer player) {
        List<TeamSummary> list = new ArrayList<>();
        for (Entry entry : team(player).entries) {
            SpectrobeEntity s;
            boolean found = true;
            if (entry.isOut()) {
                s = findLoaded(player, entry.uuid);
                found = s != null;
            } else {
                s = ModRegistry.SPECTROBE.get().create(player.serverLevel());
                if (s != null && entry.data != null) s.load(entry.data);
            }
            list.add(s == null ? TeamSummary.missing() : TeamSummary.of(s, entry.isOut(), found));
        }
        return list;
    }

    public static void sync(ServerPlayer player, boolean openScreen) {
        if (player.connection != null && player.connection.hasChannel(ModNetwork.TeamSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ModNetwork.TeamSyncPayload(summaries(player), openScreen,
                    com.dirtrobes.combat.Support.compute(player)));
        }
    }
}
