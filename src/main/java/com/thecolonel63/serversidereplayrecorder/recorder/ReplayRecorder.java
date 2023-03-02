package com.thecolonel63.serversidereplayrecorder.recorder;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.FileHandlingUtility;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.MinecraftVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class ReplayRecorder {

    public static final Set<ReplayRecorder> active_recorders = Collections.newSetFromMap(new WeakHashMap<>());
    public final MinecraftServer ms = ServerSideReplayRecorderServer.server;
    protected final File tmp_folder;
    protected final File recording_file;
    protected final BufferedOutputStream bos;
    protected final FileOutputStream fos;
    protected final FileWriter debugFile;
    protected long start;
    protected long server_start;
    protected String fileName;
    protected NetworkState state = NetworkState.LOGIN;
    protected int timestamp;
    protected int server_timestamp;

    protected int last_timestamp;
    protected boolean startedRecording = false;
    protected boolean open = true;

    public boolean isOpen() {
        return open;
    }

    private static final ThreadFactory fileWriterFactory = new ThreadFactoryBuilder().setNameFormat("Replay-Writer-%d").setDaemon(true).build();
    protected ExecutorService fileWriterExecutor = Executors.newSingleThreadExecutor(fileWriterFactory);

    public String getFileName() {
        return fileName;
    }

    public abstract String getRecordingName();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public ReplayRecorder() throws IOException {
        tmp_folder = Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), "recording_" + this.hashCode()).toFile();
        tmp_folder.mkdirs();
        recording_file= Paths.get(tmp_folder.getAbsolutePath(), "recording.tmcpr").toFile();

        long remaining_space = recording_file.getUsableSpace();
        long storage_required = (( active_recorders.size() + 1 ) * 2L) * ServerSideReplayRecorderServer.config.getMax_file_size();
        //prevent filling storage ( causing server crash )
        if(remaining_space<storage_required){
            throw new IOException("No enough space left on device");
        }

        fos = new FileOutputStream(this.recording_file, false);
        bos = new BufferedOutputStream(fos);
        if (ServerSideReplayRecorderServer.config.isDebug()) {
            debugFile = new FileWriter(Paths.get(tmp_folder.getAbsolutePath(), "debug.json").toFile());
            debugFile.write("[{}\n");
        }else
            debugFile = null;

    }

    protected void writeMetaData(String serverName, boolean isFinishing) {
        try {
            synchronized (this) {
                JsonObject object = new JsonObject();
                object.addProperty("singleplayer", false);
                object.addProperty("serverName", serverName);
                object.addProperty("customServerName", serverName + " | " + this.getRecordingName());
                object.addProperty("duration", last_timestamp);
                object.addProperty("date", start);
                object.addProperty("mcversion", MinecraftVersion.GAME_VERSION.getName());
                object.addProperty("fileFormat", "MCPR");
                object.addProperty("fileFormatVersion", 14); //Unlikely to change any time soon, last time this was updates was several major versions ago.
                object.addProperty("protocol", SharedConstants.getProtocolVersion());
                object.addProperty("generator", "mattymatty's enhanced thecolonel63's Server Side Replay Recorder");
                object.addProperty("selfId", -1);
                object.add("players", new JsonArray());
                FileWriter fw = new FileWriter(tmp_folder + "/metaData.json", false);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(object.toString());
                bw.close();
                fw.close();
                if (isFinishing)
                    compressReplay();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
    
    protected abstract String getSaveFolder();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void compressReplay() {


        File output = Paths.get(this.getSaveFolder(),fileName).toFile();
        File[] filesToCompress = tmp_folder.listFiles(File::isFile);

        output.getParentFile().mkdirs();

        try {
            assert filesToCompress != null;
            FileHandlingUtility.zip(Arrays.asList(filesToCompress), output.getAbsolutePath(), true, tmp_folder);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public synchronized void onPacket(Packet<?> packet) {
        if (! this.open)
            return;
        active_recorders.add(this);

        if (!startedRecording) {
            start = System.currentTimeMillis(); //More accurate timestamps.
            server_start = ServerSideReplayRecorderServer.server.getTicks();
            Date time = new Date(start);
            fileName = String.format("%s.mcpr", new SimpleDateFormat("yyyy-M-dd_HH-mm-ss").format(time));
            startedRecording = true;
        }

        if (packet instanceof LoginCompressionS2CPacket) {
            return; //We don't compress anything in replays, so ignore the packet.
        } else if (packet instanceof LoginSuccessS2CPacket loginSuccessS2CPacket) {
            state = NetworkState.PLAY; //We are now dealing with "playing" packets, so set the network state accordingly.
        }

        this.timestamp = (int) (System.currentTimeMillis() - start);
        save(packet, timestamp);
    }

    public synchronized void handleDisconnect(){
        this.handleDisconnect(false);
    }

    public synchronized void handleDisconnect(boolean immediate) {
            this.open = false;
            active_recorders.remove(this);
            Runnable endTask = () -> {
                try {
                    bos.close();
                    fos.close();
                    if (debugFile != null) {
                        debugFile.write("]");
                        debugFile.close();
                    }

                    writeMetaData(ServerSideReplayRecorderServer.config.getServer_name(), true);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            if(immediate){
                //kill all tasks and close
                this.fileWriterExecutor.shutdownNow();
                new Thread(endTask).start();
            }else {
                //wait for all tasks and close
                this.fileWriterExecutor.execute(endTask);
                this.fileWriterExecutor.shutdown();
            }
    }

    public synchronized void onServerTick(){
        int old_timestamp = this.server_timestamp;
        int new_timestamp = this.server_timestamp = (ms.getTicks() - (int)server_start) * 50;
        if (ServerSideReplayRecorderServer.config.use_server_timestamps()){
            LinkedList<Packet<?>> tick_packets;
            synchronized (packetQueue) {
                tick_packets = new LinkedList<>(this.packetQueue);
                packetQueue.clear();
            }
            this.fileWriterExecutor.execute(()->{
                double delta = (new_timestamp - old_timestamp)/(double)tick_packets.size();
                double curr_timestamp = old_timestamp;
                for (Packet<?> packet : tick_packets){
                    _save(packet,(int)Math.floor(curr_timestamp));
                    curr_timestamp+=delta;
                }
            });
        }
    }

    protected final Queue<Packet<?>> packetQueue = new LinkedList<>();

    public void save(Packet<?> packet) {
        this.save(packet, this.timestamp);
    }

    public synchronized void save(Packet<?> packet, int timestamp) {
        if (ServerSideReplayRecorderServer.config.use_server_timestamps()) {
            synchronized (packetQueue){
                packetQueue.add(packet);
            }
        }else{
            //use a separate thread to write to file ( to not hang up the server )
            this.fileWriterExecutor.execute(() -> this._save(packet, timestamp));
        }
    }

    private long current_file_size=0;

    private void _save(Packet<?> packet, int timestamp) {

        if(this.current_file_size > ServerSideReplayRecorderServer.config.getMax_file_size()){
            ServerSideReplayRecorderServer.LOGGER.warn("Max File Size Reached, stopping recording %s".formatted(this.getRecordingName()));
            this.handleDisconnect(true);
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);

        try {
            synchronized (bos) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                //Write the timestamp and the amount of readable bytes, including an extra byte for the packet ID.
                buffer.write(intToByteArray(timestamp));
                buffer.write(intToByteArray(buf.readableBytes() + 1));

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
                    buffer.write(packetId);
                }

                //Write the packet.
                buffer.write(buf.array(), 0, buf.readableBytes());
                bos.write(buffer.toByteArray());
                this.current_file_size+=buffer.size();
                writeMetaData(ServerSideReplayRecorderServer.config.getServer_name(), false);
                if (debugFile!=null) {

                    debugFile.write(",{\"time\": %d, \"name\": \"%s\", \"size\": %d}\n".formatted(timestamp, FabricLoader.getInstance().getMappingResolver().unmapClassName(FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace(),packet.getClass().getSimpleName()), buffer.size()));
                }
                buffer.close();
                this.last_timestamp = timestamp;
            }
        } catch (IOException e) {
            if (e.getMessage().equals("No space left on device")){
                ServerSideReplayRecorderServer.LOGGER.warn("Disk space is too low, stopping recording %s".formatted(this.getRecordingName()));
                this.handleDisconnect();
            }else {
                e.printStackTrace();
            }
        }
    }

    protected byte[] intToByteArray(int input) {
        //Takes an int, gives back the int as a byte array.
        ByteBuffer x = ByteBuffer.allocate(4);
        x.order(ByteOrder.BIG_ENDIAN);
        x.putInt(input);
        return x.array();
    }

    private final LocalDateTime start_time = LocalDateTime.now();

    public Duration getUptime(){
        return Duration.between(start_time, LocalDateTime.now());
    }

    @Override
    public int hashCode() {
        return (this.getRecordingName()==null)?super.hashCode():this.getRecordingName().hashCode();
    }


}
