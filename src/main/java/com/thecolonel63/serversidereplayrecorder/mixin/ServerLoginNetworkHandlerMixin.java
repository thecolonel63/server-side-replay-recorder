package com.thecolonel63.serversidereplayrecorder.mixin;

import com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.PlayerThreadRecorder;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(ServerLoginNetworkHandler.class)
public class ServerLoginNetworkHandlerMixin {
    @Shadow
    @Final
    public ClientConnection connection;

    @Inject(method = "onKey", at = @At("TAIL"))
    private void startRecording(LoginKeyC2SPacket packet, CallbackInfo ci) {
        synchronized (ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap) {
            //Try to start the recording
            try {
                ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.put(this.connection, new PlayerThreadRecorder(this.connection));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
