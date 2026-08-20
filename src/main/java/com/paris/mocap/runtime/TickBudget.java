package com.paris.mocap.runtime;

public final class TickBudget {
    private final int pasteBlocks;
    private final int clearBlocks;
    private final int sceneEvents;
    private final int snapshotChunks;
    private final int maxFaults;
    private final float poseEpsilon;
    private final float entityMoveEpsilon;

    public TickBudget(
        int pasteBlocks,
        int clearBlocks,
        int sceneEvents,
        int snapshotChunks,
        int maxFaults,
        float poseEpsilon,
        float entityMoveEpsilon
    ) {
        this.pasteBlocks = Math.max(256, pasteBlocks);
        this.clearBlocks = Math.max(256, clearBlocks);
        this.sceneEvents = Math.max(32, sceneEvents);
        this.snapshotChunks = Math.max(1, snapshotChunks);
        this.maxFaults = Math.max(2, maxFaults);
        this.poseEpsilon = Math.max(0.0001F, poseEpsilon);
        this.entityMoveEpsilon = Math.max(0.001F, entityMoveEpsilon);
    }

    public int pasteBlocks() {
        return this.pasteBlocks;
    }

    public int clearBlocks() {
        return this.clearBlocks;
    }

    public int sceneEvents() {
        return this.sceneEvents;
    }

    public int snapshotChunks() {
        return this.snapshotChunks;
    }

    public int maxFaults() {
        return this.maxFaults;
    }

    public float poseEpsilon() {
        return this.poseEpsilon;
    }

    public float entityMoveEpsilon() {
        return this.entityMoveEpsilon;
    }
}
