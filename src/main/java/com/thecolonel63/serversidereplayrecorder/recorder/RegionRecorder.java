package com.thecolonel63.serversidereplayrecorder.recorder;

import com.mojang.authlib.GameProfile;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.ChunkBox;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderStorage;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.*;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class RegionRecorder extends PlayerRecorder {

    public static final Map<String, RegionRecorder> recorders = new HashMap<>();

    public static RegionRecorder create(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world) throws IOException{
        if (recorders.containsKey(regionName))
            return recorders.get(regionName);
        RegionRecorder recorder = new RegionRecorder(regionName, pos1, pos2, world);
        recorder.init();
        recorders.put(regionName, recorder);
        return recorder;
    }

    public static CompletableFuture<RegionRecorder> createAsync(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world){
        return CompletableFuture.supplyAsync(()-> {
            try {
                return create(regionName,pos1,pos2,world);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, ServerSideReplayRecorderServer.recorderExecutor);
    }

    public static final GameProfile FAKE_GAMEPROFILE = new GameProfile(PlayerEntity.getOfflinePlayerUuid("Camera"), "Camera");

    public static final String REGION_FOLDER = "region";

    public final String regionName;

    public final ChunkBox region;

    protected Vec3i viewpoint;

    public final ServerWorld world;

    private RegionRecorder(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world) throws IOException {
        super(new ClientConnection(NetworkSide.SERVERBOUND){
            @Override
            public void disableAutoRead()
            {
            }

            @Override
            public void handleDisconnection()
            {
            }
        });
        this.regionName = regionName;
        this.region = new ChunkBox(pos1,pos2);
        this.viewpoint = new Vec3i(region.center.getCenterX(),(world.getBottomY() + world.getTopY())/2,region.center.getCenterZ());
        this.world = world;
        this.playerSpawned = true;
    }

    public void init(){
        //skip the login Phase and start immediatly with the Game packets
        onPacket(new LoginSuccessS2CPacket(FAKE_GAMEPROFILE));
        //override the fake player name with the region name
        this.playerName = regionName;

        WorldProperties worldProperties = world.getLevelProperties();


        //save basic login packets
        onPacket(new GameJoinS2CPacket(
                0,
                GameMode.SPECTATOR,
                GameMode.SPECTATOR,
                BiomeAccess.hashSeed(world.getSeed()),
                worldProperties.isHardcore(),
                ms.getWorldRegistryKeys(),
                (DynamicRegistryManager.Impl) ms.getRegistryManager(),
                world.getDimension(),
                world.getRegistryKey(),
                ms.getMaxPlayerCount(),
                region.radius,
                false,
                false,
                world.isDebugWorld(),
                world.isFlat()
        ));
        onPacket(
                new CustomPayloadS2CPacket(CustomPayloadS2CPacket.BRAND, new PacketByteBuf(Unpooled.buffer()).writeString(ms.getServerModName()))
        );
        onPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
        onPacket(new PlayerAbilitiesS2CPacket(new PlayerAbilities()));
        onPacket(new SynchronizeTagsS2CPacket(ms.getTagManager().toPacket(ms.getRegistryManager())));

        //save current player list
        ms.getPlayerManager().getPlayerList().forEach( p -> {
            onPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, p));
        });

        //save world ( dimension ) information
        WorldBorder worldBorder = world.getWorldBorder();
        onPacket(new WorldBorderInitializeS2CPacket(worldBorder));
        onPacket(new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)));
        onPacket(new PlayerSpawnPositionS2CPacket(world.getSpawnPos(), world.getSpawnAngle()));
        if (world.isRaining()) {
            onPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED, GameStateChangeS2CPacket.DEMO_OPEN_SCREEN));
            onPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, world.getRainGradient(1.0F)));
            onPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED, world.getThunderGradient(1.0F)));
        }
        if (!ms.getResourcePackUrl().isEmpty()) {
            onPacket(new ResourcePackSendS2CPacket(ms.getResourcePackUrl(), ms.getResourcePackHash(), ms.requireResourcePack(), ms.getResourcePackPrompt()));
        }

        //set the render distance center
        onPacket(new ChunkRenderDistanceCenterS2CPacket(this.region.center.x, this.region.center.z));

        //close loading screen ( yes the inventory packet closes the loading screen )
        onPacket(new InventoryS2CPacket(0, 0, DefaultedList.ofSize(36, ItemStack.EMPTY), ItemStack.EMPTY));

        //load all watched chunks data
        for (ChunkPos pos : this.region.expandedChunks ){
            //get chunk, load if needed, no create
            Chunk chunk = world.getChunk(pos.x,pos.z, ChunkStatus.EMPTY);
            WorldChunk worldChunk = null;
            if (chunk instanceof  WorldChunk)
                worldChunk = (WorldChunk) chunk;
            else if (chunk instanceof ReadOnlyChunk readOnlyChunk) {
                worldChunk = readOnlyChunk.getWrappedChunk();
            }
            // if chunk was already generated
            if(worldChunk != null) {
                //if center chunk has data
                if (pos.equals(this.region.center)) {
                    int surface_y = worldChunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, viewpoint.getX(), viewpoint.getZ());
                    BlockPos b_pos = new BlockPos(viewpoint.getX(), surface_y, viewpoint.getZ());
                    while (!worldChunk.getBlockState(b_pos).isOpaque() && surface_y != chunk.getBottomY()) {
                        b_pos = new BlockPos(viewpoint.getX(), --surface_y, viewpoint.getZ());
                    }
                    //find the highest non-transparent block as viewpoint
                    this.viewpoint = new Vec3i(viewpoint.getX(), surface_y + 1, viewpoint.getZ());
                }
                //save chunk
                onPacket(new ChunkDataS2CPacket(worldChunk));
                onPacket(new LightUpdateS2CPacket(pos, world.getLightingProvider(), null, null, true));
            }
        }

        //register as world (dimension) event listeners
        ((RegionRecorderWorld)world).getRegionRecorders().add(this);
        //register as chunk watcher
        this.region.includedChunks.forEach( p -> ((RegionRecorderWorld)world).getRegionRecordersByChunk().computeIfAbsent(p, c -> new LinkedHashSet<>()).add(this));
        this.region.expandedChunks.forEach( p -> ((RegionRecorderWorld)world).getRegionRecordersByExpandedChunk().computeIfAbsent(p, c -> new LinkedHashSet<>()).add(this));

        //register as an entity watcher ( this will also send all the packets for spawning entities already in the region )
        ((RegionRecorderStorage)world.getChunkManager().threadedAnvilChunkStorage).registerRecorder(this);

        //set the replay viewpoint to the center of the watched region
        onPacket(new PlayerPositionLookS2CPacket(viewpoint.getX() + 0.5,viewpoint.getY(),viewpoint.getZ() + 0.5,0,0, Collections.emptySet(),0,false));

        //ready to record changes
    }

    @Override
    protected String getSaveFolder(){
        String name = (this.regionName != null) ? this.regionName : "NONAME";
        return Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), REGION_FOLDER,name).toString();
    }

    @Override
    public void handleDisconnect() {
        ((RegionRecorderWorld)world).getRegionRecorders().remove(this);
        this.region.includedChunks.forEach( p -> Optional.ofNullable(((RegionRecorderWorld)world).getRegionRecordersByChunk().get(p)).ifPresent(s -> {
            s.remove(this);
            if (s.isEmpty())
                ((RegionRecorderWorld)world).getRegionRecordersByChunk().remove(p);
        }));
        this.region.expandedChunks.forEach( p -> Optional.ofNullable(((RegionRecorderWorld)world).getRegionRecordersByExpandedChunk().get(p)).ifPresent(s -> {
            s.remove(this);
            if (s.isEmpty())
                ((RegionRecorderWorld)world).getRegionRecordersByExpandedChunk().remove(p);
        }));
        super.handleDisconnect();
        recorders.remove(regionName);
    }

    @Override
    public int hashCode() {
        return (this.regionName==null)?super.hashCode():this.regionName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RegionRecorder recorder){
            return this.regionName.equals(recorder.regionName);
        }
        return false;
    }
}