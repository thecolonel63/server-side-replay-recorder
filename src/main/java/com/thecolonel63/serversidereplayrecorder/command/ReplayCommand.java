package com.thecolonel63.serversidereplayrecorder.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.thecolonel63.serversidereplayrecorder.server.ServerSideReplayRecorderServer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ReplayCommand {
    public void register(CommandDispatcher<ServerCommandSource> dispatcher){
        dispatcher.register(CommandManager.literal("replay")
                .requires( s -> s.hasPermissionLevel(4))
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
                                                    to_add.forEach( n -> {
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
                                                    to_remove.forEach( n -> {
                                                        if (ServerSideReplayRecorderServer.config.getRecordable_users().remove(n)) {
                                                            source.sendFeedback(new LiteralText("%s removed from replay list".formatted(n)), true);
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
                ).then(CommandManager.literal("go")
                        .then(CommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(new LiteralText("Recording: " + ServerSideReplayRecorderServer.config.go), true);
                            return 0;
                        }))
                        .then(CommandManager.literal("toggle")) .executes(context -> {
                            ServerSideReplayRecorderServer.config.go = !ServerSideReplayRecorderServer.config.go;
                            context.getSource().sendFeedback(new LiteralText("Recording: " + ServerSideReplayRecorderServer.config.go), true);
                            return 0;
                        })
                )
        );
    }
}
