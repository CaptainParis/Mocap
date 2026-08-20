package com.paris.mocap.playback;

import com.paris.mocap.actor.ActorPacketService;
import com.paris.mocap.actor.PacketActor;
import com.paris.mocap.cycle.CyclePhase;
import com.paris.mocap.model.ActionData;
import com.paris.mocap.model.Frame;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.Track;
import com.paris.mocap.runtime.FailForward;
import com.paris.mocap.runtime.TickBudget;
import com.paris.mocap.scene.SceneReplayer;
import com.paris.mocap.scene.SnapshotPasteJob;
import com.paris.mocap.scene.StageSlot;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlaybackEngine implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ActorPacketService packets;
    private final ActionApplier actions;
    private final FailForward failForward;
    private final TickBudget budget;
    private final Consumer<PlaybackSession> onPrepared;
    private final Consumer<PlaybackSession> onComplete;
    private final Consumer<PlaybackSession> onFinished;
    private final Map<UUID, PlaybackSession> sessions = new ConcurrentHashMap<>();
    private final ArrayList<Player> viewerScratch = new ArrayList<>(64);
    private BukkitTask task;

    public PlaybackEngine(
        JavaPlugin plugin,
        ActorPacketService packets,
        FailForward failForward,
        TickBudget budget,
        Consumer<PlaybackSession> onPrepared,
        Consumer<PlaybackSession> onComplete,
        Consumer<PlaybackSession> onFinished
    ) {
        this.plugin = plugin;
        this.packets = packets;
        this.actions = new ActionApplier(packets);
        this.failForward = failForward;
        this.budget = budget;
        this.onPrepared = onPrepared;
        this.onComplete = onComplete;
        this.onFinished = onFinished;
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    public void register(PlaybackSession session) {
        this.sessions.put(session.id(), session);
    }

    public void unregister(PlaybackSession session) {
        this.sessions.remove(session.id());
    }

    public Map<UUID, PlaybackSession> sessions() {
        return this.sessions;
    }

    private void tick() {
        if (this.sessions.isEmpty()) {
            return;
        }
        for (PlaybackSession session : this.sessions.values()) {
            if (!session.active() && session.phase() != CyclePhase.STOPPING) {
                this.sessions.remove(session.id());
                continue;
            }
            if (!session.circuit().allow()) {
                continue;
            }
            boolean ok = this.failForward.attempt("session:" + session.name(), () -> tickSession(session));
            if (ok) {
                session.circuit().success();
            } else {
                session.circuit().failure();
                if (session.circuit().justTripped()) {
                    this.plugin.getLogger().warning(
                        "Playback '" + session.name() + "' paused after repeated faults; it will retry shortly."
                    );
                }
            }
        }
    }

    private void tickSession(PlaybackSession session) {
        switch (session.phase()) {
            case PREPARING -> tickPreparing(session);
            case LOOPING, SEEKING -> tickReset(session);
            case PLAYING -> tickPlaying(session);
            case STOPPING -> tickStopping(session);
            default -> {
            }
        }
    }

    private void tickPreparing(PlaybackSession session) {
        SnapshotPasteJob job = session.pasteJob();
        if (job != null && !job.tick(this.budget.pasteBlocks())) {
            return;
        }
        session.setPasteJob(null);
        session.setPhase(CyclePhase.PLAYING);
        if (this.onPrepared != null) {
            this.onPrepared.accept(session);
        }
    }

    private void tickReset(PlaybackSession session) {
        SceneReplayer replayer = session.sceneReplayer();
        if (replayer != null && !replayer.tickCycleReset(this.budget.clearBlocks())) {
            return;
        }
        boolean seeking = session.phase() == CyclePhase.SEEKING;
        if (seeking) {
            if (replayer != null && !replayer.caughtUp(session.tick())) {
                replayer.applyTick(session.tick(), this.budget.sceneEvents());
                return;
            }
            poseActorsToTick(session, session.tick());
            session.setPhase(CyclePhase.PLAYING);
            return;
        }
        session.jumpTo(0);
        session.setPhase(CyclePhase.PLAYING);
        poseActorsToTick(session, 0);
    }

    private void tickPlaying(PlaybackSession session) {
        if (session.consumeResyncPoses()) {
            poseActorsToTick(session, session.tick());
        }
        if (session.consumeSeekFlag()) {
            SceneReplayer replayer = session.sceneReplayer();
            if (replayer != null) {
                session.setPhase(CyclePhase.SEEKING);
                replayer.beginCycleReset();
                return;
            }
            poseActorsToTick(session, session.tick());
            return;
        }
        if (session.paused()) {
            SceneReplayer replayer = session.sceneReplayer();
            if (replayer != null) {
                replayer.freezeMotion();
            }
            return;
        }
        int steps = session.stepsThisTick();
        boolean realtime = Math.abs(session.speed() - 1.0) < 0.001;
        if (steps > 0) {
            for (int step = 0; step < steps; step++) {
                if (!advanceOnce(session, realtime && steps == 1)) {
                    break;
                }
            }
        } else if (steps < 0) {
            for (int step = 0; step < -steps; step++) {
                if (!rewindOnce(session)) {
                    break;
                }
            }
        }
        float fraction = session.playbackFraction();
        if (steps == 0 || Math.abs(fraction) > 0.001f) {
            poseActorsInterpolated(session, session.tick(), fraction);
            SceneReplayer replayer = session.sceneReplayer();
            if (replayer != null && !realtime) {
                replayer.interpolate(session.tick(), fraction);
            }
        }
        if (steps == 0 && Math.abs(fraction) <= 0.001f) {
            SceneReplayer replayer = session.sceneReplayer();
            if (replayer != null) {
                replayer.freezeMotion();
            }
        }
    }

    private void tickStopping(PlaybackSession session) {
        SceneReplayer replayer = session.sceneReplayer();
        if (replayer != null && !replayer.tickCycleReset(this.budget.clearBlocks())) {
            return;
        }
        SnapshotPasteJob job = session.clearJob();
        if (job != null && !job.tick(this.budget.clearBlocks())) {
            return;
        }
        session.setClearJob(null);
        this.sessions.remove(session.id());
        if (this.onFinished != null) {
            this.onFinished.accept(session);
        }
    }

    private boolean advanceOnce(PlaybackSession session, boolean kinematic) {
        SceneReplayer sceneReplayer = session.sceneReplayer();
        int tick = session.tick();
        int duration = session.recording().durationTicks();
        if (tick > duration) {
            if (session.canLoop()) {
                session.incrementLoop();
                SceneReplayer replayer = session.sceneReplayer();
                if (replayer != null) {
                    session.setPhase(CyclePhase.LOOPING);
                    replayer.beginCycleReset();
                    return false;
                }
                session.jumpTo(0);
                poseActorsToTick(session, 0);
                return true;
            }
            if (this.onComplete != null) {
                this.onComplete.accept(session);
            }
            return false;
        }

        if (sceneReplayer != null) {
            sceneReplayer.applyTick(tick, this.budget.sceneEvents(), kinematic);
        }

        StageSlot slot = session.stageSlot();
        this.viewerScratch.clear();
        for (Map.Entry<UUID, Track> entry : session.tracks().entrySet()) {
            UUID trackId = entry.getKey();
            if (session.muted(trackId)) {
                continue;
            }
            PacketActor actor = session.actor(trackId);
            Track track = entry.getValue();
            if (actor == null || track == null) {
                continue;
            }
            Frame frame = frameAt(track, session.displayTick(track, tick));
            if (frame == null) {
                continue;
            }
            Frame clock = frameAt(track, tick);
            Pose pose = PlaybackService.transformPose(frame.pose(), slot);
            actor.setPingMs(clock == null ? frame.pingMs() : clock.pingMs());
            actor.setPose(pose);
            this.viewerScratch.clear();
            this.packets.collectViewers(actor, this.viewerScratch);
            if (!this.viewerScratch.isEmpty()) {
                this.packets.teleport(actor, this.viewerScratch);
            }
            int displayTick = session.displayTick(track, tick);
            if (track.getFrame(displayTick) != null) {
                for (ActionData action : frame.actions()) {
                    this.actions.apply(actor, action, this.viewerScratch);
                }
            }
        }
        session.advance();
        return true;
    }

    private boolean rewindOnce(PlaybackSession session) {
        int tick = session.tick();
        if (tick <= 0) {
            poseActorsToTick(session, 0);
            return false;
        }
        session.retreat();
        int now = session.tick();
        SceneReplayer sceneReplayer = session.sceneReplayer();
        if (sceneReplayer != null) {
            sceneReplayer.rewindTo(now);
        }
        poseActorsToTick(session, now);
        return true;
    }

    private static Frame frameAt(Track track, int tick) {
        Frame exact = track.getFrame(tick);
        return exact != null ? exact : track.floorFrame(tick);
    }

    private void poseActorsToTick(PlaybackSession session, int tick) {
        poseActorsInterpolated(session, tick, 0f);
    }

    private void poseActorsInterpolated(PlaybackSession session, int tick, float fraction) {
        StageSlot slot = session.stageSlot();
        for (Map.Entry<UUID, Track> entry : session.tracks().entrySet()) {
            PacketActor actor = session.actor(entry.getKey());
            Track track = entry.getValue();
            if (actor == null || track == null) {
                continue;
            }
            int displayTick = session.displayTick(track, tick);
            Frame clock = frameAt(track, tick);
            Pose pose = interpolatedPose(track, displayTick, fraction, slot);
            if (pose == null) {
                continue;
            }
            actor.setPose(pose);
            actor.setPingMs(clock == null ? 0 : clock.pingMs());
            this.viewerScratch.clear();
            this.packets.collectViewers(actor, this.viewerScratch);
            if (!this.viewerScratch.isEmpty()) {
                this.packets.teleport(actor, this.viewerScratch);
            }
        }
    }

    private static Pose interpolatedPose(Track track, int tick, float fraction, StageSlot slot) {
        double time = Math.max(0, tick + fraction);
        int index = track.floorIndex((int) Math.floor(time));
        if (index < 0) {
            return null;
        }
        Frame from = track.frameAt(index);
        if (from == null) {
            return null;
        }
        Pose start = PlaybackService.transformPose(from.pose(), slot);
        if (index + 1 >= track.size()) {
            return start;
        }
        int fromTick = track.tickAt(index);
        int toTick = track.tickAt(index + 1);
        Frame to = track.frameAt(index + 1);
        if (to == null || toTick <= fromTick) {
            return start;
        }
        float span = toTick - fromTick;
        float t = (float) ((time - fromTick) / span);
        if (t <= 0f) {
            return start;
        }
        if (t >= 1f) {
            return PlaybackService.transformPose(to.pose(), slot);
        }
        return Pose.lerp(start, PlaybackService.transformPose(to.pose(), slot), t);
    }

    @Override
    public void close() {
        if (this.task != null) {
            this.task.cancel();
        }
        this.sessions.clear();
    }
}
