package com.thecolonel63.serversidereplayrecorder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.thecolonel63.serversidereplayrecorder.config.MainConfig;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Environment(EnvType.SERVER)
public class ServerSideReplayRecorderServer implements ModInitializer {

    static {
        YAMLFactoryBuilder builder = YAMLFactory.builder();
        builder.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        builder.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        yaml = new ObjectMapper(builder.build()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        placeholders = ResourceBundle.getBundle("placeholders");
        upload_sites = ResourceBundle.getBundle("upload_sites");
    }

    public static final ExecutorService recorderExecutor = Executors.newSingleThreadExecutor(new DefaultThreadFactory("Replay",true));

    private static final ObjectMapper yaml;

    public static final ResourceBundle placeholders;
    public static final ResourceBundle upload_sites;

    public static final Logger LOGGER = LoggerFactory.getLogger(ServerSideReplayRecorderServer.class.getName());

    public static MinecraftServer server;

    public static final String configPath = FabricLoader.getInstance().getConfigDir() + "/ServerSideReplayRecorder.yml";
    public static MainConfig config = new MainConfig();

    public static void loadConfig() {
        try {

//            yaml.findAndRegisterModules();
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
//            yaml.findAndRegisterModules();
            //noinspection ResultOfMethodCallIgnored
            new File(FabricLoader.getInstance().getConfigDir().toString()).mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(configPath));
            writer.write("#Config for Server Side Replay Recorder\n");
            writer.write("##WARNING any comments in this file might get deleted\n");
            writer.write("\n");
            yaml.writeValue(writer, config);
            writer.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /*
    // Code does not work anymore
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

    }*/

    public static void registerServer(MinecraftServer mcServer) {
        server = mcServer;
        //fixStoppedReplays();
    }


    @Override
    public void onInitialize() {
        LOGGER.info(ServerSideReplayRecorderServer.class.getSimpleName() + " loaded");
        loadConfig();
    }

}
