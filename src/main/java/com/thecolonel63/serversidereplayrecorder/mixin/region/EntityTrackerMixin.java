package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderEntityTracker;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(ThreadedAnvilChunkStorage.EntityTracker.class)
public abstract class EntityTrackerMixin implements RegionRecorderEntityTracker {

    @Shadow @Final
    Entity entity;
    @Shadow @Final EntityTrackerEntry entry;

    final Set<RegionRecorder> listenening_recorders = new HashSet<>();

    public void updateTrackedStatus(RegionRecorder recorder){
        boolean spectator = false;
        if ( entity instanceof ServerPlayerEntity serverPlayerEntity )
            spectator = serverPlayerEntity.isSpectator();
        if (recorder.isOpen() && recorder.region.isInBox(this.entity.getPos()) && !spectator){
            if (this.listenening_recorders.add(recorder)) {
                this.startTracking(recorder);
            }
        }else{
            if(this.listenening_recorders.remove(recorder)){
                this.stopTracking(recorder);
            }
        }
    }

    @Inject(method = "sendToOtherNearbyPlayers", at = @At("HEAD"))
    void sendToNearbyPlayers(Packet<?> packet, CallbackInfo ci){
        for (RegionRecorder recorder : this.listenening_recorders){
            recorder.onPacket(packet);
        }
    }

    @Inject(method = "stopTracking()V", at = @At("HEAD"))
    void stopTracking(CallbackInfo ci){
        for (RegionRecorder recorder : this.listenening_recorders){
            this.stopTracking(recorder);
        }
    }

    void startTracking(RegionRecorder recorder){
        this.entry.sendPackets(recorder::onPacket);
    }

    void stopTracking(RegionRecorder recorder){
        recorder.onPacket(new EntitiesDestroyS2CPacket(this.entity.getId()));
    }

}
