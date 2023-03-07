package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Redirect(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handleNewPlayer(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(packet));
    }

    @Inject(method = "broadcastChatMessage", at= @At("TAIL"))
    void handleBroadcast(Text message, MessageType type, UUID sender, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new GameMessageS2CPacket(message, type, sender)));
    }

    @Inject(method = "sendToOtherTeams", at= @At("TAIL"))
    void handleOtherTeamMessage(PlayerEntity source, Text message, CallbackInfo ci){
        AbstractTeam abstractTeam = source.getScoreboardTeam();
        if (abstractTeam != null) {
            RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new GameMessageS2CPacket(message, MessageType.SYSTEM, source.getUuid())));
        }
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handlePlayerDisconnectPlayer(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(packet));
    }
}
