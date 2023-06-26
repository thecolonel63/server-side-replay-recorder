package com.thecolonel63.serversidereplayrecorder.recorder;

import com.thecolonel63.serversidereplayrecorder.ServerSideVoiceChatPlugin;
import com.thecolonel63.serversidereplayrecorder.net.StaticSoundPacket;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import com.thecolonel63.serversidereplayrecorder.net.EntitySoundPacket;
import com.thecolonel63.serversidereplayrecorder.net.LocationalSoundPacket;
import com.thecolonel63.serversidereplayrecorder.net.Packet;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;

import java.util.UUID;

public class VoiceRecorder {

    public static void onEntitySound(EntitySoundPacketEvent event) {
        EntitySoundPacket ssp = (EntitySoundPacket)event.getPacket();
        send(new EntitySoundPacket(event.getPacket().getEntityUuid(), ssp.getRawAudio(),  event.getPacket().isWhispering(), event.getPacket().getDistance()));
    }

    public static void onLocationalSound(LocationalSoundPacketEvent event) {
        LocationalSoundPacket ssp = (LocationalSoundPacket)event.getPacket();
        send(new LocationalSoundPacket(event.getPacket().getSender(), ssp.getRawAudio(), event.getPacket().getPosition(), event.getPacket().getDistance()));
    }

    public static void onStaticSound(StaticSoundPacketEvent event) {
        StaticSoundPacket ssp = (StaticSoundPacket)event.getPacket();
        send(new StaticSoundPacket(event.getPacket().getSender(), ssp.getRawAudio()));
    }

    public static void onSound(MicrophonePacketEvent event) {
        UUID id = event.getSenderConnection().getPlayer().getUuid();

        OpusDecoder decoder = ServerSideVoiceChatPlugin.decoder;
        byte[] opusData = event.getPacket().getOpusEncodedData();

        short[] rawAudio = decoder.decode(opusData);

        if (event.getSenderConnection().getGroup() != null) {
            send(new StaticSoundPacket(id, rawAudio));
        } else {
            send(new EntitySoundPacket(id, rawAudio, event.getPacket().isWhispering(), (float) ServerSideVoiceChatPlugin.distance));
        }
    }

    public static void send(Packet<?> packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.toBytes(buf);
        CustomPayloadS2CPacket fakeP = new CustomPayloadS2CPacket(packet.getIdentifier(), buf);
        for (ReplayRecorder recorder : ReplayRecorder.active_recorders){
            recorder.save(fakeP);
        }
    }
}
