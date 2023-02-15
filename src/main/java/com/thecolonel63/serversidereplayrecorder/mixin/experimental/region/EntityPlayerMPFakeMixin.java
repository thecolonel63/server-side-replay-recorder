package com.thecolonel63.serversidereplayrecorder.mixin.experimental.region;

import carpet.patches.EntityPlayerMPFake;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.network.Packet;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityPlayerMPFake.class)
public class EntityPlayerMPFakeMixin {

    @Redirect(method = "createShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    static void handleShadowPlayer(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.recorders.values().forEach(r -> r.onPacket(packet));
    }

}
