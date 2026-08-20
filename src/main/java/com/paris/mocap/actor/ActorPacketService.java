package com.paris.mocap.actor;

import com.paris.mocap.model.Pose;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class ActorPacketService {
    private final JavaPlugin plugin;
    private final ProtocolManager protocol;
    private final PacketType positionSyncType;
    private final WrappedDataWatcher.Serializer cachedByteSerializer;
    private final WrappedDataWatcher.Serializer cachedPoseSerializer;
    private final WrappedDataWatcher.Serializer cachedOptionalComponentSerializer;
    private final WrappedDataWatcher.Serializer cachedBooleanSerializer;
    private final int skinPartsIndex;
    private final int livingFlagsIndex;
    private final Class<?> pmrClass;
    private final Constructor<?> vec3Ctor;
    private final Constructor<?> pmrCtor;
    private final Constructor<?> positionSyncCtor;
    private boolean warnedLegacyTeleport;
    private boolean warnedSwing;
    private boolean warnedAction;

    public ActorPacketService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.positionSyncType = resolvePacketType("ENTITY_POSITION_SYNC");
        this.cachedByteSerializer = resolveByteSerializer();
        this.cachedPoseSerializer = resolvePoseSerializer();
        this.cachedOptionalComponentSerializer = resolveOptionalComponentSerializer();
        this.cachedBooleanSerializer = resolveBooleanSerializer();
        this.skinPartsIndex = resolveSkinPartsIndex();
        this.livingFlagsIndex = resolveLivingFlagsIndex();
        Class<?> vec3Class = resolveClass("net.minecraft.world.phys.Vec3", "net.minecraft.world.phys.Vec3D");
        this.pmrClass = resolveClass("net.minecraft.world.entity.PositionMoveRotation");
        this.vec3Ctor = constructor(vec3Class, double.class, double.class, double.class);
        this.pmrCtor = constructor(this.pmrClass, vec3Class, vec3Class, float.class, float.class);
        this.positionSyncCtor = constructor(
            resolveClass("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket"),
            int.class,
            this.pmrClass,
            boolean.class
        );
    }

    public void spawnFor(PacketActor actor, Player viewer) {
        try {

            sendPlayerInfoAdd(actor, viewer);
            UUID viewerId = viewer.getUniqueId();
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                Player online = Bukkit.getPlayer(viewerId);
                if (online == null || !online.isOnline() || !actor.viewers().contains(viewerId)) {
                    return;
                }
                try {
                    sendSpawn(actor, online);
                    sendMetadata(actor, online);
                    sendEquipment(actor, online);
                    sendTeleport(actor, online);
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        Player still = Bukkit.getPlayer(viewerId);
                        if (still != null && still.isOnline() && actor.viewers().contains(viewerId)) {
                            sendMetadata(actor, still);
                        }
                    }, 18L);
                } catch (Exception ex) {
                    this.plugin.getLogger().log(Level.WARNING, "Failed delayed actor spawn for " + online.getName(), ex);
                }
            }, 2L);
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "Failed spawning actor for " + viewer.getName(), ex);
        }
    }

    public void despawnFor(PacketActor actor, Player viewer) {
        try {
            sendDestroy(actor, viewer);
            sendPlayerInfoRemove(actor, viewer);
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "Failed despawning actor for " + viewer.getName(), ex);
        }
    }

    public void teleport(PacketActor actor, Iterable<Player> viewers) {
        for (Player viewer : viewers) {
            try {
                sendTeleport(actor, viewer);
            } catch (Exception ex) {
                this.plugin.getLogger().log(Level.FINE, "Teleport failed for " + viewer.getName(), ex);
            }
        }
    }

    public void metadata(PacketActor actor, Iterable<Player> viewers) {
        for (Player viewer : viewers) {
            sendMetadata(actor, viewer);
        }
    }

    public void equipment(PacketActor actor, Iterable<Player> viewers) {
        for (Player viewer : viewers) {
            sendEquipment(actor, viewer);
        }
    }

    public void swing(PacketActor actor, boolean offHand, Iterable<Player> viewers) {
        int animation = offHand ? 3 : 0;
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ANIMATION);
        boolean wrote = writeAnimation(packet, actor.entityId(), animation);
        if (!wrote && !this.warnedSwing) {
            this.warnedSwing = true;
            this.plugin.getLogger().warning(
                "Unable to write ANIMATION packet fields; actor hit/place swings will not play."
            );
        }
        if (wrote) {
            broadcast(packet, viewers);
        }
    }

    public void entityStatus(PacketActor actor, byte status, Iterable<Player> viewers) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_STATUS);
        packet.getIntegers().write(0, actor.entityId());
        packet.getBytes().write(0, status);
        broadcast(packet, viewers);
    }

    public void useItemState(PacketActor actor, byte handState, Iterable<Player> viewers) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, actor.entityId());
        WrappedDataWatcher.Serializer serializer = byteSerializer();
        if (serializer == null) {
            return;
        }
        packet.getDataValueCollectionModifier().write(
            0,
            List.of(new WrappedDataValue(this.livingFlagsIndex, serializer, handState))
        );
        broadcast(packet, viewers);
    }

    private void sendPlayerInfoAdd(PacketActor actor, Player viewer) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
        EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
            EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY
        );
        packet.getPlayerInfoActions().write(0, actions);

        WrappedGameProfile profile = profileOf(actor);
        WrappedChatComponent nametag = WrappedChatComponent.fromText(trimName(actor.name()));

        PlayerInfoData data = new PlayerInfoData(
            actor.uniqueId(),
            Math.min(4095, actor.pingMs()),
            false,
            EnumWrappers.NativeGameMode.SURVIVAL,
            profile,
            nametag
        );

        try {
            packet.getPlayerInfoDataLists().write(1, List.of(data));
        } catch (Exception ex) {
            packet.getPlayerInfoDataLists().write(0, List.of(data));
        }
        send(viewer, packet);
    }

    private void sendPlayerInfoRemove(PacketActor actor, Player viewer) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, List.of(actor.uniqueId()));
        send(viewer, packet);
    }

    private void sendSpawn(PacketActor actor, Player viewer) {
        Pose pose = actor.pose();
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        packet.getIntegers().write(0, actor.entityId());
        packet.getUUIDs().write(0, actor.uniqueId());
        packet.getEntityTypeModifier().write(0, EntityType.PLAYER);
        packet.getDoubles().write(0, (double) pose.x());
        packet.getDoubles().write(1, (double) pose.y());
        packet.getDoubles().write(2, (double) pose.z());
        writeSpawnRotation(packet, pose.yaw(), pose.pitch());
        send(viewer, packet);
        sendLook(actor, viewer, pose);
        sendHeadRotation(actor, viewer, pose.yaw());
    }

    private void sendTeleport(PacketActor actor, Player viewer) {
        Pose pose = actor.pose();
        PacketType type = this.positionSyncType != null
            ? this.positionSyncType
            : PacketType.Play.Server.ENTITY_TELEPORT;

        PacketContainer packet = createPositionPacket(type, actor.entityId(), pose);
        if (packet != null) {
            send(viewer, packet);
        }
        sendLook(actor, viewer, pose);
        sendHeadRotation(actor, viewer, pose.yaw());
    }

    private PacketContainer createPositionPacket(PacketType type, int entityId, Pose pose) {
        Object pmr = newPositionMoveRotation(pose);
        if (pmr != null && this.positionSyncCtor != null && type == this.positionSyncType) {
            try {
                Object handle = this.positionSyncCtor.newInstance(entityId, pmr, true);
                return new PacketContainer(type, handle);
            } catch (Exception ex) {
                this.plugin.getLogger().log(Level.FINE, "Could not construct ENTITY_POSITION_SYNC", ex);
            }
        }

        PacketContainer packet = this.protocol.createPacket(type);
        packet.getIntegers().write(0, entityId);
        boolean wroteModern = writePositionMoveRotation(packet, pose, pmr);
        if (!wroteModern) {
            if (hasPositionMoveRotationStructure(packet)) {
                if (!this.warnedLegacyTeleport) {
                    this.warnedLegacyTeleport = true;
                    this.plugin.getLogger().warning(
                        "Unable to write PositionMoveRotation; actor motion may stall. "
                            + "Update ProtocolLib if actors do not move."
                    );
                }
                return null;
            }
            packet.getDoubles().write(0, (double) pose.x());
            packet.getDoubles().write(1, (double) pose.y());
            packet.getDoubles().write(2, (double) pose.z());
            try {
                packet.getBytes().write(0, toAngle(pose.yaw()));
                packet.getBytes().write(1, toAngle(pose.pitch()));
            } catch (Exception ignored) {
            }
        }

        try {
            if (packet.getBooleans().size() > 0) {
                packet.getBooleans().write(0, true);
            }
        } catch (Exception ignored) {
        }

        tryWriteEmptyRelatives(packet);
        return packet;
    }

    private boolean writePositionMoveRotation(PacketContainer packet, Pose pose, Object pmr) {
        if (pmr != null && this.pmrClass != null) {
            try {
                var modifier = packet.getModifier().withType(this.pmrClass);
                if (modifier.size() > 0) {
                    modifier.write(0, pmr);
                    return true;
                }
            } catch (Exception ex) {
                this.plugin.getLogger().log(Level.FINE, "PositionMoveRotation modifier write failed", ex);
            }
        }
        try {
            if (!hasPositionMoveRotationStructure(packet)) {
                return false;
            }
            InternalStructure change = packet.getStructures().read(0);
            if (change.getVectors().size() < 2) {
                try {
                    change.getVectors().write(0, new Vector(pose.x(), pose.y(), pose.z()));
                    change.getVectors().write(1, new Vector(0, 0, 0));
                } catch (Exception ex) {
                    return false;
                }
            } else {
                change.getVectors().write(0, new Vector(pose.x(), pose.y(), pose.z()));
                change.getVectors().write(1, new Vector(0, 0, 0));
            }

            if (change.getFloat().size() >= 2) {
                change.getFloat().write(0, pose.yaw());
                change.getFloat().write(1, pose.pitch());
            } else if (change.getFloat().size() == 1) {
                change.getFloat().write(0, pose.yaw());
            }

            packet.getStructures().write(0, change);
            return true;
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "PositionMoveRotation write failed, trying legacy", ex);
            return false;
        }
    }

    private Object newPositionMoveRotation(Pose pose) {
        if (this.vec3Ctor == null || this.pmrCtor == null) {
            return null;
        }
        try {
            Object pos = this.vec3Ctor.newInstance((double) pose.x(), (double) pose.y(), (double) pose.z());
            Object vel = this.vec3Ctor.newInstance(0d, 0d, 0d);
            return this.pmrCtor.newInstance(pos, vel, pose.yaw(), pose.pitch());
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "Could not construct PositionMoveRotation", ex);
            return null;
        }
    }

    private void writeSpawnRotation(PacketContainer packet, float yawDeg, float pitchDeg) {
        byte yaw = toAngle(yawDeg);
        byte pitch = toAngle(pitchDeg);
        try {
            int bytes = packet.getBytes().size();
            if (bytes >= 2) {
                packet.getBytes().write(0, pitch);
                packet.getBytes().write(1, yaw);
            }
            if (bytes >= 3) {
                packet.getBytes().write(2, yaw);
            }
            if (bytes >= 2) {
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            int floats = packet.getFloat().size();
            if (floats >= 2) {
                packet.getFloat().write(0, pitchDeg);
                packet.getFloat().write(1, yawDeg);
                if (floats >= 3) {
                    packet.getFloat().write(2, yawDeg);
                }
                return;
            }
        } catch (Exception ignored) {
        }
        writeHandleBytes(packet.getHandle(), pitch, yaw, yaw);
    }

    private void sendLook(PacketActor actor, Player viewer, Pose pose) {
        try {
            PacketContainer look = this.protocol.createPacket(PacketType.Play.Server.ENTITY_LOOK);
            look.getIntegers().write(0, actor.entityId());
            byte yaw = toAngle(pose.yaw());
            byte pitch = toAngle(pose.pitch());
            try {
                look.getBytes().write(0, yaw);
                look.getBytes().write(1, pitch);
            } catch (Exception ex) {
                writeHandleBytes(look.getHandle(), yaw, pitch);
            }
            try {
                if (look.getBooleans().size() > 0) {
                    look.getBooleans().write(0, true);
                }
            } catch (Exception ignored) {
            }
            send(viewer, look);
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "ENTITY_LOOK failed", ex);
        }
    }

    private void sendHeadRotation(PacketActor actor, Player viewer, float yawDeg) {
        try {
            PacketContainer head = this.protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            try {
                head.getIntegers().write(0, actor.entityId());
            } catch (Exception ex) {
                writeHandleInts(head.getHandle(), actor.entityId());
            }
            try {
                head.getBytes().write(0, toAngle(yawDeg));
            } catch (Exception ex) {
                writeHandleBytes(head.getHandle(), toAngle(yawDeg));
            }
            send(viewer, head);
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "ENTITY_HEAD_ROTATION failed", ex);
        }
    }

    private boolean writeAnimation(PacketContainer packet, int entityId, int animation) {
        try {
            var ints = packet.getIntegers();
            if (ints.size() >= 2) {
                ints.write(0, entityId);
                ints.write(1, animation);
                return true;
            }
            if (ints.size() == 1) {
                ints.write(0, entityId);
            }
        } catch (Exception ignored) {
        }
        return writeHandleInts(packet.getHandle(), entityId, animation);
    }

    private static boolean hasPositionMoveRotationStructure(PacketContainer packet) {
        try {
            return packet.getStructures() != null && packet.getStructures().size() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void tryWriteEmptyRelatives(PacketContainer packet) {
        try {
            var sets = packet.getModifier().withType(Set.class);
            if (sets != null && sets.size() > 0) {
                sets.write(0, Collections.emptySet());
            }
        } catch (Exception ignored) {
        }
    }

    private void sendMetadata(PacketActor actor, Player viewer) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, actor.entityId());
        WrappedDataWatcher.Serializer byteSerializer = byteSerializer();
        if (byteSerializer == null) {
            return;
        }

        byte sharedFlags = 0;
        if (actor.sneaking()) {
            sharedFlags |= 0x02;
        }
        if (actor.sprinting()) {
            sharedFlags |= 0x08;
        }
        if (actor.swimming()) {
            sharedFlags |= 0x10;
        }
        if (actor.gliding()) {
            sharedFlags |= 0x80;
        }

        List<WrappedDataValue> values = new ArrayList<>(4);
        values.add(new WrappedDataValue(0, byteSerializer, sharedFlags));

        Object nmsPose = entityPoseHandle(resolveEntityPose(actor));
        WrappedDataWatcher.Serializer poseSerializer = poseSerializer();
        if (nmsPose != null && poseSerializer != null) {

            values.add(new WrappedDataValue(6, poseSerializer, nmsPose));
        }

        values.add(new WrappedDataValue(this.skinPartsIndex, byteSerializer, actor.skinParts()));
        writeNametag(values, actor);

        packet.getDataValueCollectionModifier().write(0, values);
        send(viewer, packet);
    }

    private void writeNametag(List<WrappedDataValue> values, PacketActor actor) {
        if (this.cachedOptionalComponentSerializer == null || this.cachedBooleanSerializer == null) {
            return;
        }
        try {
            WrappedChatComponent name = WrappedChatComponent.fromText(trimName(actor.name()));
            values.add(new WrappedDataValue(
                2,
                this.cachedOptionalComponentSerializer,
                Optional.of(name.getHandle())
            ));
            values.add(new WrappedDataValue(
                3,
                this.cachedBooleanSerializer,
                !actor.nametagHidden()
            ));
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.FINE, "Could not write actor nametag metadata", ex);
        }
    }

    private void sendEquipment(PacketActor actor, Player viewer) {
        List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> slots = new ArrayList<>(6);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.name().equals("BODY")) {
                continue;
            }
            EnumWrappers.ItemSlot mapped = mapSlot(slot);
            if (mapped == null) {
                continue;
            }
            ItemStack item = actor.equipment(slot);
            if (item == null || item.getType().isAir()) {
                item = ItemStack.empty();
            }
            slots.add(new com.comphenix.protocol.wrappers.Pair<>(mapped, item));
        }
        if (slots.isEmpty()) {
            return;
        }
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, actor.entityId());
        packet.getSlotStackPairLists().write(0, slots);
        send(viewer, packet);
    }

    private void sendDestroy(PacketActor actor, Player viewer) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntLists().write(0, List.of(actor.entityId()));
        send(viewer, packet);
    }

    private WrappedGameProfile profileOf(PacketActor actor) {
        String texture = actor.skinTexture();
        String signature = actor.skinSignature() == null ? "" : actor.skinSignature();
        String name = actor.profileName();
        UUID uuid = actor.uniqueId();

        if (texture == null || texture.isEmpty()) {
            this.plugin.getLogger().warning(
                "Actor '" + actor.name() + "' has no skin textures; client will show default skin."
            );
            return new WrappedGameProfile(uuid, name);
        }
        if (signature.isEmpty()) {
            this.plugin.getLogger().warning(
                "Actor '" + actor.name() + "' skin signature is empty; skin may not render in online-mode."
            );
        }

        try {
            return buildProfileWithTextures(uuid, name, texture, signature);
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "Could not apply skin textures to actor profile", ex);
            return new WrappedGameProfile(uuid, name);
        }
    }

    private WrappedGameProfile buildProfileWithTextures(
        UUID uuid,
        String name,
        String texture,
        String signature
    ) throws ReflectiveOperationException {

        try {
            com.destroystokyo.paper.profile.PlayerProfile paper =
                Bukkit.createProfileExact(uuid, name);
            paper.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                "textures", texture, signature
            ));
            Object handle = invokeNoArg(paper, "getGameProfile", "getProfile");
            if (handle != null) {
                return WrappedGameProfile.fromHandle(handle);
            }
        } catch (Throwable ignored) {
        }

        Object property = newProperty(texture, signature);
        Object propertyMap = newPropertyMapWithTextures(property);
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        try {
            Constructor<?> ctor = gameProfileClass.getConstructor(
                UUID.class, String.class, propertyMap.getClass()
            );
            return WrappedGameProfile.fromHandle(ctor.newInstance(uuid, name, propertyMap));
        } catch (NoSuchMethodException ignored) {

            for (Constructor<?> ctor : gameProfileClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3
                    && params[0] == UUID.class
                    && params[1] == String.class
                    && params[2].isAssignableFrom(propertyMap.getClass())) {
                    return WrappedGameProfile.fromHandle(ctor.newInstance(uuid, name, propertyMap));
                }
            }
        }

        Object handle = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid, name);
        Object map = invokeNoArg(handle, "properties", "getProperties");
        if (map == null) {
            map = readPropertiesField(handle);
        }
        if (map != null) {
            tryPut(map, "textures", property);
            return WrappedGameProfile.fromHandle(handle);
        }

        throw new NoSuchMethodException("Unable to attach GameProfile textures on this server");
    }

    private static Object newProperty(String texture, String signature) throws ReflectiveOperationException {
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

        try {
            return propertyClass.getConstructor(String.class, String.class, String.class)
                .newInstance("textures", texture, signature);
        } catch (NoSuchMethodException ex) {
            return propertyClass.getConstructor(String.class, String.class)
                .newInstance("textures", texture);
        }
    }

    private static Object newPropertyMapWithTextures(Object property) throws ReflectiveOperationException {
        Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
        Object map;
        try {
            map = propertyMapClass.getConstructor().newInstance();
        } catch (NoSuchMethodException ex) {

            try {
                Class<?> hashMultimap = Class.forName("com.google.common.collect.HashMultimap");
                Object multimap = hashMultimap.getMethod("create").invoke(null);
                hashMultimap.getMethod("put", Object.class, Object.class).invoke(multimap, "textures", property);
                return propertyMapClass.getConstructor(Class.forName("com.google.common.collect.Multimap"))
                    .newInstance(multimap);
            } catch (ReflectiveOperationException nested) {
                throw ex;
            }
        }
        tryPut(map, "textures", property);
        return map;
    }

    private static void tryPut(Object map, String key, Object property) throws ReflectiveOperationException {

        try {
            findPut(map.getClass()).invoke(map, key, property);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            map.getClass().getMethod("put", String.class, property.getClass()).invoke(map, key, property);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        Object inner = readField(map, "properties", "map", "delegate");
        if (inner != null && inner != map) {
            findPut(inner.getClass()).invoke(inner, key, property);
            return;
        }
        throw new NoSuchMethodException("PropertyMap.put unavailable (immutable?)");
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object readPropertiesField(Object handle) {
        return readField(handle, "properties", "propertyMap");
    }

    private static Object readField(Object target, String... names) {
        Class<?> cursor = target.getClass();
        while (cursor != null) {
            for (String name : names) {
                try {
                    var field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static Method findPut(Class<?> type) throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getMethod("put", Object.class, Object.class);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException("put(Object,Object) on " + type.getName());
    }

    private static String trimName(String name) {
        if (name == null || name.isEmpty()) {
            return "Mocap";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    private static byte toAngle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private EnumWrappers.ItemSlot mapSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> EnumWrappers.ItemSlot.MAINHAND;
            case OFF_HAND -> EnumWrappers.ItemSlot.OFFHAND;
            case FEET -> EnumWrappers.ItemSlot.FEET;
            case LEGS -> EnumWrappers.ItemSlot.LEGS;
            case CHEST -> EnumWrappers.ItemSlot.CHEST;
            case HEAD -> EnumWrappers.ItemSlot.HEAD;
            default -> null;
        };
    }

    private static EnumWrappers.EntityPose resolveEntityPose(PacketActor actor) {
        if (actor.sleeping()) {
            return EnumWrappers.EntityPose.SLEEPING;
        }
        if (actor.swimming()) {
            return EnumWrappers.EntityPose.SWIMMING;
        }
        if (actor.gliding()) {
            return EnumWrappers.EntityPose.FALL_FLYING;
        }
        if (actor.sneaking()) {
            return EnumWrappers.EntityPose.CROUCHING;
        }
        return EnumWrappers.EntityPose.STANDING;
    }

    private static Object entityPoseHandle(EnumWrappers.EntityPose pose) {
        try {
            return pose.toNms();
        } catch (Exception ex) {
            try {
                return EnumWrappers.getEntityPoseConverter().getGeneric(pose);
            } catch (Exception ex2) {
                return null;
            }
        }
    }

    private WrappedDataWatcher.Serializer byteSerializer() {
        return this.cachedByteSerializer;
    }

    private WrappedDataWatcher.Serializer poseSerializer() {
        return this.cachedPoseSerializer;
    }

    private static WrappedDataWatcher.Serializer resolveByteSerializer() {
        try {
            return WrappedDataWatcher.Registry.get(Byte.class, false);
        } catch (Exception ex) {
            try {
                return WrappedDataWatcher.Registry.get(Byte.class);
            } catch (Exception ex2) {
                return null;
            }
        }
    }

    private static WrappedDataWatcher.Serializer resolvePoseSerializer() {
        try {
            return WrappedDataWatcher.Registry.get(EnumWrappers.getEntityPoseClass());
        } catch (Exception ex) {
            return null;
        }
    }

    private static WrappedDataWatcher.Serializer resolveOptionalComponentSerializer() {
        try {
            return WrappedDataWatcher.Registry.getChatComponentSerializer(true);
        } catch (Exception ex) {
            try {
                return WrappedDataWatcher.Registry.get(
                    Class.forName("net.minecraft.network.chat.Component"),
                    true
                );
            } catch (Exception ex2) {
                return null;
            }
        }
    }

    private static WrappedDataWatcher.Serializer resolveBooleanSerializer() {
        try {
            return WrappedDataWatcher.Registry.get(Boolean.class, false);
        } catch (Exception ex) {
            try {
                return WrappedDataWatcher.Registry.get(Boolean.class);
            } catch (Exception ex2) {
                return null;
            }
        }
    }

    private static int resolveLivingFlagsIndex() {
        int index = accessorIndex(
            new String[] {"net.minecraft.world.entity.LivingEntity"},
            new String[] {"DATA_LIVING_ENTITY_FLAGS", "LIVING_FLAGS"}
        );
        return index >= 0 ? index : 8;
    }

    private static int resolveSkinPartsIndex() {
        int index = accessorIndex(
            new String[] {
                "net.minecraft.world.entity.Avatar",
                "net.minecraft.world.entity.player.Player"
            },
            new String[] {
                "DATA_PLAYER_MODE_CUSTOMISATION",
                "PLAYER_MODE_CUSTOMIZATION_ID"
            }
        );
        return index >= 0 ? index : 16;
    }

    private static int accessorIndex(String[] classes, String[] fields) {
        for (String className : classes) {
            try {
                Class<?> type = Class.forName(className);
                for (String fieldName : fields) {
                    try {
                        Field field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object accessor = field.get(null);
                        if (accessor == null) {
                            continue;
                        }
                        for (String methodName : new String[] {"id", "getId"}) {
                            try {
                                Method method = accessor.getClass().getMethod(methodName);
                                Object value = method.invoke(accessor);
                                if (value instanceof Integer resolved && resolved >= 0 && resolved < 64) {
                                    return resolved;
                                }
                            } catch (ReflectiveOperationException ignored) {
                            }
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return -1;
    }

    private static Class<?> resolveClass(String... names) {
        for (String name : names) {
            if (name == null) {
                continue;
            }
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... params) {
        if (type == null) {
            return null;
        }
        for (Class<?> param : params) {
            if (param == null) {
                return null;
            }
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(params);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static boolean writeHandleInts(Object handle, int... values) {
        return writeHandlePrimitives(handle, int.class, values, null);
    }

    private static boolean writeHandleBytes(Object handle, byte... values) {
        return writeHandlePrimitives(handle, byte.class, null, values);
    }

    private static boolean writeHandlePrimitives(Object handle, Class<?> primitive, int[] ints, byte[] bytes) {
        if (handle == null) {
            return false;
        }
        int needed = ints != null ? ints.length : bytes.length;
        int index = 0;
        Class<?> cursor = handle.getClass();
        while (cursor != null && index < needed) {
            for (Field field : cursor.getDeclaredFields()) {
                if (index >= needed) {
                    break;
                }
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != primitive) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (ints != null) {
                        field.setInt(handle, ints[index++]);
                    } else {
                        field.setByte(handle, bytes[index++]);
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return index == needed;
    }

    private static PacketType resolvePacketType(String name) {
        try {
            return (PacketType) PacketType.Play.Server.class.getField(name).get(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private void broadcast(PacketContainer packet, Iterable<Player> viewers) {
        for (Player viewer : viewers) {
            send(viewer, packet);
        }
    }

    private void send(Player viewer, PacketContainer packet) {
        try {
            this.protocol.sendServerPacket(viewer, packet, false);
        } catch (Exception ex) {
            try {
                this.protocol.sendServerPacket(viewer, packet);
            } catch (Exception nested) {
                this.plugin.getLogger().log(Level.FINE, "Packet send failed to " + viewer.getName(), nested);
            }
        }
    }

    public void logActionFailure(String action, Throwable ex) {
        if (!this.warnedAction) {
            this.warnedAction = true;
            this.plugin.getLogger().log(Level.WARNING, "Failed applying actor action " + action, ex);
        } else {
            this.plugin.getLogger().log(Level.FINE, "Failed applying actor action " + action, ex);
        }
    }

    public List<Player> resolveViewers(PacketActor actor) {
        List<Player> players = new ArrayList<>(actor.viewers().size());
        collectViewers(actor, players);
        return players;
    }

    public void collectViewers(PacketActor actor, List<Player> into) {
        for (UUID id : actor.viewers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                into.add(player);
            }
        }
    }
}
