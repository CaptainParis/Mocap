package com.paris.mocap.scene;

import org.bukkit.Location;
import org.bukkit.World;

public final class StageSlot {
    private final int index;
    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int size;
    private final CaptureBounds captureBounds;

    public StageSlot(
        int index,
        World world,
        int originX,
        int originY,
        int originZ,
        int size,
        CaptureBounds captureBounds
    ) {
        this.index = index;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.size = size;
        this.captureBounds = captureBounds;
    }

    public int index() {
        return this.index;
    }

    public World world() {
        return this.world;
    }

    public int originX() {
        return this.originX;
    }

    public int originY() {
        return this.originY;
    }

    public int originZ() {
        return this.originZ;
    }

    public int size() {
        return this.size;
    }

    public CaptureBounds captureBounds() {
        return this.captureBounds;
    }

    public double offsetX() {
        return this.originX - this.captureBounds.minX();
    }

    public double offsetY() {
        return this.originY - this.captureBounds.minY();
    }

    public double offsetZ() {
        return this.originZ - this.captureBounds.minZ();
    }

    public int absX(int relX) {
        return this.originX + relX;
    }

    public int absY(int relY) {
        return this.originY + relY;
    }

    public int absZ(int relZ) {
        return this.originZ + relZ;
    }

    public Location toLocation(double relX, double relY, double relZ, float yaw, float pitch) {
        return new Location(
            this.world,
            this.originX + relX,
            this.originY + relY,
            this.originZ + relZ,
            yaw,
            pitch
        );
    }

    public Location spawnLocation() {
        return new Location(
            this.world,
            this.originX + this.captureBounds.sizeX() * 0.5,
            this.originY + Math.min(80, this.captureBounds.sizeY() * 0.5),
            this.originZ + this.captureBounds.sizeZ() * 0.5
        );
    }

    public static StageSlot identity(World world) {
        return identity(world, new CaptureBounds(world.getName(), 0, 0, 0, 0, 0, 0));
    }

    public static StageSlot identity(World world, CaptureBounds captureBounds) {
        CaptureBounds bounds = captureBounds == null
            ? new CaptureBounds(world.getName(), 0, 0, 0, 0, 0, 0)
            : captureBounds;
        return new StageSlot(-1, world, bounds.minX(), bounds.minY(), bounds.minZ(), 1, bounds);
    }

    public boolean identityRemap() {
        return this.index < 0;
    }
}
