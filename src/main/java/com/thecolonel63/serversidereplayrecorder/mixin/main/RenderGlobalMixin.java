package com.thecolonel63.serversidereplayrecorder.mixin.main;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class RenderGlobalMixin {

    //Block breaking

    @Inject(method = "setBlockBreakingInfo", at = @At("TAIL"))
    private void saveBlockBreakingProgressPacket(int entityId, BlockPos pos, int progress, CallbackInfo ci) {
        synchronized (ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap) {
            ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.forEach((connection, playerThreadRecorder) -> {
                playerThreadRecorder.onBlockBreakAnim(entityId, pos, progress);
            });
        }
    }
}
