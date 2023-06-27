package com.thecolonel63.serversidereplayrecorder;

import com.thecolonel63.serversidereplayrecorder.util.VoiceRecorder;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

public class ServerSideVoiceChatPlugin implements VoicechatPlugin {

    public static OpusDecoder decoder;
    public static double distance;
    public static VoicechatApi SERVERAPI;
    @Override
    public String getPluginId() {
        return "ServerSideReplayMod";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (decoder == null) {
            decoder = api.createDecoder();
        }
        System.out.println("Started recording voice chat using ServerSideReplayMod");
        this.distance = api.getVoiceChatDistance();
        this.SERVERAPI = api;


    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, VoiceRecorder::onSound);
    }
}
