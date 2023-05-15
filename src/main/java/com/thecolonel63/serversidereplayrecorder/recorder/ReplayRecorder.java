package com.thecolonel63.serversidereplayrecorder.recorder;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.util.FileHandlingUtility;
import com.thecolonel63.serversidereplayrecorder.util.WrappedPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.MinecraftVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ReplayRecorder {

    public static final Set<ReplayRecorder> active_recorders = new HashSet<>();
    public static final Set<ReplayRecorder> writing_recorders = new HashSet<>();
    public final MinecraftServer ms = ServerSideReplayRecorderServer.server;
    protected final File tmp_folder;
    protected final File recording_file;
    protected File out_file;
    protected final BufferedOutputStream bos;
    protected final FileOutputStream fos;
    protected final FileWriter debugFile;
    protected final AtomicLong start = new AtomicLong();
    protected final AtomicInteger server_start = new AtomicInteger();
    protected final String fileName;
    protected NetworkState state = NetworkState.LOGIN;
    protected final AtomicInteger server_timestamp = new AtomicInteger();

    protected final AtomicInteger last_timestamp = new AtomicInteger();
    protected final AtomicBoolean startedRecording = new AtomicBoolean(false);
    protected final AtomicBoolean open = new AtomicBoolean(true);

    protected final AtomicReference<ReplayRecorder.ReplayStatus> status = new AtomicReference<>(ReplayRecorder.ReplayStatus.Not_Started);

    public ReplayRecorder.ReplayStatus getStatus() {
        return this.status.get();
    }

    public boolean isOpen() {
        return open.get();
    }

    private static final ThreadFactory fileWriterFactory = new ThreadFactoryBuilder().setNameFormat("Replay-Writer-%d").setDaemon(true).build();
    protected final ThreadPoolExecutor fileWriterExecutor = new ThreadPoolExecutor(1, 1,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), fileWriterFactory, new ThreadPoolExecutor.DiscardPolicy());

    public String getFileName() {
        return fileName;
    }

    public abstract String getRecordingName();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected ReplayRecorder() throws IOException {
        if (new File(ServerSideReplayRecorderServer.config.getReplay_folder_name()).isAbsolute())
            tmp_folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), "recording_" + this.hashCode()).toFile();
        else
            tmp_folder = Paths.get(FabricLoader.getInstance().getGameDir().toString(), ServerSideReplayRecorderServer.config.getReplay_folder_name(), "recording_" + this.hashCode()).toFile();
        tmp_folder.mkdirs();
        recording_file= Paths.get(tmp_folder.getAbsolutePath(), "recording.tmcpr").toFile();
        fileName = String.format("%s.mcpr", new SimpleDateFormat("yyyy-M-dd_HH-mm-ss").format(new Date()));

        long remaining_space = tmp_folder.getUsableSpace();
        long storage_required = (( active_recorders.size() + 1 ) * 2L) * ServerSideReplayRecorderServer.config.getMax_file_size();
        long storage_used = active_recorders.stream().mapToLong(ReplayRecorder::getCurrent_file_size).sum();

        //prevent filling storage ( causing server crash )
        if(remaining_space<(storage_required-storage_used)){
            throw new IOException("Not enough space left on device");
        }

        fos = new FileOutputStream(this.recording_file, false);
        bos = new BufferedOutputStream(fos);
        if (ServerSideReplayRecorderServer.config.isDebug()) {
            debugFile = new FileWriter(Paths.get(tmp_folder.getAbsolutePath(), "debug.json").toFile());
            debugFile.write("[{}\n");
        }else
            debugFile = null;
        ReplayRecorder.writing_recorders.add(this);
        status.set(ReplayStatus.Recording);
        ServerSideReplayRecorderServer.LOGGER.info("Started recording %s:%s".formatted(this.getClass().getSimpleName(), this.getRecordingName()));
    }

    AtomicBoolean compressing = new AtomicBoolean(false);
    private void writeMetaData(boolean isFinishing) {
        if (compressing.compareAndSet(false, isFinishing)) {
            try {
                String serverName = ServerSideReplayRecorderServer.config.getServer_name();
                JsonObject object = new JsonObject();
                object.addProperty("singleplayer", false);
                object.addProperty("serverName", serverName);
                object.addProperty("customServerName", serverName + " | " + this.getRecordingName());
                object.addProperty("duration", last_timestamp);
                object.addProperty("date", start);
                object.addProperty("mcversion", MinecraftVersion.CURRENT.getName());
                object.addProperty("fileFormat", "MCPR");
                object.addProperty("fileFormatVersion", 14); //Unlikely to change any time soon, last time this was updates was several major versions ago.
                object.addProperty("protocol", SharedConstants.getProtocolVersion());
                object.addProperty("generator", "mattymatty's enhanced thecolonel63's Server Side Replay Recorder");
                object.addProperty("selfId", -1);
                object.add("players", new JsonArray());
                FileWriter fw = new FileWriter(Paths.get(tmp_folder.getAbsolutePath(), "metaData.json").toFile(), false);
                fw.write(object.toString());
                fw.close();
                this.writeMarkers();
                if (isFinishing)
                    compressReplay();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            } finally {
                this.metadataQueued.set(false);
            }
        }
    }

    private final JsonArray markers = new JsonArray();
    private void writeMarkers() {
        try {
            if (markers.size()>0) {
                FileWriter fw = new FileWriter(Paths.get(tmp_folder.getAbsolutePath(), "markers.json").toFile(), false);
                fw.write(markers.toString());
                fw.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void addMarker(String name) {
        this.addMarker(0,0,0,0,0,0, name);
    }

    public void addMarker(double x, double y, double z, float yaw, float pitch, float roll, String name){
        JsonObject entry = new JsonObject();
        JsonObject value = new JsonObject();
        JsonObject position = new JsonObject();

        entry.add("realTimestamp", new JsonPrimitive(this.last_timestamp.get()));
        if(name!=null)
            value.add("name", new JsonPrimitive(name));
        position.add("x", new JsonPrimitive(x));
        position.add("y", new JsonPrimitive(y));
        position.add("z", new JsonPrimitive(z));
        position.add("yaw", new JsonPrimitive(yaw));
        position.add("pitch", new JsonPrimitive(pitch));
        position.add("roll", new JsonPrimitive(roll));
        value.add("position", position);
        entry.add("value", value);
        markers.add(entry);
    }
    
    protected abstract String getSaveFolder();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void compressReplay() {

        File[] filesToCompress = tmp_folder.listFiles(File::isFile);

        this.out_file.getParentFile().mkdirs();

        try {
            assert filesToCompress != null;
            FileHandlingUtility.zip(Arrays.asList(filesToCompress), this.out_file.getAbsolutePath(), true, tmp_folder);
            for(ServerPlayerEntity serverPlayerEntity : ms.getPlayerManager().getPlayerList()) {
                if (ms.getPlayerManager().isOperator(serverPlayerEntity.getGameProfile())) {
                    serverPlayerEntity.sendMessage(Text.literal("Replay %s Saved".formatted(this.out_file)).formatted(Formatting.YELLOW));
                }
            }
            ServerSideReplayRecorderServer.LOGGER.info("Replay %s Saved".formatted(this.out_file));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void onPacket(Packet<?> packet) {
        if (!this.open.get())
            return;

        if (!startedRecording.getAndSet(true)) {
            active_recorders.add(this);
            start.set(System.currentTimeMillis()); //More accurate timestamps.
            server_start.set(ServerSideReplayRecorderServer.server.getTicks());
            out_file = Paths.get(this.getSaveFolder(),fileName).toFile();
        }

        if (packet instanceof LoginCompressionS2CPacket) {
            return; //We don't compress anything in replays, so ignore the packet.
        }

        if(packet instanceof LightUpdateS2CPacket)
            return; //skip LightUpdates to greatly reduce file size ( client ignores them anyway )

        if(packet instanceof BundleS2CPacket bundleS2CPacket){
            for (Packet<?> bundle_packet : bundleS2CPacket.getPackets()){
                this.onPacket(bundle_packet);
            }
            return;
        }

        save(packet);
    }

    public void handleDisconnect(){
        this.handleDisconnect(false);
    }

    public void handleDisconnect(boolean immediate) {
        if (Thread.currentThread() == ms.getThread()){
            this.onServerTick();
            if (this.open.compareAndSet(true,false)) {
                this.status.set(ReplayStatus.Saving);
                ServerSideReplayRecorderServer.LOGGER.info("Stopping recording %s:%s".formatted(this.getClass().getSimpleName(), this.getRecordingName()));
                active_recorders.remove(this);
                Runnable endTask = () -> {
                    try {
                        try {
                            bos.close();
                            fos.close();
                            if (debugFile != null) {
                                debugFile.write("]");
                                debugFile.close();
                            }
                        }catch (IOException ignored) {}

                        writeMetaData(true);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        writing_recorders.remove(this);
                        this.status.set(ReplayStatus.Saved);
                    }
                };
                if (immediate) {
                    //kill all tasks and close
                    this.fileWriterExecutor.shutdownNow();
                    new Thread(endTask).start();
                } else {
                    //wait for all tasks and close
                    this.fileWriterExecutor.execute(endTask);
                    new Thread(this.fileWriterExecutor::shutdown).start();
                }
            }
        }else{
            CompletableFuture.runAsync( ()-> this.handleDisconnect(immediate),ms).join();
        }

    }

    public void onServerTick(){
        if (Thread.currentThread() == ms.getThread()){
            if (!this.open.get())
                return;
            int old_timestamp = this.server_timestamp.get();
            int new_timestamp = (ms.getTicks() - server_start.get()) * 50;
            this.server_timestamp.set(new_timestamp);
            if (ServerSideReplayRecorderServer.config.use_server_timestamps()){
                Queue<Packet<?>> tick_packets = this.packetQueue.getAndSet(new ConcurrentLinkedQueue<>());
                this.fileWriterExecutor.execute(()->{
                    double delta = (new_timestamp - old_timestamp)/(double)tick_packets.size();
                    double curr_timestamp = old_timestamp;
                    for (Packet<?> packet : tick_packets){
                        _save(packet,(int)Math.floor(curr_timestamp));
                        curr_timestamp+=delta;
                    }
                });
            }
        }else{
            CompletableFuture.runAsync(this::onServerTick,ms).join();
        }
    }

    protected final AtomicReference<Queue<Packet<?>>> packetQueue = new AtomicReference<>(new ConcurrentLinkedQueue<>());

    protected void save(Packet<?> packet) {
        if (ServerSideReplayRecorderServer.config.use_server_timestamps()) {
            //queue the packets and wait for the server to complete a tick before saving them
            packetQueue.get().add(packet);
        }else{
            //use a separate thread to write to file ( to not hang up the server )
            int timestamp =(int) (System.currentTimeMillis() - start.get());
            this.fileWriterExecutor.execute(() -> this._save(packet, timestamp));
        }
    }

    private final AtomicLong current_file_size = new AtomicLong();

    public long getCurrent_file_size() {
        return current_file_size.get();
    }

    private final AtomicBoolean tooBigFileSize = new AtomicBoolean(false);
    private final AtomicBoolean metadataQueued = new AtomicBoolean(false);

    private void _save(Packet<?> packet, int timestamp) {

        if(this.current_file_size.get() > ServerSideReplayRecorderServer.config.getMax_file_size()){
            if (tooBigFileSize.compareAndSet(false,true)) {
                ServerSideReplayRecorderServer.LOGGER.warn("Max File Size Reached, stopping recording %s:%s".formatted(this.getClass().getSimpleName(), this.getRecordingName()));
                this.handleDisconnect(true);
            }
            return;
        }

        //unwrap packets ( wrapped packets are intended to skip the filters )
        if (packet instanceof WrappedPacket wrappedPacket){
            packet = wrappedPacket.wrappedPacket();
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);

        try {
            synchronized (bos) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                //Write the timestamp and the amount of readable bytes, including an extra byte for the packet ID.
                buffer.write(intToByteArray(timestamp));
                buffer.write(intToByteArray(buf.readableBytes() + 1));

                if (packet instanceof LoginSuccessS2CPacket) {
                    state = NetworkState.PLAY;
                    //We are now dealing with "playing" packets, so set the network state accordingly.
                }

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
                this.current_file_size.addAndGet(buffer.size());
                if(this.metadataQueued.compareAndSet(false,true))
                    this.fileWriterExecutor.submit(() -> writeMetaData(false));
                if (debugFile!=null) {

                    debugFile.write(",{\"time\": %d, \"name\": \"%s\", \"size\": %d}\n".formatted(timestamp, FabricLoader.getInstance().getMappingResolver().unmapClassName(FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace(),packet.getClass().getSimpleName()), buffer.size()));
                }
                buffer.close();
                this.last_timestamp.set(timestamp);
            }
        } catch (IOException e) {
            if (e.getMessage().equals("No space left on device")){
                ServerSideReplayRecorderServer.LOGGER.warn("Disk space is too low, stopping recording %s:%s".formatted(this.getClass().getSimpleName(), this.getRecordingName()));
                this.handleDisconnect(true);
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

    public Duration getUptime(){
        return Duration.ofMillis(this.last_timestamp.get());
    }
    public long getFileSize(){
        return this.current_file_size.get();
    }

    public long getRemainingTasks(){
        long submitted = this.fileWriterExecutor.getTaskCount();
        long completed = this.fileWriterExecutor.getCompletedTaskCount();
        return submitted - completed;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (ServerSideReplayRecorderServer.config.isDebug()) {
            ServerSideReplayRecorderServer.LOGGER.debug("Object %s:%s-%d has been deleted!".formatted(this.getClass().getSimpleName(),this.getRecordingName(),this.start.get()));
        }
    }

    public enum ReplayStatus {
        Not_Started,
        Recording,
        Saving,
        Saved
    }

}
