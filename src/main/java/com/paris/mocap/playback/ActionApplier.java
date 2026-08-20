package com.paris.mocap.playback;

import com.paris.mocap.actor.ActorPacketService;
import com.paris.mocap.actor.PacketActor;
import com.paris.mocap.model.ActionData;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

final class ActionApplier {
    private final ActorPacketService packets;

    ActionApplier(ActorPacketService packets) {
        this.packets = packets;
    }

    void apply(PacketActor actor, ActionData action, List<Player> viewers) {
        try {
            switch (action.type()) {
                case SWING -> this.packets.swing(actor, action.flag(), viewers);
                case SNEAK -> {
                    actor.setSneaking(action.flag());
                    this.packets.metadata(actor, viewers);
                }
                case SPRINT -> {
                    actor.setSprinting(action.flag());
                    this.packets.metadata(actor, viewers);
                }
                case SWIM -> {
                    actor.setSwimming(action.flag());
                    this.packets.metadata(actor, viewers);
                }
                case GLIDE -> {
                    actor.setGliding(action.flag());
                    this.packets.metadata(actor, viewers);
                }
                case EQUIPMENT -> {
                    EquipmentSlot slot = action.slot();
                    if (slot != null) {
                        actor.setEquipment(slot, action.item());
                        this.packets.equipment(actor, viewers);
                    }
                }
                case BLOCK -> this.packets.useItemState(actor, (byte) (action.flag() ? 3 : 0), viewers);
                case USE_ITEM -> this.packets.useItemState(actor, action.handState(), viewers);
                case TOTEM -> this.packets.entityStatus(actor, (byte) 35, viewers);
                case SLEEP -> {
                    actor.setSleeping(action.flag());
                    this.packets.metadata(actor, viewers);
                }
                case CHEST, FISHING, VEHICLE -> {
                }
            }
        } catch (Throwable ex) {
            this.packets.logActionFailure(action.type().name(), ex);
        }
    }
}
