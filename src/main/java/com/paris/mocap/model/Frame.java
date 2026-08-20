package com.paris.mocap.model;

import java.util.Arrays;

public final class Frame {
    private static final ActionData[] EMPTY = new ActionData[0];

    private final Pose pose;
    private final ActionData[] actions;
    private final int pingMs;

    public Frame(Pose pose) {
        this(pose, EMPTY, 0);
    }

    public Frame(Pose pose, int pingMs) {
        this(pose, EMPTY, pingMs);
    }

    public Frame(Pose pose, ActionData[] actions) {
        this(pose, actions, 0);
    }

    public Frame(Pose pose, ActionData[] actions, int pingMs) {
        this.pose = pose;
        this.actions = actions == null || actions.length == 0 ? EMPTY : actions;
        this.pingMs = Math.max(0, pingMs);
    }

    public Pose pose() {
        return this.pose;
    }

    public ActionData[] actions() {
        return this.actions;
    }

    public int pingMs() {
        return this.pingMs;
    }

    public Frame withPose(Pose pose) {
        return new Frame(pose, this.actions, this.pingMs);
    }

    public Frame withPing(int pingMs) {
        return new Frame(this.pose, this.actions, pingMs);
    }

    public Frame withAction(ActionData action) {
        ActionData[] next = Arrays.copyOf(this.actions, this.actions.length + 1);
        next[this.actions.length] = action;
        return new Frame(this.pose, next, this.pingMs);
    }
}
