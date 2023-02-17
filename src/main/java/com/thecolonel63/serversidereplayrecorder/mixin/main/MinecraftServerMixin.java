package com.thecolonel63.serversidereplayrecorder.mixin.main;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.server.MinecraftServer;
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
        for (PlayerRecorder recorder : ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.values()){
            recorder.handleDisconnect();
        }

        for (RegionRecorder recorder : RegionRecorder.recorders.values()){
            recorder.handleDisconnect();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickWorlds(Ljava/util/function/BooleanSupplier;)V"))
    private void onStartTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerSideReplayRecorderServer.tick();
    }

}
