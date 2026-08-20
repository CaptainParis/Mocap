package com.paris.mocap.api.event;

import com.paris.mocap.model.Recording;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MocapRecordingStopEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Recording recording;

    public MocapRecordingStopEvent(@Nullable Player player, Recording recording) {
        this.player = player;
        this.recording = recording;
    }

    @Nullable
    public Player getPlayer() {
        return this.player;
    }

    public Recording getRecording() {
        return this.recording;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
