package com.paris.mocap.recording;

import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.cycle.CyclePhase;
import com.paris.mocap.model.ActionData;
import com.paris.mocap.model.ActionType;
import com.paris.mocap.model.Frame;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.RecordingSettings;
import com.paris.mocap.model.Track;
import com.paris.mocap.runtime.FailForward;
import com.paris.mocap.api.event.MocapRecordingStartEvent;
import com.paris.mocap.api.event.MocapRecordingStopEvent;
import com.paris.mocap.scene.SceneCaptureService;
import com.paris.mocap.scene.WorldCaptureMode;
import com.paris.mocap.storage.RecordingRepository;
import com.paris.mocap.util.Text;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RecordingService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final MocapConfig config;
    private final RecordingRepository repository;
    private final RecordingSettings settings = new RecordingSettings();
    private final SceneCaptureService scenes;
    private final FailForward failForward;
    private final float poseEpsilon;

    private final Map<UUID, Recorder> recorders = new ConcurrentHashMap<>();
    private final Map<String, Recording> active = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> clocks = new ConcurrentHashMap<>();
    private final Map<String, CyclePhase> phases = new ConcurrentHashMap<>();
    private final Map<String, Boolean> quiet = new ConcurrentHashMap<>();
    private BukkitTask sampler;

    public RecordingService(
        JavaPlugin plugin,
        RecordingRepository repository,
        MocapConfig config,
        FailForward failForward
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.failForward = failForward;
        this.poseEpsilon = config.tickBudget().poseEpsilon();
        this.settings.setTickRate(config.defaultTickRate());
        this.settings.setMaxDurationTicks(config.defaultMaxDurationTicks());
        this.settings.setWorldCaptureMode(config.defaultWorldCaptureMode());
        this.scenes = new SceneCaptureService(plugin, config, this.settings, this::globalSceneTick, failForward);
    }

    public RecordingSettings settings() {
        return this.settings;
    }

    public SceneCaptureService scenes() {
        return this.scenes;
    }

    public boolean isRecording(Player player) {
        return this.recorders.containsKey(player.getUniqueId());
    }

    public void start(Player player, String name) {
        start(player, name, CaptureOptions.MANUAL, null);
    }

    public boolean start(Player player, String name, CaptureOptions options, String gameType) {
        if (player == null || options == null) {
            return false;
        }
        if (name == null || name.isBlank()) {
            if (options.notifyPlayers()) {
                player.sendMessage(Text.prefix("Usage: name the recording first.", NamedTextColor.RED));
            }
            return false;
        }
        name = name.trim();
        if (isRecording(player)) {
            if (options.notifyPlayers()) {
                player.sendMessage(Text.prefix("You are already recording.", NamedTextColor.RED));
            }
            return false;
        }

        Recording existing = this.active.get(name);
        if (existing == null && this.repository.exists(name)) {
            if (options.notifyPlayers()) {
                player.sendMessage(Text.prefix("Recording '" + name + "' already exists.", NamedTextColor.RED));
            }
            return false;
        }
        if (existing != null
            && existing.track(player.getUniqueId()) == null
            && existing.tracks().size() >= options.maxTracks()) {
            return false;
        }

        MocapRecordingStartEvent event = new MocapRecordingStartEvent(player, name);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        Recording recording = this.active.computeIfAbsent(name, Recording::new);
        if (gameType != null) {
            recording.setGameType(gameType);
        }
        this.clocks.putIfAbsent(name, new AtomicInteger(0));
        this.phases.put(name, CyclePhase.RECORDING);
        this.quiet.putIfAbsent(name, !options.notifyPlayers());

        Track track = recording.track(player.getUniqueId());
        if (track == null) {
            String[] skin = readSkin(player.getPlayerProfile());
            track = new Track(player.getUniqueId(), player.getName(), skin[0], skin[1]);
            track.setSkinParts(readSkinParts(player));
            track.setEntityReach(readEntityReach(player));
            recording.addTrack(track);
        }
        int joinTick = this.clocks.getOrDefault(name, new AtomicInteger(0)).get();
        Recorder recorder = new Recorder(name, track);
        recorder.tick = joinTick;
        recorder.maxDurationTicks = options.maxDurationTicks();
        recorder.ignoreFilters = options.ignoreFilters();
        this.recorders.put(player.getUniqueId(), recorder);

        WorldCaptureMode captureMode = options.worldCaptureOverride() != null
            ? options.worldCaptureOverride()
            : this.settings.worldCaptureMode();
        if (recording.worldScene() == null) {
            WorldCaptureMode previous = this.settings.worldCaptureMode();
            this.settings.setWorldCaptureMode(captureMode);
            try {
                this.failForward.run("world-capture:" + name, () -> this.scenes.begin(recording, player));
            } finally {
                this.settings.setWorldCaptureMode(previous);
            }
            if (options.notifyPlayers() && recording.hasWorldScene() && captureMode != WorldCaptureMode.OFF) {
                player.sendMessage(Text.prefix(
                    "World capture " + captureMode.label()
                        + " started (" + recording.worldScene().entities().size() + " entities).",
                    NamedTextColor.AQUA
                ));
            }
        }

        snapshotEquipment(player, recorder);
        int stagger = recording.tracks().size();
        Track capturedTrack = track;
        if (options.completeSkin()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> completeSkinAsync(player, capturedTrack), stagger);
        }

        if (options.notifyPlayers()) {
            player.sendMessage(Text.prefix("Recording started: " + name, NamedTextColor.GREEN));
        }
        ensureSampler();
        return true;
    }

    public String startGroup(String gameType, Collection<Player> players) {
        return startGroup(null, gameType, players, CaptureOptions.game(this.config));
    }

    public String startGroup(String gameType, Collection<Player> players, CaptureOptions options) {
        return startGroup(null, gameType, players, options);
    }

    public String startGroup(String recordingId, String gameType, Collection<Player> players, CaptureOptions options) {
        if (players == null || players.isEmpty() || options == null) {
            return null;
        }
        String id = recordingId == null || recordingId.isBlank() ? nextGroupId(gameType) : recordingId.trim();
        if (this.repository.exists(id) && !this.active.containsKey(id)) {
            return null;
        }
        int added = 0;
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (start(player, id, options, gameType)) {
                added++;
            }
            if (added >= options.maxTracks()) {
                break;
            }
        }
        if (added == 0) {
            return null;
        }
        this.plugin.getLogger().info("Group capture '" + id + "' started for " + added + " players"
            + (gameType == null ? "." : " (" + gameType + ")."));
        return id;
    }

    public boolean join(String recordingId, Player player) {
        return join(recordingId, player, CaptureOptions.game(this.config));
    }

    public boolean join(String recordingId, Player player, CaptureOptions options) {
        if (recordingId == null || player == null || !this.active.containsKey(recordingId)) {
            return false;
        }
        Recording recording = this.active.get(recordingId);
        return start(player, recordingId, options, recording == null ? null : recording.gameType());
    }

    public boolean detach(Player player) {
        if (player == null) {
            return false;
        }
        Recorder recorder = this.recorders.remove(player.getUniqueId());
        if (recorder == null) {
            return false;
        }
        if (!hasRecorders(recorder.recordingName)) {
            finalizeRecording(recorder.recordingName, null);
        }
        stopSamplerIfIdle();
        return true;
    }

    public boolean stopNamed(String name) {
        if (name == null || !this.active.containsKey(name)) {
            return false;
        }
        Player notifier = null;
        for (UUID id : new ArrayList<>(this.recorders.keySet())) {
            Recorder other = this.recorders.get(id);
            if (other != null && name.equals(other.recordingName)) {
                this.recorders.remove(id);
                if (notifier == null) {
                    notifier = Bukkit.getPlayer(id);
                }
            }
        }
        boolean quietStop = Boolean.TRUE.equals(this.quiet.get(name));
        finalizeRecording(name, quietStop ? null : notifier);
        stopSamplerIfIdle();
        return true;
    }

    public boolean isActive(String name) {
        return name != null && this.active.containsKey(name);
    }

    public Recording activeRecording(String name) {
        return name == null ? null : this.active.get(name);
    }

    public String recordingName(Player player) {
        if (player == null) {
            return null;
        }
        Recorder recorder = this.recorders.get(player.getUniqueId());
        return recorder == null ? null : recorder.recordingName;
    }

    public List<Recording> recordingsForGame(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            return List.of();
        }
        String needle = gameType.trim();
        List<Recording> matches = new ArrayList<>();
        for (Recording recording : this.repository.list()) {
            if (needle.equalsIgnoreCase(recording.gameType())) {
                matches.add(recording);
            }
        }
        matches.sort(Comparator.comparingLong(Recording::createdAt).reversed());
        return matches;
    }

    public Set<String> gameTypes() {
        Set<String> types = new LinkedHashSet<>();
        List<Recording> all = new ArrayList<>(this.repository.list());
        all.sort(Comparator.comparing(Recording::gameType, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        for (Recording recording : all) {
            if (recording.gameType() != null && !recording.gameType().isBlank()) {
                types.add(recording.gameType());
            }
        }
        return types;
    }

    private String nextGroupId(String gameType) {
        String slug = gameType == null || gameType.isBlank()
            ? "take"
            : gameType.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "take";
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        String id = slug + "_" + stamp;
        if (this.active.containsKey(id) || this.repository.exists(id)) {
            id = id + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt());
        }
        return id;
    }

    private boolean hasRecorders(String name) {
        for (Recorder remaining : this.recorders.values()) {
            if (name.equals(remaining.recordingName)) {
                return true;
            }
        }
        return false;
    }

    public void stop(Player player) {
        Recorder recorder = this.recorders.get(player.getUniqueId());
        if (recorder == null) {
            player.sendMessage(Text.prefix("You are not recording.", NamedTextColor.RED));
            return;
        }
        String name = recorder.recordingName;
        boolean quietStop = Boolean.TRUE.equals(this.quiet.get(name));
        for (UUID id : new ArrayList<>(this.recorders.keySet())) {
            Recorder other = this.recorders.get(id);
            if (other != null && name.equals(other.recordingName)) {
                this.recorders.remove(id);
                if (!quietStop) {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null && !online.getUniqueId().equals(player.getUniqueId())) {
                        online.sendMessage(Text.prefix("Recording '" + name + "' was stopped.", NamedTextColor.YELLOW));
                    }
                }
            }
        }
        finalizeRecording(name, quietStop ? null : player);
        stopSamplerIfIdle();
    }

    public void recordAction(Player player, ActionData action) {
        Recorder recorder = this.recorders.get(player.getUniqueId());
        if (recorder == null) {
            return;
        }
        Frame frame = recorder.track.getFrame(recorder.tick);
        int pingMs = pingOf(player);
        if (frame == null) {
            frame = new Frame(Pose.from(player.getLocation()), pingMs);
        } else {
            frame = frame.withPing(pingMs);
        }
        recorder.track.putFrame(recorder.tick, frame.withAction(action));
        if (action.type() == ActionType.EQUIPMENT && action.slot() != null) {
            recorder.remember(action.slot(), action.item());
        }
    }

    public void noteBlockChange(Player player, Location location, org.bukkit.block.data.BlockData before) {
        if (player == null || location == null) {
            return;
        }
        Recorder recorder = this.recorders.get(player.getUniqueId());
        if (recorder == null) {
            return;
        }
        String recordingId = recorder.recordingName;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.recorders.containsKey(player.getUniqueId())) {
                return;
            }
            org.bukkit.block.data.BlockData now = location.getBlock().getBlockData();
            boolean beforeAir = before == null || before.getMaterial().isAir();
            boolean nowAir = now.getMaterial().isAir();
            if (before != null && now.equals(before)) {
                return;
            }
            this.scenes.recordPlayerBlock(recordingId, location, now, !beforeAir && nowAir);
        });
    }

    public void setBedLocation(UUID playerId, Location location) {
        Recorder recorder = this.recorders.get(playerId);
        if (recorder == null) {
            return;
        }
        recorder.bed = location == null ? null : location.clone();
    }

    public Location bedLocation(UUID playerId) {
        Recorder recorder = this.recorders.get(playerId);
        return recorder == null ? null : recorder.bed;
    }

    private int globalSceneTick() {
        int max = 0;
        for (Recorder recorder : this.recorders.values()) {
            if (recorder.tick > max) {
                max = recorder.tick;
            }
        }
        for (AtomicInteger clock : this.clocks.values()) {
            max = Math.max(max, clock.get());
        }
        return max;
    }

    private void ensureSampler() {
        if (this.sampler != null && !this.sampler.isCancelled()) {
            return;
        }
        this.sampler = Bukkit.getScheduler().runTaskTimer(this.plugin, this::sample, 1L, 1L);
    }

    private void sample() {
        this.failForward.run("recording-sample", this::sampleInternal);
    }

    private void sampleInternal() {
        int rate = this.settings.tickRate();
        for (UUID playerId : new ArrayList<>(this.recorders.keySet())) {
            Recorder recorder = this.recorders.get(playerId);
            if (recorder == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                String name = recorder.recordingName;
                this.recorders.remove(playerId);
                if (!hasRecorders(name)) {
                    finalizeRecording(name, null);
                }
                continue;
            }
            if (!recorder.ignoreFilters && !this.settings.matches(player)) {
                continue;
            }

            int tick = recorder.tick++;
            this.clocks.computeIfAbsent(recorder.recordingName, key -> new AtomicInteger(0))
                .updateAndGet(current -> Math.max(current, tick));

            int cap = recorder.maxDurationTicks > 0 ? recorder.maxDurationTicks : this.settings.maxDurationTicks();
            if (cap > 0 && tick >= cap) {
                stopNamed(recorder.recordingName);
                continue;
            }
            if (tick % rate != 0) {
                continue;
            }

            Pose pose = Pose.from(player.getLocation());
            int pingMs = pingOf(player);
            boolean sneak = this.settings.recordSneak() && player.isSneaking();
            boolean sprint = this.settings.recordSprint() && player.isSprinting();
            boolean swim = player.isSwimming();
            boolean glide = player.isGliding();
            byte hand = this.settings.recordBlocking() ? resolveHandState(player) : 0;

            boolean poseChanged = recorder.lastPose == null || !recorder.lastPose.nearlyEquals(pose, this.poseEpsilon);
            boolean flagsChanged = sneak != recorder.sneak
                || sprint != recorder.sprint
                || swim != recorder.swim
                || glide != recorder.glide
                || hand != recorder.hand;
            boolean heartbeat = tick % 10 == 0;

            Frame frame = recorder.track.getFrame(tick);
            if (frame == null) {
                frame = new Frame(pose, pingMs);
            } else {
                frame = frame.withPose(pose).withPing(pingMs);
            }
            Frame withEquipment = this.settings.recordEquipment()
                ? appendEquipmentChanges(player, recorder, frame)
                : frame;
            boolean equipmentChanged = withEquipment != frame;
            frame = withEquipment;

            if (!poseChanged && !flagsChanged && recorder.bed == null && !equipmentChanged && !heartbeat) {
                continue;
            }

            if (this.settings.recordSneak() && sneak != recorder.sneak) {
                frame = frame.withAction(ActionData.sneak(sneak));
            }
            if (this.settings.recordSprint() && sprint != recorder.sprint) {
                frame = frame.withAction(ActionData.sprint(sprint));
            }
            if (swim != recorder.swim) {
                frame = frame.withAction(ActionData.swim(swim));
            }
            if (glide != recorder.glide) {
                frame = frame.withAction(ActionData.glide(glide));
            }
            if (this.settings.recordBlocking() && hand != recorder.hand) {
                frame = frame.withAction(ActionData.useItem(hand));
                if (player.isBlocking()) {
                    frame = frame.withAction(ActionData.block(true));
                } else if (recorder.hand != 0 && hand == 0) {
                    frame = frame.withAction(ActionData.block(false));
                }
            }
            if (recorder.bed != null && player.isSleeping()) {
                frame = frame.withAction(ActionData.sleep(true, Pose.from(recorder.bed)));
            }

            recorder.lastPose = pose;
            recorder.sneak = sneak;
            recorder.sprint = sprint;
            recorder.swim = swim;
            recorder.glide = glide;
            recorder.hand = hand;
            recorder.track.putFrame(tick, frame);
        }
        stopSamplerIfIdle();
    }

    private static byte resolveHandState(Player player) {
        if (!player.isHandRaised() && !player.isBlocking()) {
            return 0;
        }
        try {
            EquipmentSlot hand = player.getActiveItemHand();
            return hand == EquipmentSlot.OFF_HAND ? (byte) 3 : (byte) 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private void snapshotEquipment(Player player, Recorder recorder) {
        if (!this.settings.recordEquipment() || recorder == null) {
            return;
        }
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (ignoredSlot(slot)) {
                continue;
            }
            ItemStack item = equipment.getItem(slot);
            recorder.remember(slot, item);
            if (item != null && !item.getType().isAir()) {
                recordAction(player, ActionData.equipment(slot, item));
            }
        }
    }

    private Frame appendEquipmentChanges(Player player, Recorder recorder, Frame frame) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return frame;
        }
        Frame result = frame;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (ignoredSlot(slot)) {
                continue;
            }
            ItemStack now = equipment.getItem(slot);
            if (!itemChanged(recorder.gear(slot), now)) {
                continue;
            }
            recorder.remember(slot, now);
            result = result.withAction(ActionData.equipment(slot, now));
        }
        return result;
    }

    private static boolean ignoredSlot(EquipmentSlot slot) {
        String name = slot.name();
        return name.equals("BODY") || name.equals("SADDLE");
    }

    private static boolean itemChanged(ItemStack previous, ItemStack next) {
        boolean prevEmpty = previous == null || previous.getType().isAir();
        boolean nextEmpty = next == null || next.getType().isAir();
        if (prevEmpty && nextEmpty) {
            return false;
        }
        if (prevEmpty != nextEmpty) {
            return true;
        }
        return previous.getAmount() != next.getAmount() || !previous.isSimilar(next);
    }

    private void completeSkinAsync(Player player, Track track) {
        if (track.skinTexture() != null && !track.skinTexture().isEmpty()
            && track.skinSignature() != null && !track.skinSignature().isEmpty()) {
            return;
        }
        PlayerProfile profile = player.getPlayerProfile();
        profile.update().thenAccept(updated -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.recorders.containsKey(player.getUniqueId())) {
                return;
            }
            String[] skin = readSkin(updated);
            if (skin[0] != null) {
                track.setSkin(skin[0], skin[1]);
            }
        })).exceptionally(ex -> {
            this.plugin.getLogger().warning("Skin complete failed for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }

    private static int pingOf(Player player) {
        return Math.max(0, Math.min(65535, player.getPing()));
    }

    private static byte readSkinParts(Player player) {
        try {
            Object parts = player.getClientOption(com.destroystokyo.paper.ClientOption.SKIN_PARTS);
            if (parts != null) {
                Object raw = parts.getClass().getMethod("getRaw").invoke(parts);
                if (raw instanceof Number number) {
                    return (byte) (number.intValue() & 0x7F);
                }
            }
        } catch (Throwable ignored) {
        }
        return Track.ALL_SKIN_PARTS;
    }

    private static float readEntityReach(Player player) {
        try {
            AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            if (range != null && range.getValue() > 0.1) {
                return (float) range.getValue();
            }
        } catch (Throwable ignored) {
        }
        return Track.DEFAULT_ENTITY_REACH;
    }

    private static String[] readSkin(PlayerProfile profile) {
        String texture = null;
        String signature = null;
        for (ProfileProperty property : profile.getProperties()) {
            if ("textures".equals(property.getName())) {
                texture = property.getValue();
                signature = property.getSignature();
                break;
            }
        }
        return new String[] {texture, signature};
    }

    private void finalizeRecording(String name, Player notifier) {
        this.phases.put(name, CyclePhase.FINALIZING);
        this.failForward.run("scene-end:" + name, () -> this.scenes.end(name));
        this.clocks.remove(name);
        this.quiet.remove(name);
        Recording recording = this.active.remove(name);
        this.phases.remove(name);
        if (recording == null) {
            return;
        }
        this.repository.save(recording);
        Bukkit.getPluginManager().callEvent(new MocapRecordingStopEvent(notifier, recording));
        if (notifier != null) {
            String sceneInfo = recording.hasWorldScene()
                ? ", scene " + recording.worldScene().snapshot().size() + " blocks"
                : "";
            notifier.sendMessage(Text.prefix(
                "Saved '" + name + "' (" + recording.durationTicks() + " ticks, "
                    + recording.tracks().size() + " tracks" + sceneInfo + ").",
                NamedTextColor.GREEN
            ));
        }
    }

    private void stopSamplerIfIdle() {
        if (this.recorders.isEmpty() && this.sampler != null) {
            this.sampler.cancel();
            this.sampler = null;
        }
    }

    @Override
    public void close() {
        for (UUID id : new ArrayList<>(this.recorders.keySet())) {
            Recorder recorder = this.recorders.remove(id);
            if (recorder != null) {
                finalizeRecording(recorder.recordingName, null);
            }
        }
        this.scenes.close();
        stopSamplerIfIdle();
    }

    private static final class Recorder {
        private final String recordingName;
        private final Track track;
        private int tick;
        private Pose lastPose;
        private boolean sneak;
        private boolean sprint;
        private boolean swim;
        private boolean glide;
        private byte hand;
        private Location bed;
        private int maxDurationTicks;
        private boolean ignoreFilters;
        private final ItemStack[] gear = new ItemStack[EquipmentSlot.values().length];

        private Recorder(String recordingName, Track track) {
            this.recordingName = recordingName;
            this.track = track;
        }

        private ItemStack gear(EquipmentSlot slot) {
            return this.gear[slot.ordinal()];
        }

        private void remember(EquipmentSlot slot, ItemStack item) {
            this.gear[slot.ordinal()] = item == null || item.getType().isAir() ? null : item.clone();
        }
    }
}
