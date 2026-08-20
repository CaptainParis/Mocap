package com.paris.mocap.scene;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

public final class CaptureBounds {
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public CaptureBounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static CaptureBounds fromCenterRadius(Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        return new CaptureBounds(
            world.getName(),
            cx - radius,
            Math.max(minY, cy - radius),
            cz - radius,
            cx + radius,
            Math.min(maxY, cy + radius),
            cz + radius
        );
    }

    public static CaptureBounds fromChunks(World world, int centerChunkX, int centerChunkZ, int chunkRadius) {
        int minChunkX = centerChunkX - chunkRadius;
        int maxChunkX = centerChunkX + chunkRadius;
        int minChunkZ = centerChunkZ - chunkRadius;
        int maxChunkZ = centerChunkZ + chunkRadius;
        return new CaptureBounds(
            world.getName(),
            minChunkX << 4,
            world.getMinHeight(),
            minChunkZ << 4,
            (maxChunkX << 4) + 15,
            world.getMaxHeight() - 1,
            (maxChunkZ << 4) + 15
        );
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !this.world.equals(location.getWorld().getName())) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        if (!this.world.equals(block.getWorld().getName())) {
            return false;
        }
        return contains(block.getX(), block.getY(), block.getZ());
    }

    public boolean contains(Entity entity) {
        return contains(entity.getLocation());
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX
            && y >= this.minY && y <= this.maxY
            && z >= this.minZ && z <= this.maxZ;
    }

    public int sizeX() {
        return this.maxX - this.minX + 1;
    }

    public int sizeY() {
        return this.maxY - this.minY + 1;
    }

    public int sizeZ() {
        return this.maxZ - this.minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public int relX(int absX) {
        return absX - this.minX;
    }

    public int relY(int absY) {
        return absY - this.minY;
    }

    public int relZ(int absZ) {
        return absZ - this.minZ;
    }

    public int absX(int relX) {
        return this.minX + relX;
    }

    public int absY(int relY) {
        return this.minY + relY;
    }

    public int absZ(int relZ) {
        return this.minZ + relZ;
    }

    public String world() {
        return this.world;
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }
}
