package com.thecolonel63.serversidereplayrecorder.util;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.PacketListener;

@SuppressWarnings({"rawtypes", "unchecked"})
public record WrappedPacket(Packet wrappedPacket) implements Packet {
    public WrappedPacket {
        java.util.Objects.requireNonNull(wrappedPacket);
    }

    @Override
    public void write(PacketByteBuf buf) {
        wrappedPacket.write(buf);
    }

    @Override
    public void apply(PacketListener listener) {
        wrappedPacket.apply(listener);
    }
}
