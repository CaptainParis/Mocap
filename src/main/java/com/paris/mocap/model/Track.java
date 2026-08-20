package com.paris.mocap.model;

import java.util.Arrays;
import java.util.UUID;

public final class Track {
    public static final byte ALL_SKIN_PARTS = 0x7F;
    public static final float DEFAULT_ENTITY_REACH = 3.0F;

    private final UUID playerId;
    private final String playerName;
    private String skinTexture;
    private String skinSignature;
    private byte skinParts = ALL_SKIN_PARTS;
    private float entityReach = DEFAULT_ENTITY_REACH;

    private int[] ticks = new int[64];
    private Frame[] frames = new Frame[64];
    private int size;
    private int maxTick;

    public Track(UUID playerId, String playerName, String skinTexture, String skinSignature) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
    }

    public void putFrame(int tick, Frame frame) {
        int index = Arrays.binarySearch(this.ticks, 0, this.size, tick);
        if (index >= 0) {
            this.frames[index] = frame;
            return;
        }
        int insert = -index - 1;
        ensureCapacity(this.size + 1);
        if (insert < this.size) {
            System.arraycopy(this.ticks, insert, this.ticks, insert + 1, this.size - insert);
            System.arraycopy(this.frames, insert, this.frames, insert + 1, this.size - insert);
        }
        this.ticks[insert] = tick;
        this.frames[insert] = frame;
        this.size++;
        if (tick > this.maxTick) {
            this.maxTick = tick;
        }
    }

    public void addAction(int tick, ActionData action) {
        Frame existing = getFrame(tick);
        if (existing == null) {
            return;
        }
        putFrame(tick, existing.withAction(action));
    }

    public Frame getFrame(int tick) {
        int index = Arrays.binarySearch(this.ticks, 0, this.size, tick);
        return index >= 0 ? this.frames[index] : null;
    }

    public Frame floorFrame(int tick) {
        if (this.size == 0) {
            return null;
        }
        int index = Arrays.binarySearch(this.ticks, 0, this.size, tick);
        if (index >= 0) {
            return this.frames[index];
        }
        int insertion = -index - 1;
        if (insertion == 0) {
            return null;
        }
        return this.frames[insertion - 1];
    }

    public int floorIndex(int tick) {
        if (this.size == 0) {
            return -1;
        }
        int index = Arrays.binarySearch(this.ticks, 0, this.size, tick);
        if (index >= 0) {
            return index;
        }
        return -index - 2;
    }

    public int size() {
        return this.size;
    }

    public int maxTick() {
        return this.maxTick;
    }

    public int tickAt(int index) {
        return this.ticks[index];
    }

    public Frame frameAt(int index) {
        return this.frames[index];
    }

    public UUID playerId() {
        return this.playerId;
    }

    public String playerName() {
        return this.playerName;
    }

    public String skinTexture() {
        return this.skinTexture;
    }

    public String skinSignature() {
        return this.skinSignature;
    }

    public void setSkin(String texture, String signature) {
        this.skinTexture = texture;
        this.skinSignature = signature;
    }

    public byte skinParts() {
        return this.skinParts;
    }

    public void setSkinParts(byte skinParts) {
        this.skinParts = skinParts;
    }

    public float entityReach() {
        return this.entityReach;
    }

    public void setEntityReach(float entityReach) {
        this.entityReach = entityReach > 0.1F ? entityReach : DEFAULT_ENTITY_REACH;
    }

    private void ensureCapacity(int min) {
        if (min <= this.ticks.length) {
            return;
        }
        int next = Math.max(min, this.ticks.length << 1);
        this.ticks = Arrays.copyOf(this.ticks, next);
        this.frames = Arrays.copyOf(this.frames, next);
    }
}
