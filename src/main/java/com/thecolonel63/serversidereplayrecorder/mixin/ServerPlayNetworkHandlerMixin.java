package com.thecolonel63.serversidereplayrecorder.mixin;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public abstract ClientConnection getConnection();

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("TAIL"))
    private void savePacket(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> listener, CallbackInfo ci) {
        synchronized (connectionPlayerThreadRecorderMap) {
            //Get the recorder instance dedicated to this connection and give it the packet to record.
            //If there is no recorder instance for this connection, don't do anything.
            if (!connectionPlayerThreadRecorderMap.containsKey(this.getConnection())) {
                return;
            }
            connectionPlayerThreadRecorderMap.get(this.getConnection()).onPacket(packet);
        }
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void handleDisconnectionOfRecorder(Text reason, CallbackInfo ci) {
        synchronized (connectionPlayerThreadRecorderMap) {
            //Tell the recorder to handle a disconnect, if there *is* a recorder.
            if (!connectionPlayerThreadRecorderMap.containsKey(this.getConnection())) {
                return;
            }
            connectionPlayerThreadRecorderMap.get(this.getConnection()).handleDisconnect();
        }
    }

}
