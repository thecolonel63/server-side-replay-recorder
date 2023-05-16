package com.thecolonel63.serversidereplayrecorder.util.interfaces;

import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;

public interface RecorderHolder {
    void setRecorder(ReplayRecorder recorder);
    ReplayRecorder getRecorder();
}
