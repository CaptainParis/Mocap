package com.paris.mocap.api;

import com.paris.mocap.Mocap;
import com.paris.mocap.api.event.MocapPlaybackStopEvent;
import com.paris.mocap.dialog.MocapDialogs;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.playback.PlaybackSession;
import com.paris.mocap.recording.CaptureOptions;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MocapApiImpl implements MocapApi {
    private final Mocap plugin;
    private final MocapDialogs dialogs;

    public MocapApiImpl(Mocap plugin, MocapDialogs dialogs) {
        this.plugin = plugin;
        this.dialogs = dialogs;
    }

    @Override
    public Plugin plugin() {
        return this.plugin;
    }

    @Override
    public Collection<Recording> recordings() {
        return this.plugin.repository().list();
    }

    @Override
    public Recording recording(String id) {
        return this.plugin.repository().get(id);
    }

    @Override
    public boolean hasRecording(String id) {
        return this.plugin.repository().exists(id);
    }

    @Override
    public boolean deleteRecording(String id) {
        if (!this.plugin.repository().exists(id)) {
            return false;
        }
        this.plugin.repository().delete(id);
        return true;
    }

    @Override
    public boolean startRecording(Player player, String name) {
        return startRecording(player, name, CaptureOptions.MANUAL, null);
    }

    @Override
    public boolean startRecording(Player player, String name, CaptureOptions options) {
        return startRecording(player, name, options, null);
    }

    @Override
    public boolean startRecording(Player player, String name, CaptureOptions options, String gameType) {
        if (player == null || name == null || name.isBlank() || options == null) {
            return false;
        }
        return this.plugin.recordings().start(player, name.trim(), options, gameType);
    }

    @Override
    public boolean stopRecording(Player player) {
        if (player == null || !this.plugin.recordings().isRecording(player)) {
            return false;
        }
        this.plugin.recordings().stop(player);
        return true;
    }

    @Override
    public boolean isRecording(Player player) {
        return player != null && this.plugin.recordings().isRecording(player);
    }

    @Override
    public String activeRecordingId(Player player) {
        return this.plugin.recordings().recordingName(player);
    }

    @Override
    public String recordGroup(String gameType, Collection<Player> players) {
        return this.plugin.recordings().startGroup(gameType, players);
    }

    @Override
    public String recordGroup(String gameType, Collection<Player> players, CaptureOptions options) {
        return this.plugin.recordings().startGroup(gameType, players, options);
    }

    @Override
    public String recordGroup(String recordingId, String gameType, Collection<Player> players) {
        return this.plugin.recordings().startGroup(recordingId, gameType, players, gameCaptureOptions());
    }

    @Override
    public String recordGroup(String recordingId, String gameType, Collection<Player> players, CaptureOptions options) {
        return this.plugin.recordings().startGroup(recordingId, gameType, players, options);
    }

    @Override
    public boolean joinRecording(String recordingId, Player player) {
        return this.plugin.recordings().join(recordingId, player);
    }

    @Override
    public boolean joinRecording(String recordingId, Player player, CaptureOptions options) {
        return this.plugin.recordings().join(recordingId, player, options);
    }

    @Override
    public boolean detach(Player player) {
        return this.plugin.recordings().detach(player);
    }

    @Override
    public boolean stopNamedRecording(String recordingId) {
        return this.plugin.recordings().stopNamed(recordingId);
    }

    @Override
    public boolean isRecordingActive(String recordingId) {
        return this.plugin.recordings().isActive(recordingId);
    }

    @Override
    public Collection<Recording> recordingsForGame(String gameType) {
        return this.plugin.recordings().recordingsForGame(gameType);
    }

    @Override
    public Set<String> gameTypes() {
        return this.plugin.recordings().gameTypes();
    }

    @Override
    public CaptureOptions gameCaptureOptions() {
        return CaptureOptions.game(this.plugin.mocapConfig());
    }

    @Override
    public UUID play(String recordingId, Player viewer) {
        return play(recordingId, null, viewer, null);
    }

    @Override
    public UUID play(String recordingId, String trackPlayerName, Player viewer) {
        return play(recordingId, trackPlayerName, viewer, null);
    }

    @Override
    public UUID play(String recordingId, Player viewer, World playbackWorld) {
        return play(recordingId, null, viewer, playbackWorld);
    }

    @Override
    public UUID play(String recordingId, String trackPlayerName, Player viewer, World playbackWorld) {
        Recording recording = this.plugin.repository().get(recordingId);
        if (recording == null) {
            return null;
        }
        return this.plugin.playback().play(recording, trackPlayerName, viewer, playbackWorld);
    }

    @Override
    public boolean stopPlayback(UUID sessionId) {
        PlaybackSession session = this.plugin.playback().session(sessionId);
        if (session == null) {
            return false;
        }
        this.plugin.playback().stop(session, MocapPlaybackStopEvent.Reason.STOPPED);
        return true;
    }

    @Override
    public boolean stopPlayback(String sessionName) {
        PlaybackSession session = this.plugin.playback().sessionByName(sessionName);
        if (session == null) {
            return false;
        }
        this.plugin.playback().stop(session, MocapPlaybackStopEvent.Reason.STOPPED);
        return true;
    }

    @Override
    public void stopAllPlayback() {
        this.plugin.playback().stopAll();
    }

    @Override
    public PlaybackSession session(UUID sessionId) {
        return this.plugin.playback().session(sessionId);
    }

    @Override
    public Collection<PlaybackSession> sessions() {
        return List.copyOf(this.plugin.playback().sessions());
    }

    @Override
    public RecordingSettings captureSettings() {
        return this.plugin.recordings().settings();
    }

    @Override
    public void openMenu(Player player) {
        this.dialogs.home(player);
    }

    @Override
    public void openLibrary(Player player) {
        this.dialogs.library(player, 0);
    }

    @Override
    public void openGames(Player player) {
        this.dialogs.games(player, 0);
    }

    @Override
    public void openSettings(Player player) {
        this.dialogs.settings(player);
    }
}
