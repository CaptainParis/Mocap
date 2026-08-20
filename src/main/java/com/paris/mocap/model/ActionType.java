package com.paris.mocap.model;

public enum ActionType {
    SWING,
    SNEAK,
    SPRINT,
    SWIM,
    GLIDE,
    EQUIPMENT,
    BLOCK,
    CHEST,
    FISHING,
    VEHICLE,
    USE_ITEM,
    SLEEP,
    TOTEM;

    private static final ActionType[] VALUES = values();

    public static ActionType byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("Unknown action id: " + id);
        }
        return VALUES[id];
    }
}
