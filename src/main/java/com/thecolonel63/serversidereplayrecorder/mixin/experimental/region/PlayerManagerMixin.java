package com.thecolonel63.serversidereplayrecorder.mixin.experimental.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.network.Packet;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Redirect(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handleNewPlayer(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.recorders.values().forEach( r -> r.onPacket(packet));
    }


    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handlePlayerDisconnectPlayer(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.recorders.values().forEach( r -> r.onPacket(packet));
    }
}
