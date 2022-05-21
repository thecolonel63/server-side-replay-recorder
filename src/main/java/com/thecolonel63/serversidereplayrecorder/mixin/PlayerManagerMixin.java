package com.thecolonel63.serversidereplayrecorder.mixin;

import com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    //Trying to do this as soon as the packet is sent doesn't work, as it's too early.
    //So, doing it here allows all the values to be properly set on time.

    //Also, the respawnPlayer method isn't called going between dimensions for some reason, so we
    //set a variable while this method is running, and just manually respawn on the packet when it isn't run.

    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void setRespawning(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.forEach(((connection, playerThreadRecorder) -> {
            if (player != null && playerThreadRecorder.playerId.equals(player.getUuid())) {
                playerThreadRecorder.isRespawning = true;
            }
        }));
    }

    @Inject(method = "respawnPlayer", at = @At("TAIL"))
    private void respawnPlayer(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.forEach((connection, playerThreadRecorder) -> {
            if (player != null && playerThreadRecorder.playerId.equals(player.getUuid())) {
                playerThreadRecorder.spawnRecordingPlayer();
                playerThreadRecorder.isRespawning = false;
            }
        });
    }
}
