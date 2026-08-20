package com.paris.mocap.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MocapRecordingStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recordingId;
    private boolean cancelled;

    public MocapRecordingStartEvent(Player player, String recordingId) {
        this.player = player;
        this.recordingId = recordingId;
    }

    public Player getPlayer() {
        return this.player;
    }

    public String getRecordingId() {
        return this.recordingId;
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
