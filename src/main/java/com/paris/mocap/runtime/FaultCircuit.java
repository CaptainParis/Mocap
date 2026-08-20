package com.paris.mocap.runtime;

public final class FaultCircuit {
    private final int tripAfter;
    private int consecutive;
    private int cooldownTicks;
    private boolean open;
    private boolean announced;

    public FaultCircuit(int tripAfter) {
        this.tripAfter = Math.max(2, tripAfter);
    }

    public boolean allow() {
        if (!this.open) {
            return true;
        }
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            return false;
        }
        this.open = false;
        this.consecutive = 0;
        return true;
    }

    public void success() {
        this.consecutive = 0;
        this.open = false;
        this.announced = false;
    }

    public void failure() {
        this.consecutive++;
        if (this.consecutive >= this.tripAfter) {
            this.open = true;
            this.cooldownTicks = 100;
        }
    }

    public boolean justTripped() {
        if (this.open && !this.announced) {
            this.announced = true;
            return true;
        }
        return false;
    }

    public boolean open() {
        return this.open;
    }

    public void reset() {
        this.consecutive = 0;
        this.open = false;
        this.cooldownTicks = 0;
        this.announced = false;
    }
}
