package com.paris.mocap.model;

import com.paris.mocap.cycle.SettingsCycle;
import com.paris.mocap.scene.WorldCaptureMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RecordingSettings {
    private int maxDurationTicks;
    private Location center;
    private double radius;
    private boolean recordOnlyOps;
    private int tickRate = 1;
    private boolean recordAnimations = true;
    private boolean recordEquipment = true;
    private boolean recordSneak = true;
    private boolean recordSprint = true;
    private boolean recordBlocking = true;
    private boolean recordChestOpen = true;
    private boolean recordFishing = true;
    private WorldCaptureMode worldCaptureMode = WorldCaptureMode.LOADED_CHUNKS;

    public int maxDurationTicks() {
        return this.maxDurationTicks;
    }

    public void setMaxDurationTicks(int ticks) {
        this.maxDurationTicks = Math.max(0, ticks);
    }

    public String durationLabel() {
        if (this.maxDurationTicks == 0) {
            return "Unlimited";
        }
        int seconds = this.maxDurationTicks / 20;
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    public void cycleMaxDuration() {
        this.maxDurationTicks = SettingsCycle.nextInt(SettingsCycle.DURATIONS_TICKS, this.maxDurationTicks);
    }

    public boolean global() {
        return this.center == null;
    }

    public double radius() {
        return this.radius;
    }

    public Location center() {
        return this.center;
    }

    public void setArea(Location center, double radius) {
        this.center = center == null ? null : center.clone();
        this.radius = radius;
        if (this.center == null) {
            this.radius = 0.0;
        }
    }

    public void clearArea() {
        this.center = null;
        this.radius = 0.0;
    }

    public String areaLabel() {
        return global() ? "Global (Entire World)" : ((int) this.radius) + " Blocks";
    }

    public void cycleArea(Location at) {
        double next = SettingsCycle.nextDouble(SettingsCycle.AREA_RADII, global() ? 0.0 : this.radius);
        if (next <= 0.0) {
            clearArea();
        } else {
            setArea(at, next);
        }
    }

    public boolean recordOnlyOps() {
        return this.recordOnlyOps;
    }

    public void setRecordOnlyOps(boolean value) {
        this.recordOnlyOps = value;
    }

    public int tickRate() {
        return this.tickRate;
    }

    public void setTickRate(int tickRate) {
        this.tickRate = Math.max(1, tickRate);
    }

    public String tickRateLabel() {
        return switch (this.tickRate) {
            case 1 -> "20 FPS (every tick)";
            case 2 -> "10 FPS (every 2 ticks)";
            case 4 -> "5 FPS (every 4 ticks)";
            default -> this.tickRate + " ticks/frame";
        };
    }

    public void cycleTickRate() {
        this.tickRate = SettingsCycle.nextInt(SettingsCycle.TICK_RATES, this.tickRate);
    }

    public boolean recordAnimations() {
        return this.recordAnimations;
    }

    public void setRecordAnimations(boolean value) {
        this.recordAnimations = value;
    }

    public boolean recordEquipment() {
        return this.recordEquipment;
    }

    public void setRecordEquipment(boolean value) {
        this.recordEquipment = value;
    }

    public boolean recordSneak() {
        return this.recordSneak;
    }

    public void setRecordSneak(boolean value) {
        this.recordSneak = value;
    }

    public boolean recordSprint() {
        return this.recordSprint;
    }

    public void setRecordSprint(boolean value) {
        this.recordSprint = value;
    }

    public boolean recordBlocking() {
        return this.recordBlocking;
    }

    public void setRecordBlocking(boolean value) {
        this.recordBlocking = value;
    }

    public boolean recordChestOpen() {
        return this.recordChestOpen;
    }

    public void setRecordChestOpen(boolean value) {
        this.recordChestOpen = value;
    }

    public boolean recordFishing() {
        return this.recordFishing;
    }

    public void setRecordFishing(boolean value) {
        this.recordFishing = value;
    }

    public WorldCaptureMode worldCaptureMode() {
        return this.worldCaptureMode;
    }

    public void setWorldCaptureMode(WorldCaptureMode mode) {
        this.worldCaptureMode = mode == null ? WorldCaptureMode.OFF : mode;
    }

    public void cycleWorldCaptureMode() {
        this.worldCaptureMode = this.worldCaptureMode.next();
    }

    public boolean matches(Player player) {
        if (this.recordOnlyOps && !player.isOp()) {
            return false;
        }
        if (!global()) {
            if (player.getWorld() != this.center.getWorld()) {
                return false;
            }
            return player.getLocation().distanceSquared(this.center) <= this.radius * this.radius;
        }
        return true;
    }
}
