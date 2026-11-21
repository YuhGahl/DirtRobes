package com.dirtrobes.storage;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/** Un rangement de Spectrobes par joueur, en cases numérotées (boîtes de 30). */
public interface SpectrobeStorage {
    int BOX_SIZE = 30;

    @Nullable
    CompoundTag get(UUID owner, int index);

    void set(UUID owner, int index, @Nullable CompoundTag tag);

    /** Nombre de cases disponibles pour ce joueur (multiple de 30). */
    int capacity(UUID owner);

    default int boxes(UUID owner) {
        return capacity(owner) / BOX_SIZE;
    }

    default int count(UUID owner) {
        int n = 0;
        for (int i = 0; i < capacity(owner); i++) if (get(owner, i) != null) n++;
        return n;
    }
}
