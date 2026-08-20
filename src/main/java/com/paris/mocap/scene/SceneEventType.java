package com.paris.mocap.scene;

public enum SceneEventType {
    BLOCK_SET,
    BLOCK_BREAK,
    EXPLOSION,
    ENTITY_SPAWN,
    ENTITY_MOVE,
    ENTITY_DAMAGE,
    ENTITY_DEATH,
    ENTITY_REMOVE,
    PROJECTILE_SPAWN;

    private static final SceneEventType[] VALUES = values();

    public static SceneEventType byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("Unknown scene event id: " + id);
        }
        return VALUES[id];
    }
}
