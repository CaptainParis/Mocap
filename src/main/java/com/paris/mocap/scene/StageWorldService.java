package com.paris.mocap.scene;

import com.paris.mocap.config.MocapConfig;
import java.util.BitSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class StageWorldService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final MocapConfig config;
    private final BitSet usedSlots = new BitSet();
    private World stageWorld;

    public StageWorldService(JavaPlugin plugin, MocapConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void ensureWorld() {
        if (this.stageWorld != null) {
            return;
        }
        String name = this.config.stageWorldName();
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            this.stageWorld = existing;
            return;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generator(new VoidGenerator());
        this.stageWorld = creator.createWorld();
        if (this.stageWorld != null) {
            this.stageWorld.setAutoSave(false);
            this.stageWorld.setSpawnFlags(false, false);
            this.plugin.getLogger().info("Created stage world '" + name + "'");
        } else {
            this.plugin.getLogger().severe("Failed to create stage world '" + name + "'");
        }
    }

    public World world() {
        ensureWorld();
        return this.stageWorld;
    }

    private int nextOriginX;

    public StageSlot allocate(CaptureBounds captureBounds) {
        ensureWorld();
        if (this.stageWorld == null) {
            return null;
        }
        int index = this.usedSlots.nextClearBit(0);
        this.usedSlots.set(index);
        int width = Math.max(
            this.config.stageSlotSize(),
            captureBounds == null ? this.config.stageSlotSize() : captureBounds.sizeX() + 64
        );
        int originX = this.nextOriginX;
        this.nextOriginX += width;
        int originY = 64;
        int originZ = 0;
        return new StageSlot(index, this.stageWorld, originX, originY, originZ, width, captureBounds);
    }

    public void release(StageSlot slot) {
        if (slot != null) {
            this.usedSlots.clear(slot.index());
        }
    }

    @Override
    public void close() {
        this.usedSlots.clear();
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
