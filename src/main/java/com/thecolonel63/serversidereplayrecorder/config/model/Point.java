package com.thecolonel63.serversidereplayrecorder.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;

public class Point {

    private int ChunkX;
    private int ChunkZ;

    public Point() {

    }
    public Point(int ChunkX, int ChunkZ) {
        this.ChunkX = ChunkX;
        this.ChunkZ = ChunkZ;
    }

    public int getChunkX() {
        return ChunkX;
    }

    public ChunkPos toChunkPos(){
        return new ChunkPos(ChunkSectionPos.getSectionCoord(ChunkX), ChunkSectionPos.getSectionCoord(ChunkZ));
    }

    public void setChunkX(int ChunkX) {
        this.ChunkX = ChunkX;
    }

    public int getChunkZ() {
        return ChunkZ;
    }

    public void setChunkZ(int ChunkZ) {
        this.ChunkZ = ChunkZ;
    }
}
