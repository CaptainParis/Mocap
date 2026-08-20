package com.paris.mocap.api.event;

import com.paris.mocap.playback.PlaybackSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MocapPlaybackStopEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        FINISHED,
        STOPPED,
        DISABLED
    }

    private final PlaybackSession session;
    private final Reason reason;

    public MocapPlaybackStopEvent(PlaybackSession session, Reason reason) {
        this.session = session;
        this.reason = reason;
    }

    public PlaybackSession getSession() {
        return this.session;
    }

    public Reason getReason() {
        return this.reason;
    }

    @Nullable
    public Player getViewer() {
        return this.session.requester();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
