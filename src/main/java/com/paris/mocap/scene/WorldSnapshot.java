package com.paris.mocap.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldSnapshot {
    private final List<String> palette;
    private final int[] relX;
    private final int[] relY;
    private final int[] relZ;
    private final int[] paletteIndex;
    private transient Map<Long, Integer> spatialIndex;

    public WorldSnapshot(List<String> palette, int[] relX, int[] relY, int[] relZ, int[] paletteIndex) {
        this.palette = List.copyOf(palette);
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.paletteIndex = paletteIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> palette() {
        return this.palette;
    }

    public int size() {
        return this.relX.length;
    }

    public int relX(int i) {
        return this.relX[i];
    }

    public int relY(int i) {
        return this.relY[i];
    }

    public int relZ(int i) {
        return this.relZ[i];
    }

    public int paletteIndex(int i) {
        return this.paletteIndex[i];
    }

    public String blockData(int i) {
        return this.palette.get(this.paletteIndex[i]);
    }

    public String blockDataAt(int relX, int relY, int relZ) {
        Integer index = index().get(pack(relX, relY, relZ));
        if (index == null) {
            return "minecraft:air";
        }
        return this.palette.get(index);
    }

    public Map<Long, Integer> index() {
        if (this.spatialIndex == null) {
            Map<Long, Integer> map = new HashMap<>(Math.max(16, this.relX.length * 2));
            for (int i = 0; i < this.relX.length; i++) {
                map.put(pack(this.relX[i], this.relY[i], this.relZ[i]), this.paletteIndex[i]);
            }
            this.spatialIndex = map;
        }
        return this.spatialIndex;
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    public static final class Builder {
        private final Map<String, Integer> index = new HashMap<>();
        private final List<String> palette = new ArrayList<>();
        private int[] xs = new int[256];
        private int[] ys = new int[256];
        private int[] zs = new int[256];
        private int[] ids = new int[256];
        private int size;

        public void add(int relX, int relY, int relZ, String blockData) {
            if (blockData == null || blockData.equals("minecraft:air") || blockData.equals("air")) {
                return;
            }
            int paletteId = this.index.computeIfAbsent(blockData, key -> {
                this.palette.add(key);
                return this.palette.size() - 1;
            });
            if (this.size == this.xs.length) {
                int next = this.xs.length << 1;
                this.xs = Arrays.copyOf(this.xs, next);
                this.ys = Arrays.copyOf(this.ys, next);
                this.zs = Arrays.copyOf(this.zs, next);
                this.ids = Arrays.copyOf(this.ids, next);
            }
            this.xs[this.size] = relX;
            this.ys[this.size] = relY;
            this.zs[this.size] = relZ;
            this.ids[this.size] = paletteId;
            this.size++;
        }

        public WorldSnapshot build() {
            return new WorldSnapshot(
                this.palette,
                Arrays.copyOf(this.xs, this.size),
                Arrays.copyOf(this.ys, this.size),
                Arrays.copyOf(this.zs, this.size),
                Arrays.copyOf(this.ids, this.size)
            );
        }
    }
}
