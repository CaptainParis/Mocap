package com.paris.mocap.storage;

import com.paris.mocap.model.ActionData;
import com.paris.mocap.model.ActionType;
import com.paris.mocap.model.Frame;
import com.paris.mocap.model.PlaybackSettings;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.Recording;
import com.paris.mocap.model.Track;
import com.paris.mocap.model.VisibilityMode;
import com.paris.mocap.scene.CaptureBounds;
import com.paris.mocap.scene.EntitySnapshot;
import com.paris.mocap.scene.SceneEvent;
import com.paris.mocap.scene.SceneEventType;
import com.paris.mocap.scene.WorldScene;
import com.paris.mocap.scene.WorldSnapshot;
import com.paris.mocap.util.VarInts;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.inventory.EquipmentSlot;

public final class BinaryRecordingCodec {
    public static final int VERSION = 5;
    public static final int VERSION_V1 = 1;
    public static final int VERSION_V2 = 2;
    public static final int VERSION_V3 = 3;
    public static final int VERSION_V4 = 4;
    public static final int VERSION_V5 = 5;
    private static final byte[] MAGIC = {'M', 'C', 'P', 'B'};

    public void encode(Recording recording, OutputStream raw) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(raw));
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeUTF(recording.id());
            out.writeLong(recording.createdAt());
            writeOptionalString(out, recording.iconMaterialName());
            writePlaybackSettings(out, recording.playbackSettings());

            List<String> worlds = new ArrayList<>();
            Map<String, Integer> worldIndex = new HashMap<>();
            collectWorlds(recording, worlds, worldIndex);

            VarInts.writeUnsigned(out, worlds.size());
            for (String world : worlds) {
                out.writeUTF(world);
            }

            VarInts.writeUnsigned(out, recording.tracks().size());
            for (Track track : recording.tracks()) {
                writeUuid(out, track.playerId());
                out.writeUTF(track.playerName());
                writeOptionalString(out, track.skinTexture());
                writeOptionalString(out, track.skinSignature());
                out.writeByte(track.skinParts());
                out.writeFloat(track.entityReach());
                VarInts.writeUnsigned(out, track.size());

                int previousTick = 0;
                for (int i = 0; i < track.size(); i++) {
                    int tick = track.tickAt(i);
                    VarInts.writeUnsigned(out, tick - previousTick);
                    previousTick = tick;
                    Frame frame = track.frameAt(i);
                    writePose(out, frame.pose(), worldIndex);
                    out.writeShort(Math.min(65535, Math.max(0, frame.pingMs())));
                    ActionData[] actions = frame.actions();
                    VarInts.writeUnsigned(out, actions.length);
                    for (ActionData action : actions) {
                        writeAction(out, action, worldIndex);
                    }
                }
            }

            writeWorldScene(out, recording.worldScene());
            writeOptionalString(out, recording.gameType());
        }
    }

    public Recording decode(InputStream raw) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(raw));
             DataInputStream in = new DataInputStream(gzip)) {
            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("Not an MCPB recording");
            }
            int version = in.readUnsignedShort();
            if (version < VERSION_V1 || version > VERSION) {
                throw new IOException("Unsupported MCPB version: " + version);
            }

            String id = in.readUTF();
            long createdAt = in.readLong();
            Recording recording = new Recording(id, createdAt);
            recording.setIconMaterial(readOptionalString(in));
            readPlaybackSettings(in, recording.playbackSettings());

            int worldCount = VarInts.readUnsigned(in);
            String[] worlds = new String[worldCount];
            for (int i = 0; i < worldCount; i++) {
                worlds[i] = in.readUTF();
            }

            int trackCount = VarInts.readUnsigned(in);
            for (int t = 0; t < trackCount; t++) {
                UUID playerId = readUuid(in);
                String name = in.readUTF();
                String texture = readOptionalString(in);
                String signature = readOptionalString(in);
                Track track = new Track(playerId, name, texture, signature);
                if (version >= VERSION_V5) {
                    track.setSkinParts(in.readByte());
                    track.setEntityReach(in.readFloat());
                }
                int frameCount = VarInts.readUnsigned(in);
                int tick = 0;
                for (int f = 0; f < frameCount; f++) {
                    tick += VarInts.readUnsigned(in);
                    Pose pose = readPose(in, worlds);
                    int pingMs = version >= VERSION_V5 ? in.readUnsignedShort() : 0;
                    int actionCount = VarInts.readUnsigned(in);
                    ActionData[] actions = new ActionData[actionCount];
                    for (int a = 0; a < actionCount; a++) {
                        actions[a] = readAction(in, worlds);
                    }
                    track.putFrame(tick, new Frame(pose, actions, pingMs));
                }
                recording.addTrack(track);
            }

            if (version >= VERSION_V2) {
                recording.setWorldScene(readWorldScene(in, version));
            }
            if (version >= VERSION_V4) {
                recording.setGameType(readOptionalString(in));
            }
            return recording;
        }
    }

    private static void writeWorldScene(DataOutputStream out, WorldScene scene) throws IOException {
        if (scene == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        CaptureBounds bounds = scene.bounds();
        out.writeUTF(bounds.world());
        out.writeInt(bounds.minX());
        out.writeInt(bounds.minY());
        out.writeInt(bounds.minZ());
        out.writeInt(bounds.maxX());
        out.writeInt(bounds.maxY());
        out.writeInt(bounds.maxZ());

        WorldSnapshot snapshot = scene.snapshot();
        VarInts.writeUnsigned(out, snapshot.palette().size());
        for (String entry : snapshot.palette()) {
            out.writeUTF(entry);
        }
        VarInts.writeUnsigned(out, snapshot.size());
        for (int i = 0; i < snapshot.size(); i++) {
            VarInts.writeUnsigned(out, snapshot.relX(i));
            VarInts.writeUnsigned(out, snapshot.relY(i));
            VarInts.writeUnsigned(out, snapshot.relZ(i));
            VarInts.writeUnsigned(out, snapshot.paletteIndex(i));
        }

        VarInts.writeUnsigned(out, scene.entities().size());
        for (EntitySnapshot entity : scene.entities()) {
            VarInts.writeUnsigned(out, entity.captureId());
            out.writeUTF(entity.entityType());
            out.writeFloat(entity.relX());
            out.writeFloat(entity.relY());
            out.writeFloat(entity.relZ());
            out.writeFloat(entity.yaw());
            out.writeFloat(entity.pitch());
            out.writeFloat(entity.health());
        }

            List<SceneEvent> events = scene.events();
        VarInts.writeUnsigned(out, events.size());
        int previousTick = 0;
        for (SceneEvent event : events) {
            VarInts.writeUnsigned(out, event.tick() - previousTick);
            previousTick = event.tick();
            writeSceneEvent(out, event);
        }
    }

    private static WorldScene readWorldScene(DataInputStream in, int version) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        CaptureBounds bounds = new CaptureBounds(
            in.readUTF(),
            in.readInt(),
            in.readInt(),
            in.readInt(),
            in.readInt(),
            in.readInt(),
            in.readInt()
        );

        int paletteSize = VarInts.readUnsigned(in);
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(in.readUTF());
        }
        int blockCount = VarInts.readUnsigned(in);
        int[] rx = new int[blockCount];
        int[] ry = new int[blockCount];
        int[] rz = new int[blockCount];
        int[] pi = new int[blockCount];
        for (int i = 0; i < blockCount; i++) {
            rx[i] = VarInts.readUnsigned(in);
            ry[i] = VarInts.readUnsigned(in);
            rz[i] = VarInts.readUnsigned(in);
            pi[i] = VarInts.readUnsigned(in);
        }
        WorldSnapshot snapshot = new WorldSnapshot(palette, rx, ry, rz, pi);

        int entityCount = VarInts.readUnsigned(in);
        List<EntitySnapshot> entities = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            entities.add(new EntitySnapshot(
                VarInts.readUnsigned(in),
                in.readUTF(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat(),
                in.readFloat()
            ));
        }

        WorldScene scene = new WorldScene(bounds, snapshot, entities);
        int eventCount = VarInts.readUnsigned(in);
        List<SceneEvent> events = new ArrayList<>(eventCount);
        int tick = 0;
        for (int i = 0; i < eventCount; i++) {
            tick += VarInts.readUnsigned(in);
            events.add(readSceneEvent(in, tick, version));
        }
        scene.setEvents(events);
        return scene;
    }

    private static void writeSceneEvent(DataOutputStream out, SceneEvent event) throws IOException {
        out.writeByte(event.type().ordinal());
        switch (event.type()) {
            case BLOCK_SET -> {
                VarInts.writeUnsigned(out, event.relX());
                VarInts.writeUnsigned(out, event.relY());
                VarInts.writeUnsigned(out, event.relZ());
                out.writeUTF(event.blockData() == null ? "minecraft:air" : event.blockData());
            }
            case BLOCK_BREAK -> {
                VarInts.writeUnsigned(out, event.relX());
                VarInts.writeUnsigned(out, event.relY());
                VarInts.writeUnsigned(out, event.relZ());
            }
            case EXPLOSION -> {
                VarInts.writeUnsigned(out, event.relX());
                VarInts.writeUnsigned(out, event.relY());
                VarInts.writeUnsigned(out, event.relZ());
                out.writeFloat(event.power());
            }
            case ENTITY_SPAWN, PROJECTILE_SPAWN -> {
                VarInts.writeUnsigned(out, event.entityId());
                out.writeUTF(event.entityType() == null ? "PIG" : event.entityType());
                out.writeFloat(event.posX());
                out.writeFloat(event.posY());
                out.writeFloat(event.posZ());
                out.writeFloat(event.yaw());
                out.writeFloat(event.pitch());
                out.writeFloat(event.velX());
                out.writeFloat(event.velY());
                out.writeFloat(event.velZ());
            }
            case ENTITY_MOVE -> {
                VarInts.writeUnsigned(out, event.entityId());
                out.writeFloat(event.posX());
                out.writeFloat(event.posY());
                out.writeFloat(event.posZ());
                out.writeFloat(event.yaw());
                out.writeFloat(event.pitch());
                out.writeFloat(event.velX());
                out.writeFloat(event.velY());
                out.writeFloat(event.velZ());
            }
            case ENTITY_DAMAGE -> {
                VarInts.writeUnsigned(out, event.entityId());
                out.writeFloat(event.amount());
                out.writeInt(event.otherEntityId());
            }
            case ENTITY_DEATH, ENTITY_REMOVE -> VarInts.writeUnsigned(out, event.entityId());
        }
    }

    private static SceneEvent readSceneEvent(DataInputStream in, int tick, int version) throws IOException {
        SceneEventType type = SceneEventType.byId(in.readUnsignedByte());
        return switch (type) {
            case BLOCK_SET -> SceneEvent.blockSet(
                tick,
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in),
                in.readUTF()
            );
            case BLOCK_BREAK -> SceneEvent.blockBreak(
                tick,
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in)
            );
            case EXPLOSION -> SceneEvent.explosion(
                tick,
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in),
                VarInts.readUnsigned(in),
                in.readFloat()
            );
            case ENTITY_SPAWN -> readEntityPoseEvent(in, tick, version, false);
            case PROJECTILE_SPAWN -> readEntityPoseEvent(in, tick, version, true);
            case ENTITY_MOVE -> readEntityMoveEvent(in, tick, version);
            case ENTITY_DAMAGE -> SceneEvent.entityDamage(
                tick,
                VarInts.readUnsigned(in),
                in.readFloat(),
                in.readInt()
            );
            case ENTITY_DEATH -> SceneEvent.entityDeath(tick, VarInts.readUnsigned(in));
            case ENTITY_REMOVE -> SceneEvent.entityRemove(tick, VarInts.readUnsigned(in));
        };
    }

    private static SceneEvent readEntityPoseEvent(
        DataInputStream in,
        int tick,
        int version,
        boolean projectile
    ) throws IOException {
        int entityId = VarInts.readUnsigned(in);
        String entityType = in.readUTF();
        float x;
        float y;
        float z;
        float yaw;
        float pitch;
        float vx = 0;
        float vy = 0;
        float vz = 0;
        if (version >= VERSION_V3) {
            x = in.readFloat();
            y = in.readFloat();
            z = in.readFloat();
            yaw = in.readFloat();
            pitch = in.readFloat();
            vx = in.readFloat();
            vy = in.readFloat();
            vz = in.readFloat();
        } else {
            x = VarInts.readUnsigned(in);
            y = VarInts.readUnsigned(in);
            z = VarInts.readUnsigned(in);
            yaw = in.readFloat();
            pitch = in.readFloat();
        }
        return projectile
            ? SceneEvent.projectileSpawn(tick, entityId, entityType, x, y, z, yaw, pitch, vx, vy, vz)
            : SceneEvent.entitySpawn(tick, entityId, entityType, x, y, z, yaw, pitch, vx, vy, vz);
    }

    private static SceneEvent readEntityMoveEvent(DataInputStream in, int tick, int version) throws IOException {
        int entityId = VarInts.readUnsigned(in);
        float x;
        float y;
        float z;
        float yaw;
        float pitch;
        float vx = 0;
        float vy = 0;
        float vz = 0;
        if (version >= VERSION_V3) {
            x = in.readFloat();
            y = in.readFloat();
            z = in.readFloat();
            yaw = in.readFloat();
            pitch = in.readFloat();
            vx = in.readFloat();
            vy = in.readFloat();
            vz = in.readFloat();
        } else {
            x = VarInts.readUnsigned(in);
            y = VarInts.readUnsigned(in);
            z = VarInts.readUnsigned(in);
            yaw = in.readFloat();
            pitch = in.readFloat();
        }
        return SceneEvent.entityMove(tick, entityId, x, y, z, yaw, pitch, vx, vy, vz);
    }

    private static void collectWorlds(Recording recording, List<String> worlds, Map<String, Integer> worldIndex) {
        for (Track track : recording.tracks()) {
            for (int i = 0; i < track.size(); i++) {
                Frame frame = track.frameAt(i);
                indexWorld(frame.pose().world(), worlds, worldIndex);
                for (ActionData action : frame.actions()) {
                    if (action.blockPose() != null) {
                        indexWorld(action.blockPose().world(), worlds, worldIndex);
                    }
                }
            }
        }
        if (recording.worldScene() != null) {
            indexWorld(recording.worldScene().bounds().world(), worlds, worldIndex);
        }
    }

    private static void indexWorld(String world, List<String> worlds, Map<String, Integer> worldIndex) {
        worldIndex.computeIfAbsent(world, key -> {
            worlds.add(key);
            return worlds.size() - 1;
        });
    }

    private static void writePlaybackSettings(DataOutputStream out, PlaybackSettings settings) throws IOException {
        out.writeBoolean(settings.loop());
        out.writeInt(settings.loopCount());
        out.writeByte(settings.visibilityMode().ordinal());
        out.writeDouble(settings.defaultSpeed());
    }

    private static void readPlaybackSettings(DataInputStream in, PlaybackSettings settings) throws IOException {
        settings.setLoop(in.readBoolean());
        settings.setLoopCount(in.readInt());
        settings.setVisibilityMode(VisibilityMode.values()[in.readUnsignedByte()]);
        settings.setDefaultSpeed(in.readDouble());
    }

    private static void writePose(DataOutputStream out, Pose pose, Map<String, Integer> worldIndex) throws IOException {
        VarInts.writeUnsigned(out, worldIndex.get(pose.world()));
        out.writeFloat(pose.x());
        out.writeFloat(pose.y());
        out.writeFloat(pose.z());
        out.writeFloat(pose.yaw());
        out.writeFloat(pose.pitch());
    }

    private static Pose readPose(DataInputStream in, String[] worlds) throws IOException {
        int world = VarInts.readUnsigned(in);
        return new Pose(worlds[world], in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void writeAction(
        DataOutputStream out,
        ActionData action,
        Map<String, Integer> worldIndex
    ) throws IOException {
        out.writeByte(action.type().ordinal());
        switch (action.type()) {
            case SWING, SNEAK, SPRINT, SWIM, GLIDE, BLOCK, FISHING -> out.writeBoolean(action.flag());
            case USE_ITEM -> out.writeByte(action.handState());
            case TOTEM -> {
            }
            case EQUIPMENT -> {
                out.writeByte(action.slot().ordinal());
                ItemBinary.write(out, action.item());
            }
            case CHEST, SLEEP -> {
                out.writeBoolean(action.flag());
                if (action.blockPose() != null) {
                    out.writeBoolean(true);
                    writePose(out, action.blockPose(), worldIndex);
                } else {
                    out.writeBoolean(false);
                }
            }
            case VEHICLE -> {
                out.writeBoolean(action.flag());
                out.writeUTF(action.vehicleType() == null ? "MINECART" : action.vehicleType());
            }
        }
    }

    private static ActionData readAction(DataInputStream in, String[] worlds) throws IOException {
        ActionType type = ActionType.byId(in.readUnsignedByte());
        return switch (type) {
            case SWING -> ActionData.swing(in.readBoolean());
            case SNEAK -> ActionData.sneak(in.readBoolean());
            case SPRINT -> ActionData.sprint(in.readBoolean());
            case SWIM -> ActionData.swim(in.readBoolean());
            case GLIDE -> ActionData.glide(in.readBoolean());
            case BLOCK -> ActionData.block(in.readBoolean());
            case FISHING -> ActionData.fishing(in.readBoolean());
            case USE_ITEM -> ActionData.useItem(in.readByte());
            case TOTEM -> ActionData.totem();
            case EQUIPMENT -> ActionData.equipment(EquipmentSlot.values()[in.readUnsignedByte()], ItemBinary.read(in));
            case CHEST -> {
                boolean opening = in.readBoolean();
                Pose pose = in.readBoolean() ? readPose(in, worlds) : null;
                yield ActionData.chest(pose, opening);
            }
            case SLEEP -> {
                boolean sleeping = in.readBoolean();
                Pose pose = in.readBoolean() ? readPose(in, worlds) : null;
                yield ActionData.sleep(sleeping, pose);
            }
            case VEHICLE -> {
                boolean entering = in.readBoolean();
                yield ActionData.vehicle(in.readUTF(), entering);
            }
        };
    }

    private static void writeOptionalString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readOptionalString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
