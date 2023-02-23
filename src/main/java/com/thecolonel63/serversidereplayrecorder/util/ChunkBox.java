package com.thecolonel63.serversidereplayrecorder.util;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.stream.Collectors;

public class ChunkBox {

    public final ChunkPos min;

    public final ChunkPos max;

    public  final ChunkPos center;

    public final int radius;

    public final Set<ChunkPos> includedChunks;
    public final Set<ChunkPos> expandedChunks;

    public ChunkBox(ChunkPos pos1, ChunkPos pos2) {
        this.includedChunks = ChunkPos.stream(pos1, pos2).collect(Collectors.toUnmodifiableSet());

        int min_x = Math.min(pos1.x,pos2.x);
        int min_z = Math.min(pos1.z,pos2.z);

        int max_x = Math.max(pos1.x,pos2.x);
        int max_z = Math.max(pos1.z,pos2.z);


        this.min = new ChunkPos(min_x,min_z);
        this.max = new ChunkPos(max_x,max_z);

        this.expandedChunks = ChunkPos.stream(new ChunkPos(min_x-1,min_z-1), new ChunkPos(max_x+1,max_z+1)).collect(Collectors.toUnmodifiableSet());

        this.center = new ChunkPos((min_x + max_x)/2,(min_z + max_z)/2 );
        this.radius = Math.max( (max_z - min_z)/2, (max_x - min_x)/2 );
    }

    public boolean isInBox(ChunkPos pos){
        return this.includedChunks.contains(pos);
    }

    public boolean isInBox(Vec3d pos){
        return this.isInBox(new ChunkPos(ChunkSectionPos.getSectionCoord(pos.x),ChunkSectionPos.getSectionCoord(pos.z)));
    }

}
