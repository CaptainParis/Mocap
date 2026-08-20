package com.paris.mocap.scene;

import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.runtime.FailForward;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class SceneCaptureService implements AutoCloseable {
    private static final long MAX_VOLUME = 2_000_000L;

    private final JavaPlugin plugin;
    private final MocapConfig config;
    private final RecordingSettings settings;
    private final IntSupplier tickSupplier;
    private final FailForward failForward;

    private final Map<String, WorldScene> activeScenes = new ConcurrentHashMap<>();
    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> entityIds = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> nextEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityToRecording = new ConcurrentHashMap<>();
    private final Map<String, WorldSnapshotJob> snapshotJobs = new ConcurrentHashMap<>();
    private final Map<String, List<EntitySnapshot>> pendingEntities = new ConcurrentHashMap<>();
    private final Map<UUID, float[]> lastEntitySample = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> liveEntities = new ConcurrentHashMap<>();
    private final Set<String> eventsOnly = ConcurrentHashMap.newKeySet();
    private final Map<String, List<PendingBlock>> pendingBlocks = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, String>> lastCellState = new ConcurrentHashMap<>();
    private final Map<String, UUID> packetSamplers = new ConcurrentHashMap<>();
    private final BlockUpdateCapture blockPackets;
    private BukkitTask ticker;
    private int sampleAge;

    public SceneCaptureService(
        JavaPlugin plugin,
        MocapConfig config,
        RecordingSettings settings,
        IntSupplier tickSupplier,
        FailForward failForward
    ) {
        this.plugin = plugin;
        this.config = config;
        this.settings = settings;
        this.tickSupplier = tickSupplier;
        this.failForward = failForward;
        this.blockPackets = new BlockUpdateCapture(plugin, this);
        this.blockPackets.register();
    }

    public void begin(Recording recording, Player recorder) {
        this.failForward.run("scene-begin:" + recording.id(), () -> beginInternal(recording, recorder));
    }

    private void beginInternal(Recording recording, Player recorder) {
        WorldCaptureMode mode = this.settings.worldCaptureMode();
        if (mode == WorldCaptureMode.OFF) {
            beginEventsOnly(recording, recorder.getLocation());
            return;
        }
        CaptureBounds bounds = resolveBounds(mode, recorder);
        if (bounds == null) {
            this.plugin.getLogger().warning(
                "World capture AREA needs a radius; using auto-box for " + recorder.getName()
            );
            bounds = CaptureBounds.fromCenterRadius(recorder.getLocation(), this.config.autoBoxRadius());
        }
        if (bounds.volume() > MAX_VOLUME) {
            this.plugin.getLogger().warning(
                "Capture volume " + bounds.volume() + " exceeds cap; shrinking to auto-box."
            );
            bounds = CaptureBounds.fromCenterRadius(recorder.getLocation(), this.config.autoBoxRadius());
        }

        World world = Bukkit.getWorld(bounds.world());
        if (world == null) {
            return;
        }

        List<EntitySnapshot> entities = new ArrayList<>();
        Map<UUID, Integer> ids = new ConcurrentHashMap<>();
        AtomicInteger nextId = new AtomicInteger(1);
        captureInitialEntities(world, bounds, recording.id(), entities, ids, nextId);

        this.recordings.put(recording.id(), recording);
        this.entityIds.put(recording.id(), ids);
        this.nextEntityId.put(recording.id(), nextId);
        this.pendingEntities.put(recording.id(), entities);
        this.snapshotJobs.put(recording.id(), new WorldSnapshotJob(bounds, world));

        WorldScene placeholder = new WorldScene(bounds, WorldSnapshot.builder().build(), entities);
        recording.setWorldScene(placeholder);
        this.activeScenes.put(recording.id(), placeholder);
        ensureTicker();

        this.plugin.getLogger().info(
            "World capture [" + mode.label() + "] for '" + recording.id()
                + "': scanning " + SnapshotPasteJob.chunksIn(bounds).size() + " chunks, "
                + entities.size() + " entities"
        );
    }

    private void beginEventsOnly(Recording recording, Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        int radius = Math.max(this.config.autoBoxRadius(), 256);
        CaptureBounds bounds = CaptureBounds.fromCenterRadius(at, radius);
        WorldScene scene = new WorldScene(bounds, WorldSnapshot.builder().build(), List.of());
        recording.setWorldScene(scene);
        this.activeScenes.put(recording.id(), scene);
        this.recordings.put(recording.id(), recording);
        this.eventsOnly.add(recording.id());
        this.pendingBlocks.putIfAbsent(recording.id(), new ArrayList<>());
        this.entityIds.putIfAbsent(recording.id(), new ConcurrentHashMap<>());
        this.nextEntityId.putIfAbsent(recording.id(), new AtomicInteger(1));
        ensureTicker();
    }

    public void end(String recordingId) {
        WorldSnapshotJob job = this.snapshotJobs.remove(recordingId);
        Recording recording = this.recordings.remove(recordingId);
        if (job != null && recording != null) {
            finishSnapshot(recordingId, recording, job, true);
        }
        bakePendingBlocks(recordingId, recording);
        this.eventsOnly.remove(recordingId);
        this.pendingBlocks.remove(recordingId);
        this.lastCellState.remove(recordingId);
        this.activeScenes.remove(recordingId);
        this.pendingEntities.remove(recordingId);
        Map<UUID, Integer> ids = this.entityIds.remove(recordingId);
        this.nextEntityId.remove(recordingId);
        if (ids != null) {
            for (UUID uuid : ids.keySet()) {
                this.entityToRecording.remove(uuid);
                this.lastEntitySample.remove(uuid);
                this.liveEntities.remove(uuid);
            }
        }
        if (this.activeScenes.isEmpty() && this.snapshotJobs.isEmpty() && this.ticker != null) {
            this.ticker.cancel();
            this.ticker = null;
            this.packetSamplers.clear();
        }
    }

    public WorldScene scene(String recordingId) {
        return this.activeScenes.get(recordingId);
    }

    public boolean isCapturing() {
        return !this.activeScenes.isEmpty();
    }

    public String recordingForLocation(Location location) {
        for (Map.Entry<String, WorldScene> entry : this.activeScenes.entrySet()) {
            if (entry.getValue().bounds().contains(location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void recordBlockSet(Location location, BlockData data) {
        WorldScene scene = sceneAt(location);
        if (scene == null) {
            return;
        }
        recordRelativeBlock(recordingForLocation(location), scene, location, data, false);
    }

    public void recordBlockBreak(Location location) {
        WorldScene scene = sceneAt(location);
        if (scene == null) {
            return;
        }
        recordRelativeBlock(recordingForLocation(location), scene, location, null, true);
    }

    public void recordObserved(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        WorldScene scene = sceneAt(location);
        if (scene == null) {
            return;
        }
        BlockData now = location.getBlock().getBlockData();
        recordRelativeBlock(
            recordingForLocation(location),
            scene,
            location,
            now,
            now.getMaterial().isAir()
        );
    }

    public void observeSoon(Location location) {
        if (location == null || location.getWorld() == null || !isCapturing()) {
            return;
        }
        Location copy = location.clone();
        Bukkit.getScheduler().runTask(this.plugin, () -> recordObserved(copy));
    }

    public void observeSoon(List<Location> locations) {
        if (locations == null || locations.isEmpty() || !isCapturing()) {
            return;
        }
        List<Location> copies = new ArrayList<>(locations.size());
        for (Location location : locations) {
            if (location != null && location.getWorld() != null) {
                copies.add(location.clone());
            }
        }
        if (copies.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (Location location : copies) {
                recordObserved(location);
            }
        });
    }

    boolean isPacketSampler(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName();
        UUID current = this.packetSamplers.get(world);
        Player online = current == null ? null : Bukkit.getPlayer(current);
        if (online == null || !online.isOnline() || online.getWorld() == null
            || !world.equals(online.getWorld().getName())) {
            this.packetSamplers.put(world, player.getUniqueId());
            return true;
        }
        return current.equals(player.getUniqueId());
    }

    public void recordPlayerBlock(String recordingId, Location location, BlockData now, boolean broken) {
        if (recordingId == null || location == null || location.getWorld() == null) {
            return;
        }
        WorldScene scene = this.activeScenes.get(recordingId);
        if (scene == null) {
            Recording recording = this.recordings.get(recordingId);
            if (recording == null) {
                return;
            }
            beginEventsOnly(recording, location);
            scene = this.activeScenes.get(recordingId);
            if (scene == null) {
                return;
            }
        }
        if (scene.bounds().contains(location)) {
            recordRelativeBlock(recordingId, scene, location, now, broken);
            return;
        }
        this.pendingBlocks.computeIfAbsent(recordingId, key -> new ArrayList<>()).add(
            new PendingBlock(tick(), location, now, broken)
        );
    }

    private void recordRelativeBlock(
        String recordingId,
        WorldScene scene,
        Location location,
        BlockData data,
        boolean broken
    ) {
        CaptureBounds bounds = scene.bounds();
        int relX = bounds.relX(location.getBlockX());
        int relY = bounds.relY(location.getBlockY());
        int relZ = bounds.relZ(location.getBlockZ());
        boolean air = broken || data == null || data.getMaterial().isAir();
        String sig = air ? "air" : data.getAsString();
        if (recordingId != null) {
            String previous = this.lastCellState
                .computeIfAbsent(recordingId, key -> new ConcurrentHashMap<>())
                .put(WorldSnapshot.pack(relX, relY, relZ), sig);
            if (sig.equals(previous)) {
                return;
            }
        }
        if (air) {
            scene.addEvent(SceneEvent.blockBreak(tick(), relX, relY, relZ));
            return;
        }
        scene.addEvent(SceneEvent.blockSet(tick(), relX, relY, relZ, sig));
    }

    public void recordExplosion(Location location, float power) {
        WorldScene scene = sceneAt(location);
        if (scene == null) {
            return;
        }
        CaptureBounds bounds = scene.bounds();
        scene.addEvent(SceneEvent.explosion(
            tick(),
            bounds.relX(location.getBlockX()),
            bounds.relY(location.getBlockY()),
            bounds.relZ(location.getBlockZ()),
            power
        ));
    }

    public void trackEntity(Entity entity) {
        if (entity instanceof Player) {
            return;
        }
        String recordingId = recordingForLocation(entity.getLocation());
        if (recordingId == null && this.activeScenes.size() == 1) {
            recordingId = this.activeScenes.keySet().iterator().next();
        }
        if (recordingId == null) {
            return;
        }
        Map<UUID, Integer> ids = this.entityIds.computeIfAbsent(recordingId, key -> new ConcurrentHashMap<>());
        WorldScene scene = this.activeScenes.get(recordingId);
        if (scene == null) {
            return;
        }
        this.liveEntities.put(entity.getUniqueId(), entity);
        Integer existingId = ids.get(entity.getUniqueId());
        if (existingId != null) {
            sampleEntity(entity, existingId, scene, true);
            return;
        }
        int captureId = this.nextEntityId.computeIfAbsent(recordingId, key -> new AtomicInteger(1))
            .getAndIncrement();
        ids.put(entity.getUniqueId(), captureId);
        this.entityToRecording.put(entity.getUniqueId(), recordingId);
        CaptureBounds bounds = scene.bounds();
        Location loc = entity.getLocation();
        Vector velocity = entity.getVelocity();
        float px = (float) (loc.getX() - bounds.minX());
        float py = (float) (loc.getY() - bounds.minY());
        float pz = (float) (loc.getZ() - bounds.minZ());
        float vx = (float) velocity.getX();
        float vy = (float) velocity.getY();
        float vz = (float) velocity.getZ();
        String typeName = entityTypeName(entity);
        SceneEvent event = entity instanceof Projectile
            ? SceneEvent.projectileSpawn(
                tick(), captureId, typeName,
                px, py, pz, loc.getYaw(), loc.getPitch(), vx, vy, vz
            )
            : SceneEvent.entitySpawn(
                tick(), captureId, typeName,
                px, py, pz, loc.getYaw(), loc.getPitch(), vx, vy, vz
            );
        scene.addEvent(event);
        this.lastEntitySample.put(
            entity.getUniqueId(),
            new float[] {px, py, pz, loc.getYaw(), loc.getPitch(), vx, vy, vz}
        );
        if (isKinematic(entity)) {
            Entity captured = entity;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (captured.isValid()) {
                    refreshMotion(captured);
                }
            });
        }
    }

    public void refreshMotion(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return;
        }
        String recordingId = this.entityToRecording.get(entity.getUniqueId());
        if (recordingId == null) {
            trackEntity(entity);
            return;
        }
        Map<UUID, Integer> ids = this.entityIds.get(recordingId);
        WorldScene scene = this.activeScenes.get(recordingId);
        if (ids == null || scene == null) {
            return;
        }
        Integer captureId = ids.get(entity.getUniqueId());
        if (captureId == null) {
            return;
        }
        this.liveEntities.put(entity.getUniqueId(), entity);
        sampleEntity(entity, captureId, scene, true);
    }

    private static String entityTypeName(Entity entity) {
        if (entity instanceof org.bukkit.entity.Item item) {
            org.bukkit.inventory.ItemStack stack = item.getItemStack();
            if (stack != null && !stack.getType().isAir()) {
                return "ITEM:" + stack.getType().name() + "*" + Math.max(1, stack.getAmount());
            }
            return "ITEM:STONE*1";
        }
        return entity.getType().name();
    }

    public void recordDamage(Entity victim, Entity damager, double amount) {
        Integer victimId = captureId(victim);
        if (victimId == null) {
            return;
        }
        WorldScene scene = sceneForEntity(victim);
        if (scene == null) {
            return;
        }
        int damagerId = -1;
        if (damager != null) {
            Integer id = captureId(damager);
            if (id != null) {
                damagerId = id;
            }
        }
        scene.addEvent(SceneEvent.entityDamage(tick(), victimId, (float) amount, damagerId));
    }

    public void recordDeath(Entity entity) {
        Integer id = captureId(entity);
        WorldScene scene = sceneForEntity(entity);
        if (id == null || scene == null) {
            return;
        }
        scene.addEvent(SceneEvent.entityDeath(tick(), id));
    }

    public void recordRemove(Entity entity) {
        Integer id = captureId(entity);
        WorldScene scene = sceneForEntity(entity);
        if (id == null || scene == null) {
            return;
        }
        scene.addEvent(SceneEvent.entityRemove(tick(), id));
        String recordingId = this.entityToRecording.remove(entity.getUniqueId());
        this.lastEntitySample.remove(entity.getUniqueId());
        this.liveEntities.remove(entity.getUniqueId());
        if (recordingId != null) {
            Map<UUID, Integer> ids = this.entityIds.get(recordingId);
            if (ids != null) {
                ids.remove(entity.getUniqueId());
            }
        }
    }

    private CaptureBounds resolveBounds(WorldCaptureMode mode, Player recorder) {
        Location at = recorder.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        return switch (mode) {
            case OFF -> null;
            case AREA -> {
                if (this.settings.global() || this.settings.center() == null) {
                    yield null;
                }
                yield CaptureBounds.fromCenterRadius(this.settings.center(), (int) Math.ceil(this.settings.radius()));
            }
            case AUTO_BOX -> CaptureBounds.fromCenterRadius(at, this.config.autoBoxRadius());
            case LOADED_CHUNKS -> CaptureBounds.fromChunks(
                world,
                at.getBlockX() >> 4,
                at.getBlockZ() >> 4,
                this.config.chunkRadius()
            );
        };
    }

    private void captureInitialEntities(
        World world,
        CaptureBounds bounds,
        String recordingId,
        List<EntitySnapshot> entities,
        Map<UUID, Integer> ids,
        AtomicInteger nextId
    ) {
        Location center = new Location(
            world,
            (bounds.minX() + bounds.maxX()) * 0.5,
            (bounds.minY() + bounds.maxY()) * 0.5,
            (bounds.minZ() + bounds.maxZ()) * 0.5
        );
        for (Entity entity : world.getNearbyEntities(
            center,
            bounds.sizeX() * 0.5 + 1,
            bounds.sizeY() * 0.5 + 1,
            bounds.sizeZ() * 0.5 + 1
        )) {
            if (entity instanceof Player || !bounds.contains(entity)) {
                continue;
            }
            if (!(entity instanceof LivingEntity)
                && !(entity instanceof Projectile)
                && !(entity instanceof org.bukkit.entity.Item)
                && !(entity instanceof FallingBlock)
                && !(entity instanceof TNTPrimed)) {
                continue;
            }
            int captureId = nextId.getAndIncrement();
            ids.put(entity.getUniqueId(), captureId);
            this.entityToRecording.put(entity.getUniqueId(), recordingId);
            this.liveEntities.put(entity.getUniqueId(), entity);
            float health = entity instanceof LivingEntity living ? (float) living.getHealth() : 0F;
            Location loc = entity.getLocation();
            entities.add(new EntitySnapshot(
                captureId,
                entityTypeName(entity),
                (float) (loc.getX() - bounds.minX()),
                (float) (loc.getY() - bounds.minY()),
                (float) (loc.getZ() - bounds.minZ()),
                loc.getYaw(),
                loc.getPitch(),
                health
            ));
        }
    }

    private void ensureTicker() {
        if (this.ticker != null && !this.ticker.isCancelled()) {
            return;
        }
        this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickJobs, 1L, 1L);
    }

    private void tickJobs() {
        this.failForward.run("scene-tick", () -> {
            int chunkBudget = this.config.tickBudget().snapshotChunks();
            for (Map.Entry<String, WorldSnapshotJob> entry : List.copyOf(this.snapshotJobs.entrySet())) {
                WorldSnapshotJob job = entry.getValue();
                boolean done = job.tick(chunkBudget);
                if (done) {
                    this.snapshotJobs.remove(entry.getKey());
                    Recording recording = this.recordings.get(entry.getKey());
                    if (recording != null) {
                        finishSnapshot(entry.getKey(), recording, job, false);
                    }
                }
            }
            this.sampleAge++;
            sampleEntities();
        });
    }

    private void finishSnapshot(String recordingId, Recording recording, WorldSnapshotJob job, boolean drain) {
        if (drain) {
            int guard = 10_000;
            while (!job.tick(8) && guard-- > 0) {

            }
        }
        List<EntitySnapshot> entities = this.pendingEntities.getOrDefault(recordingId, List.of());
        WorldScene current = this.activeScenes.get(recordingId);
        CaptureBounds bounds = current != null ? current.bounds() : null;
        if (bounds == null) {
            return;
        }
        WorldScene scene = new WorldScene(bounds, job.complete(), entities);
        if (current != null) {
            scene.setEvents(current.events());
        }
        recording.setWorldScene(scene);
        this.activeScenes.put(recordingId, scene);
        this.plugin.getLogger().info(
            "World snapshot ready for '" + recordingId + "': "
                + scene.snapshot().size() + " blocks, " + entities.size() + " entities"
        );
    }

    private void sampleEntities() {
        if (this.entityIds.isEmpty()) {
            return;
        }
        int interval = Math.max(1, this.config.entitySampleInterval());
        boolean due = this.sampleAge % interval == 0;
        for (Map.Entry<String, Map<UUID, Integer>> entry : this.entityIds.entrySet()) {
            WorldScene scene = this.activeScenes.get(entry.getKey());
            if (scene == null) {
                continue;
            }
            for (Map.Entry<UUID, Integer> entityEntry : List.copyOf(entry.getValue().entrySet())) {
                Entity entity = resolveEntity(entityEntry.getKey());
                if (entity == null || entity.isDead()) {
                    continue;
                }
                if (!due && !isKinematic(entity)) {
                    continue;
                }
                sampleEntity(entity, entityEntry.getValue(), scene, false);
            }
        }
    }

    private void sampleEntity(Entity entity, int captureId, WorldScene scene, boolean force) {
        CaptureBounds bounds = scene.bounds();
        if (!force && !isKinematic(entity) && !bounds.contains(entity)) {
            return;
        }
        Location loc = entity.getLocation();
        Vector velocity = entity.getVelocity();
        float px = (float) (loc.getX() - bounds.minX());
        float py = (float) (loc.getY() - bounds.minY());
        float pz = (float) (loc.getZ() - bounds.minZ());
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();
        float vx = (float) velocity.getX();
        float vy = (float) velocity.getY();
        float vz = (float) velocity.getZ();
        float epsilon = isKinematic(entity) ? 0.001F : this.config.tickBudget().entityMoveEpsilon();
        float velEpsilon = isKinematic(entity) ? 0.001F : 0.01F;
        float[] last = this.lastEntitySample.get(entity.getUniqueId());
        if (!force && last != null
            && Math.abs(last[0] - px) <= epsilon
            && Math.abs(last[1] - py) <= epsilon
            && Math.abs(last[2] - pz) <= epsilon
            && Math.abs(last[3] - yaw) <= 0.5F
            && Math.abs(last[4] - pitch) <= 0.5F
            && Math.abs(last[5] - vx) <= velEpsilon
            && Math.abs(last[6] - vy) <= velEpsilon
            && Math.abs(last[7] - vz) <= velEpsilon) {
            return;
        }
        this.lastEntitySample.put(
            entity.getUniqueId(),
            new float[] {px, py, pz, yaw, pitch, vx, vy, vz}
        );
        scene.addEvent(SceneEvent.entityMove(tick(), captureId, px, py, pz, yaw, pitch, vx, vy, vz));
    }

    private Entity resolveEntity(UUID uuid) {
        Entity cached = this.liveEntities.get(uuid);
        if (cached != null && cached.isValid() && !cached.isDead()) {
            return cached;
        }
        Entity found = Bukkit.getEntity(uuid);
        if (found != null) {
            this.liveEntities.put(uuid, found);
        } else {
            this.liveEntities.remove(uuid);
        }
        return found;
    }

    private static boolean isKinematic(Entity entity) {
        return entity instanceof org.bukkit.entity.Item
            || entity instanceof Projectile
            || entity instanceof FallingBlock
            || entity instanceof TNTPrimed;
    }

    private WorldScene sceneAt(Location location) {
        String id = recordingForLocation(location);
        return id == null ? null : this.activeScenes.get(id);
    }

    private void bakePendingBlocks(String recordingId, Recording recording) {
        List<PendingBlock> pending = this.pendingBlocks.get(recordingId);
        if (recording == null || pending == null || pending.isEmpty()) {
            return;
        }
        WorldScene current = this.activeScenes.get(recordingId);
        if (current == null) {
            current = recording.worldScene();
        }
        if (current == null) {
            return;
        }
        CaptureBounds old = current.bounds();
        int minX = old.minX();
        int minY = old.minY();
        int minZ = old.minZ();
        int maxX = old.maxX();
        int maxY = old.maxY();
        int maxZ = old.maxZ();
        boolean grew = false;
        for (PendingBlock block : pending) {
            if (!old.world().equals(block.world)) {
                continue;
            }
            if (block.x < minX) {
                minX = block.x;
                grew = true;
            }
            if (block.y < minY) {
                minY = block.y;
                grew = true;
            }
            if (block.z < minZ) {
                minZ = block.z;
                grew = true;
            }
            if (block.x > maxX) {
                maxX = block.x;
                grew = true;
            }
            if (block.y > maxY) {
                maxY = block.y;
                grew = true;
            }
            if (block.z > maxZ) {
                maxZ = block.z;
                grew = true;
            }
        }
        CaptureBounds bounds = grew
            ? new CaptureBounds(old.world(), minX - 2, minY - 2, minZ - 2, maxX + 2, maxY + 2, maxZ + 2)
            : old;
        int dx = old.minX() - bounds.minX();
        int dy = old.minY() - bounds.minY();
        int dz = old.minZ() - bounds.minZ();
        WorldScene baked = new WorldScene(bounds, current.snapshot(), current.entities());
        for (SceneEvent event : current.events()) {
            baked.addEvent(event.translated(dx, dy, dz));
        }
        for (PendingBlock block : pending) {
            if (!bounds.world().equals(block.world)) {
                continue;
            }
            if (block.broken) {
                baked.addEvent(SceneEvent.blockBreak(
                    block.tick,
                    bounds.relX(block.x),
                    bounds.relY(block.y),
                    bounds.relZ(block.z)
                ));
            } else if (block.data != null) {
                baked.addEvent(SceneEvent.blockSet(
                    block.tick,
                    bounds.relX(block.x),
                    bounds.relY(block.y),
                    bounds.relZ(block.z),
                    block.data
                ));
            }
        }
        recording.setWorldScene(baked);
        this.activeScenes.put(recordingId, baked);
    }

    private static final class PendingBlock {
        private final int tick;
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String data;
        private final boolean broken;

        private PendingBlock(int tick, Location location, BlockData data, boolean broken) {
            this.tick = tick;
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
            this.data = data == null ? null : data.getAsString();
            this.broken = broken;
        }
    }

    private WorldScene sceneForEntity(Entity entity) {
        String id = this.entityToRecording.get(entity.getUniqueId());
        return id == null ? null : this.activeScenes.get(id);
    }

    private Integer captureId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String recordingId = this.entityToRecording.get(entity.getUniqueId());
        if (recordingId == null) {
            trackEntity(entity);
            recordingId = this.entityToRecording.get(entity.getUniqueId());
        }
        if (recordingId == null) {
            return null;
        }
        Map<UUID, Integer> ids = this.entityIds.get(recordingId);
        return ids == null ? null : ids.get(entity.getUniqueId());
    }

    private int tick() {
        return Math.max(0, this.tickSupplier.getAsInt());
    }

    @Override
    public void close() {
        this.blockPackets.unregister();
        for (String id : List.copyOf(this.activeScenes.keySet())) {
            end(id);
        }
    }
}
