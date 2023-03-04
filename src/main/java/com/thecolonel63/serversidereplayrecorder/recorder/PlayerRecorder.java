package com.thecolonel63.serversidereplayrecorder.recorder;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.mixin.main.LoginSuccessfulS2CPacketAccessor;
import com.thecolonel63.serversidereplayrecorder.util.WrappedPacket;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.LightUpdatePacketAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRecorder extends ReplayRecorder {
    public static final String PLAYER_FOLDER = "player";
    public static final Map<ClientConnection, PlayerRecorder> playerRecorderMap = new ConcurrentHashMap<>();
    public final ClientConnection connection;
    protected final ItemStack[] playerItems = new ItemStack[6];
    public UUID playerId;
    public String playerName;
    public boolean isRespawning;
    protected boolean playerSpawned = false;
    protected Double lastX, lastY, lastZ;
    protected int ticksSinceLastCorrection;
    protected Integer rotationYawHeadBefore;
    protected int lastRiding = -1;
    protected boolean wasSleeping;

    public PlayerRecorder(ClientConnection connection) throws IOException {
        super();
        this.connection = connection;
    }

    @Override
    public String getRecordingName() {
        return this.playerName;
    }

    @Override
    protected String getSaveFolder(){
        String name = (playerName != null) ? playerName : "NONAME";
        return Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), PLAYER_FOLDER,name).toString();
    }


    public void onPacket(Packet<?> packet) {
        if (!playerSpawned && packet instanceof PlayerListS2CPacket) {
            spawnRecordingPlayer();
        }

        if (!isRespawning && packet instanceof PlayerRespawnS2CPacket) {
            //Catches a dimension change that isn't technically a respawn, but should still be count as one.
            spawnRecordingPlayer();
        }

        if (packet instanceof LightUpdateS2CPacket lightUpdateS2CPacket){
            if(((LightUpdatePacketAccessor)lightUpdateS2CPacket).isOnChunkLoad()){
                //be sure to record new chunk light packets
                packet = new WrappedPacket(packet);
            }
        }

        super.onPacket(packet);
        if (packet instanceof LoginSuccessS2CPacket loginSuccessS2CPacket) {
            GameProfile profile = ((LoginSuccessfulS2CPacketAccessor) loginSuccessS2CPacket).getProfile();
            playerId = profile.getId();
            playerName = profile.getName();
        }
    }

    @Override
    public synchronized void handleDisconnect() {
        synchronized (playerRecorderMap) {
            //Player has disconnected, so remove our recorder from the map and close the output streams.
            playerRecorderMap.remove(this.connection);
        }
        super.handleDisconnect();
    }
    
    public void spawnRecordingPlayer() {
        try {
            ServerPlayerEntity player = ms.getPlayerManager().getPlayer(playerId);
            if (player == null) return;
            onPacket(new PlayerSpawnS2CPacket(player));
            onPacket(new EntityTrackerUpdateS2CPacket(player.getId(), player.getDataTracker(), false));
            playerSpawned = true;
            lastX = lastY = lastZ = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onPlayerTick() {
        try {

            if (!playerSpawned) return; //We can't update what the player is *doing* if they don't exist...

            //Update player position.
            Packet<?> packet;

            ServerPlayerEntity player = ms.getPlayerManager().getPlayer(playerId);
            if (player == null) return;

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

            onPacket(packet);

            //Update player rotation
            int rotationYawHead = ((int) (player.headYaw * 256.0F / 360.0F));
            if (!Objects.equals(rotationYawHead, rotationYawHeadBefore)) {
                onPacket(new EntitySetHeadYawS2CPacket(player, (byte) rotationYawHead));
                rotationYawHeadBefore = rotationYawHead;
            }

            //Update player velocity
            onPacket(new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));

            //Update player hand swinging and other animations.
            if (player.handSwinging && player.handSwingTicks == 0) {
                onPacket(new EntityAnimationS2CPacket(
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
                    onPacket(new EntityEquipmentUpdateS2CPacket(player.getId(), equipment));
                }
            }

            //Update player vehicle
            Entity vehicle = player.getVehicle();
            int vehicleId = vehicle == null ? -1 : vehicle.getId();
            if (lastRiding != vehicleId) {
                lastRiding = vehicleId;
                onPacket(new EntityAttachS2CPacket(
                        //#if MC<10904
                        //$$ 0,
                        //#endif
                        player,
                        vehicle
                ));
            }

            //Sleeping
            if (!player.isSleeping() && wasSleeping) {
                onPacket(new EntityAnimationS2CPacket(player, 2));
                wasSleeping = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClientSound(SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        try {
            // Send to all other players in ServerWorldEventHandler#playSoundToAllNearExcept
            onPacket(new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClientEffect(int type, BlockPos pos, int data) {
        try {
            // Send to all other players in ServerWorldEventHandler#playEvent
            onPacket(new WorldEventS2CPacket(type, pos, data, false));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onBlockBreakAnim(int breakerId, BlockPos pos, int progress) {
        if (playerId == null) return;
        PlayerEntity thePlayer = ms.getPlayerManager().getPlayer(playerId);
        if (thePlayer != null && breakerId == thePlayer.getId()) {
            onPacket(new BlockBreakingProgressS2CPacket(breakerId, pos, progress));
        }
    }

    private final LocalDateTime start_time = LocalDateTime.now();

    public Duration getUptime(){
        return Duration.between(start_time, LocalDateTime.now());
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PlayerRecorder recorder){
            return this.playerName.equals(recorder.playerName);
        }
        return false;
    }

}
