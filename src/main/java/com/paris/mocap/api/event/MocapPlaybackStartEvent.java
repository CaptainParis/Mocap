package com.paris.mocap.api.event;

import com.paris.mocap.model.Recording;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MocapPlaybackStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player viewer;
    private final Recording recording;
    private final String trackPlayerName;
    private boolean cancelled;

    public MocapPlaybackStartEvent(@Nullable Player viewer, Recording recording, @Nullable String trackPlayerName) {
        this.viewer = viewer;
        this.recording = recording;
        this.trackPlayerName = trackPlayerName;
    }

    @Nullable
    public Player getViewer() {
        return this.viewer;
    }

    public Recording getRecording() {
        return this.recording;
    }

    @Nullable
    public String getTrackPlayerName() {
        return this.trackPlayerName;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
