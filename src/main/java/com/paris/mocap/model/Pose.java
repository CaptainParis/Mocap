package com.paris.mocap.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Pose {
    private final String world;
    private final float x;
    private final float y;
    private final float z;
    private final float yaw;
    private final float pitch;

    public Pose(String world, float x, float y, float z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static Pose from(Location location) {
        return new Pose(
            location.getWorld().getName(),
            (float) location.getX(),
            (float) location.getY(),
            (float) location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    public Location toLocation() {
        World worldHandle = Bukkit.getWorld(this.world);
        if (worldHandle == null) {
            return null;
        }
        return new Location(worldHandle, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public void writeInto(Location target) {
        World worldHandle = Bukkit.getWorld(this.world);
        if (worldHandle == null) {
            return;
        }
        target.setWorld(worldHandle);
        target.setX(this.x);
        target.setY(this.y);
        target.setZ(this.z);
        target.setYaw(this.yaw);
        target.setPitch(this.pitch);
    }

    public String world() {
        return this.world;
    }

    public float x() {
        return this.x;
    }

    public float y() {
        return this.y;
    }

    public float z() {
        return this.z;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public Pose transformed(String targetWorld, double dx, double dy, double dz) {
        return new Pose(
            targetWorld,
            (float) (this.x + dx),
            (float) (this.y + dy),
            (float) (this.z + dz),
            this.yaw,
            this.pitch
        );
    }

    public boolean nearlyEquals(Pose other, float epsilon) {
        if (other == null) {
            return false;
        }
        if (this.world != other.world && (this.world == null || !this.world.equals(other.world))) {
            return false;
        }
        return Math.abs(this.x - other.x) <= epsilon
            && Math.abs(this.y - other.y) <= epsilon
            && Math.abs(this.z - other.z) <= epsilon
            && Math.abs(this.yaw - other.yaw) <= 0.15F
            && Math.abs(this.pitch - other.pitch) <= 0.15F;
    }

    public static Pose lerp(Pose from, Pose to, float t) {
        if (from == null) {
            return to;
        }
        if (to == null || t <= 0f) {
            return from;
        }
        if (t >= 1f) {
            return to;
        }
        return new Pose(
            from.world,
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t,
            from.yaw + wrapDegrees(to.yaw - from.yaw) * t,
            from.pitch + (to.pitch - from.pitch) * t
        );
    }

    private static float wrapDegrees(float delta) {
        delta %= 360f;
        if (delta >= 180f) {
            delta -= 360f;
        } else if (delta < -180f) {
            delta += 360f;
        }
        return delta;
    }
}
