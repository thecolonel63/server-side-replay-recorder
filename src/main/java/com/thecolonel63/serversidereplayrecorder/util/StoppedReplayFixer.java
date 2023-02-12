package com.thecolonel63.serversidereplayrecorder.util;

import com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer;
import net.minecraft.MinecraftVersion;
import net.minecraft.SharedConstants;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.UUID;

public class StoppedReplayFixer {

    static int lastTimestamp = 0;

    static String metaData = "{\"singleplayer\":false,\"serverName\":\""+ServerSideReplayRecorderServer.serverName+"\",\"customServerName\":\""+ServerSideReplayRecorderServer.serverName+"\",\"duration\":%DURATION%,\"date\":%DATE%,\"mcversion\":\""+MinecraftVersion.GAME_VERSION.getName()+"\",\"fileFormat\":\"MCPR\",\"fileFormatVersion\":14,\"protocol\":"+ SharedConstants.getProtocolVersion()+",\"generator\":\"thecolonel63's Server Side Replay Recorder\",\"selfId\":-1,\"players\":[]}";
    static String loginName = "NONAME";
    static UUID loginUuid = new UUID(0, 0);
    static boolean loggedIn = false;

    public static void fixReplay(File stoppedFolder, boolean skipWritingMetadata) throws IOException {
        File file = new File(stoppedFolder+"/recording.tmcpr");

        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        while (true) {
            if(skipWritingMetadata && loggedIn) {
                dis.close();
                break;
            }
            try {
                readPacket(dis);
            } catch (Exception e) {
                dis.close();
                break;
            }
        }

        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        ZonedDateTime zone = attr.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault());
        if(!skipWritingMetadata) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(stoppedFolder+"/metaData.json"));
            writer.write((metaData.replaceAll("%DURATION%", String.valueOf(lastTimestamp)).replaceAll("%DATE%", String.valueOf(attr.lastModifiedTime().toInstant().toEpochMilli() - lastTimestamp))));
            writer.close();
        }

        File movedReplayFile = new File(stoppedFolder.getParentFile()+"/"+(ServerSideReplayRecorderServer.useUsernameForRecordings ? loginName : loginUuid)+"/"+getReplayName(zone));
        movedReplayFile.getParentFile().mkdirs();

        FileHandlingUtility.zip(new ArrayList<>(){{add(new File(stoppedFolder+"/metaData.json")); add(new File(stoppedFolder+"/recording.tmcpr"));}}, movedReplayFile.toString(), false, null);
        new File(stoppedFolder+"/metaData.json").delete();
        new File(stoppedFolder+"/recording.tmcpr").delete();
        stoppedFolder.delete();
    }

    public static String getReplayName(ZonedDateTime zdt) {
        return (zdt.getYear() + "_" +
                padWithZeros(zdt.getMonthValue(), 2) + "_" +
                padWithZeros(zdt.getDayOfMonth(), 2) + "_" +
                padWithZeros(zdt.getHour(), 2) + "_" +
                padWithZeros(zdt.getMinute(), 2) + "_" +
                padWithZeros(zdt.getSecond(), 2) + ".mcpr");
    }

    public static String padWithZeros(int inputString, int length) {
        return String.format("%1$" + length + "s", inputString).replace(' ', '0');
    }

    public static void readPacket(DataInputStream dis) throws IOException {
        lastTimestamp = new BigInteger(dis.readNBytes(4)).intValue();
        int length = new BigInteger(dis.readNBytes(4)).intValue();
        byte id = dis.readByte();
        if(id == 0x02 && !loggedIn) {
            loggedIn = true;
            loginUuid = new UUID(dis.readLong(), dis.readLong());
            dis.readByte();
            loginName = new String(dis.readNBytes(length-18));
            System.out.println("Login uuid: " + loginUuid);
            System.out.println("Login name: " + loginName);
            return;
        }
        dis.readNBytes(length-1);
    }

}
