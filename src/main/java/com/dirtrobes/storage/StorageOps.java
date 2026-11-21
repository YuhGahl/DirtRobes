package com.dirtrobes.storage;

import com.dirtrobes.team.PrizmodTeam;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** Déplacements entre l'équipe du joueur et un rangement (PC ou terminal du laboratoire). */
public final class StorageOps {
    private StorageOps() {}

    public static final int AREA_TEAM = 0;
    public static final int AREA_BOX = 1;

    /** Déplace (ou échange) un Spectrobe. @return true si quelque chose a bougé */
    public static boolean move(ServerPlayer player, SpectrobeStorage storage, int fromArea, int fromIndex, int toArea, int toIndex) {
        if (fromArea == toArea && fromIndex == toIndex) return false;
        UUID owner = player.getUUID();
        int teamSize = PrizmodTeam.team(player).entries.size();
        if (toArea == AREA_BOX && (toIndex < 0 || toIndex >= storage.capacity(owner))) return false;
        CompoundTag a = read(player, storage, fromArea, fromIndex);
        if (a == null) return false;
        CompoundTag b = toArea == AREA_TEAM && toIndex >= teamSize ? null : read(player, storage, toArea, toIndex);
        if (toArea == AREA_TEAM && b == null && toIndex >= teamSize && teamSize >= PrizmodTeam.MAX) return false;

        if (fromArea == AREA_TEAM && toArea == AREA_TEAM) {
            if (b == null) return false;
            PrizmodTeam.putRecord(player, fromIndex, b);
            PrizmodTeam.putRecord(player, toIndex, a);
        } else if (fromArea == AREA_BOX && toArea == AREA_BOX) {
            storage.set(owner, fromIndex, b);
            storage.set(owner, toIndex, a);
        } else if (fromArea == AREA_TEAM) {
            storage.set(owner, toIndex, a);
            PrizmodTeam.putRecord(player, fromIndex, b);
        } else {
            storage.set(owner, fromIndex, b);
            PrizmodTeam.putRecord(player, toIndex < teamSize ? toIndex : teamSize, a);
        }
        return true;
    }

    @Nullable
    private static CompoundTag read(ServerPlayer player, SpectrobeStorage storage, int area, int index) {
        return area == AREA_TEAM ? PrizmodTeam.peekRecord(player, index) : storage.get(player.getUUID(), index);
    }
}
