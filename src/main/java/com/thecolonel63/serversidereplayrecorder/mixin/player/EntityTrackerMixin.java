package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderEntityTracker;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Mixin(ThreadedAnvilChunkStorage.EntityTracker.class)
public abstract class EntityTrackerMixin implements RegionRecorderEntityTracker {
    @Shadow @Final EntityTrackerEntry entry;

    WeakReference<PlayerRecorder> recorder = new WeakReference<>(null);

    Set<Packet<?>> packets_to_ignore = new HashSet<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    void constructor(ThreadedAnvilChunkStorage threadedAnvilChunkStorage, Entity entity, int maxDistance, int tickInterval, boolean alwaysUpdateVelocity, CallbackInfo ci){
        if (entity instanceof ServerPlayerEntity serverPlayer){
            this.recorder = new WeakReference<>(PlayerRecorder.playerRecorderMap.get(serverPlayer.networkHandler.connection));
            //send the spawn packets when the tracker is created
            //works also for dimension change as a new tracker is created while changing dimension
            if (recorder.get() != null) {
                entry.sendPackets(Objects.requireNonNull(this.recorder.get())::onPacket);
            }
        }
    }

    @Inject(method = "sendToOtherNearbyPlayers", at = @At("HEAD"))
    void sendToOtherNearbyPlayers(Packet<?> packet, CallbackInfo ci){
        if (recorder.get()!=null)
            if(!packets_to_ignore.contains(packet))
                Objects.requireNonNull(recorder.get()).onPacket(packet);
            else
                packets_to_ignore.remove(packet);
    }

    @Inject(method = "sendToNearbyPlayers", at = @At("HEAD"))
    void sendToNearbyPlayers(Packet<?> packet, CallbackInfo ci){
        if (recorder.get()!=null){
            packets_to_ignore.add(packet);
        }
    }

}
