package com.thecolonel63.serversidereplayrecorder.recorder;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.thecolonel63.serversidereplayrecorder.util.ChunkBox;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderStorage;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.util.*;

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

    public static final int FAKE_PLAYER_ID = Integer.MAX_VALUE - 5;

    public static final GameProfile FAKE_GAMEPROFILE = new GameProfile(PlayerEntity.getOfflinePlayerUuid("Camera"), "Camera");

    public final String regionName;

    public final ChunkBox region;

    public final ServerWorld world;

    private PlayerEntity fakePlayer;

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
        this.world = world;
    }

    public void init(){
        ((RegionRecorderWorld)world).getRegionRecorders().add(this);
        this.region.includedChunks.forEach( p -> ((RegionRecorderWorld)world).getRegionRecordersByChunk().computeIfAbsent(p, c -> new LinkedHashSet<>()).add(this));
        onPacket(new LoginSuccessS2CPacket(FAKE_GAMEPROFILE));
        WorldProperties worldProperties = world.getLevelProperties();
        //spawn a dummy player
        this.fakePlayer = new PlayerEntity(world, new BlockPos(region.center.getCenterX(),(world.getBottomY() + world.getTopY())/2,region.center.getCenterZ()),0, FAKE_GAMEPROFILE) {
            @Override
            public boolean isSpectator() {
                return true;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        onPacket(new GameJoinS2CPacket(
                this.fakePlayer.getId(),
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
        //handle basic login packets
        onPacket(
                new CustomPayloadS2CPacket(CustomPayloadS2CPacket.BRAND, new PacketByteBuf(Unpooled.buffer()).writeString(ms.getServerModName()))
        );
        onPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
        onPacket(new PlayerAbilitiesS2CPacket(new PlayerAbilities()));
        onPacket(new SynchronizeTagsS2CPacket(ms.getTagManager().toPacket(ms.getRegistryManager())));

        PlayerListS2CPacket packet = new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, Collections.emptyList());
        packet.entries
                .add(
                        new PlayerListS2CPacket.Entry(
                                fakePlayer.getGameProfile(),
                                2,
                                GameMode.SPECTATOR,
                                fakePlayer.getName()
                        )
                );
        onPacket(packet);
        ms.getPlayerManager().getPlayerList().forEach( p -> {
            onPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, p));
        });


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

        //set the replay viewpoint to the center of the watched region
        onPacket(new PlayerPositionLookS2CPacket(this.fakePlayer.getX(), this.fakePlayer.getY(), fakePlayer.getZ(),0,0, Set.of(PlayerPositionLookS2CPacket.Flag.values()),0,false));
        onPacket(new InventoryS2CPacket(0, 0, DefaultedList.ofSize(36, ItemStack.EMPTY), ItemStack.EMPTY));

        //set the render center
        onPacket(new ChunkRenderDistanceCenterS2CPacket(this.region.center.x, this.region.center.z));

        ServerChunkManager storage = world.getChunkManager();
        //load all watched chunks data
        for (ChunkPos pos : this.region.includedChunks ){
            //get chunk if at least border
            Chunk chunk = storage.getWorldChunk(pos.x,pos.z);
            if (chunk instanceof WorldChunk worldChunk){
                onPacket(new ChunkDataS2CPacket(worldChunk));
                onPacket(new LightUpdateS2CPacket(pos,storage.getLightingProvider(),null,null,true));
            }
        }
        //load all watched entities
        ((RegionRecorderStorage)world.getChunkManager().threadedAnvilChunkStorage).registerRecorder(this);

    }

    @Override
    protected String getSaveFolder(){
        String name = (this.regionName != null) ? this.regionName : "NONAME";
        return this.tmp_folder.getParentFile() + "/region " + name;
    }

    @Override
    public void spawnRecordingPlayer() {
        try {
            save(new PlayerSpawnS2CPacket(this.fakePlayer));
            save(new EntityTrackerUpdateS2CPacket(this.fakePlayer.getId(), this.fakePlayer.getDataTracker(), false));
            playerSpawned = true;
            lastX = lastY = lastZ = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

/*
    public void onPlayerTick() {
        try {

            if (!playerSpawned) return; //We can't update what the player is *doing* if they don't exist...

            //Update player position.
            Packet<?> packet;

            PlayerEntity player = this.fakePlayer;

            boolean force = false;

            if (lastX == null || lastY == null || lastZ == null) {
                force = true;
                lastX = player.getX();
                lastY = player.getY();
                lastZ = player.getZ();
            }

            ticksSinceLastCorrection++;

            if (ticksSinceLastCorrection >= 100) {
                ticksSinceLastCorrection = 0;
                force = true;
            }

            double dx = player.getX() - lastX;
            double dy = player.getY() - lastY;
            double dz = player.getZ() - lastZ;

            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();

            final double maxRelDist = 8.0;

            if (force || Math.abs(dx) > maxRelDist || Math.abs(dy) > maxRelDist || Math.abs(dz) > maxRelDist) {
                packet = new EntityPositionS2CPacket(player);
            } else {
                byte newYaw = (byte) ((int) (player.getYaw() * 256.0F / 360.0F));
                byte newPitch = (byte) ((int) (player.getPitch() * 256.0F / 360.0F));
                packet = new EntityS2CPacket.RotateAndMoveRelative(player.getId(), (short) Math.round(dx * 4096), (short) Math.round(dy * 4096), (short) Math.round(dz * 4096), newYaw, newPitch, player.isOnGround());
            }

            save(packet);

            //Update player rotation
            int rotationYawHead = ((int) (player.headYaw * 256.0F / 360.0F));
            if (!Objects.equals(rotationYawHead, rotationYawHeadBefore)) {
                save(new EntitySetHeadYawS2CPacket(player, (byte) rotationYawHead));
                rotationYawHeadBefore = rotationYawHead;
            }

            //Update player velocity
            save(new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));

            //Update player hand swinging and other animations.
            if (player.handSwinging && player.handSwingTicks == 0) {
                save(new EntityAnimationS2CPacket(
                        player, player.preferredHand == Hand.MAIN_HAND ? 0 : 3
                ));
            }

            //Update player items
            List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
            boolean needsToUpdate = false;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getEquippedStack(slot);
                if (playerItems[slot.ordinal()] != stack) {
                    playerItems[slot.ordinal()] = stack;
                    equipment.add(new Pair<>(slot, stack));
                    needsToUpdate = true;
                }
                if (needsToUpdate) {
                    save(new EntityEquipmentUpdateS2CPacket(player.getId(), equipment));
                }
            }

            //Update player vehicle
            Entity vehicle = player.getVehicle();
            int vehicleId = vehicle == null ? -1 : vehicle.getId();
            if (lastRiding != vehicleId) {
                lastRiding = vehicleId;
                save(new EntityAttachS2CPacket(
                        //#if MC<10904
                        //$$ 0,
                        //#endif
                        player,
                        vehicle
                ));
            }

            //Sleeping
            if (!player.isSleeping() && wasSleeping) {
                save(new EntityAnimationS2CPacket(player, 2));
                wasSleeping = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/

    @Override
    public void handleDisconnect() {
        ((RegionRecorderWorld)world).getRegionRecorders().remove(this);
        this.region.includedChunks.forEach( p -> Optional.ofNullable(((RegionRecorderWorld)world).getRegionRecordersByChunk().get(p)).ifPresent(s -> {
            s.remove(this);
            if (s.isEmpty())
                ((RegionRecorderWorld)world).getRegionRecordersByChunk().remove(p);
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