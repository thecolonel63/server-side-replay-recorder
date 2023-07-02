package com.thecolonel63.serversidereplayrecorder.mixin.main;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.config.model.Region;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"))
    private void onInitServer(CallbackInfo ci) {
        ServerSideReplayRecorderServer.registerServer((MinecraftServer)(Object)this);
    }
    @Inject(method = "shutdown", at = @At(value = "HEAD"))
    private void onStopServer(CallbackInfo ci) {
        for (ReplayRecorder recorder : ReplayRecorder.active_recorders){
            recorder.handleDisconnect();
        }
    }

    @Inject(method = "shutdown", at = @At(value = "RETURN"))
    private void onStopServerTail(CallbackInfo ci) {
        ServerSideReplayRecorderServer.LOGGER.warn("Waiting for all recorders to finish saving");
    }

    @Inject(method = "tick", at = @At("RETURN"))
    void onTickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci){
        ReplayRecorder.active_recorders.forEach(ReplayRecorder::onServerTick);
    }

    @Inject(method = "prepareStartRegion", at = @At("RETURN"))
    void onWorldCreation(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci){
        if (ServerSideReplayRecorderServer.config.isRecording_enabled()){
            for (Region region : ServerSideReplayRecorderServer.config.getRegions()){
                if(region.isAutoRecord()){
                     RegionRecorder.createAsync(region.getName(), region.getTo().toChunkPos(), region.getFrom().toChunkPos(), region.toServerWorld());
                }
            }
        }

    }

}
