package com.thecolonel63.serversidereplayrecorder.mixin.main;

import com.thecolonel63.serversidereplayrecorder.util.interfaces.LightUpdatePacketAccessor;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(LightUpdateS2CPacket.class)
public class LightUpdatePacketMixin implements LightUpdatePacketAccessor {
    boolean onChunkLoad;

    @Override
    public boolean isOnChunkLoad() {
        return onChunkLoad;
    }

    @Inject(method = "<init>(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/chunk/light/LightingProvider;Ljava/util/BitSet;Ljava/util/BitSet;)V", at = @At("RETURN"))
    void onInit(ChunkPos chunkPos, LightingProvider lightProvider, BitSet skyBits, BitSet blockBits, CallbackInfo ci){
        this.onChunkLoad = skyBits == null && blockBits == null;
    }
}
