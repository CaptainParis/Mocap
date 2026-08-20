package com.paris.mocap.playback;

import com.paris.mocap.actor.PacketActor;
import com.paris.mocap.cycle.SettingsCycle;
import com.paris.mocap.cycle.CyclePhase;
import com.paris.mocap.model.Frame;
import com.paris.mocap.model.PlaybackSettings;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.Track;
import com.paris.mocap.runtime.FaultCircuit;
import com.paris.mocap.scene.SceneReplayer;
import com.paris.mocap.scene.SnapshotPasteJob;
import com.paris.mocap.scene.StageSlot;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

public final class PlaybackSession {
    private final UUID id;
    private final Recording recording;
    private final PlaybackSettings settings;
    private final Map<UUID, PacketActor> actors = new ConcurrentHashMap<>();
    private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();
    private final Set<UUID> mutedTracks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger tick = new AtomicInteger();
    private final FaultCircuit circuit;
    private volatile boolean paused;
    private volatile boolean active = true;
    private volatile boolean seekFlag;
    private volatile double speed;
    private volatile double speedAccumulator;
    private volatile int loopIndex;
    private volatile String name;
    private volatile CyclePhase phase = CyclePhase.PREPARING;
    private volatile UUID requesterId;
    private volatile org.bukkit.Location returnLocation;
    private volatile boolean tearingDown;
    private volatile boolean pingPoses;
    private volatile boolean resyncPoses;
    private volatile boolean exclusive;
    private StageSlot stageSlot;
    private SceneReplayer sceneReplayer;
    private SnapshotPasteJob pasteJob;
    private SnapshotPasteJob clearJob;

    public PlaybackSession(UUID id, Recording recording, PlaybackSettings settings, int maxFaults) {
        this.id = id;
        this.recording = recording;
        this.settings = settings;
        this.speed = settings.defaultSpeed();
        this.name = recording.id();
        this.circuit = new FaultCircuit(maxFaults);
    }

    public UUID id() {
        return this.id;
    }

    public Recording recording() {
        return this.recording;
    }

    public PlaybackSettings settings() {
        return this.settings;
    }

    public FaultCircuit circuit() {
        return this.circuit;
    }

    public CyclePhase phase() {
        return this.phase;
    }

    public void setPhase(CyclePhase phase) {
        this.phase = phase;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID requesterId() {
        return this.requesterId;
    }

    public void setRequesterId(UUID requesterId) {
        this.requesterId = requesterId;
    }

    public Player requester() {
        if (this.requesterId == null) {
            return null;
        }
        return org.bukkit.Bukkit.getPlayer(this.requesterId);
    }

    public org.bukkit.Location returnLocation() {
        return this.returnLocation;
    }

    public void setReturnLocation(org.bukkit.Location returnLocation) {
        this.returnLocation = returnLocation == null ? null : returnLocation.clone();
    }

    public boolean tearingDown() {
        return this.tearingDown;
    }

    public boolean beginTeardown() {
        if (this.tearingDown) {
            return false;
        }
        this.tearingDown = true;
        return true;
    }

    public void bind(Track track, PacketActor actor) {
        this.tracks.put(track.playerId(), track);
        this.actors.put(track.playerId(), actor);
    }

    public PacketActor actor(UUID trackId) {
        return this.actors.get(trackId);
    }

    public Collection<PacketActor> actors() {
        return this.actors.values();
    }

    public Map<UUID, Track> tracks() {
        return this.tracks;
    }

    public boolean paused() {
        return this.paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void togglePause() {
        this.paused = !this.paused;
    }

    public boolean exclusive() {
        return this.exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean pingPoses() {
        return this.pingPoses;
    }

    public void setPingPoses(boolean pingPoses) {
        if (this.pingPoses == pingPoses) {
            return;
        }
        this.pingPoses = pingPoses;
        this.resyncPoses = true;
    }

    public void togglePingPoses() {
        setPingPoses(!this.pingPoses);
    }

    public boolean consumeResyncPoses() {
        if (!this.resyncPoses) {
            return false;
        }
        this.resyncPoses = false;
        return true;
    }

    public static int oneWayDelayTicks(int pingMs) {
        if (pingMs < 50) {
            return 0;
        }
        return Math.max(1, Math.round(pingMs / 100.0f));
    }

    public int displayTick(Track track, int tick) {
        if (!this.pingPoses || track == null) {
            return tick;
        }
        Frame now = track.floorFrame(tick);
        if (now == null) {
            return tick;
        }
        return Math.max(0, tick - oneWayDelayTicks(now.pingMs()));
    }

    public int tick() {
        return this.tick.get();
    }

    public void setTick(int tick) {
        jumpTo(tick);
        this.seekFlag = true;
    }

    public void skipTicks(int delta) {
        setTick(this.tick.get() + delta);
    }

    public void jumpTo(int tick) {
        this.tick.set(Math.max(0, Math.min(tick, this.recording.durationTicks())));
    }

    public boolean consumeSeekFlag() {
        if (!this.seekFlag) {
            return false;
        }
        this.seekFlag = false;
        return true;
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        double clamped = Math.max(-4.0, Math.min(4.0, speed));
        if (Math.abs(clamped) < 0.25) {
            clamped = clamped < 0 ? -0.25 : 0.25;
        }
        this.speed = clamped;
        this.speedAccumulator = 0;
    }

    public void cycleSpeed() {
        this.settings.cycleSpeed();
        setSpeed(this.settings.defaultSpeed());
    }

    public void cycleSpeed(boolean reverse) {
        nudgeSpeed(reverse ? -1 : 1);
    }

    public void nudgeSpeed(int steps) {
        setSpeed(SettingsCycle.stepDouble(SettingsCycle.SPEEDS, this.speed, steps));
    }

    public int stepsThisTick() {
        this.speedAccumulator += this.speed;
        int steps = (int) this.speedAccumulator;
        this.speedAccumulator -= steps;
        return steps;
    }

    public float playbackFraction() {
        return (float) this.speedAccumulator;
    }

    public void advance() {
        this.tick.incrementAndGet();
    }

    public void retreat() {
        this.tick.updateAndGet(value -> Math.max(0, value - 1));
    }

    public boolean muted(UUID trackId) {
        return this.mutedTracks.contains(trackId);
    }

    public void setMuted(UUID trackId, boolean muted) {
        if (muted) {
            this.mutedTracks.add(trackId);
        } else {
            this.mutedTracks.remove(trackId);
        }
    }

    public int loopIndex() {
        return this.loopIndex;
    }

    public void incrementLoop() {
        this.loopIndex++;
    }

    public boolean canLoop() {
        return this.settings.loop()
            && (this.settings.loopCount() == -1 || this.loopIndex < this.settings.loopCount() - 1);
    }

    public boolean active() {
        return this.active;
    }

    public void stop() {
        this.active = false;
        this.phase = CyclePhase.STOPPING;
    }

    public StageSlot stageSlot() {
        return this.stageSlot;
    }

    public void setStageSlot(StageSlot stageSlot) {
        this.stageSlot = stageSlot;
    }

    public SceneReplayer sceneReplayer() {
        return this.sceneReplayer;
    }

    public void setSceneReplayer(SceneReplayer sceneReplayer) {
        this.sceneReplayer = sceneReplayer;
    }

    public SnapshotPasteJob pasteJob() {
        return this.pasteJob;
    }

    public void setPasteJob(SnapshotPasteJob pasteJob) {
        this.pasteJob = pasteJob;
    }

    public SnapshotPasteJob clearJob() {
        return this.clearJob;
    }

    public void setClearJob(SnapshotPasteJob clearJob) {
        this.clearJob = clearJob;
    }

    public boolean hasStage() {
        return this.stageSlot != null;
    }
}
