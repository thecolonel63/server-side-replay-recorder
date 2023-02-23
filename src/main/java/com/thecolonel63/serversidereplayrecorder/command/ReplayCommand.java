package com.thecolonel63.serversidereplayrecorder.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.FileHandlingUtility;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColumnPos;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ReplayCommand {
    private static RequiredArgumentBuilder<ServerCommandSource, String> handleFile(String subFolder) {
        return CommandManager.argument("name", StringArgumentType.word())
                .suggests(
                        (context, builder) -> {
                            File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder).toFile();
                            Set<String> suggestions = new LinkedHashSet<>();
                            if (folder.exists() && folder.isDirectory()) {
                                for (File file : Objects.requireNonNull(folder.listFiles(File::isDirectory))) {
                                    if (!file.getName().matches(".*\\s.*"))
                                        suggestions.add(file.getName());
                                }
                            }
                            return CommandSource.suggestMatching(
                                    suggestions,
                                    builder
                            );
                        }
                ).then(
                        CommandManager.literal("delete")
                                .then(
                                        CommandManager.argument("filename", StringArgumentType.greedyString())
                                        .suggests(
                                                (context, builder) -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name).toFile();
                                                    Set<String> suggestions = new LinkedHashSet<>();
                                                    if (folder.exists() && folder.isDirectory()) {
                                                        for (File file : Objects.requireNonNull(folder.listFiles(File::isFile))) {
                                                            if (FilenameUtils.getExtension(file.getName()).equals("mcpr"))
                                                                suggestions.add(file.getName());
                                                        }
                                                    }
                                                    return CommandSource.suggestMatching(
                                                            suggestions,
                                                            builder
                                                    );
                                                }
                                        )
                                        .executes(
                                                context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    String filename = StringArgumentType.getString(context, "filename");
                                                    File file = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name, filename).toFile();
                                                    ServerCommandSource source = context.getSource();
                                                    if (file.exists() && file.isFile()) {
                                                        try {
                                                            Files.delete(file.toPath());
                                                            source.sendFeedback(new LiteralText("File %s deleted".formatted(file.toString())).formatted(Formatting.YELLOW), true);
                                                            if (Objects.requireNonNull(file.getParentFile().list()).length == 0){
                                                                Files.delete(file.getParentFile().toPath());
                                                                source.sendFeedback(new LiteralText("Folder %s deleted".formatted(file.getParentFile().toString())).formatted(Formatting.YELLOW), true);
                                                            }
                                                        } catch (Throwable t) {
                                                            source.sendError(new LiteralText("An Error occurred while deleting File %s".formatted(file.toString())).formatted(Formatting.RED));
                                                            return 1;
                                                        }
                                                        return 0;
                                                    } else {
                                                        source.sendError(new LiteralText("File %s does not Exist".formatted(file.toString())).formatted(Formatting.RED));
                                                        return 1;
                                                    }
                                                }
                                        )
                        )
                ).then(
                        CommandManager.literal("upload")
                                .then(
                                        CommandManager.argument("filename", StringArgumentType.greedyString())
                                                .suggests(
                                                        (context, builder) -> {
                                                            String name = StringArgumentType.getString(context, "name");
                                                            File folder = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name).toFile();
                                                            Set<String> suggestions = new LinkedHashSet<>();
                                                            if (folder.exists() && folder.isDirectory()) {
                                                                for (File file : Objects.requireNonNull(folder.listFiles(File::isFile))) {
                                                                    if (FilenameUtils.getExtension(file.getName()).equals("mcpr"))
                                                                        suggestions.add(file.getName());
                                                                }
                                                            }
                                                            return CommandSource.suggestMatching(
                                                                    suggestions,
                                                                    builder
                                                            );
                                                        }
                                                )
                                                .executes(
                                                        context -> {
                                                            String name = StringArgumentType.getString(context, "name");
                                                            String filename = StringArgumentType.getString(context, "filename");
                                                            File file = Paths.get(ServerSideReplayRecorderServer.config.getReplay_folder_name(), subFolder, name, filename).toFile();
                                                            ServerCommandSource source = context.getSource();
                                                            if (file.exists() && file.isFile()) {
                                                                try {
                                                                    String result = FileHandlingUtility.uploadToTemp(file);
                                                                    source.sendFeedback(new LiteralText(result).formatted(Formatting.YELLOW), true);
                                                                } catch (Throwable t) {
                                                                    source.sendError(new LiteralText("An Error occurred while uploading File %s".formatted(file.toString())).formatted(Formatting.RED));
                                                                    return 1;
                                                                }
                                                                return 0;
                                                            } else {
                                                                source.sendError(new LiteralText("File %s does not Exist".formatted(file.toString())).formatted(Formatting.RED));
                                                                return 1;
                                                            }
                                                        }
                                                )
                                )
                );
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("replay")
                .requires(s -> s.hasPermissionLevel(ServerSideReplayRecorderServer.config.command_op_level))
                .then(
                        CommandManager.literal("players")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                                                .suggests(
                                                        (context, builder) -> {
                                                            PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                                            return CommandSource.suggestMatching(
                                                                    playerManager.getPlayerList()
                                                                            .stream()
                                                                            .map(player -> player.getGameProfile().getName())
                                                                            .filter(Predicate.not(ServerSideReplayRecorderServer.config.getRecordable_users()::contains)),
                                                                    builder
                                                            );
                                                        }
                                                )
                                                .executes(context -> {
                                                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "targets");
                                                    List<String> to_add = profiles.stream().map(GameProfile::getName).toList();
                                                    ServerCommandSource source = context.getSource();
                                                    AtomicInteger count = new AtomicInteger();
                                                    to_add.forEach(n -> {
                                                        if (ServerSideReplayRecorderServer.config.getRecordable_users().add(n)) {
                                                            source.sendFeedback(new LiteralText("%s added from replay list".formatted(n)).formatted(Formatting.YELLOW), true);
                                                            count.getAndIncrement();
                                                        }
                                                    });
                                                    if (count.get() > 0) {
                                                        source.sendFeedback(new LiteralText("Added Players will start recording next time they join the server").formatted(Formatting.YELLOW), true);
                                                        ServerSideReplayRecorderServer.saveConfig();
                                                    }
                                                    return 0;
                                                })
                                        )).then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                                                .suggests(
                                                        (context, builder) -> {
                                                            PlayerManager playerManager = context.getSource().getServer().getPlayerManager();
                                                            return CommandSource.suggestMatching(
                                                                    playerManager.getPlayerList()
                                                                            .stream()
                                                                            .map(player -> player.getGameProfile().getName())
                                                                            .filter(ServerSideReplayRecorderServer.config.getRecordable_users()::contains),
                                                                    builder
                                                            );
                                                        }
                                                )
                                                .executes(context -> {
                                                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "targets");
                                                    List<String> to_remove = profiles.stream().map(GameProfile::getName).toList();
                                                    ServerCommandSource source = context.getSource();
                                                    AtomicInteger count = new AtomicInteger();
                                                    to_remove.forEach(n -> {
                                                        if (ServerSideReplayRecorderServer.config.getRecordable_users().remove(n)) {
                                                            source.sendFeedback(new LiteralText("%s removed to replay list".formatted(n)).formatted(Formatting.YELLOW), true);
                                                            count.getAndIncrement();
                                                        }
                                                    });
                                                    if (count.get() > 0) {
                                                        source.sendFeedback(new LiteralText("Removed players will stop recording on logout").formatted(Formatting.YELLOW), true);
                                                        ServerSideReplayRecorderServer.saveConfig();
                                                    }
                                                    return 0;
                                                })
                                        )).then(CommandManager.literal("list")
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            source.sendFeedback(new LiteralText("Replay allowed users: %s"
                                                            .formatted(String.join(", ", ServerSideReplayRecorderServer.config.getRecordable_users()))).formatted(Formatting.YELLOW),
                                                    false);
                                            return 0;
                                        })
                                )
                ).then(
                        CommandManager.literal("status")
                                .executes(context -> {
                                    context.getSource().sendFeedback(new LiteralText("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);

                                    Collection<RegionRecorder> r_recorders = RegionRecorder.recorders.values();

                                    if(!r_recorders.isEmpty()) {
                                        context.getSource().sendFeedback(new LiteralText("Region Recordings:").formatted(Formatting.YELLOW), true);
                                        r_recorders.forEach( r -> {
                                            context.getSource().sendFeedback(new LiteralText("    %s: %s %s".formatted(r.regionName, r.world.getRegistryKey().getValue(), r.getUptime())).formatted(Formatting.YELLOW), true);
                                        });
                                    }

                                    Collection<PlayerRecorder> p_recorders = ServerSideReplayRecorderServer.connectionPlayerThreadRecorderMap.values();

                                    if(!p_recorders.isEmpty()) {
                                        context.getSource().sendFeedback(new LiteralText("Player Recordings:").formatted(Formatting.YELLOW), true);
                                        p_recorders.forEach( r -> {
                                            context.getSource().sendFeedback(new LiteralText("    %s: %s".formatted(r.playerName, r.getUptime())).formatted(Formatting.YELLOW), true);
                                        });
                                    }
                                    return 0;
                                })
                                .then(CommandManager.literal("enable").executes(context -> {
                                    ServerSideReplayRecorderServer.config.setRecording_enabled(true);
                                    context.getSource().sendFeedback(new LiteralText("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);
                                    ServerSideReplayRecorderServer.saveConfig();
                                    return 0;
                                }))
                                .then(CommandManager.literal("disable").executes(context -> {
                                    ServerSideReplayRecorderServer.config.setRecording_enabled(false);
                                    context.getSource().sendFeedback(new LiteralText("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")).formatted(Formatting.YELLOW), true);
                                    ServerSideReplayRecorderServer.saveConfig();
                                    return 0;
                                }))
                ).then(
                        CommandManager.literal("region")
                                .then(
                                        CommandManager.argument("regionName", StringArgumentType.word())
                                                .suggests(
                                                        (context, builder) -> CommandSource.suggestMatching(
                                                                RegionRecorder.recorders.keySet(),
                                                                builder
                                                        )
                                                )
                                                .executes(
                                                        context -> {
                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                            RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                            if (recorder != null) {
                                                                ServerCommandSource source = context.getSource();
                                                                source.sendFeedback(new LiteralText("Region %s:".formatted(regionName)).formatted(Formatting.YELLOW), true);
                                                                source.sendFeedback(new LiteralText("Dimension: %s".formatted(recorder.world.getRegistryKey().getValue()).formatted(Formatting.YELLOW)), true);
                                                                source.sendFeedback(new LiteralText("Area: %d %d to %d %d".formatted(recorder.region.min.x, recorder.region.min.z, recorder.region.max.x, recorder.region.max.z).formatted(Formatting.YELLOW)), true);
                                                                source.sendFeedback(new LiteralText("Uptime: %s".formatted(DurationFormatUtils.formatDurationHMS(recorder.getUptime().toMillis())).formatted(Formatting.YELLOW)), true);
                                                                return 0;
                                                            } else {
                                                                context.getSource().sendError(new LiteralText("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                return 1;
                                                            }
                                                        }
                                                ).then(
                                                        CommandManager.literal("start")
                                                                .then(
                                                                        CommandManager.argument("from", ColumnPosArgumentType.columnPos())
                                                                                .then(
                                                                                        CommandManager.argument("to", ColumnPosArgumentType.columnPos())
                                                                                                .executes(
                                                                                                        context -> {
                                                                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                                                                            ColumnPos pos1 = ColumnPosArgumentType.getColumnPos(context, "from");
                                                                                                            ColumnPos pos2 = ColumnPosArgumentType.getColumnPos(context, "to");
                                                                                                            ChunkPos cpos1 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos1.x), ChunkSectionPos.getSectionCoord(pos1.z));
                                                                                                            ChunkPos cpos2 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos2.x), ChunkSectionPos.getSectionCoord(pos2.z));
                                                                                                            if (ServerSideReplayRecorderServer.config.isRecording_enabled()) {
                                                                                                                RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                                                                                if (recorder == null) {
                                                                                                                    ServerCommandSource source = context.getSource();
                                                                                                                    try {
                                                                                                                        recorder = RegionRecorder.create(regionName, cpos1, cpos2, source.getWorld());
                                                                                                                        source.sendFeedback(new LiteralText("Started Recording Region %s, from %d %d to %d %d".formatted(regionName, recorder.region.min.x, recorder.region.min.z, recorder.region.max.x, recorder.region.max.z)).formatted(Formatting.YELLOW), true);
                                                                                                                        return 0;
                                                                                                                    } catch (
                                                                                                                            Throwable e) {
                                                                                                                        e.printStackTrace();
                                                                                                                        context.getSource().sendError(new LiteralText("An Exception occurred while starting %s recording".formatted(regionName)).formatted(Formatting.RED));
                                                                                                                        return 2;
                                                                                                                    }
                                                                                                                } else {
                                                                                                                    context.getSource().sendError(new LiteralText("Region %s already started".formatted(regionName)).formatted(Formatting.RED));
                                                                                                                    return 1;
                                                                                                                }
                                                                                                            } else {
                                                                                                                context.getSource().sendError(new LiteralText("Recording is disabled").formatted(Formatting.RED));
                                                                                                                return 3;
                                                                                                            }
                                                                                                        }
                                                                                                )
                                                                                )
                                                                )
                                                ).then(
                                                        CommandManager.literal("stop")
                                                                .executes(
                                                                        context -> {
                                                                            String regionName = StringArgumentType.getString(context, "regionName");
                                                                            RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                                            if (recorder != null) {
                                                                                ServerCommandSource source = context.getSource();
                                                                                recorder.handleDisconnect();
                                                                                source.sendFeedback(new LiteralText("Region %s stopped and saved (%s)".formatted(regionName, recorder.getFileName())).formatted(Formatting.YELLOW), true);
                                                                                return 0;
                                                                            } else {
                                                                                context.getSource().sendError(new LiteralText("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                                return 1;
                                                                            }
                                                                        }
                                                                )
                                                ).then(
                                                        CommandManager.literal("status")
                                                                .executes(
                                                                context -> {
                                                                    String regionName = StringArgumentType.getString(context, "regionName");
                                                                    RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                                    if (recorder != null) {
                                                                        ServerCommandSource source = context.getSource();
                                                                        source.sendFeedback(new LiteralText("Region %s:".formatted(regionName)).formatted(Formatting.YELLOW), true);
                                                                        source.sendFeedback(new LiteralText("Dimension: %s".formatted(recorder.world.getRegistryKey().getValue()).formatted(Formatting.YELLOW)), true);
                                                                        source.sendFeedback(new LiteralText("Area: %d %d to %d %d".formatted(recorder.region.min.x, recorder.region.min.z, recorder.region.max.x, recorder.region.max.z).formatted(Formatting.YELLOW)), true);
                                                                        source.sendFeedback(new LiteralText("Uptime: %s".formatted(DurationFormatUtils.formatDurationHMS(recorder.getUptime().toMillis())).formatted(Formatting.YELLOW)), true);
                                                                        return 0;
                                                                    } else {
                                                                        context.getSource().sendError(new LiteralText("Unknown Region %s".formatted(regionName)).formatted(Formatting.RED));
                                                                        return 1;
                                                                    }
                                                                }
                                                        )
                                                )
                                )
                ).then(
                        CommandManager.literal("file")
                                .then(
                                        CommandManager.literal("player")
                                                .then(
                                                        handleFile(PlayerRecorder.PLAYER_FOLDER)
                                                )
                                ).then(
                                        CommandManager.literal("region")
                                                .then(
                                                        handleFile(RegionRecorder.REGION_FOLDER)
                                                )
                                )
                )
        );
    }
}
