package com.thecolonel63.serversidereplayrecorder.util.interfaces;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;

public interface RegionRecorderEntityTracker {
    void updateTrackedStatus(RegionRecorder recorder);
    void updateTrackedStatus(Iterable<RegionRecorder> recorder);
}
