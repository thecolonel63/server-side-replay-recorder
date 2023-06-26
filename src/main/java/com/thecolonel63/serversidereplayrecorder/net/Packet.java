package com.thecolonel63.serversidereplayrecorder.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public interface Packet<T extends Packet<T>> {

    Identifier getIdentifier();

    T fromBytes(PacketByteBuf buf) throws VersionCompatibilityException;

    void toBytes(PacketByteBuf buf);


}
