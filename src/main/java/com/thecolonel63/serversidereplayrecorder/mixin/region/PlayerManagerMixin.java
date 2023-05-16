package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;
import java.util.function.Predicate;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Shadow @Final private MinecraftServer server;

    @Inject(method = "broadcast(Lnet/minecraft/text/Text;Ljava/util/function/Function;Z)V", at= @At("HEAD"))
    void handleBroadcast(Text message, Function<ServerPlayerEntity, Text> playerMessageFactory, boolean overlay, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new GameMessageS2CPacket(message, overlay)));
    }

    @Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V", at= @At("HEAD"))
    void handleBroadcast2(SignedMessage message, Predicate<ServerPlayerEntity> shouldSendFiltered, @Nullable ServerPlayerEntity sender, MessageType.Parameters params, CallbackInfo ci){
        //Frick the encryption, just store all messages as not secure
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new ProfilelessChatMessageS2CPacket(
                message.getContent(),
                params.toSerialized(this.server.getRegistryManager())
        )));
    }

    @Inject(method = "sendToOtherTeams", at= @At("HEAD"))
    void handleOtherTeamMessage(PlayerEntity source, Text message, CallbackInfo ci){
        AbstractTeam abstractTeam = source.getScoreboardTeam();
        if (abstractTeam != null) {
            RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(new GameMessageS2CPacket(message,false)));
        }
    }

    @Inject(method = "sendToDimension", at= @At("HEAD"))
    void handleDimensionPacket(Packet<?> packet, RegistryKey<World> dimension, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().stream().filter(r -> r.world.getRegistryKey().equals(dimension)).forEach(r -> r.onPacket(packet));
    }

    @Inject(method = "sendToAll", at= @At("HEAD"))
    void handleAllPacket(Packet<?> packet, CallbackInfo ci){
        RegionRecorder.regionRecorderMap.values().forEach(r -> r.onPacket(packet));
    }

    @Inject(method = "sendToAround", at = @At("HEAD"))
    private void handleLevelEvent(@Nullable PlayerEntity player, double x, double y, double z, double distance, RegistryKey<World> worldKey, Packet<?> packet, CallbackInfo ci) {
        RegionRecorder.regionRecorderMap.values().stream().filter(r -> r.world.getRegistryKey().equals(worldKey)).filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(packet)
        );
    }

}
