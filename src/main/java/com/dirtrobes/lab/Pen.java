package com.dirtrobes.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Enclos invisible d'un terminal : une boîte (blocs min et max inclus) et un point de spawn à l'intérieur.
 * Les Spectrobes apparaissent au point de spawn, puis ne vont que là où ils peuvent marcher depuis ce point.
 */
public record Pen(BlockPos min, BlockPos max, BlockPos spawn) {
    public static final int MIN_XZ = 3;
    public static final int MIN_Y = 2;
    public static final int MAX_XZ = 48;
    public static final int MAX_Y = 24;
    /** Distance maximale entre le terminal et l'enclos (sur chaque axe). */
    public static final int MAX_OFFSET = 32;

    public static Pen of(BlockPos a, BlockPos b, BlockPos spawn) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new Pen(min, max, clampInside(spawn, min, max));
    }

    /** Enclos par défaut : 9 × 4 × 9 posé à côté du terminal, spawn au centre du sol. */
    public static Pen defaultFor(BlockPos terminal) {
        BlockPos min = terminal.offset(1, 0, -4);
        BlockPos max = min.offset(8, 3, 8);
        return new Pen(min, max, new BlockPos(min.getX() + 4, min.getY(), min.getZ() + 4));
    }

    /** Enclos décrit par rapport au terminal (ce que règle l'interface). */
    public static Pen fromRelative(BlockPos terminal, int ox, int oy, int oz, int sx, int sy, int sz, int px, int py, int pz) {
        sx = clamp(sx, MIN_XZ, MAX_XZ);
        sy = clamp(sy, MIN_Y, MAX_Y);
        sz = clamp(sz, MIN_XZ, MAX_XZ);
        ox = clamp(ox, -MAX_OFFSET - sx, MAX_OFFSET);
        oy = clamp(oy, -MAX_OFFSET, MAX_OFFSET);
        oz = clamp(oz, -MAX_OFFSET - sz, MAX_OFFSET);
        BlockPos min = terminal.offset(ox, oy, oz);
        BlockPos max = min.offset(sx - 1, sy - 1, sz - 1);
        return new Pen(min, max, clampInside(min.offset(px, py, pz), min, max));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BlockPos clampInside(BlockPos p, BlockPos min, BlockPos max) {
        return new BlockPos(clamp(p.getX(), min.getX(), max.getX()), clamp(p.getY(), min.getY(), max.getY()), clamp(p.getZ(), min.getZ(), max.getZ()));
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public boolean contains(Vec3 pos) {
        return box().inflate(0.3).contains(pos);
    }

    public boolean containsBlock(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * Où faire apparaître un Spectrobe : au point de spawn (ou juste au-dessus s'il est bouché),
     * avec un petit décalage au hasard pour qu'ils ne s'empilent pas. Null si la zone n'est pas chargée.
     */
    @Nullable
    public Vec3 spawnSpot(ServerLevel level, Entity entity, RandomSource random) {
        if (!level.isLoaded(spawn)) return null;
        for (int tries = 0; tries < 12; tries++) {
            int dx = tries == 0 ? 0 : random.nextInt(3) - 1;
            int dz = tries == 0 ? 0 : random.nextInt(3) - 1;
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos p = spawn.offset(dx, dy, dz);
                if (!containsBlock(p)) continue;
                Vec3 spot = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (level.noCollision(entity, entity.getDimensions(entity.getPose()).makeBoundingBox(spot))) return spot;
            }
        }
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Min", NbtUtils.writeBlockPos(min));
        tag.put("Max", NbtUtils.writeBlockPos(max));
        tag.put("Spawn", NbtUtils.writeBlockPos(spawn));
        return tag;
    }

    @Nullable
    public static Pen load(CompoundTag tag) {
        Optional<BlockPos> a = NbtUtils.readBlockPos(tag, "Min");
        Optional<BlockPos> b = NbtUtils.readBlockPos(tag, "Max");
        if (a.isEmpty() || b.isEmpty()) return null;
        Optional<BlockPos> s = NbtUtils.readBlockPos(tag, "Spawn");
        BlockPos spawn = s.orElse(new BlockPos((a.get().getX() + b.get().getX()) / 2, Math.min(a.get().getY(), b.get().getY()),
                (a.get().getZ() + b.get().getZ()) / 2));
        return of(a.get(), b.get(), spawn);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(min);
        buf.writeBlockPos(max);
        buf.writeBlockPos(spawn);
    }

    public static Pen read(FriendlyByteBuf buf) {
        return new Pen(buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos());
    }
}
