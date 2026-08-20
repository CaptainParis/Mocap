package com.paris.mocap.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorldScene {
    private final CaptureBounds bounds;
    private final WorldSnapshot snapshot;
    private final List<EntitySnapshot> entities;
    private final List<SceneEvent> events = new ArrayList<>();

    public WorldScene(CaptureBounds bounds, WorldSnapshot snapshot, List<EntitySnapshot> entities) {
        this.bounds = bounds;
        this.snapshot = snapshot;
        this.entities = List.copyOf(entities);
    }

    public CaptureBounds bounds() {
        return this.bounds;
    }

    public WorldSnapshot snapshot() {
        return this.snapshot;
    }

    public List<EntitySnapshot> entities() {
        return this.entities;
    }

    public List<SceneEvent> events() {
        return Collections.unmodifiableList(this.events);
    }

    public void addEvent(SceneEvent event) {
        this.events.add(event);
    }

    public void addEvents(List<SceneEvent> more) {
        this.events.addAll(more);
    }

    public void setEvents(List<SceneEvent> events) {
        this.events.clear();
        this.events.addAll(events);
    }

    public int maxEventTick() {
        int max = 0;
        for (SceneEvent event : this.events) {
            if (event.tick() > max) {
                max = event.tick();
            }
        }
        return max;
    }
}
