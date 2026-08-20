package com.paris.mocap.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

public final class SnapshotPasteJob {
    private final JavaPlugin plugin;
    private final StageSlot slot;
    private final WorldSnapshot snapshot;
    private final BlockData[] resolved;
    private final BlockData air;
    private int cursor;
    private boolean clearing;

    public SnapshotPasteJob(JavaPlugin plugin, StageSlot slot, WorldSnapshot snapshot) {
        this.plugin = plugin;
        this.slot = slot;
        this.snapshot = snapshot;
        this.air = Material.AIR.createBlockData();
        this.resolved = resolvePalette(plugin, snapshot);
    }

    public static SnapshotPasteJob clear(JavaPlugin plugin, StageSlot slot, WorldSnapshot snapshot) {
        SnapshotPasteJob job = new SnapshotPasteJob(plugin, slot, snapshot);
        job.clearing = true;
        return job;
    }

    public boolean tick(int budget) {
        World world = this.slot.world();
        int n = this.snapshot.size();
        int remaining = budget;
        while (remaining-- > 0 && this.cursor < n) {
            int i = this.cursor++;
            try {
                Block block = world.getBlockAt(
                    this.slot.absX(this.snapshot.relX(i)),
                    this.slot.absY(this.snapshot.relY(i)),
                    this.slot.absZ(this.snapshot.relZ(i))
                );
                block.setBlockData(this.clearing ? this.air : this.resolved[this.snapshot.paletteIndex(i)], false);
            } catch (Throwable fault) {
                this.plugin.getLogger().log(Level.FINE, "Paste cell skipped", fault);
            }
        }
        return this.cursor >= n;
    }

    public int progress() {
        return this.cursor;
    }

    public int total() {
        return this.snapshot.size();
    }

    public StageSlot slot() {
        return this.slot;
    }

    private static BlockData[] resolvePalette(JavaPlugin plugin, WorldSnapshot snapshot) {
        List<String> palette = snapshot.palette();
        BlockData[] resolved = new BlockData[palette.size()];
        BlockData air = Material.AIR.createBlockData();
        for (int i = 0; i < palette.size(); i++) {
            try {
                resolved[i] = Bukkit.createBlockData(palette.get(i));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.FINE, "Unknown block data: " + palette.get(i), ex);
                resolved[i] = air;
            }
        }
        return resolved;
    }

    public static List<ChunkCoord> chunksIn(CaptureBounds bounds) {
        int minCx = bounds.minX() >> 4;
        int maxCx = bounds.maxX() >> 4;
        int minCz = bounds.minZ() >> 4;
        int maxCz = bounds.maxZ() >> 4;
        List<ChunkCoord> chunks = new ArrayList<>((maxCx - minCx + 1) * (maxCz - minCz + 1));
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(new ChunkCoord(cx, cz));
            }
        }
        return chunks;
    }

    public record ChunkCoord(int x, int z) {
    }
}
