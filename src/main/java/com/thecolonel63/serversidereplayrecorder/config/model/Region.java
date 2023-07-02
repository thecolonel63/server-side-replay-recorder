package com.thecolonel63.serversidereplayrecorder.config.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ColumnPos;

import java.util.Iterator;

public class Region {
    private String name;
    private Point to;
    private Point from;
    private boolean auto_record;
    private String world;

    public Region() {

    }
    public Region(String name, ColumnPos pos1, ColumnPos pos2, String world, boolean autoRecord) {
        this.name = name;
        this.to = new Point(pos1.x(), pos1.z());
        this.from = new Point(pos2.x(), pos2.z());
        this.auto_record = autoRecord;
        this.world = world;
        ServerSideReplayRecorderServer.server.getWorlds();

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Point getTo() {
        return to;
    }

    public void setTo(Point to) {
        this.to = to;
    }


    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }
    @JsonIgnore
    public ServerWorld toServerWorld(){
        Iterator<ServerWorld> it = ServerSideReplayRecorderServer.server.getWorlds().iterator();
        while(it.hasNext()) {
            ServerWorld sw = it.next();
            if(!(sw.toString().equals(this.world))) continue;
            return sw;
        }
        return null;
    }

    public Point getFrom() {
        return from;
    }

    public void setFrom(Point from) {
        this.from = from;
    }

    @JsonProperty("autoRecord")
    public boolean isAutoRecord() {
        return auto_record;
    }

    public void setAutoRecord(boolean auto_record) {
        this.auto_record = auto_record;
    }

}