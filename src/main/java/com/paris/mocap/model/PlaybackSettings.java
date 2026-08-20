package com.paris.mocap.model;

import com.paris.mocap.cycle.SettingsCycle;

public final class PlaybackSettings {
    private boolean loop;
    private int loopCount = -1;
    private VisibilityMode visibilityMode = VisibilityMode.ALL;
    private double defaultSpeed = 1.0;

    public boolean loop() {
        return this.loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public int loopCount() {
        return this.loopCount;
    }

    public void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

    public String loopLabel() {
        if (!this.loop) {
            return "Off";
        }
        return this.loopCount == -1 ? "Infinite" : this.loopCount + "x";
    }

    public void cycleLoopCount() {
        if (!this.loop) {
            return;
        }
        this.loopCount = SettingsCycle.nextInt(SettingsCycle.LOOP_COUNTS, this.loopCount);
    }

    public VisibilityMode visibilityMode() {
        return this.visibilityMode;
    }

    public void setVisibilityMode(VisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode;
    }

    public void cycleVisibility() {
        this.visibilityMode = this.visibilityMode.next();
    }

    public double defaultSpeed() {
        return this.defaultSpeed;
    }

    public void setDefaultSpeed(double defaultSpeed) {
        this.defaultSpeed = Math.max(-4.0, Math.min(4.0, defaultSpeed));
        if (Math.abs(this.defaultSpeed) < 0.25) {
            this.defaultSpeed = this.defaultSpeed < 0 ? -0.25 : 0.25;
        }
    }

    public String speedLabel() {
        double speed = this.defaultSpeed;
        String body = speed == (double) ((int) speed)
            ? String.valueOf((int) Math.abs(speed))
            : String.valueOf(Math.abs(speed));
        return (speed < 0 ? "-" : "") + body + "x";
    }

    public void cycleSpeed() {
        this.defaultSpeed = SettingsCycle.nextDouble(SettingsCycle.SPEEDS, this.defaultSpeed);
    }
}
