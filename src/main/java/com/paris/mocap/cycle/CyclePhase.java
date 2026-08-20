package com.paris.mocap.cycle;

public enum CyclePhase {
    IDLE,
    RECORDING,
    FINALIZING,
    READY,
    PREPARING,
    PLAYING,
    LOOPING,
    SEEKING,
    STOPPING;

    public boolean tickingActors() {
        return this == PLAYING;
    }

    public boolean occupyingStage() {
        return this == PREPARING || this == PLAYING || this == LOOPING || this == SEEKING;
    }
}
