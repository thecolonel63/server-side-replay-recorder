package com.thecolonel63.serversidereplayrecorder.mixin.experimental.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.filter.TextStream;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public abstract ServerPlayerEntity getPlayer();

    @Inject(method = "handleMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleChatMessage(TextStream.Message message, CallbackInfo ci, String string, Text text, Text text2){
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new GameMessageS2CPacket(text2, MessageType.CHAT, this.getPlayer().getUuid())));
    }
}
