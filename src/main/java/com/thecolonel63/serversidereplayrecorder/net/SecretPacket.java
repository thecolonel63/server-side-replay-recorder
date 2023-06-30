package com.thecolonel63.serversidereplayrecorder.net;

import de.maxhenkel.voicechat.config.ServerConfig;
import de.maxhenkel.voicechat.plugins.PluginManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class SecretPacket implements Packet<SecretPacket> {
    public static final Identifier SECRET = new Identifier("voicechat", "secret");
    private UUID secret;
    private int serverPort;
    private UUID playerUUID;
    private ServerConfig.Codec codec;
    private int mtuSize;
    private double voiceChatDistance;
    private int keepAlive;
    private boolean groupsEnabled;
    private String voiceHost;
    private boolean allowRecording;

    public SecretPacket() {
    }

    public SecretPacket(ServerPlayerEntity player, UUID secret, int port, ServerConfig serverConfig) {
        this.secret = secret;
        this.serverPort = port;
        this.playerUUID = player.getUuid();
        this.codec = (ServerConfig.Codec)serverConfig.voiceChatCodec.get();
        this.mtuSize = (Integer)serverConfig.voiceChatMtuSize.get();
        this.voiceChatDistance = (Double)serverConfig.voiceChatDistance.get();
        this.keepAlive = (Integer)serverConfig.keepAlive.get();
        this.groupsEnabled = (Boolean)serverConfig.groupsEnabled.get();
        this.voiceHost = PluginManager.instance().getVoiceHost((String)serverConfig.voiceHost.get());
        this.allowRecording = (Boolean)serverConfig.allowRecording.get();
    }

    public UUID getSecret() {
        return this.secret;
    }

    public int getServerPort() {
        return this.serverPort;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public ServerConfig.Codec getCodec() {
        return this.codec;
    }

    public int getMtuSize() {
        return this.mtuSize;
    }

    public double getVoiceChatDistance() {
        return this.voiceChatDistance;
    }

    public int getKeepAlive() {
        return this.keepAlive;
    }

    public boolean groupsEnabled() {
        return this.groupsEnabled;
    }

    public String getVoiceHost() {
        return this.voiceHost;
    }

    public Identifier getIdentifier() {
        return SECRET;
    }

    public boolean allowRecording() {
        return this.allowRecording;
    }

    public SecretPacket fromBytes(PacketByteBuf buf) {
        this.secret = buf.readUuid();
        this.serverPort = buf.readInt();
        this.playerUUID = buf.readUuid();
        this.codec = ServerConfig.Codec.values()[buf.readByte()];
        this.mtuSize = buf.readInt();
        this.voiceChatDistance = buf.readDouble();
        this.keepAlive = buf.readInt();
        this.groupsEnabled = buf.readBoolean();
        this.voiceHost = buf.readString(32767);
        this.allowRecording = buf.readBoolean();
        return this;
    }

    public void toBytes(PacketByteBuf buf) {
        buf.writeUuid(this.secret);
        buf.writeInt(this.serverPort);
        buf.writeUuid(this.playerUUID);
        buf.writeByte(this.codec.ordinal());
        buf.writeInt(this.mtuSize);
        buf.writeDouble(this.voiceChatDistance);
        buf.writeInt(this.keepAlive);
        buf.writeBoolean(this.groupsEnabled);
        buf.writeString(this.voiceHost);
        buf.writeBoolean(this.allowRecording);
    }
}