package com.paris.mocap.model;

import com.paris.mocap.scene.WorldScene;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Recording {
    private final String id;
    private final long createdAt;
    private final Map<UUID, Track> tracks = new LinkedHashMap<>();
    private String iconMaterial;
    private String gameType;
    private final PlaybackSettings playbackSettings = new PlaybackSettings();
    private WorldScene worldScene;

    public Recording(String id) {
        this(id, System.currentTimeMillis());
    }

    public Recording(String id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public String id() {
        return this.id;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public void addTrack(Track track) {
        this.tracks.put(track.playerId(), track);
    }

    public Track track(UUID playerId) {
        return this.tracks.get(playerId);
    }

    public Track trackByName(String name) {
        for (Track track : this.tracks.values()) {
            if (track.playerName().equalsIgnoreCase(name)) {
                return track;
            }
        }
        return null;
    }

    public Collection<Track> tracks() {
        return Collections.unmodifiableCollection(this.tracks.values());
    }

    public int durationTicks() {
        int max = 0;
        for (Track track : this.tracks.values()) {
            if (track.maxTick() > max) {
                max = track.maxTick();
            }
        }
        if (this.worldScene != null) {
            max = Math.max(max, this.worldScene.maxEventTick());
        }
        return max;
    }

    public WorldScene worldScene() {
        return this.worldScene;
    }

    public void setWorldScene(WorldScene worldScene) {
        this.worldScene = worldScene;
    }

    public boolean hasWorldScene() {
        return this.worldScene != null;
    }

    public String iconMaterialName() {
        return this.iconMaterial;
    }

    public void setIconMaterial(String iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public PlaybackSettings playbackSettings() {
        return this.playbackSettings;
    }

    public String gameType() {
        return this.gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType == null || gameType.isBlank() ? null : gameType;
    }
}
