package com.paris.mocap.scene;

public enum WorldCaptureMode {
    OFF,
    AREA,
    AUTO_BOX,
    LOADED_CHUNKS;

    private static final WorldCaptureMode[] VALUES = values();

    public static WorldCaptureMode byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return OFF;
        }
        return VALUES[id];
    }

    public WorldCaptureMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String label() {
        return switch (this) {
            case OFF -> "Players + block edits";
            case AREA -> "Area radius";
            case AUTO_BOX -> "Auto box";
            case LOADED_CHUNKS -> "Loaded chunks";
        };
    }
}
