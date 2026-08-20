package com.paris.mocap.config;

import com.paris.mocap.runtime.TickBudget;
import com.paris.mocap.scene.WorldCaptureMode;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MocapConfig {
    public static final int MAX_GAME_PLAYERS = 500;
    private final JavaPlugin plugin;
    private String storageDirectory;
    private boolean asyncIo;
    private double viewDistanceSquared;
    private int viewerRefreshTicks;
    private int entityIdBase;
    private int defaultTickRate;
    private int defaultMaxDurationTicks;
    private double defaultRadius;
    private WorldCaptureMode defaultWorldCaptureMode;
    private int autoBoxRadius;
    private int chunkRadius;
    private int entitySampleInterval;
    private String stageWorldName;
    private int stageSlotSize;
    private TickBudget tickBudget;
    private int gameMaxPlayers;
    private WorldCaptureMode gameWorldCapture;
    private int gameMaxDurationTicks;
    private boolean gameNotify;
    private boolean gameCompleteSkin;
    private boolean gameSnapshotEquipment;

    public MocapConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        FileConfiguration cfg = this.plugin.getConfig();
        this.storageDirectory = cfg.getString("storage.directory", "recordings");
        this.asyncIo = cfg.getBoolean("storage.async-io", true);
        this.viewDistanceSquared = cfg.getDouble("playback.view-distance-squared", 9216.0);
        this.viewerRefreshTicks = Math.max(5, cfg.getInt("playback.viewer-refresh-ticks", 20));
        this.entityIdBase = cfg.getInt("playback.entity-id-base", 1_500_000_000);
        this.defaultTickRate = Math.max(1, cfg.getInt("recording.default-tick-rate", 1));
        this.defaultMaxDurationTicks = Math.max(0, cfg.getInt("recording.default-max-duration-ticks", 0));
        this.defaultRadius = Math.max(0.0, cfg.getDouble("recording.default-radius", 0.0));

        String modeName = cfg.getString("world-capture.default-mode", "LOADED_CHUNKS");
        try {
            this.defaultWorldCaptureMode = WorldCaptureMode.valueOf(modeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            this.defaultWorldCaptureMode = WorldCaptureMode.LOADED_CHUNKS;
        }
        this.autoBoxRadius = Math.max(8, cfg.getInt("world-capture.auto-box-radius", 48));
        this.chunkRadius = Math.max(0, cfg.getInt("world-capture.chunk-radius", 2));
        this.entitySampleInterval = Math.max(1, cfg.getInt("world-capture.entity-sample-interval", 1));
        this.stageWorldName = cfg.getString("stage.world-name", "mocap_stage");
        this.stageSlotSize = Math.max(64, cfg.getInt("stage.slot-size", 256));
        this.tickBudget = new TickBudget(
            cfg.getInt("performance.paste-blocks-per-tick", 6144),
            cfg.getInt("performance.clear-blocks-per-tick", 8192),
            cfg.getInt("performance.scene-events-per-tick", 384),
            cfg.getInt("performance.snapshot-chunks-per-tick", 4),
            cfg.getInt("performance.max-consecutive-faults", 16),
            (float) cfg.getDouble("performance.pose-epsilon", 0.002),
            (float) cfg.getDouble("performance.entity-move-epsilon", 0.02)
        );
        this.gameMaxPlayers = Math.min(MAX_GAME_PLAYERS, Math.max(1, cfg.getInt("games.max-players", MAX_GAME_PLAYERS)));
        String gameModeName = cfg.getString("games.world-capture", "OFF");
        try {
            this.gameWorldCapture = WorldCaptureMode.valueOf(gameModeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            this.gameWorldCapture = WorldCaptureMode.OFF;
        }
        this.gameMaxDurationTicks = Math.max(0, cfg.getInt("games.max-duration-ticks", 24_000));
        this.gameNotify = cfg.getBoolean("games.notify", false);
        this.gameCompleteSkin = cfg.getBoolean("games.complete-skin", true);
        this.gameSnapshotEquipment = cfg.getBoolean("games.snapshot-equipment", false);
    }

    public File recordingsFolder() {
        return new File(this.plugin.getDataFolder(), this.storageDirectory);
    }

    public boolean asyncIo() {
        return this.asyncIo;
    }

    public double viewDistanceSquared() {
        return this.viewDistanceSquared;
    }

    public int viewerRefreshTicks() {
        return this.viewerRefreshTicks;
    }

    public int entityIdBase() {
        return this.entityIdBase;
    }

    public int defaultTickRate() {
        return this.defaultTickRate;
    }

    public int defaultMaxDurationTicks() {
        return this.defaultMaxDurationTicks;
    }

    public double defaultRadius() {
        return this.defaultRadius;
    }

    public WorldCaptureMode defaultWorldCaptureMode() {
        return this.defaultWorldCaptureMode;
    }

    public int autoBoxRadius() {
        return this.autoBoxRadius;
    }

    public int chunkRadius() {
        return this.chunkRadius;
    }

    public int entitySampleInterval() {
        return this.entitySampleInterval;
    }

    public String stageWorldName() {
        return this.stageWorldName;
    }

    public int stageSlotSize() {
        return this.stageSlotSize;
    }

    public TickBudget tickBudget() {
        return this.tickBudget;
    }

    public int gameMaxPlayers() {
        return this.gameMaxPlayers;
    }

    public WorldCaptureMode gameWorldCapture() {
        return this.gameWorldCapture;
    }

    public int gameMaxDurationTicks() {
        return this.gameMaxDurationTicks;
    }

    public boolean gameNotify() {
        return this.gameNotify;
    }

    public boolean gameCompleteSkin() {
        return this.gameCompleteSkin;
    }

    public boolean gameSnapshotEquipment() {
        return this.gameSnapshotEquipment;
    }
}
