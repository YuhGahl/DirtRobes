package com.dirtrobes.lab;

import com.dirtrobes.entity.SpectrobeEntity;
import com.dirtrobes.registry.ModRegistry;
import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Terminal du laboratoire : rangement relié à un enclos. Chaque joueur y range jusqu'à {@link #BOXES} × {@link #BOX_SIZE}
 * Spectrobes. Si un enclos est délimité, les Spectrobes rangés y apparaissent et s'y promènent : ce sont des
 * copies vivantes (non sauvegardées avec le monde) de ce que le PC contient, recréées au chargement.
 */
public class LabTerminalBlockEntity extends BlockEntity implements com.dirtrobes.storage.SpectrobeStorage {
    public static final int BOXES = 1;
    public static final int BOX_SIZE = 30;
    public static final int CAPACITY = BOXES * BOX_SIZE;
    /** Nombre maximum de Spectrobes visibles dans l'enclos (pour ménager les performances). */
    public static final int MAX_SHOWN = 60;

    /** Zones de l'interface. */
    public static final int AREA_TEAM = com.dirtrobes.storage.StorageOps.AREA_TEAM;
    public static final int AREA_BOX = com.dirtrobes.storage.StorageOps.AREA_BOX;

    private final Map<UUID, CompoundTag[]> storage = new HashMap<>();
    @Nullable
    private Pen pen;
    private int refreshTimer;
    /** Spectrobes en trop (anciens terminaux à 4 boîtes) : envoyés au PC au premier tick. */
    private final Map<UUID, List<CompoundTag>> overflow = new HashMap<>();

    public LabTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LAB_TERMINAL_BE.get(), pos, state);
    }

    @Nullable
    public Pen pen() {
        return pen;
    }

    public void setPen(@Nullable Pen pen) {
        this.pen = pen;
        removeAllShown();
        refreshTimer = 0;
        setChanged();
    }

    private CompoundTag[] slots(UUID owner) {
        return storage.computeIfAbsent(owner, k -> new CompoundTag[CAPACITY]);
    }

    @Nullable
    @Override
    public CompoundTag get(UUID owner, int index) {
        if (index < 0 || index >= CAPACITY) return null;
        CompoundTag[] s = storage.get(owner);
        return s == null ? null : s[index];
    }

    @Override
    public int count(UUID owner) {
        CompoundTag[] s = storage.get(owner);
        if (s == null) return 0;
        int n = 0;
        for (CompoundTag t : s) if (t != null) n++;
        return n;
    }

    /** Ce Spectrobe (par UUID) est-il rangé dans ce PC ? */
    public boolean holds(UUID spectrobe) {
        for (CompoundTag[] s : storage.values()) {
            for (CompoundTag t : s) if (t != null && t.hasUUID("UUID") && t.getUUID("UUID").equals(spectrobe)) return true;
        }
        return false;
    }

    @Override
    public void set(UUID owner, int index, @Nullable CompoundTag tag) {
        if (index < 0 || index >= CAPACITY) return;
        CompoundTag[] s = slots(owner);
        CompoundTag old = s[index];
        s[index] = tag;
        if (old != null && old.hasUUID("UUID")) removeShown(old.getUUID("UUID"));
        setChanged();
        refreshTimer = 0;
    }

    /** Déplace (ou échange) un Spectrobe entre l'équipe et les boîtes de ce terminal. */
    public boolean move(ServerPlayer player, int fromArea, int fromIndex, int toArea, int toIndex) {
        return com.dirtrobes.storage.StorageOps.move(player, this, fromArea, fromIndex, toArea, toIndex);
    }

    @Override
    public int capacity(UUID owner) {
        return CAPACITY;
    }

    /** Terminal détruit : tous les Spectrobes rangés ici partent dans le PC de leur dresseur. */
    public void transferAllToPc(ServerLevel server) {
        com.dirtrobes.storage.GlobalPc pc = com.dirtrobes.storage.GlobalPc.get(server.getServer());
        removeAllShown();
        for (Map.Entry<UUID, CompoundTag[]> e : storage.entrySet()) {
            int moved = 0;
            for (int i = 0; i < e.getValue().length; i++) {
                if (e.getValue()[i] == null) continue;
                pc.add(e.getKey(), e.getValue()[i]);
                e.getValue()[i] = null;
                moved++;
            }
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(e.getKey());
            if (owner != null && moved > 0) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dirtrobes.terminal_to_pc", moved)
                        .withStyle(net.minecraft.ChatFormatting.AQUA), false);
            }
        }
        storage.clear();
        setChanged();
    }

    // ------------------------------------------------------------------ Spectrobes visibles dans l'enclos

    public static void serverTick(Level level, BlockPos pos, BlockState state, LabTerminalBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!be.overflow.isEmpty()) {
            com.dirtrobes.storage.GlobalPc pc = com.dirtrobes.storage.GlobalPc.get(server.getServer());
            be.overflow.forEach((owner, list) -> list.forEach(t -> pc.add(owner, t)));
            be.overflow.clear();
            be.setChanged();
        }
        if (--be.refreshTimer > 0) return;
        be.refreshTimer = 40;
        be.refreshShown(server);
    }

    private void refreshShown(ServerLevel server) {
        if (pen == null) return;
        int shown = 0;
        for (Map.Entry<UUID, CompoundTag[]> e : storage.entrySet()) {
            for (CompoundTag tag : e.getValue()) {
                if (tag == null || !tag.hasUUID("UUID")) continue;
                if (shown++ >= MAX_SHOWN) return;
                if (server.getEntity(tag.getUUID("UUID")) != null) continue;
                spawnShown(server, tag);
            }
        }
    }

    private void spawnShown(ServerLevel server, CompoundTag tag) {
        SpectrobeEntity s = ModRegistry.SPECTROBE.get().create(server);
        if (s == null || pen == null) return;
        s.load(tag);
        Vec3 spot = pen.spawnSpot(server, s, server.random);
        if (spot == null) return;
        s.moveTo(spot.x, spot.y, spot.z, server.random.nextFloat() * 360f, 0f);
        s.setPenResident(worldPosition, pen);
        server.addFreshEntity(s);
    }

    private void removeShown(UUID uuid) {
        if (level instanceof ServerLevel server) {
            Entity e = server.getEntity(uuid);
            if (e instanceof SpectrobeEntity s && s.isPenResident()) s.discard();
        }
    }

    private void removeAllShown() {
        for (CompoundTag[] s : storage.values()) {
            for (CompoundTag t : s) if (t != null && t.hasUUID("UUID")) removeShown(t.getUUID("UUID"));
        }
    }

    @Override
    public void setRemoved() {
        removeAllShown();
        super.setRemoved();
    }

    /** Tout ce qui est rangé ici (pour rendre les Spectrobes quand on casse le terminal). */
    public List<CompoundTag> allRecords(UUID owner) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag[] s = storage.get(owner);
        if (s != null) for (CompoundTag t : s) if (t != null) out.add(t);
        return out;
    }

    public boolean isEmpty() {
        for (CompoundTag[] s : storage.values()) for (CompoundTag t : s) if (t != null) return false;
        return true;
    }

    // ------------------------------------------------------------------ sauvegarde

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag owners = new ListTag();
        storage.forEach((owner, slots) -> {
            CompoundTag o = new CompoundTag();
            o.putUUID("Owner", owner);
            ListTag list = new ListTag();
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", i);
                entry.put("Data", slots[i]);
                list.add(entry);
            }
            o.put("Slots", list);
            owners.add(o);
        });
        tag.put("Storage", owners);
        if (pen != null) tag.put("Pen", pen.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.clear();
        for (Tag t : tag.getList("Storage", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            CompoundTag[] slots = new CompoundTag[CAPACITY];
            for (Tag st : o.getList("Slots", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) st;
                int i = entry.getInt("Slot");
                if (i >= 0 && i < CAPACITY) slots[i] = entry.getCompound("Data");
                else overflow.computeIfAbsent(o.getUUID("Owner"), k -> new ArrayList<>()).add(entry.getCompound("Data"));
            }
            storage.put(o.getUUID("Owner"), slots);
        }
        pen = tag.contains("Pen") ? Pen.load(tag.getCompound("Pen")) : null;
    }
}
