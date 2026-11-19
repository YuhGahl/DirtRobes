package com.dirtrobes.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/** Enclos invisible : une boîte délimitée par deux coins (blocs inclus). */
public record Pen(BlockPos min, BlockPos max) {
    public static final int MAX_XZ = 48;
    public static final int MAX_Y = 24;

    public static Pen of(BlockPos a, BlockPos b) {
        return new Pen(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
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

    /** Taille acceptée : au moins 3×2×3, au plus 48×24×48. */
    public boolean isValidSize() {
        return sizeX() >= 3 && sizeZ() >= 3 && sizeY() >= 2 && sizeX() <= MAX_XZ && sizeZ() <= MAX_XZ && sizeY() <= MAX_Y;
    }

    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public boolean contains(Vec3 pos) {
        return box().inflate(0.3).contains(pos);
    }

    public Vec3 center() {
        return box().getCenter();
    }

    /** Un endroit au sol, libre, dans l'enclos (null si rien trouvé). */
    @Nullable
    public Vec3 randomGroundSpot(ServerLevel level, Entity entity, RandomSource random) {
        for (int tries = 0; tries < 16; tries++) {
            int x = min.getX() + random.nextInt(sizeX());
            int z = min.getZ() + random.nextInt(sizeZ());
            for (int y = max.getY(); y >= min.getY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) return null;
                boolean solidBelow = !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
                if (!solidBelow) continue;
                Vec3 spot = new Vec3(x + 0.5, y, z + 0.5);
                if (level.noCollision(entity, entity.getDimensions(entity.getPose()).makeBoundingBox(spot))) return spot;
                break;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Min", NbtUtils.writeBlockPos(min));
        tag.put("Max", NbtUtils.writeBlockPos(max));
        return tag;
    }

    @Nullable
    public static Pen load(CompoundTag tag) {
        Optional<BlockPos> a = NbtUtils.readBlockPos(tag, "Min");
        Optional<BlockPos> b = NbtUtils.readBlockPos(tag, "Max");
        return a.isPresent() && b.isPresent() ? of(a.get(), b.get()) : null;
    }
}
