package com.thecolonel63.serversidereplayrecorder.mixin.experimental.region;

import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.function.BooleanSupplier;

@SuppressWarnings("rawtypes")
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "tickWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToDimension(Lnet/minecraft/network/Packet;Lnet/minecraft/util/registry/RegistryKey;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleWorldTime(BooleanSupplier shouldKeepTicking, CallbackInfo ci, Iterator var2, ServerWorld serverWorld){
        ((RegionRecorderWorld)serverWorld).getRegionRecorders().forEach( r -> r.onPacket(new WorldTimeUpdateS2CPacket(serverWorld.getTime(), serverWorld.getTimeOfDay(), serverWorld.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE))));
    }

}
