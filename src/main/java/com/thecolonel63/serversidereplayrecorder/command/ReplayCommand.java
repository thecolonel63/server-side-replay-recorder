package com.thecolonel63.serversidereplayrecorder.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thecolonel63.serversidereplayrecorder.config.MainConfig;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.ChunkBox;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColumnPos;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ReplayCommand {
    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("replay")
                .requires(s -> s.hasPermissionLevel(4))
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
                                                            source.sendFeedback(new LiteralText("%s added from replay list".formatted(n)), true);
                                                            count.getAndIncrement();
                                                        }
                                                    });
                                                    if (count.get() > 0) {
                                                        source.sendFeedback(new LiteralText("Added Players will start recording next time they join the server"), true);
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
                                                            source.sendFeedback(new LiteralText("%s removed to replay list".formatted(n)), true);
                                                            count.getAndIncrement();
                                                        }
                                                    });
                                                    if (count.get() > 0) {
                                                        source.sendFeedback(new LiteralText("Removed players will stop recording on logout"), true);
                                                        ServerSideReplayRecorderServer.saveConfig();
                                                    }
                                                    return 0;
                                                })
                                        )).then(CommandManager.literal("list")
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            source.sendFeedback(new LiteralText("Replay allowed users: %s"
                                                            .formatted(String.join(", ", ServerSideReplayRecorderServer.config.getRecordable_users()))),
                                                    false);
                                            return 0;
                                        })
                                )
                ).then(
                        CommandManager.literal("status")
                                .executes(context -> {
                                    context.getSource().sendFeedback(new LiteralText("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")), true);
                                    return 0;
                                })
                                .then(CommandManager.literal("toggle").executes(context -> {
                                    ServerSideReplayRecorderServer.config.setRecording_enabled(!ServerSideReplayRecorderServer.config.isRecording_enabled());
                                    context.getSource().sendFeedback(new LiteralText("Recording " + ((ServerSideReplayRecorderServer.config.isRecording_enabled()) ? "Enabled" : "Disabled")), true);
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
                                                            String regionName = context.getArgument("regionName", String.class);
                                                            RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                            if( recorder != null){
                                                                ServerCommandSource source = context.getSource();
                                                                source.sendFeedback(new LiteralText("Region %s:".formatted(regionName)),true);
                                                                source.sendFeedback(new LiteralText("Dimension: %s".formatted(recorder.world.getRegistryKey().getValue())),true);
                                                                source.sendFeedback(new LiteralText("Area: %d %d to %d %d".formatted(recorder.region.pos1.x,recorder.region.pos1.z,recorder.region.pos2.x,recorder.region.pos2.z)),true);
                                                                source.sendFeedback(new LiteralText("Uptime: %s".formatted(DurationFormatUtils.formatDurationHMS(recorder.getUptime().toMillis()))),true);
                                                                return 0;
                                                            }else{
                                                                context.getSource().sendError(new LiteralText("Unknown Region %s".formatted(regionName)));
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
                                                                                                            String regionName = context.getArgument("regionName", String.class);
                                                                                                            ColumnPos pos1 = ColumnPosArgumentType.getColumnPos(context, "from");
                                                                                                            ColumnPos pos2 = ColumnPosArgumentType.getColumnPos(context, "to");
                                                                                                            ChunkPos cpos1 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos1.x),ChunkSectionPos.getSectionCoord(pos1.z));
                                                                                                            ChunkPos cpos2 = new ChunkPos(ChunkSectionPos.getSectionCoord(pos2.x),ChunkSectionPos.getSectionCoord(pos2.z));

                                                                                                            RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                                                                            if( recorder == null){
                                                                                                                ServerCommandSource source = context.getSource();
                                                                                                                try {
                                                                                                                    recorder = RegionRecorder.create(regionName, cpos1, cpos2, source.getWorld());
                                                                                                                    source.sendFeedback(new LiteralText("Started Recording Region %s, from %d %d to %d %d".formatted(regionName, recorder.region.pos1.x,recorder.region.pos1.z,recorder.region.pos2.x,recorder.region.pos2.z)),true);
                                                                                                                    return 0;
                                                                                                                } catch (Throwable e) {
                                                                                                                    e.printStackTrace();
                                                                                                                    context.getSource().sendError(new LiteralText("An Exception occurred while starting %s recording".formatted(regionName)));
                                                                                                                    return 2;
                                                                                                                }
                                                                                                            }else{
                                                                                                                context.getSource().sendError(new LiteralText("Region %s already started".formatted(regionName)));
                                                                                                                return 1;
                                                                                                            }
                                                                                                        }
                                                                                                )
                                                                                )
                                                                )
                                                ).then(
                                                        CommandManager.literal("stop")
                                                                .executes(
                                                                    context -> {
                                                                        String regionName = context.getArgument("regionName", String.class);
                                                                        RegionRecorder recorder = RegionRecorder.recorders.get(regionName);
                                                                        if( recorder != null){
                                                                            ServerCommandSource source = context.getSource();
                                                                            recorder.handleDisconnect();
                                                                            source.sendFeedback(new LiteralText("Region %s stopped and saved".formatted(regionName)),true);
                                                                            return 0;
                                                                        }else{
                                                                            context.getSource().sendError(new LiteralText("Unknown Region %s".formatted(regionName)));
                                                                            return 1;
                                                                        }
                                                                    }
                                                                )
                                                )
                                )
                )
        );
    }
}
