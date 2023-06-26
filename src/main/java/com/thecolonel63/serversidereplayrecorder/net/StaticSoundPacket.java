package com.thecolonel63.serversidereplayrecorder.net;

import net.minecraft.util.Identifier;

import java.util.UUID;

public class StaticSoundPacket extends AbstractSoundPacket<StaticSoundPacket> {

    public static Identifier ID = new Identifier("replayvoicechat", "static_sound");

    public StaticSoundPacket(UUID id, short[] rawAudio) {
        super(id, rawAudio);
    }

    public StaticSoundPacket() {

    }

    @Override
    public Identifier getIdentifier() {
        return ID;
    }


}
