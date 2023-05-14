package com.thecolonel63.serversidereplayrecorder.recorder;

import com.mojang.authlib.GameProfile;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.ChunkBox;
import com.thecolonel63.serversidereplayrecorder.util.WrappedPacket;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.LightUpdatePacketAccessor;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderStorage;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ReadOnlyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RegionRecorder extends ReplayRecorder {

    public static final Map<String, RegionRecorder> regionRecorderMap = new ConcurrentHashMap<>();

    public static RegionRecorder create(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world){
        RegionRecorder recorder = regionRecorderMap.computeIfAbsent(regionName, n -> {
            try {
                return new RegionRecorder(n, pos1, pos2, world);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            if(recorder.isInit.compareAndSet(false, true))
                recorder.init();
        }catch (Throwable t){
            recorder.handleDisconnect();
            throw t;
        }
        return recorder;
    }

    public static CompletableFuture<RegionRecorder> createAsync(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world){
        return CompletableFuture.supplyAsync(()-> create(regionName,pos1,pos2,world), ServerSideReplayRecorderServer.recorderExecutor);
    }

    public static final GameProfile FAKE_GAMEPROFILE = new GameProfile(Uuids.getOfflinePlayerUuid("Camera"), "Camera");

    public static final String REGION_FOLDER = "region";

    public final String regionName;

    public final ChunkBox region;

    public final Set<ChunkPos> known_chunk_data = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public final Set<ChunkPos> known_chunk_light = Collections.newSetFromMap(new ConcurrentHashMap<>());
    protected Vec3i viewpoint;

    public final ServerWorld world;

    private final AtomicBoolean isInit = new AtomicBoolean();

    private RegionRecorder(String regionName, ChunkPos pos1, ChunkPos pos2, ServerWorld world) throws IOException {
        super();
        this.regionName = regionName;
        this.region = new ChunkBox(pos1,pos2);
        this.viewpoint = new Vec3i(region.center.getCenterX(),(world.getBottomY() + world.getTopY())/2,region.center.getCenterZ());
        this.world = world;
    }

    public void _syncInit(){

        WorldProperties worldProperties = world.getLevelProperties();


        //save basic login packets
        onPacket(new GameJoinS2CPacket(
                0,
                worldProperties.isHardcore(),
                GameMode.SPECTATOR,
                GameMode.SPECTATOR,
                ms.getWorldRegistryKeys(),
                ms.getRegistryManager().toImmutable(),
                world.getDimensionKey(),
                world.getRegistryKey(),
                world.getSeed(),
                ms.getMaxPlayerCount(),
                region.radius,
                region.radius,
                false,
                false,
                world.isDebugWorld(),
                world.isFlat(),
                Optional.empty()
        ));
        onPacket(new FeaturesS2CPacket(FeatureFlags.FEATURE_MANAGER.toId(world.getEnabledFeatures())));
        onPacket(
                new CustomPayloadS2CPacket(CustomPayloadS2CPacket.BRAND, new PacketByteBuf(Unpooled.buffer()).writeString(ms.getServerModName()))
        );
        onPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
        onPacket(new PlayerAbilitiesS2CPacket(new PlayerAbilities()));
        onPacket(new UpdateSelectedSlotS2CPacket(0));
        onPacket(new SynchronizeRecipesS2CPacket(ms.getRecipeManager().values()));
        onPacket(new SynchronizeTagsS2CPacket(TagPacketSerializer.serializeTags(ms.getCombinedDynamicRegistries())));

        //save current player list
        ms.getPlayerManager().getPlayerList().forEach( p -> onPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, p)));

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
        ms
                .getResourcePackProperties()
                .ifPresent(properties -> onPacket(new ResourcePackSendS2CPacket(properties.url(), properties.hash(), properties.isRequired(), properties.prompt())));

        //register as world (dimension) event listeners
        ((RegionRecorderWorld) world).getRegionRecorders().add(this);
        //register as chunk watcher
        this.region.includedChunks.forEach(p -> ((RegionRecorderWorld) world).getRegionRecordersByChunk().computeIfAbsent(p, c -> new LinkedHashSet<>()).add(this));
        this.region.expandedChunks.forEach(p -> ((RegionRecorderWorld) world).getRegionRecordersByExpandedChunk().computeIfAbsent(p, c -> new LinkedHashSet<>()).add(this));
    }

    public void init(){
        //skip the login Phase and start immediatly with the Game packets
        onPacket(new LoginSuccessS2CPacket(FAKE_GAMEPROFILE));


        //this code is mandatory to be run in the Main Server Thread
        if (Thread.currentThread() == ms.getThread()){
            this._syncInit();
        }else{
            CompletableFuture.runAsync(this::_syncInit,ms).join();
        }

        //set the render distance center
        onPacket(new ChunkRenderDistanceCenterS2CPacket(this.region.center.x, this.region.center.z));

        //close loading screen ( yes the inventory packet closes the loading screen )
        onPacket(new InventoryS2CPacket(0, 0, DefaultedList.ofSize(36, ItemStack.EMPTY), ItemStack.EMPTY));

        //load all watched chunks data
        for (ChunkPos pos : this.region.expandedChunks ){
            //get chunk, load if needed, no create
            //this call is deferred to the MainServer executor which runs at the end of a tick in the spare time
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

                    //find the highest non-transparent block as viewpoint
                    int surface_y = worldChunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, viewpoint.getX(), viewpoint.getZ());
                    BlockPos b_pos = new BlockPos(viewpoint.getX(), surface_y, viewpoint.getZ());
                    while (!worldChunk.getBlockState(b_pos).isOpaque() && surface_y != chunk.getBottomY()) {
                        b_pos = new BlockPos(viewpoint.getX(), --surface_y, viewpoint.getZ());
                    }
                    //if no blocks are found in the column keep the original viewpoint
                    if (surface_y != chunk.getBottomY())
                        this.viewpoint = new Vec3i(viewpoint.getX(), surface_y + 1, viewpoint.getZ());
                }
                //save chunk
                onPacket(new WrappedPacket(new ChunkDataS2CPacket(worldChunk, world.getLightingProvider(), null, null, true)));
                //--obsolete in new versions
                //onPacket(new WrappedPacket(new LightUpdateS2CPacket(pos, world.getLightingProvider(), null, null, true)));
                known_chunk_data.add(pos);
                known_chunk_light.add(pos);
            }
        }

        //register as an entity watcher ( this will also send all the packets for spawning entities already in the region )
        //this code is mandatory to be run in the Main Server Thread
        if (Thread.currentThread() == ms.getThread()){
            ((RegionRecorderStorage)world.getChunkManager().threadedAnvilChunkStorage).registerRecorder(this);
        }else{
            CompletableFuture.runAsync(()-> ((RegionRecorderStorage)world.getChunkManager().threadedAnvilChunkStorage).registerRecorder(this),ms).join();
        }

        //set the replay viewpoint to the center of the watched region
        onPacket(new PlayerPositionLookS2CPacket(viewpoint.getX() + 0.5d,viewpoint.getY(),viewpoint.getZ() + 0.5d,0f,0f, Collections.emptySet(),0));
        //ready to record changes
    }

    @Override
    public void onPacket(Packet<?> packet) {
        if(ServerSideReplayRecorderServer.config.isAssume_unloaded_chunks_dont_change()){
            if(packet instanceof ChunkDataS2CPacket newChunk) {
                ChunkPos pos = new ChunkPos(newChunk.getX(), newChunk.getZ());
                if (known_chunk_data.contains(pos))
                    return; //skip chunk data as it was already recorded previously
                else
                    known_chunk_data.add(pos);
            } else if (packet instanceof LightUpdateS2CPacket lightUpdateS2CPacket){
                if(((LightUpdatePacketAccessor)lightUpdateS2CPacket).isOnChunkLoad()){
                    ChunkPos pos = new ChunkPos(lightUpdateS2CPacket.getChunkX(),lightUpdateS2CPacket.getChunkZ());
                    //be sure to record new chunk light packets
                    //skip light data as it was already recorded previously
                    if (!known_chunk_light.contains(pos)){
                        packet = new WrappedPacket(packet);
                        known_chunk_light.add(pos);
                    }
                }

            }
        }
        if (packet instanceof UnloadChunkS2CPacket)
            //this is always ignored by clients but better be safe
            return;
        if (packet instanceof ChunkLoadDistanceS2CPacket)
            //do not update view distance
            return;
        super.onPacket(packet);
    }

    @Override
    public String getRecordingName() {
        return this.regionName;
    }

    @Override
    protected String getSaveFolder(){
        String name = (this.regionName != null) ? this.regionName : "NONAME";
        if (new File(ServerSideReplayRecorderServer.config.getReplay_folder_name()).isAbsolute())
            return Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), REGION_FOLDER,name).toString();
        else
            return Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), REGION_FOLDER,name).toString();
    }

    @Override
    public void handleDisconnect() {
        regionRecorderMap.remove(regionName);

        //be sure to run the code inside the Main server thread
        if (Thread.currentThread() == ms.getThread()){
            _unRegister();
        }else{
            CompletableFuture.runAsync(this::_unRegister,ms).join();
        }

        super.handleDisconnect();
    }

    private void _unRegister() {
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
        //un-register as an entity watcher
        ((RegionRecorderStorage)world.getChunkManager().threadedAnvilChunkStorage).registerRecorder(this);
    }

    @Override
    public void addMarker(String name) {
        this.addMarker(viewpoint.getX(), viewpoint.getY(), viewpoint.getZ(), 0, 0, 0, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RegionRecorder recorder){
            return this.regionName.equals(recorder.regionName);
        }
        return false;
    }
}