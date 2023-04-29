package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

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

    @Inject(method = "sendToDimension", at= @At("TAIL"))
    void handleDimensionPacket(Packet<?> packet, RegistryKey<World> dimension, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().stream().filter(r -> r.world.getRegistryKey().equals(dimension)).forEach(r -> r.onPacket(packet));
    }

    @Inject(method = "sendToAll", at= @At("TAIL"))
    void handleAllPacket(Packet<?> packet, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(packet));
    }

    @Inject(method = "sendToAround", at = @At("TAIL"))
    private void handleLevelEvent(@Nullable PlayerEntity player, double x, double y, double z, double distance, RegistryKey<World> worldKey, Packet<?> packet, CallbackInfo ci) {
        RegionRecorder.regionRecorderMap.values().stream().filter(r -> r.world.getRegistryKey().equals(worldKey)).filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(packet)
        );
    }

}
