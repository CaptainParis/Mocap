package com.paris.mocap.scene;

public final class SceneEvent {
    private final int tick;
    private final SceneEventType type;
    private final int relX;
    private final int relY;
    private final int relZ;
    private final float posX;
    private final float posY;
    private final float posZ;
    private final float velX;
    private final float velY;
    private final float velZ;
    private final String blockData;
    private final float yaw;
    private final float pitch;
    private final float amount;
    private final int entityId;
    private final int otherEntityId;
    private final String entityType;
    private final float power;

    private SceneEvent(
        int tick,
        SceneEventType type,
        int relX,
        int relY,
        int relZ,
        float posX,
        float posY,
        float posZ,
        float velX,
        float velY,
        float velZ,
        String blockData,
        float yaw,
        float pitch,
        float amount,
        int entityId,
        int otherEntityId,
        String entityType,
        float power
    ) {
        this.tick = tick;
        this.type = type;
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.blockData = blockData;
        this.yaw = yaw;
        this.pitch = pitch;
        this.amount = amount;
        this.entityId = entityId;
        this.otherEntityId = otherEntityId;
        this.entityType = entityType;
        this.power = power;
    }

    public static SceneEvent blockSet(int tick, int relX, int relY, int relZ, String blockData) {
        return new SceneEvent(
            tick, SceneEventType.BLOCK_SET, relX, relY, relZ, 0, 0, 0, 0, 0, 0,
            blockData, 0, 0, 0, -1, -1, null, 0
        );
    }

    public static SceneEvent blockBreak(int tick, int relX, int relY, int relZ) {
        return new SceneEvent(
            tick, SceneEventType.BLOCK_BREAK, relX, relY, relZ, 0, 0, 0, 0, 0, 0,
            "minecraft:air", 0, 0, 0, -1, -1, null, 0
        );
    }

    public static SceneEvent explosion(int tick, int relX, int relY, int relZ, float power) {
        return new SceneEvent(
            tick, SceneEventType.EXPLOSION, relX, relY, relZ, 0, 0, 0, 0, 0, 0,
            null, 0, 0, 0, -1, -1, null, power
        );
    }

    public static SceneEvent entitySpawn(
        int tick,
        int entityId,
        String entityType,
        float posX,
        float posY,
        float posZ,
        float yaw,
        float pitch,
        float velX,
        float velY,
        float velZ
    ) {
        return new SceneEvent(
            tick, SceneEventType.ENTITY_SPAWN, 0, 0, 0, posX, posY, posZ, velX, velY, velZ,
            null, yaw, pitch, 0, entityId, -1, entityType, 0
        );
    }

    public static SceneEvent entityMove(
        int tick,
        int entityId,
        float posX,
        float posY,
        float posZ,
        float yaw,
        float pitch,
        float velX,
        float velY,
        float velZ
    ) {
        return new SceneEvent(
            tick, SceneEventType.ENTITY_MOVE, 0, 0, 0, posX, posY, posZ, velX, velY, velZ,
            null, yaw, pitch, 0, entityId, -1, null, 0
        );
    }

    public static SceneEvent entityDamage(int tick, int entityId, float amount, int damagerId) {
        return new SceneEvent(
            tick, SceneEventType.ENTITY_DAMAGE, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            null, 0, 0, amount, entityId, damagerId, null, 0
        );
    }

    public static SceneEvent entityDeath(int tick, int entityId) {
        return new SceneEvent(
            tick, SceneEventType.ENTITY_DEATH, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            null, 0, 0, 0, entityId, -1, null, 0
        );
    }

    public static SceneEvent entityRemove(int tick, int entityId) {
        return new SceneEvent(
            tick, SceneEventType.ENTITY_REMOVE, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            null, 0, 0, 0, entityId, -1, null, 0
        );
    }

    public static SceneEvent projectileSpawn(
        int tick,
        int entityId,
        String entityType,
        float posX,
        float posY,
        float posZ,
        float yaw,
        float pitch,
        float velX,
        float velY,
        float velZ
    ) {
        return new SceneEvent(
            tick, SceneEventType.PROJECTILE_SPAWN, 0, 0, 0, posX, posY, posZ, velX, velY, velZ,
            null, yaw, pitch, 0, entityId, -1, entityType, 0
        );
    }

    public int tick() {
        return this.tick;
    }

    public SceneEventType type() {
        return this.type;
    }

    public int relX() {
        return this.relX;
    }

    public int relY() {
        return this.relY;
    }

    public int relZ() {
        return this.relZ;
    }

    public float posX() {
        return this.posX;
    }

    public float posY() {
        return this.posY;
    }

    public float posZ() {
        return this.posZ;
    }

    public float velX() {
        return this.velX;
    }

    public float velY() {
        return this.velY;
    }

    public float velZ() {
        return this.velZ;
    }

    public String blockData() {
        return this.blockData;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float amount() {
        return this.amount;
    }

    public int entityId() {
        return this.entityId;
    }

    public int otherEntityId() {
        return this.otherEntityId;
    }

    public String entityType() {
        return this.entityType;
    }

    public float power() {
        return this.power;
    }

    public SceneEvent translated(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return this;
        }
        return new SceneEvent(
            this.tick,
            this.type,
            this.relX + dx,
            this.relY + dy,
            this.relZ + dz,
            this.posX + dx,
            this.posY + dy,
            this.posZ + dz,
            this.velX,
            this.velY,
            this.velZ,
            this.blockData,
            this.yaw,
            this.pitch,
            this.amount,
            this.entityId,
            this.otherEntityId,
            this.entityType,
            this.power
        );
    }
}
