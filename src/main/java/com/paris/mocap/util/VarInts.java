package com.paris.mocap.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class VarInts {
    private VarInts() {
    }

    public static void writeUnsigned(DataOutput out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    public static int readUnsigned(DataInput in) throws IOException {
        int result = 0;
        int shift = 0;
        while (shift < 35) {
            byte b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IOException("VarInt too long");
    }

    public static void writeSigned(DataOutput out, int value) throws IOException {
        writeUnsigned(out, (value << 1) ^ (value >> 31));
    }

    public static int readSigned(DataInput in) throws IOException {
        int n = readUnsigned(in);
        return (n >>> 1) ^ -(n & 1);
    }
}
