package com.paris.mocap.playback;

import com.paris.mocap.actor.ActorService;
import com.paris.mocap.actor.PacketActor;
import com.paris.mocap.api.event.MocapPlaybackStartEvent;
import com.paris.mocap.api.event.MocapPlaybackStopEvent;
import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.cycle.CyclePhase;
import com.paris.mocap.model.ActionData;
import com.paris.mocap.model.ActionType;
import com.paris.mocap.model.Frame;
import com.paris.mocap.model.PlaybackSettings;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.Track;
import com.paris.mocap.runtime.FailForward;
import com.paris.mocap.scene.SceneReplayer;
import com.paris.mocap.scene.SnapshotPasteJob;
import com.paris.mocap.scene.StageSlot;
import com.paris.mocap.scene.StageWorldService;
import com.paris.mocap.scene.WorldScene;
import com.paris.mocap.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaybackService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ActorService actors;
    private final StageWorldService stage;
    private final FailForward failForward;
    private final MocapConfig config;
    private final PlaybackEngine engine;
    private final ExclusiveViewerService exclusiveViewers;

    public PlaybackService(
        JavaPlugin plugin,
        ActorService actors,
        StageWorldService stage,
        FailForward failForward,
        MocapConfig config
    ) {
        this.plugin = plugin;
        this.actors = actors;
        this.stage = stage;
        this.failForward = failForward;
        this.config = config;
        this.exclusiveViewers = new ExclusiveViewerService(plugin);
        Bukkit.getPluginManager().registerEvents(this.exclusiveViewers, plugin);
        this.engine = new PlaybackEngine(
            plugin,
            actors.packets(),
            failForward,
            config.tickBudget(),
            this::onPrepared,
            session -> stop(session, MocapPlaybackStopEvent.Reason.FINISHED),
            this::onStopped
        );
    }

    public void startEngine() {
        this.engine.start();
    }

    public Collection<PlaybackSession> sessions() {
        return this.engine.sessions().values();
    }

    public PlaybackSession session(UUID id) {
        return id == null ? null : this.engine.sessions().get(id);
    }

    public PlaybackSession sessionByName(String name) {
        for (PlaybackSession session : sessions()) {
            if (session.active() && session.name().equalsIgnoreCase(name)) {
                return session;
            }
        }
        return null;
    }

    public UUID play(Recording recording, String trackPlayerName, Player requester) {
        return play(recording, trackPlayerName, requester, null);
    }

    public UUID play(Recording recording, String trackPlayerName, Player requester, org.bukkit.World playbackWorld) {
        if (recording == null || recording.durationTicks() == 0) {
            if (requester != null) {
                requester.sendMessage(Text.prefix("Recording is empty.", NamedTextColor.RED));
            }
            return null;
        }

        MocapPlaybackStartEvent startEvent = new MocapPlaybackStartEvent(requester, recording, trackPlayerName);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            return null;
        }

        List<Track> selected = new ArrayList<>();
        if (trackPlayerName != null && !trackPlayerName.isEmpty()) {
            Track track = recording.trackByName(trackPlayerName);
            if (track == null) {
                if (requester != null) {
                    requester.sendMessage(Text.prefix("Player '" + trackPlayerName + "' not in recording.", NamedTextColor.RED));
                }
                return null;
            }
            selected.add(track);
        } else {
            selected.addAll(recording.tracks());
        }
        if (selected.isEmpty()) {
            if (requester != null) {
                requester.sendMessage(Text.prefix("Recording has no tracks.", NamedTextColor.RED));
            }
            return null;
        }

        PlaybackSettings settings = recording.playbackSettings();
        PlaybackSession session = new PlaybackSession(
            UUID.randomUUID(),
            recording,
            settings,
            this.config.tickBudget().maxFaults()
        );
        session.setName(uniqueName(recording.id()));
        if (requester != null) {
            session.setRequesterId(requester.getUniqueId());
            session.setReturnLocation(requester.getLocation());
            session.setExclusive(playbackWorld != null);
        }

        WorldScene scene = recording.worldScene();
        if (playbackWorld != null) {
            session.setStageSlot(scene == null
                ? StageSlot.identity(playbackWorld)
                : StageSlot.identity(playbackWorld, scene.bounds()));
            if (scene != null) {
                session.setSceneReplayer(new SceneReplayer(this.plugin, scene, session.stageSlot()));
                session.sceneReplayer().setupInitialEntities();
            }
            spawnActors(session, selected);
            session.setPhase(CyclePhase.PLAYING);
            if (requester != null) {
                teleportRequester(session, requester, selected.get(0));
            }
        } else if (scene != null) {
            boolean staged = this.failForward.attempt("stage-prepare:" + session.name(), () -> {
                this.stage.ensureWorld();
                StageSlot slot = this.stage.allocate(scene.bounds());
                if (slot == null) {
                    return;
                }
                session.setStageSlot(slot);
                session.setSceneReplayer(new SceneReplayer(this.plugin, scene, slot));
                session.setPasteJob(new SnapshotPasteJob(this.plugin, slot, scene.snapshot()));
            });
            if (!staged && requester != null) {
                requester.sendMessage(Text.prefix(
                    "Stage unavailable — playing actors in the original world.",
                    NamedTextColor.YELLOW
                ));
            }
        }

        if (playbackWorld == null && session.pasteJob() == null) {
            spawnActors(session, selected);
            session.setPhase(CyclePhase.PLAYING);
            if (requester != null) {
                teleportRequester(session, requester, selected.get(0));
            }
        } else if (session.pasteJob() != null) {
            for (Track track : selected) {
                session.tracks().put(track.playerId(), track);
            }
            session.setPhase(CyclePhase.PREPARING);
            if (requester != null) {
                requester.sendMessage(Text.prefix(
                    "Preparing stage for '" + session.name() + "'…",
                    NamedTextColor.AQUA
                ));
            }
        }

        this.engine.register(session);
        if (session.exclusive()) {
            this.exclusiveViewers.attach(session);
        }
        if (session.phase() == CyclePhase.PLAYING && requester != null) {
            requester.sendMessage(Text.prefix("Playing '" + session.name() + "'", NamedTextColor.GREEN));
        }
        return session.id();
    }

    public void stop(PlaybackSession session) {
        stop(session, MocapPlaybackStopEvent.Reason.STOPPED);
    }

    public void stop(PlaybackSession session, MocapPlaybackStopEvent.Reason reason) {
        if (session == null || !session.beginTeardown()) {
            return;
        }
        this.exclusiveViewers.detach(session);
        Bukkit.getPluginManager().callEvent(new MocapPlaybackStopEvent(session, reason));
        returnRequester(session);
        this.actors.destroyAll(session.actors());
        StageSlot slot = session.stageSlot();
        SceneReplayer replayer = session.sceneReplayer();
        boolean identity = slot != null && slot.identityRemap();
        if (replayer != null) {
            if (identity) {
                replayer.clearEntities();
            } else {
                replayer.beginBlockRestore(false);
            }
        }
        if (slot != null && !identity && session.recording().worldScene() != null) {
            session.setClearJob(SnapshotPasteJob.clear(
                this.plugin,
                slot,
                session.recording().worldScene().snapshot()
            ));
            session.stop();
            session.setPhase(CyclePhase.STOPPING);
            return;
        }
        session.stop();
        this.engine.unregister(session);
        releaseSlot(session);
    }

    public void stopAll() {
        for (PlaybackSession session : new ArrayList<>(sessions())) {
            stop(session, MocapPlaybackStopEvent.Reason.DISABLED);
        }
    }

    private void onPrepared(PlaybackSession session) {
        this.failForward.run("session-prepared:" + session.name(), () -> {
            SceneReplayer replayer = session.sceneReplayer();
            if (replayer != null) {
                replayer.setupInitialEntities();
            }
            List<Track> tracks = new ArrayList<>(session.tracks().values());
            spawnActors(session, tracks);
            Player requester = session.requester();
            if (requester != null && !tracks.isEmpty()) {
                teleportRequester(session, requester, tracks.get(0));
                requester.sendMessage(Text.prefix(
                    "Playing '" + session.name() + "' on stage",
                    NamedTextColor.GREEN
                ));
            }
        });
    }

    private void onStopped(PlaybackSession session) {
        this.failForward.run("session-stopped:" + session.name(), () -> releaseSlot(session));
    }

    private void returnRequester(PlaybackSession session) {
        Player requester = session.requester();
        Location home = session.returnLocation();
        if (requester == null || !requester.isOnline() || home == null || home.getWorld() == null) {
            return;
        }
        requester.teleportAsync(home).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                requester.sendMessage(Text.prefix("Returned to origin world.", NamedTextColor.GREEN));
            } else {
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    requester.teleport(home);
                    requester.sendMessage(Text.prefix("Returned to origin world.", NamedTextColor.GREEN));
                });
            }
        });
    }

    private void spawnActors(PlaybackSession session, List<Track> selected) {
        StageSlot slot = session.stageSlot();
        for (Track track : selected) {
            Frame spawnFrame = track.getFrame(0);
            if (spawnFrame == null) {
                spawnFrame = track.floorFrame(0);
            }
            if (spawnFrame == null && track.size() > 0) {
                spawnFrame = track.frameAt(0);
            }
            if (spawnFrame == null) {
                continue;
            }
            Pose spawnPose = transformPose(spawnFrame.pose(), slot);
            PacketActor actor = this.actors.create(track, spawnPose, session.settings().visibilityMode());
            if (session.exclusive() && session.requesterId() != null) {
                actor.restrictTo(session.requesterId());
            }
            applyEquipment(track, actor, track.tickAt(0));
            session.bind(track, actor);
        }
        for (PacketActor actor : session.actors()) {
            this.actors.revealNearby(actor);
        }
    }

    private static void applyEquipment(Track track, PacketActor actor, int throughTick) {
        for (int i = 0; i < track.size(); i++) {
            if (track.tickAt(i) > throughTick) {
                break;
            }
            for (ActionData action : track.frameAt(i).actions()) {
                if (action.type() == ActionType.EQUIPMENT && action.slot() != null) {
                    actor.setEquipment(action.slot(), action.item());
                }
            }
        }
    }

    private void teleportRequester(PlaybackSession session, Player requester, Track first) {
        Frame firstFrame = first.getFrame(0);
        if (firstFrame == null) {
            firstFrame = first.size() > 0 ? first.frameAt(0) : null;
        }
        if (firstFrame != null) {
            Pose pose = transformPose(firstFrame.pose(), session.stageSlot());
            if (pose.toLocation() != null) {
                requester.teleport(pose.toLocation());
                return;
            }
        }
        if (session.stageSlot() != null) {
            requester.teleport(session.stageSlot().spawnLocation());
        }
    }

    private void releaseSlot(PlaybackSession session) {
        StageSlot slot = session.stageSlot();
        if (slot != null && !slot.identityRemap()) {
            this.stage.release(slot);
        }
        session.setStageSlot(null);
        session.setSceneReplayer(null);
    }

    static Pose transformPose(Pose pose, StageSlot slot) {
        if (slot == null) {
            return pose;
        }
        return pose.transformed(slot.world().getName(), slot.offsetX(), slot.offsetY(), slot.offsetZ());
    }

    private String uniqueName(String base) {
        if (sessionByName(base) == null) {
            return base;
        }
        int i = 2;
        while (sessionByName(base + "-" + i) != null) {
            i++;
        }
        return base + "-" + i;
    }

    @Override
    public void close() {
        stopAll();
        this.engine.close();
    }
}
