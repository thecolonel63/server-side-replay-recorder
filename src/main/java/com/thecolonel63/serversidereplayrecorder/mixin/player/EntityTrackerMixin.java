package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
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
public class EntityTrackerMixin implements RecorderHolder {
    @Shadow @Final EntityTrackerEntry entry;

    WeakReference<ReplayRecorder> recorder = new WeakReference<>(null);

    @Override
    public ReplayRecorder getRecorder() {
        return this.recorder.get();
    }

    @Override
    public void setRecorder(ReplayRecorder recorder){
        this.recorder = new WeakReference<>(recorder);
        //send the spawn packets when the tracker is created
        //works also for dimension change as a new tracker is created while changing dimension
        if (recorder != null) {
            entry.sendPackets(Objects.requireNonNull(this.recorder.get())::onPacket);
        }
    }

    Set<Packet<?>> packets_to_ignore = new HashSet<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    void constructor(ThreadedAnvilChunkStorage threadedAnvilChunkStorage, Entity entity, int maxDistance, int tickInterval, boolean alwaysUpdateVelocity, CallbackInfo ci){
        if (entity instanceof ServerPlayerEntity serverPlayer){
            this.setRecorder(PlayerRecorder.playerRecorderMap.get(serverPlayer.networkHandler.connection));
        }
    }

    @Inject(method = "sendToOtherNearbyPlayers", at = @At("HEAD"))
    void sendToOtherNearbyPlayers(Packet<?> packet, CallbackInfo ci){
        ReplayRecorder recorder = this.recorder.get();
        if (recorder!=null)
            if(!packets_to_ignore.contains(packet))
                recorder.onPacket(packet);
            else
                //once ignored they are not needed anymore, so we remove them to avoid leaking memory
                packets_to_ignore.remove(packet);
    }

    @Inject(method = "sendToNearbyPlayers", at = @At("HEAD"))
    void sendToNearbyPlayers(Packet<?> packet, CallbackInfo ci){
        //this method will send the packets to the client then call the sendToOtherNearbyPlayers method
        if (this.recorder.get()!=null){
            //add the packets from this method to a set, so we avoid duplicating them
            packets_to_ignore.add(packet);
        }
    }

}
