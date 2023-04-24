package com.thecolonel63.serversidereplayrecorder.config;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainConfig {

    public MainConfig() {
    }

    private String replay_folder_name = "replay_recordings";
    private boolean use_username_for_recordings = true;
    private String server_name = "My Server";
    private Set<String> recordable_users = new HashSet<>();
    private boolean recording_enabled = false;

    public boolean isRecording_enabled() {
        return recording_enabled;
    }

    public void setRecording_enabled(boolean recording_enabled) {
        this.recording_enabled = recording_enabled;
    }

    public String getReplay_folder_name() {
        return replay_folder_name;
    }

    public void setReplay_folder_name(String replay_folder_name) {
        this.replay_folder_name = replay_folder_name;
    }

    @JsonProperty(value = "use_username_for_recordings")
    public boolean use_username_for_recordings() {
        return use_username_for_recordings;
    }

    public void setUse_username_for_recordings(boolean use_username_for_recordings) {
        this.use_username_for_recordings = use_username_for_recordings;
    }

    public String getServer_name() {
        return server_name;
    }

    public void setServer_name(String server_name) {
        this.server_name = server_name;
    }

    public void setRecordable_users(String[] recordable_users) {
        this.recordable_users = new HashSet<>(Arrays.asList(recordable_users));
    }

    public Set<String> getRecordable_users() {
        return this.recordable_users;
    }
}
