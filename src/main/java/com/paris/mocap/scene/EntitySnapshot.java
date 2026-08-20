package com.paris.mocap.scene;

public final class EntitySnapshot {
    private final int captureId;
    private final String entityType;
    private final float relX;
    private final float relY;
    private final float relZ;
    private final float yaw;
    private final float pitch;
    private final float health;

    public EntitySnapshot(
        int captureId,
        String entityType,
        float relX,
        float relY,
        float relZ,
        float yaw,
        float pitch,
        float health
    ) {
        this.captureId = captureId;
        this.entityType = entityType;
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
    }

    public int captureId() {
        return this.captureId;
    }

    public String entityType() {
        return this.entityType;
    }

    public float relX() {
        return this.relX;
    }

    public float relY() {
        return this.relY;
    }

    public float relZ() {
        return this.relZ;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float health() {
        return this.health;
    }
}
