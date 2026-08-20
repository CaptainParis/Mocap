package com.paris.mocap.api;

import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.playback.PlaybackSession;
import com.paris.mocap.recording.CaptureOptions;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public interface MocapApi {
    static MocapApi get() {
        RegisteredServiceProvider<MocapApi> provider =
            Bukkit.getServicesManager().getRegistration(MocapApi.class);
        return provider == null ? null : provider.getProvider();
    }

    Plugin plugin();

    Collection<Recording> recordings();

    Recording recording(String id);

    boolean hasRecording(String id);

    boolean deleteRecording(String id);

    boolean startRecording(Player player, String name);

    boolean startRecording(Player player, String name, CaptureOptions options);

    boolean startRecording(Player player, String name, CaptureOptions options, String gameType);

    boolean stopRecording(Player player);

    boolean isRecording(Player player);

    String activeRecordingId(Player player);

    String recordGroup(String gameType, Collection<Player> players);

    String recordGroup(String gameType, Collection<Player> players, CaptureOptions options);

    String recordGroup(String recordingId, String gameType, Collection<Player> players);

    String recordGroup(String recordingId, String gameType, Collection<Player> players, CaptureOptions options);

    boolean joinRecording(String recordingId, Player player);

    boolean joinRecording(String recordingId, Player player, CaptureOptions options);

    boolean detach(Player player);

    boolean stopNamedRecording(String recordingId);

    boolean isRecordingActive(String recordingId);

    Collection<Recording> recordingsForGame(String gameType);

    Set<String> gameTypes();

    CaptureOptions gameCaptureOptions();

    UUID play(String recordingId, Player viewer);

    UUID play(String recordingId, String trackPlayerName, Player viewer);

    UUID play(String recordingId, Player viewer, World playbackWorld);

    UUID play(String recordingId, String trackPlayerName, Player viewer, World playbackWorld);

    boolean stopPlayback(UUID sessionId);

    boolean stopPlayback(String sessionName);

    void stopAllPlayback();

    PlaybackSession session(UUID sessionId);

    Collection<PlaybackSession> sessions();

    RecordingSettings captureSettings();

    void openMenu(Player player);

    void openLibrary(Player player);

    void openGames(Player player);

    void openSettings(Player player);
}
