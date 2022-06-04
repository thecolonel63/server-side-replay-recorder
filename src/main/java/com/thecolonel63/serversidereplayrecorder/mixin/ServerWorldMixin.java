package com.thecolonel63.serversidereplayrecorder.mixin;

import com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    //Sounds
    @Inject(method = "playSound", at = @At("HEAD"))
    private void recordPlaySound(PlayerEntity except, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, long seed, CallbackInfo ci) {
        ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.forEach((connection, playerThreadRecorder) -> {
            if (playerThreadRecorder.playerId != null) {
                playerThreadRecorder.onClientSound(sound, category, x, y, z, volume, pitch, seed);
            }
        });
    }

    //Animations
    @Inject(method = "syncWorldEvent", at = @At("HEAD"))
    private void playLevelEvent(PlayerEntity player, int eventId, BlockPos pos, int data, CallbackInfo ci) {
        ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.forEach(((connection, playerThreadRecorder) -> {
            if (player != null && playerThreadRecorder.playerId != null && playerThreadRecorder.playerId == player.getUuid()) {
                playerThreadRecorder.onClientEffect(eventId, pos, data);
            }
        }));
    }

}
