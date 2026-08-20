package com.paris.mocap.model;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ActionData {
    private final ActionType type;
    private final boolean flag;
    private final byte handState;
    private final EquipmentSlot slot;
    private final ItemStack item;
    private final Pose blockPose;
    private final String vehicleType;

    private ActionData(
        ActionType type,
        boolean flag,
        byte handState,
        EquipmentSlot slot,
        ItemStack item,
        Pose blockPose,
        String vehicleType
    ) {
        this.type = type;
        this.flag = flag;
        this.handState = handState;
        this.slot = slot;
        this.item = item;
        this.blockPose = blockPose;
        this.vehicleType = vehicleType;
    }

    public static ActionData swing(boolean offHand) {
        return new ActionData(ActionType.SWING, offHand, (byte) 0, null, null, null, null);
    }

    public static ActionData sneak(boolean sneaking) {
        return new ActionData(ActionType.SNEAK, sneaking, (byte) 0, null, null, null, null);
    }

    public static ActionData sprint(boolean sprinting) {
        return new ActionData(ActionType.SPRINT, sprinting, (byte) 0, null, null, null, null);
    }

    public static ActionData swim(boolean swimming) {
        return new ActionData(ActionType.SWIM, swimming, (byte) 0, null, null, null, null);
    }

    public static ActionData glide(boolean gliding) {
        return new ActionData(ActionType.GLIDE, gliding, (byte) 0, null, null, null, null);
    }

    public static ActionData block(boolean blocking) {
        return new ActionData(ActionType.BLOCK, blocking, (byte) 0, null, null, null, null);
    }

    public static ActionData chest(Pose pose, boolean opening) {
        return new ActionData(ActionType.CHEST, opening, (byte) 0, null, null, pose, null);
    }

    public static ActionData fishing(boolean casting) {
        return new ActionData(ActionType.FISHING, casting, (byte) 0, null, null, null, null);
    }

    public static ActionData vehicle(String entityTypeName, boolean entering) {
        return new ActionData(ActionType.VEHICLE, entering, (byte) 0, null, null, null, entityTypeName);
    }

    public static ActionData useItem(byte handState) {
        return new ActionData(ActionType.USE_ITEM, false, handState, null, null, null, null);
    }

    public static ActionData sleep(boolean sleeping, Pose bedPose) {
        return new ActionData(ActionType.SLEEP, sleeping, (byte) 0, null, null, bedPose, null);
    }

    public static ActionData totem() {
        return new ActionData(ActionType.TOTEM, false, (byte) 0, null, null, null, null);
    }

    public static ActionData equipment(EquipmentSlot slot, ItemStack item) {
        ItemStack clone = item == null ? null : item.clone();
        return new ActionData(ActionType.EQUIPMENT, false, (byte) 0, slot, clone, null, null);
    }

    public ActionType type() {
        return this.type;
    }

    public boolean flag() {
        return this.flag;
    }

    public byte handState() {
        return this.handState;
    }

    public EquipmentSlot slot() {
        return this.slot;
    }

    public ItemStack item() {
        return this.item;
    }

    public Pose blockPose() {
        return this.blockPose;
    }

    public String vehicleType() {
        return this.vehicleType;
    }
}
