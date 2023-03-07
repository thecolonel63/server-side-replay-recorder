package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import net.minecraft.network.Packet;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    @Shadow @Final private HeightLimitView world;

    @Shadow @Final ChunkPos pos;

    @Inject(method = "sendPacketToPlayersWatching", at=@At("TAIL"))
    void handleChunkUpdate(Packet<?> packet, boolean onlyOnWatchDistanceEdge, CallbackInfo ci){
        Set<RegionRecorder> recorders = ((RegionRecorderWorld)this.world).getRegionRecordersByChunk().get(this.pos);
        if (recorders != null)
            recorders.forEach( r -> r.onPacket(packet));
    }


}
