package com.dirtrobes.storage;

import com.dirtrobes.DirtRobes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le PC des Spectrobes : un rangement par joueur, commun à tout le monde (enregistré avec le monde,
 * accessible même si le joueur est hors ligne, par exemple quand un terminal est détruit).
 * 8 boîtes de 30 au départ ; s'il faut plus de place, il s'agrandit.
 */
public class GlobalPc extends SavedData implements SpectrobeStorage {
    public static final int BASE_BOXES = 8;
    private static final String NAME = "dirtrobes_pc";

    private final Map<UUID, List<CompoundTag>> slots = new HashMap<>();

    public static GlobalPc get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GlobalPc::new, GlobalPc::load, null), NAME);
    }

    private List<CompoundTag> list(UUID owner) {
        List<CompoundTag> l = slots.computeIfAbsent(owner, k -> new ArrayList<>());
        while (l.size() < BASE_BOXES * BOX_SIZE) l.add(null);
        return l;
    }

    @Nullable
    @Override
    public CompoundTag get(UUID owner, int index) {
        List<CompoundTag> l = slots.get(owner);
        return l == null || index < 0 || index >= l.size() ? null : l.get(index);
    }

    @Override
    public void set(UUID owner, int index, @Nullable CompoundTag tag) {
        List<CompoundTag> l = list(owner);
        if (index < 0) return;
        while (l.size() <= index) l.add(null);
        l.set(index, tag);
        setDirty();
    }

    @Override
    public int capacity(UUID owner) {
        int size = Math.max(BASE_BOXES * BOX_SIZE, slots.getOrDefault(owner, List.of()).size());
        // toujours une boîte entière, plus une boîte libre si tout est plein
        int boxes = (size + BOX_SIZE - 1) / BOX_SIZE;
        if (count(owner, size) >= boxes * BOX_SIZE) boxes++;
        return boxes * BOX_SIZE;
    }

    private int count(UUID owner, int size) {
        List<CompoundTag> l = slots.get(owner);
        if (l == null) return 0;
        int n = 0;
        for (CompoundTag t : l) if (t != null) n++;
        return n;
    }

    /** Range dans la première case libre. Renvoie l'index utilisé. */
    public int add(UUID owner, CompoundTag tag) {
        List<CompoundTag> l = list(owner);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == null) {
                l.set(i, tag);
                setDirty();
                return i;
            }
        }
        l.add(tag);
        setDirty();
        return l.size() - 1;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        slots.forEach((owner, list) -> {
            CompoundTag o = new CompoundTag();
            o.putUUID("Owner", owner);
            ListTag entries = new ListTag();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == null) continue;
                CompoundTag e = new CompoundTag();
                e.putInt("Slot", i);
                e.put("Data", list.get(i));
                entries.add(e);
            }
            o.put("Slots", entries);
            owners.add(o);
        });
        tag.put("Owners", owners);
        return tag;
    }

    public static GlobalPc load(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalPc pc = new GlobalPc();
        for (Tag t : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            UUID owner = o.getUUID("Owner");
            for (Tag et : o.getList("Slots", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) et;
                pc.set(owner, e.getInt("Slot"), e.getCompound("Data"));
            }
        }
        DirtRobes.LOGGER.debug("PC des Spectrobes chargé ({} joueurs)", pc.slots.size());
        return pc;
    }
}
