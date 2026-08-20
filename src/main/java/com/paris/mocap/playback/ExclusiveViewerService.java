package com.paris.mocap.playback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExclusiveViewerService implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, UUID> viewerSessions = new ConcurrentHashMap<>();

    public ExclusiveViewerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void attach(PlaybackSession session) {
        if (session == null || !session.exclusive() || session.requesterId() == null) {
            return;
        }
        this.viewerSessions.put(session.requesterId(), session.id());
        Player viewer = session.requester();
        if (viewer != null && viewer.isOnline()) {
            isolate(viewer);
        }
    }

    public void detach(PlaybackSession session) {
        if (session == null || session.requesterId() == null) {
            return;
        }
        UUID viewerId = session.requesterId();
        this.viewerSessions.remove(viewerId, session.id());
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer != null && viewer.isOnline()) {
            reveal(viewer);
            restoreOthers();
        }
    }

    public boolean isExclusiveViewer(UUID playerId) {
        return playerId != null && this.viewerSessions.containsKey(playerId);
    }

    private void isolate(Player viewer) {
        World world = viewer.getWorld();
        if (world == null) {
            return;
        }
        for (Player other : world.getPlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            hideBodies(viewer, other);
        }
    }

    private void hideBodies(Player a, Player b) {
        a.hideEntity(this.plugin, b);
        b.hideEntity(this.plugin, a);
    }

    private void reveal(Player viewer) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            viewer.showEntity(this.plugin, other);
            other.showEntity(this.plugin, viewer);
        }
    }

    private void restoreOthers() {
        for (UUID id : this.viewerSessions.keySet()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.isOnline()) {
                isolate(viewer);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (this.viewerSessions.containsKey(joined.getUniqueId())) {
            isolate(joined);
            return;
        }
        hideFromExclusiveViewers(joined);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (this.viewerSessions.containsKey(player.getUniqueId())) {
            isolate(player);
            return;
        }
        hideFromExclusiveViewers(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.viewerSessions.remove(event.getPlayer().getUniqueId());
    }

    private void hideFromExclusiveViewers(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        for (Player other : world.getPlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (this.viewerSessions.containsKey(other.getUniqueId())) {
                hideBodies(other, player);
            }
        }
    }
}
