package com.thecolonel63.serversidereplayrecorder.util.interfaces;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.Set;

public interface RegionRecorderWorld {
    Set<RegionRecorder> getRegionRecorders();

    Map<ChunkPos,Set<RegionRecorder>> getRegionRecordersByChunk();

}
