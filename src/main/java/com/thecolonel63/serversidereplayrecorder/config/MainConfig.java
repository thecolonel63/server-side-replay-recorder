package com.thecolonel63.serversidereplayrecorder.config;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class MainConfig {

    public int command_op_level = 4;
    private File replay_folder_name = new File("replay_recordings");
    private boolean use_username_for_recordings = true;
    private String server_name = "My Server";
    private Set<String> recordable_users = new HashSet<>();
    private boolean recording_enabled = false;
    private boolean use_server_timestamps = true;
    private boolean assume_unloaded_chunks_dont_change = true;
    private long  max_file_size = 10000000000L;
    private URL file_storage_url;
    private boolean debug = false;

    public MainConfig() {
        try {
            file_storage_url = new URL("https://tmpfiles.org/api/v1/upload");
        } catch (Throwable ignored) {
        }
    }

    public boolean isAssume_unloaded_chunks_dont_change() {
        return assume_unloaded_chunks_dont_change;
    }

    public void setAssume_unloaded_chunks_dont_change(boolean assume_unloaded_chunks_dont_change) {
        this.assume_unloaded_chunks_dont_change = assume_unloaded_chunks_dont_change;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public long getMax_file_size() {

        return max_file_size;
    }

    public void setMax_file_size(long max_file_size) {
        this.max_file_size = max_file_size;
    }

    @JsonProperty(value = "use_server_timestamps")
    public boolean use_server_timestamps() {
        return use_server_timestamps;
    }

    public void setUse_server_timestamps(boolean use_server_timestamps) {
        this.use_server_timestamps = use_server_timestamps;
    }

    public URL getFile_storage_url() {
        return file_storage_url;
    }

    @JsonProperty(value = "file_storage_url")
    public void setFile_storage_url(String file_storage_url) throws MalformedURLException {
        this.file_storage_url = new URL(file_storage_url);
    }

    public int getCommand_op_level() {
        return command_op_level;
    }

    public void setCommand_op_level(int command_op_level) {
        this.command_op_level = command_op_level;
    }

    public boolean isRecording_enabled() {
        return recording_enabled;
    }

    public void setRecording_enabled(boolean recording_enabled) {
        this.recording_enabled = recording_enabled;
    }

    public String getReplay_folder_name() {
        return replay_folder_name.getName();
    }

    public void setReplay_folder_name(String replay_folder_name) {
        this.replay_folder_name = new File(replay_folder_name);
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

    public Set<String> getRecordable_users() {
        return this.recordable_users;
    }

    public void setRecordable_users(Set<String> recordable_users) {
        this.recordable_users = recordable_users;
    }
}
