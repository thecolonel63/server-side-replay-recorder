package com.thecolonel63.serversidereplayrecorder.server;

import com.thecolonel63.serversidereplayrecorder.util.PlayerThreadRecorder;
import com.thecolonel63.serversidereplayrecorder.util.StoppedReplayFixer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.SERVER)
public class ServerSideReplayRecorderServer {
    public static final String rootDirectory = Paths.get("").toAbsolutePath().toString();
    public static final Character[] INVALID_CHARACTERS = {'"', '*', ':', '<', '>', '?', '\\', '|', 0x7F, '\000'};
    public static final Map<ClientConnection, PlayerThreadRecorder> connectionPlayerThreadRecorderMap = new ConcurrentHashMap<>();
    public static MinecraftServer server;
    public static String replayFolderName;
    public static boolean useUsernameForRecordings;
    public static String serverName;
    static Map<String, String> configOptions = new LinkedHashMap<>();
    static Map<String, String> defaultConfigOptions = new LinkedHashMap<>() {{
        put("replay_folder_name", "replay_recordings");
        put("use_username_for_recordings", "true");
        put("server_name", "My Server");
    }};

    private static void loadConfig() {
        configOptions.putAll(defaultConfigOptions);
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(rootDirectory + "/config/ServerSideReplayRecorder.properties"));
            String line = reader.readLine();
            while (line != null) {
                parseConfigLine(line);
                line = reader.readLine();
            }
            reader.close();
            configOptions.forEach((property, value) -> {
                switch (property) {
                    case "replay_folder_name" -> replayFolderName = value;
                    case "use_username_for_recordings" -> useUsernameForRecordings = value.equalsIgnoreCase("true");
                    case "server_name" -> serverName = value;
                }
            });
        } catch (FileNotFoundException e) {
            System.out.println("Config file not found, creating with default values...");
            saveDefaultConfig();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static void parseConfigLine(String line) {
        if (line.startsWith("#") || line.length() == 0) return;
        if (line.split("=").length != 2) {
            System.out.println("Warning: Property " + line + " is invalid and will be ignored!");
            return;
        } else if (!defaultConfigOptions.containsKey(line.split("=")[0])) {
            System.out.println("Warning: Property " + line + " is invalid and will be ignored!");
            return;
        } else {
            String property = line.split("=")[0];
            String value = line.split("=")[1];
            switch (property) {
                case "replay_folder_name" -> {
                    if (isValueInvalid(value, value_type.STRING)) {
                        System.out.println("Warning: Replay Folder Name contains invalid characters! Please avoid use of special characters.");
                        return;
                    } else {
                        configOptions.put("replay_folder_name", value);
                    }
                }
                case "use_username_for_recordings" -> {
                    if (isValueInvalid(value, value_type.BOOLEAN)) {
                        System.out.println("Warning: use_username_for_recordings must either be true or false!");
                        return;
                    } else {
                        configOptions.put("use_username_for_recordings", value);
                    }
                }
                case "server_name" -> {
                    if (isValueInvalid(value, value_type.STRING)) {
                        System.out.println("Warning: Server Name contains invalid characters! Please avoid use of special characters.");
                        return;
                    } else {
                        configOptions.put("server_name", value);
                    }
                }
            }
        }
        configOptions.put(line.split("=")[0], line.split("=")[1]);
    }

    private static boolean isValueInvalid(String value, value_type type) {
        if (value == null || value.isEmpty() || value.length() > 255) return true;
        switch (type) {
            case BOOLEAN -> {
                return (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"));
            }
            case STRING -> {
                return Arrays.stream(INVALID_CHARACTERS).anyMatch(character -> value.contains(character.toString()));
            }
        }
        return true;
    }

    private static void saveDefaultConfig() {
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(rootDirectory + "/config").mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(rootDirectory + "/config/ServerSideReplayRecorder.properties"));
            writer.write("#Config for Server Side Replay Recorder");
            writer.write("\n#replay_folder_name - Folder replays are all saved to.");
            writer.write("\n#use_username_for_recordings - If false, UUIDs will be used to group replays instead.");
            writer.write("\n#server_name - The name that appears as the server name in the replay viewer.");
            writer.write("\n\n");
            configOptions.forEach((property, value) -> {
                try {
                    writer.write(property + "=" + value + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            writer.close();
            loadConfig();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static void fixStoppedReplays() {

        System.out.println("Scanning for and fixing incomplete replays...");

        if (new File(rootDirectory + "/" + replayFolderName).listFiles() == null) return;
        ArrayList<File> replaysToFixWithMetadata = new ArrayList<>();
        ArrayList<File> replaysToFix = new ArrayList<>();
        for (File file : Objects.requireNonNull(new File(rootDirectory + "/" + replayFolderName).listFiles())) {
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
        }
    }

    private enum value_type {
        BOOLEAN,
        STRING
    }

}
