package com.thecolonel63.serversidereplayrecorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.thecolonel63.serversidereplayrecorder.config.MainConfig;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.StoppedReplayFixer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.SERVER)
public class ServerSideReplayRecorderServer {

    static {
        YAMLFactoryBuilder builder = YAMLFactory.builder();
        builder.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        builder.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        yaml = new ObjectMapper(builder.build());
    }

    private static final ObjectMapper yaml;

    public static final Logger LOGGER = LoggerFactory.getLogger(ServerSideReplayRecorderServer.class.getName());


    //TODO: swap the maps with Apache Caches with either Soft or Weak keys and values to prevent memory leaks

    public static final Map<ClientConnection, PlayerRecorder> connectionPlayerThreadRecorderMap = new ConcurrentHashMap<>();

    public static MinecraftServer server;

    public static final String configPath = FabricLoader.getInstance().getConfigDir() + "/ServerSideReplayRecorder.yml";
    public static MainConfig config = new MainConfig();

    private static void loadConfig() {
        try {

            yaml.findAndRegisterModules();
            config = yaml.readValue(new FileReader(configPath), MainConfig.class);

        }catch (FileNotFoundException e){
            System.out.println("Config file not found, creating with default values...");
            saveConfig();
        }catch (Throwable t){
            throw new RuntimeException(t);
        }
    }

    public static void saveConfig() {
        try {
            yaml.findAndRegisterModules();
            //noinspection ResultOfMethodCallIgnored
            new File(FabricLoader.getInstance().getConfigDir().toString()).mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(configPath));
            writer.write("#Config for Server Side Replay Recorder");
            writer.write("\n#replay_folder_name - Folder replays are all saved to.");
            writer.write("\n#use_username_for_recordings - If false, UUIDs will be used to group replays instead.");
            writer.write("\n#server_name - The name that appears as the server name in the replay viewer.");
            writer.write("\n#recordable_users - list of usernames that are gonna be recorded.");
            writer.write("\n\n");
            yaml.writeValue(writer, config);
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static void fixStoppedReplays() {

        System.out.println("Scanning for and fixing incomplete replays...");

        if (new File(FabricLoader.getInstance().getConfigDir() + "/" + config.getReplay_folder_name()).listFiles() == null) return;
        ArrayList<File> replaysToFixWithMetadata = new ArrayList<>();
        ArrayList<File> replaysToFix = new ArrayList<>();
        for (File file : Objects.requireNonNull(new File(FabricLoader.getInstance().getConfigDir() + "/"+ config.getReplay_folder_name()).listFiles())) {
            if (file.isDirectory()) {
                if (file.listFiles() == null) continue;
                for (File file1 : Objects.requireNonNull(file.listFiles())) {
                    if (file1.getName().equals("recording.tmcpr") && !replaysToFixWithMetadata.contains(file))
                        replaysToFix.add(file);
                    if (file1.getName().equals("metaData.json")) {
                        replaysToFix.remove(file);
                        replaysToFixWithMetadata.add(file);
                    }
                }
            }
        }

        replaysToFixWithMetadata.forEach(file -> {
            try {
                StoppedReplayFixer.fixReplay(file, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        replaysToFix.forEach(file -> {
            try {
                StoppedReplayFixer.fixReplay(file, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    public static void init(MinecraftServer mcServer) {
        server = mcServer;
        loadConfig();
        fixStoppedReplays();
    }

    public static void tick() {
        synchronized (ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap) {
            connectionPlayerThreadRecorderMap.forEach((connection, playerThreadRecorder) -> {
                //Initiate the saving process of what isn't automatically saved.
                playerThreadRecorder.onPlayerTick();
            });
            RegionRecorder.recorders.forEach((s, recorder) -> {
                recorder.onPlayerTick();
            });
        }
    }

    private enum value_type {
        BOOLEAN,
        STRING
    }

}
