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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
    private final int skinPartsIndex;
    private boolean warnedLegacyTeleport;

    public ActorPacketService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.positionSyncType = resolvePacketType("ENTITY_POSITION_SYNC");
        this.cachedByteSerializer = resolveByteSerializer();
        this.cachedPoseSerializer = resolvePoseSerializer();
        this.skinPartsIndex = resolveSkinPartsIndex();
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
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.ANIMATION);
        packet.getIntegers().write(0, actor.entityId());
        packet.getIntegers().write(1, offHand ? 3 : 0);
        broadcast(packet, viewers);
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
        packet.getDataValueCollectionModifier().write(0, List.of(new WrappedDataValue(8, serializer, handState)));
        broadcast(packet, viewers);
    }

    private void sendPlayerInfoAdd(PacketActor actor, Player viewer) {
        PacketContainer packet = this.protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
        EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
        );
        packet.getPlayerInfoActions().write(0, actions);

        WrappedGameProfile profile = profileOf(actor);

        PlayerInfoData data = new PlayerInfoData(
            actor.uniqueId(),
            Math.min(4095, actor.pingMs()),
            true,
            EnumWrappers.NativeGameMode.SURVIVAL,
            profile,
            WrappedChatComponent.fromText(trimName(actor.name()))
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

        byte yaw = toAngle(pose.yaw());
        byte pitch = toAngle(pose.pitch());
        try {
            packet.getBytes().write(0, pitch);
            packet.getBytes().write(1, yaw);
        } catch (Exception ex) {
            try {
                packet.getBytes().write(0, yaw);
                packet.getBytes().write(1, pitch);
            } catch (Exception ignored) {
            }
        }
        send(viewer, packet);

        PacketContainer head = this.protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        head.getIntegers().write(0, actor.entityId());
        head.getBytes().write(0, yaw);
        send(viewer, head);
    }

    private void sendTeleport(PacketActor actor, Player viewer) {
        Pose pose = actor.pose();
        PacketType type = this.positionSyncType != null
            ? this.positionSyncType
            : PacketType.Play.Server.ENTITY_TELEPORT;

        PacketContainer packet = this.protocol.createPacket(type);
        packet.getIntegers().write(0, actor.entityId());

        boolean wroteModern = writePositionMoveRotation(packet, pose);
        if (!wroteModern) {

            if (hasPositionMoveRotationStructure(packet)) {
                if (!this.warnedLegacyTeleport) {
                    this.warnedLegacyTeleport = true;
                    this.plugin.getLogger().warning(
                        "Unable to write PositionMoveRotation for ENTITY_TELEPORT; actor motion may stall. "
                            + "Update ProtocolLib if actors do not move."
                    );
                }
                return;
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
        send(viewer, packet);

        PacketContainer head = this.protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        head.getIntegers().write(0, actor.entityId());
        head.getBytes().write(0, toAngle(pose.yaw()));
        send(viewer, head);
    }

    private boolean writePositionMoveRotation(PacketContainer packet, Pose pose) {
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

        packet.getDataValueCollectionModifier().write(0, values);
        send(viewer, packet);
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
        String name = trimName(actor.name());
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
        return (byte) (degrees * 256.0F / 360.0F);
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

    private static int resolveSkinPartsIndex() {
        String[] classes = {
            "net.minecraft.world.entity.Avatar",
            "net.minecraft.world.entity.player.Player"
        };
        String[] fields = {
            "DATA_PLAYER_MODE_CUSTOMISATION",
            "PLAYER_MODE_CUSTOMIZATION_ID"
        };
        for (String className : classes) {
            try {
                Class<?> type = Class.forName(className);
                for (String fieldName : fields) {
                    try {
                        java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object accessor = field.get(null);
                        if (accessor == null) {
                            continue;
                        }
                        for (String methodName : new String[] {"id", "getId"}) {
                            try {
                                Method method = accessor.getClass().getMethod(methodName);
                                Object value = method.invoke(accessor);
                                if (value instanceof Integer index && index >= 0 && index < 64) {
                                    return index;
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
        return 16;
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
