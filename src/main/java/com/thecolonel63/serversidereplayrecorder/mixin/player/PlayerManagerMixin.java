package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.mojang.authlib.GameProfile;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.List;

import static com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder.playerRecorderMap;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Shadow @Final private static Logger LOGGER;


    @Shadow public abstract boolean isOperator(GameProfile profile);

    @Shadow public abstract List<ServerPlayerEntity> getPlayerList();

    @Inject(method = "onPlayerConnect", at= @At("HEAD"))
    private void onConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci){
        if (!playerRecorderMap.containsKey(connection)
                && ServerSideReplayRecorderServer.config.getRecordable_users().contains(player.getGameProfile().getName()) && ServerSideReplayRecorderServer.config.isRecording_enabled()) {
            try {
                ServerSideReplayRecorderServer.LOGGER.info("Started Recording Player %s".formatted(player.getGameProfile().getName()));

                this.getPlayerList().stream().filter(p -> this.isOperator(p.getGameProfile())).forEach( p -> {
                    p.sendMessage(new LiteralText("Started Recording Player %s".formatted(player.getGameProfile().getName())).formatted(Formatting.GOLD), MessageType.SYSTEM, Util.NIL_UUID);
                });
                PlayerRecorder recorder = new PlayerRecorder(connection);
                PlayerRecorder.playerRecorderMap.put(connection, recorder);
                recorder.onPacket(new LoginSuccessS2CPacket(player.getGameProfile()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Trying to do this as soon as the packet is sent doesn't work, as it's too early.
    //So, doing it here allows all the values to be properly set on time.

    //Also, the respawnPlayer method isn't called going between dimensions for some reason, so we
    //set a variable while this method is running, and just manually respawn on the packet when it isn't run.

    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void setRespawning(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        synchronized (PlayerRecorder.playerRecorderMap) {
            PlayerRecorder.playerRecorderMap.forEach(((connection, playerThreadRecorder) -> {
                if (player != null && playerThreadRecorder.playerId.equals(player.getUuid())) {
                    playerThreadRecorder.isRespawning = true;
                }
            }));
        }
    }

    @Inject(method = "respawnPlayer", at = @At("TAIL"))
    private void respawnPlayer(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        synchronized (PlayerRecorder.playerRecorderMap) {
            PlayerRecorder.playerRecorderMap.forEach((connection, playerThreadRecorder) -> {
                if (player != null && playerThreadRecorder.playerId.equals(player.getUuid())) {
                    playerThreadRecorder.spawnRecordingPlayer();
                    playerThreadRecorder.isRespawning = false;
                }
            });
        }
    }
}
