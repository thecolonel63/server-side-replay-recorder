package com.thecolonel63.serversidereplayrecorder.mixin.main;

import carpet.patches.NetHandlerPlayServerFake;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap;

@Mixin(NetHandlerPlayServerFake.class)
public abstract class NetHandlerPlayServerFakeMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("TAIL"), require = 0)
    private void savePacket(Packet<?> packet, CallbackInfo ci) {
        synchronized (connectionPlayerThreadRecorderMap) {
            //Get the recorder instance dedicated to this connection and give it the packet to record.
            //If there is no recorder instance for this connection, don't do anything.
            if (!connectionPlayerThreadRecorderMap.containsKey(((ServerPlayNetworkHandler)(Object)this).getConnection())) {
                return;
            }
            connectionPlayerThreadRecorderMap.get(((ServerPlayNetworkHandler)(Object)this).getConnection()).onPacket(packet);
        }
    }

}
