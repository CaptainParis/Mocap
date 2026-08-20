package com.paris.mocap.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

final class ItemBinary {
    private ItemBinary() {
    }

    static void write(DataOutput out, ItemStack item) throws IOException {
        if (item == null || item.getType().isAir()) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(bytes)) {
            stream.writeObject(item);
        }
        byte[] data = bytes.toByteArray();
        out.writeInt(data.length);
        out.write(data);
    }

    static ItemStack read(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        int length = in.readInt();
        if (length <= 0 || length > 1_048_576) {
            throw new IOException("Invalid item payload length: " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            Object obj = stream.readObject();
            return obj instanceof ItemStack stack ? stack : null;
        } catch (ClassNotFoundException ex) {
            throw new IOException("Failed to decode ItemStack", ex);
        }
    }
}
