package com.paris.mocap.recording;

import com.paris.mocap.model.ActionData;
import com.paris.mocap.model.Pose;
import com.paris.mocap.model.RecordingSettings;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ActionCaptureListener implements Listener {
    private final RecordingService recordings;

    public ActionCaptureListener(RecordingService recordings) {
        this.recordings = recordings;
    }

    private RecordingSettings settings() {
        return this.recordings.settings();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent event) {
        if (!settings().recordAnimations() || !this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        boolean offHand = event.getAnimationType() == PlayerAnimationType.OFF_ARM_SWING;
        this.recordings.recordAction(event.getPlayer(), ActionData.swing(offHand));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!settings().recordAnimations() || !this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.recordAction(player, ActionData.swing(false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!this.recordings.isRecording(player)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        if (!settings().recordAnimations() && !settings().recordBlocking()) {
            return;
        }
        ItemStack item = event.getItem();
        boolean usable = item != null && !item.getType().isAir();
        boolean placeLike = action == Action.RIGHT_CLICK_BLOCK;
        if (!usable && !placeLike) {
            return;
        }
        byte handState = event.getHand() == EquipmentSlot.OFF_HAND ? (byte) 3 : (byte) 1;

        this.recordings.recordAction(player, ActionData.useItem(handState));
        this.recordings.recordAction(player, ActionData.useItem((byte) 0));
        if (settings().recordAnimations()) {
            this.recordings.recordAction(player, ActionData.swing(event.getHand() == EquipmentSlot.OFF_HAND));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        if (!settings().recordSneak() || !this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        this.recordings.recordAction(event.getPlayer(), ActionData.sneak(event.isSneaking()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEquipment(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!settings().recordEquipment() || !this.recordings.isRecording(player)) {
            return;
        }
        event.getEquipmentChanges().forEach((slot, change) ->
            this.recordings.recordAction(player, ActionData.equipment(slot, change.newItem()))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!settings().recordEquipment() || !this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        this.recordings.recordAction(
            player,
            ActionData.equipment(EquipmentSlot.HAND, player.getInventory().getItem(event.getNewSlot()))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!settings().recordEquipment() || !this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        this.recordings.recordAction(player, ActionData.equipment(EquipmentSlot.HAND, event.getMainHandItem()));
        this.recordings.recordAction(player, ActionData.equipment(EquipmentSlot.OFF_HAND, event.getOffHandItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.recordAction(player, ActionData.glide(event.isGliding()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!settings().recordChestOpen() || !this.recordings.isRecording(player)) {
            return;
        }
        if (!isContainer(event.getInventory().getType())) {
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block != null) {
            this.recordings.recordAction(player, ActionData.chest(Pose.from(block.getLocation()), true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!settings().recordChestOpen() || !this.recordings.isRecording(player)) {
            return;
        }
        if (!isContainer(event.getInventory().getType())) {
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block != null) {
            this.recordings.recordAction(player, ActionData.chest(Pose.from(block.getLocation()), false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!settings().recordFishing() || !this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        boolean casting = event.getState() == PlayerFishEvent.State.FISHING;
        boolean reeling = event.getState() == PlayerFishEvent.State.REEL_IN
            || event.getState() == PlayerFishEvent.State.IN_GROUND
            || event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT
            || event.getState() == PlayerFishEvent.State.CAUGHT_FISH
            || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY;
        if (casting) {
            this.recordings.recordAction(event.getPlayer(), ActionData.fishing(true));
        } else if (reeling) {
            this.recordings.recordAction(event.getPlayer(), ActionData.fishing(false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player) || !this.recordings.isRecording(player)) {
            return;
        }
        EntityType type = event.getVehicle().getType();
        this.recordings.recordAction(player, ActionData.vehicle(type.name(), true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player) || !this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.recordAction(player, ActionData.vehicle(event.getVehicle().getType().name(), false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        this.recordings.setBedLocation(event.getPlayer().getUniqueId(), event.getBed().getLocation());
        this.recordings.recordAction(
            event.getPlayer(),
            ActionData.sleep(true, Pose.from(event.getBed().getLocation()))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        if (!this.recordings.isRecording(event.getPlayer())) {
            return;
        }
        this.recordings.setBedLocation(event.getPlayer().getUniqueId(), null);
        this.recordings.recordAction(event.getPlayer(), ActionData.sleep(false, null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.recordAction(player, ActionData.totem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!this.recordings.isRecording(player)) {
            return;
        }
        if (settings().recordAnimations()) {
            this.recordings.recordAction(player, ActionData.swing(event.getHand() == EquipmentSlot.OFF_HAND));
        }
        this.recordings.noteBlockChange(player, event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!this.recordings.isRecording(player)) {
            return;
        }
        if (settings().recordAnimations()) {
            this.recordings.recordAction(player, ActionData.swing(false));
        }
        this.recordings.noteBlockChange(player, event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.noteBlockChange(player, event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!this.recordings.isRecording(player)) {
            return;
        }
        this.recordings.noteBlockChange(player, event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    private static boolean isContainer(InventoryType type) {
        return type == InventoryType.CHEST
            || type == InventoryType.ENDER_CHEST
            || type == InventoryType.BARREL
            || type == InventoryType.SHULKER_BOX;
    }
}
