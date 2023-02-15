package com.thecolonel63.serversidereplayrecorder.recorder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.thecolonel63.serversidereplayrecorder.mixin.main.LoginSuccessfulS2CPacketAccessor;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.FileHandlingUtility;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.MinecraftVersion;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class PlayerRecorder {

    public static final String PLAYER_FOLDER = "player";
    public final ClientConnection connection;
    public final MinecraftServer ms = ServerSideReplayRecorderServer.server;
    protected final File tmp_folder;

    protected final BufferedOutputStream bos;
    protected final FileOutputStream fos;
    protected final ItemStack[] playerItems = new ItemStack[6];
    protected final JsonArray uuids = new JsonArray();
    public UUID playerId;
    public String playerName;
    public boolean isRespawning;
    protected long start;
    protected String fileName;
    protected NetworkState state = NetworkState.LOGIN;
    protected int timestamp;
    protected boolean startedRecording = false;
    protected boolean playerSpawned = false;
    protected Double lastX, lastY, lastZ;
    protected int ticksSinceLastCorrection;
    protected Integer rotationYawHeadBefore;
    protected int lastRiding = -1;
    protected boolean wasSleeping;
    protected boolean open = true;

    public String getFileName() {
        return fileName;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public PlayerRecorder(ClientConnection connection) throws IOException {
        tmp_folder = Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), "recording_" + this.hashCode()).toFile();
        tmp_folder.mkdirs();
        fos = new FileOutputStream(Paths.get(tmp_folder.getAbsolutePath(), "recording.tmcpr").toFile(), true);
        bos = new BufferedOutputStream(fos);
        this.connection = connection;
    }

    protected void writeMetaData(String serverName, boolean isFinishing) {
        try {
            JsonObject object = new JsonObject();
            object.addProperty("singleplayer", false);
            object.addProperty("serverName", serverName);
            object.addProperty("customServerName", serverName);
            object.addProperty("duration", timestamp);
            object.addProperty("date", start);
            object.addProperty("mcversion", MinecraftVersion.GAME_VERSION.getName());
            object.addProperty("fileFormat", "MCPR");
            object.addProperty("fileFormatVersion", 14); //Unlikely to change any time soon, last time this was updates was several major versions ago.
            object.addProperty("protocol", SharedConstants.getProtocolVersion());
            object.addProperty("generator", "thecolonel63's Server Side Replay Recorder");
            object.addProperty("selfId", -1);
            object.add("players", uuids);
            FileWriter fw = new FileWriter(tmp_folder + "/metaData.json", false);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(object.toString());
            bw.close();
            fw.close();
            if (isFinishing) compressReplay();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
    
    protected String getSaveFolder(){
        String name = (playerName != null) ? playerName : "NONAME";
        return Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), PLAYER_FOLDER,name).toString();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void compressReplay() {


        File output = Paths.get(this.getSaveFolder(),fileName).toFile();
        ArrayList<File> filesToCompress = new ArrayList<>() {{
            add(new File(tmp_folder + "/metaData.json"));
            add(new File(tmp_folder + "/recording.tmcpr"));
        }};

        output.getParentFile().mkdirs();

        try {
            FileHandlingUtility.zip(filesToCompress, output.getAbsolutePath(), true, tmp_folder);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void onPacket(Packet<?> packet) {
        if (!startedRecording) {
            start = System.currentTimeMillis(); //More accurate timestamps.
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(start);
            fileName = cal.get(Calendar.YEAR) + "_" + String.format("%02d", (cal.get(Calendar.MONTH) + 1)) + "_" + String.format("%02d", (cal.get(Calendar.DAY_OF_MONTH))) + "_" + String.format("%02d", (cal.get(Calendar.HOUR_OF_DAY))) + "_" + String.format("%02d", (cal.get(Calendar.MINUTE))) + "_" + String.format("%02d", (cal.get(Calendar.SECOND))) + ".mcpr";
            startedRecording = true;
        }

        if (packet instanceof LoginCompressionS2CPacket) {
            return; //We don't compress anything in replays, so ignore the packet.
        } else if (packet instanceof LoginSuccessS2CPacket loginSuccessS2CPacket) {
            state = NetworkState.PLAY; //We are now dealing with "playing" packets, so set the network state accordingly.
            //Also, set the profile for use in fixing.
            GameProfile profile = ((LoginSuccessfulS2CPacketAccessor) loginSuccessS2CPacket).getProfile();
            playerId = profile.getId();
            playerName = profile.getName();
        }/* else if (packet instanceof PlayerSpawnS2CPacket packet1) {
            uuids.add(String.valueOf(packet1.getPlayerUuid())); //Broken, seems to be unused by the regular replay client.
        }*/

        timestamp = (int) (System.currentTimeMillis() - start);
        save(packet);
    }

    public synchronized void handleDisconnect() {
        synchronized (ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap) {
            //Player has disconnected, so remove our recorder from the map and close the output streams.
            ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.remove(this.connection);
            try {
                this.open = false;
                bos.close();
                fos.close();
                Thread savingThread = new Thread(() -> writeMetaData(ServerSideReplayRecorderServer.config.getServer_name(), true));
                savingThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void save(Packet<?> packet) {
        //shallow all packets if this recording is already closed
        if (! this.open)
            return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        try {

            //Write the timestamp and the amount of readable bytes, including an extra byte for the packet ID.
            bos.write(intToByteArray(timestamp));
            bos.write(intToByteArray(buf.readableBytes() + 1));

            //Get the packet ID
            Integer packetId = state.getPacketId(NetworkSide.CLIENTBOUND, packet);
            if (packet instanceof LoginSuccessS2CPacket) {
                packetId = 2; //Here because the connection state was already changed when the packet was first read, so trying to do the above *will* result in an error.
            }

            if (packetId == null) {
                //The packet ID is something we do not have an ID for.
                throw new IOException("Unknown packet ID for class " + packet.getClass());
            } else {
                //Write the packet ID.
                bos.write(packetId);
            }

            //Write the packet.
            bos.write(buf.array(), 0, buf.readableBytes());

            if (!playerSpawned && packet instanceof PlayerListS2CPacket) {
                spawnRecordingPlayer();
            }

            if (!isRespawning && packet instanceof PlayerRespawnS2CPacket) {
                //Catches a dimension change that isn't technically a respawn, but should still be count as one.
                spawnRecordingPlayer();
            }

            writeMetaData(ServerSideReplayRecorderServer.config.getServer_name(), false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected byte[] intToByteArray(int input) {
        //Takes an int, gives back the int as a byte array.
        ByteBuffer x = ByteBuffer.allocate(4);
        x.order(ByteOrder.BIG_ENDIAN);
        x.putInt(input);
        return x.array();
    }

    public void spawnRecordingPlayer() {
        try {
            ServerPlayerEntity player = ms.getPlayerManager().getPlayer(playerId);
            if (player == null) return;
            save(new PlayerSpawnS2CPacket(player));
            save(new EntityTrackerUpdateS2CPacket(player.getId(), player.getDataTracker(), false));
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

    public void onClientSound(SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        try {
            // Send to all other players in ServerWorldEventHandler#playSoundToAllNearExcept
            save(new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClientEffect(int type, BlockPos pos, int data) {
        try {
            // Send to all other players in ServerWorldEventHandler#playEvent
            save(new WorldEventS2CPacket(type, pos, data, false));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onBlockBreakAnim(int breakerId, BlockPos pos, int progress) {
        if (playerId == null) return;
        PlayerEntity thePlayer = ms.getPlayerManager().getPlayer(playerId);
        if (thePlayer != null && breakerId == thePlayer.getId()) {
            save(new BlockBreakingProgressS2CPacket(breakerId, pos, progress));
        }
    }

    private final LocalDateTime start_time = LocalDateTime.now();

    public Duration getUptime(){
        return Duration.between(start_time, LocalDateTime.now());
    }

}
