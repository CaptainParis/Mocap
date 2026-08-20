package com.paris.mocap.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SceneReplayer {
    private final JavaPlugin plugin;
    private final WorldScene scene;
    private final StageSlot slot;
    private static final double SNAP_DISTANCE_SQ = 4.0;

    private final Map<Integer, Entity> entities = new HashMap<>();
    private final Map<Integer, Motion> motions = new HashMap<>();
    private final Set<Long> dirtyCells = new HashSet<>();
    private final Map<Long, String> originalCells = new HashMap<>();
    private final List<Long> restoreQueue = new ArrayList<>();
    private int eventIndex;
    private int lastTick = -1;
    private RestoreMode restoreMode = RestoreMode.IDLE;
    private boolean respawnAfterRestore = true;
    private boolean driveKinematic;

    public SceneReplayer(JavaPlugin plugin, WorldScene scene, StageSlot slot) {
        this.plugin = plugin;
        this.scene = scene;
        this.slot = slot;
    }

    public void setupInitialEntities() {
        for (EntitySnapshot snap : this.scene.entities()) {
            spawn(
                snap.captureId(),
                snap.entityType(),
                snap.relX(),
                snap.relY(),
                snap.relZ(),
                snap.yaw(),
                snap.pitch(),
                0,
                0,
                0,
                snap.health(),
                false
            );
        }
    }

    public void applyTick(int tick, int eventBudget) {
        applyTick(tick, eventBudget, false);
    }

    public void applyTick(int tick, int eventBudget, boolean kinematic) {
        this.driveKinematic = kinematic;
        List<SceneEvent> events = this.scene.events();
        int remaining = eventBudget;
        while (remaining-- > 0 && this.eventIndex < events.size() && events.get(this.eventIndex).tick() <= tick) {
            try {
                apply(events.get(this.eventIndex));
            } catch (Throwable fault) {
                this.plugin.getLogger().log(Level.FINE, "Scene event skipped at tick " + tick, fault);
            }
            this.eventIndex++;
        }
        this.lastTick = tick;
        if (!kinematic) {
            freezeMotion();
        }
    }

    public void rewindTo(int tick) {
        this.driveKinematic = false;
        List<SceneEvent> events = this.scene.events();
        Set<Long> cells = new HashSet<>();
        Set<Integer> moved = new HashSet<>();
        Set<Integer> spawned = new HashSet<>();
        Set<Integer> removed = new HashSet<>();
        while (this.eventIndex > 0 && events.get(this.eventIndex - 1).tick() >= tick) {
            this.eventIndex--;
            SceneEvent event = events.get(this.eventIndex);
            switch (event.type()) {
                case BLOCK_SET, BLOCK_BREAK, EXPLOSION ->
                    cells.add(WorldSnapshot.pack(event.relX(), event.relY(), event.relZ()));
                case ENTITY_SPAWN, PROJECTILE_SPAWN -> {
                    removeCaptureEntity(event.entityId());
                    spawned.add(event.entityId());
                }
                case ENTITY_MOVE -> moved.add(event.entityId());
                case ENTITY_REMOVE, ENTITY_DEATH -> removed.add(event.entityId());
                default -> {
                }
            }
        }
        for (long key : cells) {
            restoreBlockToTick(key, tick - 1);
        }
        for (int id : removed) {
            if (!spawned.contains(id)) {
                respawnFromHistory(id, tick - 1);
            }
        }
        for (int id : moved) {
            if (!spawned.contains(id)) {
                restoreEntityMove(id, tick - 1);
            }
        }
        this.driveKinematic = false;
        freezeMotion();
        this.lastTick = tick - 1;
    }

    public void interpolate(int nextTick, float fraction) {
        this.driveKinematic = true;
        float t = Math.max(0f, Math.min(1f, fraction));
        for (Map.Entry<Integer, Entity> entry : this.entities.entrySet()) {
            Entity entity = entry.getValue();
            if (!isPhysicsBody(entity) || !entity.isValid()) {
                continue;
            }
            Motion from = this.motions.get(entry.getKey());
            if (from == null) {
                continue;
            }
            SceneEvent to = nextPose(entry.getKey(), nextTick);
            float x = from.x;
            float y = from.y;
            float z = from.z;
            float yaw = from.yaw;
            float pitch = from.pitch;
            float vx = from.vx;
            float vy = from.vy;
            float vz = from.vz;
            if (to != null && t > 0f) {
                x = from.x + (to.posX() - from.x) * t;
                y = from.y + (to.posY() - from.y) * t;
                z = from.z + (to.posZ() - from.z) * t;
                yaw = from.yaw + (to.yaw() - from.yaw) * t;
                pitch = from.pitch + (to.pitch() - from.pitch) * t;
                vx = from.vx + (to.velX() - from.vx) * t;
                vy = from.vy + (to.velY() - from.vy) * t;
                vz = from.vz + (to.velZ() - from.vz) * t;
            }
            driveEntity(
                entity,
                this.slot.toLocation(x, y, z, yaw, pitch),
                vx,
                vy,
                vz,
                false
            );
        }
    }

    public void freezeMotion() {
        Vector zero = new Vector(0, 0, 0);
        for (Entity entity : this.entities.values()) {
            if (entity == null || !entity.isValid()) {
                continue;
            }
            freezeOne(entity, zero);
        }
    }

    private static void freezeOne(Entity entity, Vector zero) {
        entity.setGravity(false);
        entity.setVelocity(zero);
        entity.setFallDistance(0f);
    }

    private void respawnFromHistory(int captureId, int tick) {
        List<SceneEvent> events = this.scene.events();
        SceneEvent spawn = null;
        SceneEvent lastMove = null;
        for (int i = 0; i < this.eventIndex; i++) {
            SceneEvent event = events.get(i);
            if (event.tick() > tick) {
                break;
            }
            if (event.entityId() != captureId) {
                continue;
            }
            if (event.type() == SceneEventType.ENTITY_SPAWN
                    || event.type() == SceneEventType.PROJECTILE_SPAWN) {
                spawn = event;
                lastMove = event;
            } else if (event.type() == SceneEventType.ENTITY_MOVE) {
                lastMove = event;
            }
        }
        if (spawn == null) {
            return;
        }
        boolean projectile = spawn.type() == SceneEventType.PROJECTILE_SPAWN;
        spawn(
            captureId,
            spawn.entityType(),
            spawn.posX(),
            spawn.posY(),
            spawn.posZ(),
            spawn.yaw(),
            spawn.pitch(),
            lastMove == null ? spawn.velX() : lastMove.velX(),
            lastMove == null ? spawn.velY() : lastMove.velY(),
            lastMove == null ? spawn.velZ() : lastMove.velZ(),
            -1,
            projectile
        );
        if (lastMove != null && lastMove != spawn) {
            moveEntity(SceneEvent.entityMove(
                lastMove.tick(),
                captureId,
                lastMove.posX(),
                lastMove.posY(),
                lastMove.posZ(),
                lastMove.yaw(),
                lastMove.pitch(),
                lastMove.velX(),
                lastMove.velY(),
                lastMove.velZ()
            ), true);
        }
    }

    public boolean caughtUp(int tick) {
        List<SceneEvent> events = this.scene.events();
        if (this.eventIndex >= events.size()) {
            return true;
        }
        return events.get(this.eventIndex).tick() > tick;
    }

    public boolean beginCycleReset() {
        return beginBlockRestore(true);
    }

    public boolean beginBlockRestore(boolean respawnEntities) {
        clearEntities();
        this.eventIndex = 0;
        this.lastTick = -1;
        this.restoreQueue.clear();
        this.restoreQueue.addAll(this.dirtyCells);
        this.restoreMode = RestoreMode.BLOCKS;
        this.respawnAfterRestore = respawnEntities;
        return this.restoreQueue.isEmpty() && finishRestore();
    }

    public boolean tickCycleReset(int blockBudget) {
        if (this.restoreMode == RestoreMode.IDLE) {
            return true;
        }
        if (this.restoreMode == RestoreMode.BLOCKS) {
            int remaining = blockBudget;
            Iterator<Long> iterator = this.restoreQueue.iterator();
            while (remaining-- > 0 && iterator.hasNext()) {
                long key = iterator.next();
                iterator.remove();
                restoreCell(key);
            }
            if (this.restoreQueue.isEmpty()) {
                return finishRestore();
            }
            return false;
        }
        return true;
    }

    public void clearEntities() {
        for (Entity entity : this.entities.values()) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        this.entities.clear();
        this.motions.clear();
    }

    private boolean finishRestore() {
        this.dirtyCells.clear();
        this.restoreQueue.clear();
        this.restoreMode = RestoreMode.IDLE;
        if (this.respawnAfterRestore) {
            setupInitialEntities();
        }
        return true;
    }

    private void restoreCell(long key) {
        int x = (int) ((key >>> 42) & 0x1FFFFF);
        int y = (int) ((key >>> 21) & 0x1FFFFF);
        int z = (int) (key & 0x1FFFFF);
        setBlock(x, y, z, baselineData(x, y, z));
    }

    private void restoreBlockToTick(long key, int tick) {
        int x = (int) ((key >>> 42) & 0x1FFFFF);
        int y = (int) ((key >>> 21) & 0x1FFFFF);
        int z = (int) (key & 0x1FFFFF);
        String data = baselineData(x, y, z);
        List<SceneEvent> events = this.scene.events();
        for (int i = 0; i < this.eventIndex; i++) {
            SceneEvent event = events.get(i);
            if (event.tick() > tick) {
                break;
            }
            if (event.relX() != x || event.relY() != y || event.relZ() != z) {
                continue;
            }
            if (event.type() == SceneEventType.BLOCK_SET) {
                data = event.blockData() == null ? "minecraft:air" : event.blockData();
            } else if (event.type() == SceneEventType.BLOCK_BREAK) {
                data = "minecraft:air";
            }
        }
        setBlock(x, y, z, data);
    }

    private void restoreEntityMove(int captureId, int tick) {
        List<SceneEvent> events = this.scene.events();
        SceneEvent last = null;
        for (int i = 0; i < this.eventIndex; i++) {
            SceneEvent event = events.get(i);
            if (event.tick() > tick) {
                break;
            }
            if (event.entityId() != captureId) {
                continue;
            }
            if (event.type() == SceneEventType.ENTITY_MOVE
                    || event.type() == SceneEventType.ENTITY_SPAWN
                    || event.type() == SceneEventType.PROJECTILE_SPAWN) {
                last = event;
            }
        }
        if (last != null) {
            moveEntity(SceneEvent.entityMove(
                last.tick(),
                captureId,
                last.posX(),
                last.posY(),
                last.posZ(),
                last.yaw(),
                last.pitch(),
                last.velX(),
                last.velY(),
                last.velZ()
            ), true);
        }
    }

    private void markDirty(int relX, int relY, int relZ) {
        rememberOriginal(relX, relY, relZ);
        this.dirtyCells.add(WorldSnapshot.pack(relX, relY, relZ));
    }

    private void rememberOriginal(int relX, int relY, int relZ) {
        this.originalCells.computeIfAbsent(WorldSnapshot.pack(relX, relY, relZ), ignored -> readCell(relX, relY, relZ));
    }

    private String baselineData(int relX, int relY, int relZ) {
        String original = this.originalCells.get(WorldSnapshot.pack(relX, relY, relZ));
        if (original != null) {
            return original;
        }
        return this.scene.snapshot().blockDataAt(relX, relY, relZ);
    }

    private String readCell(int relX, int relY, int relZ) {
        Block block = this.slot.world().getBlockAt(
            this.slot.absX(relX),
            this.slot.absY(relY),
            this.slot.absZ(relZ)
        );
        BlockData data = block.getBlockData();
        if (data == null || data.getMaterial().isAir()) {
            return "minecraft:air";
        }
        return data.getAsString();
    }

    private void apply(SceneEvent event) {
        switch (event.type()) {
            case BLOCK_SET -> {
                markDirty(event.relX(), event.relY(), event.relZ());
                setBlock(event.relX(), event.relY(), event.relZ(), event.blockData());
            }
            case BLOCK_BREAK -> {
                markDirty(event.relX(), event.relY(), event.relZ());
                setBlock(event.relX(), event.relY(), event.relZ(), "minecraft:air");
            }
            case EXPLOSION -> {
                markDirty(event.relX(), event.relY(), event.relZ());
                Location at = this.slot.toLocation(event.relX() + 0.5, event.relY() + 0.5, event.relZ() + 0.5, 0, 0);
                this.slot.world().createExplosion(at, Math.max(0.5F, event.power()), false, true);
            }
            case ENTITY_SPAWN -> spawn(
                event.entityId(),
                event.entityType(),
                event.posX(),
                event.posY(),
                event.posZ(),
                event.yaw(),
                event.pitch(),
                event.velX(),
                event.velY(),
                event.velZ(),
                -1,
                false
            );
            case PROJECTILE_SPAWN -> spawn(
                event.entityId(),
                event.entityType(),
                event.posX(),
                event.posY(),
                event.posZ(),
                event.yaw(),
                event.pitch(),
                event.velX(),
                event.velY(),
                event.velZ(),
                -1,
                true
            );
            case ENTITY_MOVE -> moveEntity(event, false);
            case ENTITY_DAMAGE -> {
                Entity entity = this.entities.get(event.entityId());
                if (entity instanceof LivingEntity living && living.isValid()) {
                    living.setNoDamageTicks(0);
                    living.damage(Math.max(0.01, event.amount()));
                }
                if (event.otherEntityId() >= 0) {
                    removeCaptureEntity(event.otherEntityId());
                }
            }
            case ENTITY_DEATH -> {
                Entity entity = this.entities.get(event.entityId());
                if (entity instanceof LivingEntity living && living.isValid()) {
                    living.setHealth(0);
                } else {
                    removeCaptureEntity(event.entityId());
                }
            }
            case ENTITY_REMOVE -> removeCaptureEntity(event.entityId());
        }
    }

    private void moveEntity(SceneEvent event, boolean snap) {
        Entity entity = this.entities.get(event.entityId());
        if (entity == null || !entity.isValid()) {
            return;
        }
        Location at = this.slot.toLocation(event.posX(), event.posY(), event.posZ(), event.yaw(), event.pitch());
        driveEntity(entity, at, event.velX(), event.velY(), event.velZ(), snap);
        rememberMotion(
            event.entityId(),
            event.posX(),
            event.posY(),
            event.posZ(),
            event.yaw(),
            event.pitch(),
            event.velX(),
            event.velY(),
            event.velZ()
        );
        if (entity instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    private void setBlock(int relX, int relY, int relZ, String data) {
        try {
            BlockData blockData = Bukkit.createBlockData(data == null ? "minecraft:air" : data);
            Block block = this.slot.world().getBlockAt(this.slot.absX(relX), this.slot.absY(relY), this.slot.absZ(relZ));
            block.setBlockData(blockData, false);
        } catch (IllegalArgumentException ex) {
            this.plugin.getLogger().log(Level.FINE, "Bad block data during replay: " + data, ex);
        }
    }

    private void spawn(
        int captureId,
        String typeName,
        double relX,
        double relY,
        double relZ,
        float yaw,
        float pitch,
        float velX,
        float velY,
        float velZ,
        float health,
        boolean projectile
    ) {
        Entity existing = this.entities.get(captureId);
        if (existing != null && existing.isValid()) {
            moveEntity(SceneEvent.entityMove(
                0, captureId, (float) relX, (float) relY, (float) relZ, yaw, pitch, velX, velY, velZ
            ), true);
            return;
        }
        EntityType type;
        ItemStack dropped = null;
        if (typeName != null && typeName.startsWith("ITEM:")) {
            type = EntityType.ITEM;
            dropped = parseDroppedItem(typeName);
        } else {
            try {
                type = EntityType.valueOf(typeName);
            } catch (IllegalArgumentException | NullPointerException ex) {
                return;
            }
        }
        if (type == EntityType.PLAYER) {
            return;
        }
        Location at = this.slot.toLocation(relX, relY, relZ, yaw, pitch);
        Entity entity;
        if (type == EntityType.ITEM) {
            ItemStack stack = dropped == null ? new ItemStack(Material.STONE) : dropped;
            entity = this.slot.world().spawn(at, Item.class, item -> item.setItemStack(stack));
        } else if (!type.isSpawnable()) {
            return;
        } else {
            entity = this.slot.world().spawnEntity(at, type);
        }
        if (projectile && entity instanceof Projectile shot) {
            shot.setShooter(null);
        }
        configureReplayEntity(entity);
        driveEntity(entity, at, velX, velY, velZ, true);
        rememberMotion(captureId, (float) relX, (float) relY, (float) relZ, yaw, pitch, velX, velY, velZ);

        if (entity instanceof LivingEntity living && health > 0) {
            var attribute = living.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null && health > attribute.getValue()) {
                attribute.setBaseValue(health);
            }
            living.setHealth(Math.min(health, living.getMaxHealth()));
        }
        this.entities.put(captureId, entity);
    }

    private void configureReplayEntity(Entity entity) {
        entity.setPersistent(true);
        entity.setSilent(true);
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Projectile projectile) {
            projectile.setShooter(null);
        }
        if (entity instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCritical(false);
            arrow.setPierceLevel(0);
        }
        if (entity instanceof Explosive explosive) {
            explosive.setYield(0f);
            explosive.setIsIncendiary(false);
        }
        if (entity instanceof TNTPrimed tnt) {
            tnt.setFuseTicks(Integer.MAX_VALUE);
        }
        if (entity instanceof FallingBlock falling) {
            falling.setDropItem(false);
            falling.setHurtEntities(false);
        }
        if (entity instanceof Item item) {
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
        }
    }

    private void driveEntity(Entity entity, Location at, float velX, float velY, float velZ, boolean snap) {
        if (entity == null || !entity.isValid() || at == null) {
            return;
        }
        Vector recorded = new Vector(velX, velY, velZ);
        boolean physics = isPhysicsBody(entity);
        if (!this.driveKinematic || !physics) {
            entity.teleport(at);
            freezeOne(entity, new Vector(0, 0, 0));
            return;
        }
        Location now = entity.getLocation();
        double distSq = now.getWorld() == at.getWorld() ? now.distanceSquared(at) : Double.MAX_VALUE;
        if (snap || distSq > SNAP_DISTANCE_SQ) {
            entity.teleport(at);
            now = at;
        }
        entity.setRotation(at.getYaw(), at.getPitch());
        entity.setFallDistance(0f);
        Vector impulse = at.toVector().subtract(now.toVector());
        boolean resting = recorded.lengthSquared() < 1.0E-6 && impulse.lengthSquared() < 0.0025;
        if (resting) {
            entity.setGravity(true);
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }
        entity.setGravity(false);
        entity.setVelocity(impulse.lengthSquared() < 1.0E-8 ? recorded : impulse);
    }

    private void rememberMotion(
        int captureId,
        float x,
        float y,
        float z,
        float yaw,
        float pitch,
        float vx,
        float vy,
        float vz
    ) {
        this.motions.put(captureId, new Motion(x, y, z, yaw, pitch, vx, vy, vz));
    }

    private SceneEvent nextPose(int captureId, int nextTick) {
        List<SceneEvent> events = this.scene.events();
        for (int i = this.eventIndex; i < events.size(); i++) {
            SceneEvent event = events.get(i);
            if (event.entityId() != captureId) {
                continue;
            }
            if (event.tick() > nextTick + 40) {
                break;
            }
            if (event.type() == SceneEventType.ENTITY_MOVE
                    || event.type() == SceneEventType.ENTITY_SPAWN
                    || event.type() == SceneEventType.PROJECTILE_SPAWN) {
                return event;
            }
            if (event.type() == SceneEventType.ENTITY_REMOVE
                    || event.type() == SceneEventType.ENTITY_DEATH) {
                return null;
            }
        }
        return null;
    }

    private static boolean isPhysicsBody(Entity entity) {
        return entity instanceof Item
            || entity instanceof Projectile
            || entity instanceof FallingBlock
            || entity instanceof TNTPrimed;
    }

    private static ItemStack parseDroppedItem(String typeName) {
        String payload = typeName.substring("ITEM:".length());
        int star = payload.lastIndexOf('*');
        int amount = 1;
        String materialName = payload;
        if (star > 0) {
            materialName = payload.substring(0, star);
            try {
                amount = Math.max(1, Integer.parseInt(payload.substring(star + 1)));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            return new ItemStack(Material.STONE);
        }
        return new ItemStack(material, Math.min(material.getMaxStackSize(), amount));
    }

    private void removeCaptureEntity(int captureId) {
        this.motions.remove(captureId);
        Entity entity = this.entities.remove(captureId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private static final class Motion {
        private final float x;
        private final float y;
        private final float z;
        private final float yaw;
        private final float pitch;
        private final float vx;
        private final float vy;
        private final float vz;

        private Motion(float x, float y, float z, float yaw, float pitch, float vx, float vy, float vz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }
    }

    private enum RestoreMode {
        IDLE,
        BLOCKS
    }
}
