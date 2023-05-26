package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

import static com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder.playerRecorderMap;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin implements RecorderHolder {

    WeakReference<ReplayRecorder> recorder = new WeakReference<>(null);

    @Override
    public void setRecorder(ReplayRecorder recorder){
        this.recorder = new WeakReference<>(recorder);
    }

    @Override
    public ReplayRecorder getRecorder() {
        return this.recorder.get();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    void constructor(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci){
        ReplayRecorder recorder = playerRecorderMap.get(connection);
        if (recorder != null){
            this.setRecorder(recorder);
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("TAIL"))
    private void savePacket(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener, CallbackInfo ci) {
        //Get the recorder instance dedicated to this connection and give it the packet to record.
        //If there *is* a recorder.
        ReplayRecorder recorder = this.recorder.get();
        if (recorder != null){
            recorder.onPacket(packet);
        }
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void handleDisconnectionOfRecorder(Text reason, CallbackInfo ci) {
        //Tell the recorder to handle a disconnect, if there *is* a recorder
        ReplayRecorder recorder = this.recorder.get();
        if (recorder != null){
            recorder.handleDisconnect();
        }
    }

}
