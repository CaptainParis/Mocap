package com.paris.mocap.recording;

import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.scene.WorldCaptureMode;

public final class CaptureOptions {
    public static final CaptureOptions MANUAL = new CaptureOptions(true, MocapConfig.MAX_GAME_PLAYERS, null, true, true, 0, false);

    private final boolean notify;
    private final int maxTracks;
    private final WorldCaptureMode worldCaptureOverride;
    private final boolean snapshotEquipment;
    private final boolean completeSkin;
    private final int maxDurationTicks;
    private final boolean ignoreFilters;

    public CaptureOptions(
        boolean notify,
        int maxTracks,
        WorldCaptureMode worldCaptureOverride,
        boolean snapshotEquipment,
        boolean completeSkin
    ) {
        this(notify, maxTracks, worldCaptureOverride, snapshotEquipment, completeSkin, 0, false);
    }

    public CaptureOptions(
        boolean notify,
        int maxTracks,
        WorldCaptureMode worldCaptureOverride,
        boolean snapshotEquipment,
        boolean completeSkin,
        int maxDurationTicks,
        boolean ignoreFilters
    ) {
        this.notify = notify;
        this.maxTracks = Math.max(1, maxTracks);
        this.worldCaptureOverride = worldCaptureOverride;
        this.snapshotEquipment = snapshotEquipment;
        this.completeSkin = completeSkin;
        this.maxDurationTicks = Math.max(0, maxDurationTicks);
        this.ignoreFilters = ignoreFilters;
    }

    public static CaptureOptions game(MocapConfig config) {
        if (config == null) {
            return new CaptureOptions(false, MocapConfig.MAX_GAME_PLAYERS, WorldCaptureMode.OFF, false, true, 24_000, true);
        }
        return new CaptureOptions(
            config.gameNotify(),
            config.gameMaxPlayers(),
            config.gameWorldCapture(),
            config.gameSnapshotEquipment(),
            config.gameCompleteSkin(),
            config.gameMaxDurationTicks(),
            true
        );
    }

    public boolean notifyPlayers() {
        return this.notify;
    }

    public int maxTracks() {
        return this.maxTracks;
    }

    public WorldCaptureMode worldCaptureOverride() {
        return this.worldCaptureOverride;
    }

    public boolean snapshotEquipment() {
        return this.snapshotEquipment;
    }

    public boolean completeSkin() {
        return this.completeSkin;
    }

    public int maxDurationTicks() {
        return this.maxDurationTicks;
    }

    public boolean ignoreFilters() {
        return this.ignoreFilters;
    }
}
