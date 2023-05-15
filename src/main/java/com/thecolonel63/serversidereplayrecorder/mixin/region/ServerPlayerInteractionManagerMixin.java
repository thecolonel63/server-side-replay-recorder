package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.network.Packet;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Redirect(method = "setGameMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handlePlayerGamemode(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(packet));
    }


}
