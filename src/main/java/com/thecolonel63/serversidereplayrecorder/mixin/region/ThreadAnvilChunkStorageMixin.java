package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.mojang.datafixers.util.Either;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderEntityTracker;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderStorage;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("rawtypes")
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadAnvilChunkStorageMixin implements RegionRecorderStorage {

    @Shadow @Final
    ServerWorld world;

    @Shadow protected abstract ServerLightingProvider getLightingProvider();

    @Shadow @Final private Int2ObjectMap<ThreadedAnvilChunkStorage.EntityTracker> entityTrackers;

    @Inject(method = "loadEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage$EntityTracker;updateTrackedStatus(Ljava/util/List;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleEntityLoaded(Entity entity, CallbackInfo ci, EntityType entityType, int i, int j, ThreadedAnvilChunkStorage.EntityTracker entityTracker){
        ((RegionRecorderEntityTracker)entityTracker).updateTrackedStatus(((RegionRecorderWorld)this.world).getRegionRecorders());
    }

    @Inject(method = "tickEntityMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage$EntityTracker;updateTrackedStatus(Ljava/util/List;)V", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleEntityMovement(CallbackInfo ci, List list, List list2, ObjectIterator var3, ThreadedAnvilChunkStorage.EntityTracker entityTracker, ChunkSectionPos chunkSectionPos, ChunkSectionPos chunkSectionPos2){
        ((RegionRecorderEntityTracker)entityTracker).updateTrackedStatus(((RegionRecorderWorld)this.world).getRegionRecorders());
    }

    @Inject(method = "updatePosition", at = @At(value = "HEAD"))
    void handlePlayerMovement(ServerPlayerEntity player, CallbackInfo ci){
        ((RegionRecorderEntityTracker)this.entityTrackers.get(player.getId())).updateTrackedStatus(((RegionRecorderWorld)this.world).getRegionRecorders());
    }

    @Inject(method = "makeChunkTickable", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), locals = LocalCapture.CAPTURE_FAILHARD)
    void handleChunkLoaded(ChunkHolder holder, CallbackInfoReturnable<CompletableFuture<Either<WorldChunk, ChunkHolder.Unloaded>>> cir, ChunkPos chunkPos, CompletableFuture completableFuture, CompletableFuture<Either<WorldChunk, ChunkHolder.Unloaded>> completableFuture2){
        completableFuture2.thenApplyAsync(either -> either.ifLeft(worldChunk -> {
            Set<RegionRecorder> recorders = ((RegionRecorderWorld)this.world).getRegionRecordersByExpandedChunk().get(holder.getPos());
            if (recorders != null)
                recorders.forEach( r -> {
                    r.onPacket(new ChunkDataS2CPacket(worldChunk));
                    r.onPacket(new LightUpdateS2CPacket(worldChunk.getPos(), this.getLightingProvider(), null, null, true));
                });
        }));
    }


    @Override
    public void registerRecorder(RegionRecorder recorder) {
        this.entityTrackers.forEach(
                (integer, entityTracker) -> ((RegionRecorderEntityTracker)entityTracker).updateTrackedStatus(recorder)
        );
    }
}
