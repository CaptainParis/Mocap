package com.paris.mocap.scene;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class BlockUpdateCapture extends PacketAdapter {
    private final SceneCaptureService scenes;
    private final JavaPlugin plugin;

    BlockUpdateCapture(JavaPlugin plugin, SceneCaptureService scenes) {
        super(
            plugin,
            ListenerPriority.MONITOR,
            PacketType.Play.Server.BLOCK_CHANGE,
            PacketType.Play.Server.MULTI_BLOCK_CHANGE
        );
        this.plugin = plugin;
        this.scenes = scenes;
    }

    void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled() || !this.scenes.isCapturing()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !this.scenes.isPacketSampler(player)) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        PacketContainer packet = event.getPacket();
        PacketType type = event.getPacketType();
        try {
            if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                BlockPosition pos = packet.getBlockPositionModifier().read(0);
                if (pos != null) {
                    observe(world, pos.getX(), pos.getY(), pos.getZ());
                }
                return;
            }
            captureMulti(world, packet);
        } catch (Throwable ignored) {

        }
    }

    private void captureMulti(World world, PacketContainer packet) {
        BlockPosition section = packet.getSectionPositions().read(0);
        short[] locals = packet.getShortArrays().read(0);
        if (section == null || locals == null) {
            return;
        }
        int baseX = section.getX() << 4;
        int baseY = section.getY() << 4;
        int baseZ = section.getZ() << 4;
        for (short packed : locals) {
            int x = baseX + ((packed >> 8) & 0xF);
            int y = baseY + (packed & 0xF);
            int z = baseZ + ((packed >> 4) & 0xF);
            observe(world, x, y, z);
        }
    }

    private void observe(World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        if (Bukkit.isPrimaryThread()) {
            this.scenes.recordObserved(loc);
        } else {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.scenes.recordObserved(loc));
        }
    }
}
