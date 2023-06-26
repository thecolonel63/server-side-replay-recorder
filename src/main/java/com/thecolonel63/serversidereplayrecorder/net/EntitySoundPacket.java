package com.thecolonel63.serversidereplayrecorder.net;




import com.thecolonel63.serversidereplayrecorder.ServerSideVoiceChatPlugin;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class EntitySoundPacket extends AbstractSoundPacket<EntitySoundPacket> {

    public static Identifier ID = new Identifier("replayvoicechat", "entity_sound");

    private boolean whispering;
    private float distance;

    public EntitySoundPacket(UUID id, short[] rawAudio, boolean whispering, float distance) {
        super(id, rawAudio);
        this.whispering = whispering;
        this.distance = distance;
    }

    public EntitySoundPacket() {

    }

    @Override
    public Identifier getIdentifier() {
        return ID;
    }

    public boolean isWhispering() {
        return whispering;
    }

    public float getDistance() {
        return distance;
    }

    @Override
    public EntitySoundPacket fromBytes(PacketByteBuf buf) throws VersionCompatibilityException {
        super.fromBytes(buf);
        whispering = buf.readBoolean();
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
        buf.writeBoolean(whispering);
        buf.writeFloat(distance);
    }


}
