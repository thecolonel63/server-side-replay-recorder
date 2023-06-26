package com.thecolonel63.serversidereplayrecorder.net;

import com.thecolonel63.serversidereplayrecorder.ServerSideVoiceChatPlugin;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import de.maxhenkel.voicechat.api.Position;

import java.util.UUID;

public class LocationalSoundPacket extends AbstractSoundPacket<LocationalSoundPacket> {

    public static Identifier ID = new Identifier("replayvoicechat", "locational_sound");

    private Position location;
    private float distance;

    public LocationalSoundPacket(UUID id, short[] rawAudio, Position location, float distance) {
        super(id, rawAudio);
        this.location = location;
        this.distance = distance;
    }

    public LocationalSoundPacket() {

    }

    public Position getLocation() {
        return location;
    }

    public float getDistance() {
        return distance;
    }

    @Override
    public Identifier getIdentifier() {
        return ID;
    }

    @Override
    public LocationalSoundPacket fromBytes(PacketByteBuf buf) throws VersionCompatibilityException {
        super.fromBytes(buf);
        location = ServerSideVoiceChatPlugin.SERVERAPI.createPosition(buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (version >= 1) {
            distance = buf.readFloat();
        } else {
            distance = (float) ServerSideVoiceChatPlugin.distance;
        }
        return this;
    }

    @Override
    public void toBytes(PacketByteBuf buf) {
        super.toBytes(buf);
        buf.writeDouble(location.getX());
        buf.writeDouble(location.getY());
        buf.writeDouble(location.getZ());
        buf.writeFloat(distance);
    }

}
