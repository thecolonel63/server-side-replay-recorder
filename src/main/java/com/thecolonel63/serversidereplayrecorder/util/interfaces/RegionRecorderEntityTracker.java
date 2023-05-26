package com.thecolonel63.serversidereplayrecorder.util.interfaces;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;

public interface RegionRecorderEntityTracker {
    void updateTrackedStatus(RegionRecorder recorder);
    default void updateTrackedStatus(Iterable<RegionRecorder> recorderIterable) {
        for(RegionRecorder recorder : recorderIterable)
            this.updateTrackedStatus(recorder);
    }
}
