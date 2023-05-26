package com.thecolonel63.serversidereplayrecorder.mixin.player;

import carpet.patches.NetHandlerPlayServerFake;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(NetHandlerPlayServerFake.class)
public abstract class NetHandlerPlayServerFakeMixin {

    @Inject(method = "sendPacket", at = @At("TAIL"), require = 0)
    private void savePacket(Packet<?> packet, CallbackInfo ci) {
        //Get the recorder instance dedicated to this connection and give it the packet to record.
        //If there is no recorder instance for this connection, don't do anything.
        Optional.ofNullable(((RecorderHolder)this).getRecorder()).ifPresent(r->r.onPacket(packet));
    }

}
