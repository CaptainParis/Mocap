package com.paris.mocap.scene;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class SceneCaptureListener implements Listener {
    private final SceneCaptureService scenes;

    public SceneCaptureListener(SceneCaptureService scenes) {
        this.scenes = scenes;
    }

    private boolean idle() {
        return !this.scenes.isCapturing();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockSet(event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockBreak(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        if (idle()) {
            return;
        }
        recordNewState(event.getBlock(), event.getNewState());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakBlock(BlockBreakBlockEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockBreak(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockBreak(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        if (idle()) {
            return;
        }
        for (BlockState state : event.getBlocks()) {
            this.scenes.observeSoon(state.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluid(FluidLevelChangeEvent event) {
        if (idle()) {
            return;
        }
        recordNewState(event.getBlock(), event.getNewData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (idle()) {
            return;
        }
        Block block = event.getBlock();
        this.scenes.recordBlockSet(block.getLocation(), block.getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockBreak(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockSet(event.getBlock().getLocation(), event.getNewState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockSet(event.getBlock().getLocation(), event.getNewState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordBlockSet(event.getBlock().getLocation(), event.getNewState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.observeSoon(event.getBlock().getLocation());
        this.scenes.observeSoon(event.getToBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (idle()) {
            return;
        }
        Block block = event.getBlock();
        this.scenes.recordBlockSet(block.getLocation(), block.getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (idle()) {
            return;
        }
        observePiston(event.getBlock(), event.getDirection(), event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (idle()) {
            return;
        }
        observePiston(event.getBlock(), event.getDirection(), event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (idle()) {
            return;
        }
        recordNewState(event.getBlock(), event.getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordExplosion(event.getBlock().getLocation(), event.getYield());
        for (Block block : event.blockList()) {
            this.scenes.recordBlockBreak(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordExplosion(event.getLocation(), event.getYield());
        for (Block block : event.blockList()) {
            this.scenes.recordBlockBreak(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.trackEntity(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.trackEntity(event.getEntity());
        this.scenes.refreshMotion(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordRemove(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordRemove(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordRemove(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.trackEntity(event.getEntity());
        this.scenes.refreshMotion(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.trackEntity(event.getItemDrop());
        this.scenes.refreshMotion(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (idle()) {
            return;
        }
        Entity damager = event.getDamager();

        if (damager instanceof org.bukkit.entity.Projectile) {
            this.scenes.trackEntity(damager);
        }
        if (!(event.getEntity() instanceof Player)) {
            this.scenes.trackEntity(event.getEntity());
        }
        this.scenes.recordDamage(event.getEntity(), damager, event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (idle()) {
            return;
        }
        this.scenes.recordRemove(event.getEntity());
    }

    private void recordNewState(Block block, BlockData now) {
        if (now == null || now.getMaterial().isAir()) {
            this.scenes.recordBlockBreak(block.getLocation());
            return;
        }
        this.scenes.recordBlockSet(block.getLocation(), now);
    }

    private void observePiston(Block piston, BlockFace direction, List<Block> moved) {
        List<Location> cells = new ArrayList<>(moved.size() * 2 + 4);
        cells.add(piston.getLocation());
        cells.add(piston.getRelative(direction).getLocation());
        for (Block block : moved) {
            cells.add(block.getLocation());
            cells.add(block.getRelative(direction).getLocation());
        }
        this.scenes.observeSoon(cells);
    }
}
