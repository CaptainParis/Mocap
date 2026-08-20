package com.paris.mocap.actor;

import com.paris.mocap.config.MocapConfig;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.Track;
import com.paris.mocap.model.VisibilityMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ActorService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final MocapConfig config;
    private final EntityIdAllocator ids;
    private final ActorPacketService packets;
    private final Map<Integer, PacketActor> actors = new ConcurrentHashMap<>();
    private BukkitTask viewerTask;

    public ActorService(JavaPlugin plugin, MocapConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.ids = new EntityIdAllocator(config.entityIdBase());
        this.packets = new ActorPacketService(plugin);
    }

    public void start() {
        this.viewerTask = Bukkit.getScheduler().runTaskTimer(
            this.plugin,
            this::refreshViewers,
            this.config.viewerRefreshTicks(),
            this.config.viewerRefreshTicks()
        );
    }

    public PacketActor create(Track track, Pose spawnPose, VisibilityMode visibility) {

        UUID actorId = track.playerId();
        if (Bukkit.getPlayer(actorId) != null) {
            actorId = new UUID(
                actorId.getMostSignificantBits() ^ 0x4D4F4341504C555FL,
                actorId.getLeastSignificantBits() ^ 0x534B494E55554944L
            );
        }
        PacketActor actor = new PacketActor(
            this.ids.nextId(),
            actorId,
            track.playerName(),
            track.skinTexture(),
            track.skinSignature(),
            spawnPose
        );
        actor.setVisibilityMode(visibility);
        actor.setSkinParts(track.skinParts());
        actor.setEntityReach(track.entityReach());
        bindNametagTeam(actor);
        this.actors.put(actor.entityId(), actor);
        return actor;
    }

    public void revealNearby(PacketActor actor) {
        if (actor == null) {
            return;
        }
        Pose pose = actor.pose();
        World world = Bukkit.getWorld(pose.world());
        if (world == null) {
            return;
        }
        Location anchor = new Location(world, pose.x(), pose.y(), pose.z());
        double rangeSq = this.config.viewDistanceSquared();
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = player.getWorld() == world
                && actor.isVisibleTo(player)
                && player.getLocation().distanceSquared(anchor) <= rangeSq;
            if (!shouldSee) {
                continue;
            }
            if (actor.addViewer(player.getUniqueId())) {
                this.packets.spawnFor(actor, player);
            }
        }
    }

    public void destroy(PacketActor actor) {
        if (actor == null) {
            return;
        }
        this.actors.remove(actor.entityId());
        unbindNametagTeam(actor);
        for (Player viewer : this.packets.resolveViewers(actor)) {
            this.packets.despawnFor(actor, viewer);
        }
        actor.clearViewers();
    }

    public void destroyAll(Collection<PacketActor> actors) {
        for (PacketActor actor : actors) {
            destroy(actor);
        }
    }

    public ActorPacketService packets() {
        return this.packets;
    }

    public JavaPlugin plugin() {
        return this.plugin;
    }

    public void teleport(PacketActor actor) {
        this.packets.teleport(actor, this.packets.resolveViewers(actor));
    }

    public void syncMetadata(PacketActor actor) {
        this.packets.metadata(actor, this.packets.resolveViewers(actor));
    }

    public void syncEquipment(PacketActor actor) {
        this.packets.equipment(actor, this.packets.resolveViewers(actor));
    }

    private void bindNametagTeam(PacketActor actor) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(actor.teamName());
        if (team == null) {
            team = board.registerNewTeam(actor.teamName());
        }
        team.setOption(
            Team.Option.NAME_TAG_VISIBILITY,
            actor.nametagHidden() ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS
        );
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        try {
            team.displayName(net.kyori.adventure.text.Component.text(actor.name()));
            team.prefix(net.kyori.adventure.text.Component.empty());
            team.suffix(net.kyori.adventure.text.Component.empty());
        } catch (Throwable ignored) {
        }
        if (!team.hasEntry(actor.profileName())) {
            team.addEntry(actor.profileName());
        }
    }

    private void unbindNametagTeam(PacketActor actor) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(actor.teamName());
        if (team == null) {
            return;
        }
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
        }
    }

    private void refreshViewers() {
        if (this.actors.isEmpty()) {
            return;
        }
        double rangeSq = this.config.viewDistanceSquared();
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        for (PacketActor actor : this.actors.values()) {
            try {
                refreshActor(actor, online, rangeSq);
            } catch (Throwable ignored) {

            }
        }
    }

    private void refreshActor(PacketActor actor, List<Player> online, double rangeSq) {
        Pose pose = actor.pose();
        World world = Bukkit.getWorld(pose.world());
        if (world == null) {
            return;
        }
        Location anchor = new Location(world, pose.x(), pose.y(), pose.z());
        for (Player player : online) {
            boolean shouldSee = player.getWorld() == world
                && actor.isVisibleTo(player)
                && player.getLocation().distanceSquared(anchor) <= rangeSq;
            boolean seeing = actor.viewers().contains(player.getUniqueId());
            if (shouldSee && !seeing) {
                actor.addViewer(player.getUniqueId());
                this.packets.spawnFor(actor, player);
            } else if (!shouldSee && seeing) {
                actor.removeViewer(player.getUniqueId());
                this.packets.despawnFor(actor, player);
            }
        }
        for (UUID viewerId : new ArrayList<>(actor.viewers())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                actor.removeViewer(viewerId);
            }
        }
    }

    @Override
    public void close() {
        if (this.viewerTask != null) {
            this.viewerTask.cancel();
        }
        for (PacketActor actor : List.copyOf(this.actors.values())) {
            destroy(actor);
        }
    }
}
