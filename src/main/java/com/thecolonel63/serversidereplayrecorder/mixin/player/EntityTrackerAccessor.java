package com.thecolonel63.serversidereplayrecorder.mixin.player;

import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThreadedAnvilChunkStorage.EntityTracker.class)
public interface EntityTrackerAccessor {
    @Accessor("entry")
    EntityTrackerEntry getEntry();
}
