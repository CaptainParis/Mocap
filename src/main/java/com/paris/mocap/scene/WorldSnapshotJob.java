package com.paris.mocap.scene;

import java.util.List;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public final class WorldSnapshotJob {
    private final CaptureBounds bounds;
    private final World world;
    private final List<SnapshotPasteJob.ChunkCoord> chunks;
    private final WorldSnapshot.Builder builder = WorldSnapshot.builder();
    private int cursor;

    public WorldSnapshotJob(CaptureBounds bounds, World world) {
        this.bounds = bounds;
        this.world = world;
        this.chunks = SnapshotPasteJob.chunksIn(bounds);
    }

    public boolean tick(int chunkBudget) {
        int remaining = chunkBudget;
        while (remaining-- > 0 && this.cursor < this.chunks.size()) {
            SnapshotPasteJob.ChunkCoord coord = this.chunks.get(this.cursor++);
            if (!this.world.isChunkLoaded(coord.x(), coord.z())) {
                continue;
            }
            ChunkSnapshot snapshot = this.world.getChunkAt(coord.x(), coord.z())
                .getChunkSnapshot(false, false, false);
            scanChunk(snapshot, coord.x(), coord.z());
        }
        return this.cursor >= this.chunks.size();
    }

    public WorldSnapshot complete() {
        return this.builder.build();
    }

    public int chunksDone() {
        return this.cursor;
    }

    public int chunksTotal() {
        return this.chunks.size();
    }

    private void scanChunk(ChunkSnapshot snapshot, int chunkX, int chunkZ) {
        int minX = Math.max(this.bounds.minX(), chunkX << 4);
        int maxX = Math.min(this.bounds.maxX(), (chunkX << 4) + 15);
        int minZ = Math.max(this.bounds.minZ(), chunkZ << 4);
        int maxZ = Math.min(this.bounds.maxZ(), (chunkZ << 4) + 15);
        int minY = Math.max(this.bounds.minY(), this.world.getMinHeight());
        int maxY = Math.min(this.bounds.maxY(), this.world.getMaxHeight() - 1);

        for (int x = minX; x <= maxX; x++) {
            int lx = x & 15;
            for (int z = minZ; z <= maxZ; z++) {
                int lz = z & 15;
                for (int y = minY; y <= maxY; y++) {
                    Material material = snapshot.getBlockType(lx, y, lz);
                    if (material.isAir()) {
                        continue;
                    }
                    BlockData data = snapshot.getBlockData(lx, y, lz);
                    this.builder.add(
                        this.bounds.relX(x),
                        this.bounds.relY(y),
                        this.bounds.relZ(z),
                        data.getAsString()
                    );
                }
            }
        }
    }
}
