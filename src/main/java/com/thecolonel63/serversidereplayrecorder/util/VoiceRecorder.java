package com.thecolonel63.serversidereplayrecorder.util;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.ServerSideVoiceChatPlugin;
import com.thecolonel63.serversidereplayrecorder.net.StaticSoundPacket;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import com.thecolonel63.serversidereplayrecorder.net.EntitySoundPacket;
import com.thecolonel63.serversidereplayrecorder.net.Packet;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.Collection;
import java.util.UUID;

public class VoiceRecorder {

    public static void onSound(MicrophonePacketEvent event) {
        ServerPlayer player = event.getSenderConnection().getPlayer();
        ServerLevel level = player.getServerLevel();
        Position position = player.getPosition();
        UUID id = player.getUuid();
        Packet<?> packet = createPacket(event, id);

        Collection<ServerPlayer> playersInRange = event.getVoicechat().getPlayersInRange(
                level,
                position,
                ServerSideReplayRecorderServer.server.getPlayerManager().getViewDistance() * 16
        );

        sendNearbyPlayerPacket(playersInRange, packet);
        //sendRegionPacket(position, packet);
    }

    private static Packet<?> createPacket(MicrophonePacketEvent event, UUID id) {
        OpusDecoder decoder = ServerSideVoiceChatPlugin.decoder;
        byte[] opusData = event.getPacket().getOpusEncodedData();
        short[] rawAudio = decoder.decode(opusData);

        if (event.getSenderConnection().getGroup() != null) {
            return new StaticSoundPacket(id, rawAudio);
        } else {
            return new EntitySoundPacket(id, rawAudio, event.getPacket().isWhispering(), (float) ServerSideVoiceChatPlugin.distance);
        }
    }

    private static void sendNearbyPlayerPacket(Collection<ServerPlayer> playersInRange, Packet<?> packet) {
        playersInRange.forEach(player -> {
            PlayerRecorder recorder = getPlayerRecorder((ServerPlayerEntity)player.getEntity());
            if (recorder != null) {
                sendPlayerPacket(recorder, packet);
            }
        });
    }

    public static PlayerRecorder getPlayerRecorder(ServerPlayerEntity player){
        return PlayerRecorder.playerRecorderMap.get(player.networkHandler.connection);
    }

    public static void sendPlayerPacket(PlayerRecorder recorder, Packet<?> packet) {
        CustomPayloadS2CPacket fakePacket = createCustomPacket(packet);
        recorder.onPacket(fakePacket);
    }

    public static void sendRegionPacket(Position pos, Packet<?> packet) {
        Vec3d playerPos = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        CustomPayloadS2CPacket fakePacket = createCustomPacket(packet);

        for(RegionRecorder recorder : RegionRecorder.regionRecorderMap.values()){
            if(recorder.region.isInBox(playerPos)){
                recorder.onPacket(fakePacket);
            }
        }
    }

    private static CustomPayloadS2CPacket createCustomPacket(Packet<?> packet) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.toBytes(buf);
        return new CustomPayloadS2CPacket(packet.getIdentifier(), buf);
    }
}
