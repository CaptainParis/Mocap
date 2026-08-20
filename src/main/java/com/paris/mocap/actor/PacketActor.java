package com.paris.mocap.actor;

import com.paris.mocap.model.Pose;
import com.paris.mocap.model.VisibilityMode;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PacketActor {
    private final int entityId;
    private final UUID uniqueId;
    private final String name;
    private final String profileName;
    private final String skinTexture;
    private final String skinSignature;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final LocationScratch scratch = new LocationScratch();
    private volatile Set<UUID> audience;

    private volatile Pose pose;
    private volatile VisibilityMode visibilityMode = VisibilityMode.ALL;
    private volatile boolean sneaking;
    private volatile boolean sprinting;
    private volatile boolean swimming;
    private volatile boolean gliding;
    private volatile boolean sleeping;
    private volatile boolean nametagHidden;
    private volatile byte skinParts = (byte) 0x7F;
    private volatile float entityReach = 3.0F;
    private volatile int pingMs;
    private final ItemStack[] equipment = new ItemStack[EquipmentSlot.values().length];

    PacketActor(
        int entityId,
        UUID uniqueId,
        String name,
        String skinTexture,
        String skinSignature,
        Pose spawnPose
    ) {
        this.entityId = entityId;
        this.uniqueId = uniqueId;
        this.name = name;
        this.profileName = uniqueProfileName(entityId);
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.pose = spawnPose;
    }

    public int entityId() {
        return this.entityId;
    }

    public UUID uniqueId() {
        return this.uniqueId;
    }

    public String name() {
        return this.name;
    }

    /**
     * GameProfile / scoreboard entry name. Unique so actors do not inherit a real player's
     * {@code NAME_TAG_VISIBILITY=NEVER} team (teams are keyed by name, not UUID).
     */
    public String profileName() {
        return this.profileName;
    }

    public String teamName() {
        String id = "mc" + Integer.toHexString(this.entityId);
        return id.length() <= 16 ? id : id.substring(0, 16);
    }

    private static String uniqueProfileName(int entityId) {
        String name = "m" + Integer.toUnsignedString(entityId, 36);
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    public String skinTexture() {
        return this.skinTexture;
    }

    public String skinSignature() {
        return this.skinSignature;
    }

    public Pose pose() {
        return this.pose;
    }

    public void setPose(Pose pose) {
        this.pose = pose;
    }

    public boolean setPoseIfChanged(Pose pose, float epsilon) {
        Pose current = this.pose;
        if (current != null && current.nearlyEquals(pose, epsilon)) {
            return false;
        }
        this.pose = pose;
        return true;
    }

    public VisibilityMode visibilityMode() {
        return this.visibilityMode;
    }

    public void setVisibilityMode(VisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode;
    }

    public boolean sneaking() {
        return this.sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean sprinting() {
        return this.sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean swimming() {
        return this.swimming;
    }

    public void setSwimming(boolean swimming) {
        this.swimming = swimming;
    }

    public boolean gliding() {
        return this.gliding;
    }

    public void setGliding(boolean gliding) {
        this.gliding = gliding;
    }

    public boolean sleeping() {
        return this.sleeping;
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
    }

    public boolean nametagHidden() {
        return this.nametagHidden;
    }

    public void setNametagHidden(boolean nametagHidden) {
        this.nametagHidden = nametagHidden;
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
        this.entityReach = entityReach;
    }

    public int pingMs() {
        return this.pingMs;
    }

    public void setPingMs(int pingMs) {
        this.pingMs = Math.max(0, pingMs);
    }

    public double eyeHeight() {
        if (this.sleeping || this.swimming || this.gliding) {
            return 0.4;
        }
        if (this.sneaking) {
            return 1.27;
        }
        return 1.62;
    }

    public double hitboxHeight() {
        if (this.sleeping || this.swimming || this.gliding) {
            return 0.6;
        }
        if (this.sneaking) {
            return 1.5;
        }
        return 1.8;
    }

    public double hitboxWidth() {
        return 0.6;
    }

    public void setEquipment(EquipmentSlot slot, ItemStack item) {
        this.equipment[slot.ordinal()] = item == null ? null : item.clone();
    }

    public ItemStack equipment(EquipmentSlot slot) {
        return this.equipment[slot.ordinal()];
    }

    public ItemStack[] equipmentSnapshot() {
        return this.equipment.clone();
    }

    public Set<UUID> viewers() {
        return Collections.unmodifiableSet(this.viewers);
    }

    public boolean addViewer(UUID playerId) {
        return this.viewers.add(playerId);
    }

    public boolean removeViewer(UUID playerId) {
        return this.viewers.remove(playerId);
    }

    public void clearViewers() {
        this.viewers.clear();
    }

    public void restrictTo(UUID playerId) {
        this.audience = playerId == null ? null : Set.of(playerId);
    }

    public boolean isVisibleTo(Player player) {
        Set<UUID> allowed = this.audience;
        if (allowed != null && !allowed.contains(player.getUniqueId())) {
            return false;
        }
        return switch (this.visibilityMode) {
            case ALL -> true;
            case OPS_ONLY -> player.isOp();
            case NO_OPS -> !player.isOp();
        };
    }

    public LocationScratch scratch() {
        return this.scratch;
    }

    public static final class LocationScratch {
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        public void set(Pose pose) {
            this.world = pose.world();
            this.x = pose.x();
            this.y = pose.y();
            this.z = pose.z();
            this.yaw = pose.yaw();
            this.pitch = pose.pitch();
        }
    }
}
