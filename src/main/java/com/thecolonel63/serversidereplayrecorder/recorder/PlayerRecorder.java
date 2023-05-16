package com.thecolonel63.serversidereplayrecorder.recorder;

import com.mojang.authlib.GameProfile;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.mixin.main.LoginSuccessfulS2CPacketAccessor;
import com.thecolonel63.serversidereplayrecorder.util.WrappedPacket;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.LightUpdatePacketAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRecorder extends ReplayRecorder {
    public static final String PLAYER_FOLDER = "player";
    public static final Map<ClientConnection, PlayerRecorder> playerRecorderMap = new ConcurrentHashMap<>();
    public final ClientConnection connection;
    public UUID playerId;
    public String playerName;

    public PlayerRecorder(ClientConnection connection) throws IOException {
        super();
        this.connection = connection;
    }

    @Override
    public String getRecordingName() {
        return this.playerName;
    }

    @Override
    protected String getSaveFolder(){
        String name = (playerName != null) ? playerName : "NONAME";
        if (new File(ServerSideReplayRecorderServer.config.getReplay_folder_name()).isAbsolute())
            return Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), PLAYER_FOLDER,name).toString();
        else
            return Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), PLAYER_FOLDER,name).toString();
    }


    public void onPacket(Packet<?> packet) {

        if (packet instanceof LightUpdateS2CPacket lightUpdateS2CPacket){
            if(((LightUpdatePacketAccessor)lightUpdateS2CPacket).isOnChunkLoad()){
                //be sure to record new chunk light packets
                packet = new WrappedPacket(packet);
            }
        }

        if (packet instanceof LoginSuccessS2CPacket loginSuccessS2CPacket) {
            GameProfile profile = ((LoginSuccessfulS2CPacketAccessor) loginSuccessS2CPacket).getProfile();
            playerId = profile.getId();
            playerName = profile.getName();
        }

        super.onPacket(packet);
    }

    @Override
    public void handleDisconnect() {
        //Player has disconnected, so remove our recorder from the map
        playerRecorderMap.remove(this.connection);
        super.handleDisconnect();
    }


    //moved to PlayerManagerMixin#sendToAround
    /*public void onClientSound(RegistryEntry<SoundEvent> sound, SoundCategory category, double x, double y, double z, float volume, float pitch, long seed) {
        try {
            // Send to all other players in ServerWorldEventHandler#playSoundToAllNearExcept
            onPacket(new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch, seed));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/


    //moved to PlayerManagerMixin#sendToAround
    /*public void onClientEffect(int type, BlockPos pos, int data) {
        try {
            // Send to all other players in ServerWorldEventHandler#playEvent
            onPacket(new WorldEventS2CPacket(type, pos, data, false));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    public void onBlockBreakAnim(int breakerId, BlockPos pos, int progress) {
        if (playerId == null) return;
        PlayerEntity thePlayer = ms.getPlayerManager().getPlayer(playerId);
        if (thePlayer != null && breakerId == thePlayer.getId()) {
            onPacket(new BlockBreakingProgressS2CPacket(breakerId, pos, progress));
        }
    }

    private final LocalDateTime start_time = LocalDateTime.now();

    public Duration getUptime(){
        return Duration.between(start_time, LocalDateTime.now());
    }

    @Override
    public void addMarker(String name) {
        ServerPlayerEntity player = ms.getPlayerManager().getPlayer(playerId);
        if (player == null)
            super.addMarker(name);
        else
            this.addMarker(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.getRoll(), name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PlayerRecorder recorder){
            return this.playerName.equals(recorder.playerName);
        }
        return false;
    }

}
