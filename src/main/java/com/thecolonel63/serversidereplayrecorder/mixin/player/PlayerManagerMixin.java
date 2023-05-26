package com.thecolonel63.serversidereplayrecorder.mixin.player;

import com.mojang.authlib.GameProfile;
import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import com.thecolonel63.serversidereplayrecorder.recorder.PlayerRecorder;
import com.thecolonel63.serversidereplayrecorder.recorder.ReplayRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RecorderHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Shadow public abstract boolean isOperator(GameProfile profile);

    @Shadow public abstract List<ServerPlayerEntity> getPlayerList();

    @Inject(method = "onPlayerConnect", at= @At("HEAD"))
    private void onConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci){
        if (!PlayerRecorder.playerRecorderMap.containsKey(connection)
                && ServerSideReplayRecorderServer.config.getRecordable_users().contains(player.getGameProfile().getName()) && ServerSideReplayRecorderServer.config.isRecording_enabled()) {
            try {
                ServerSideReplayRecorderServer.LOGGER.info("Started Recording Player %s".formatted(player.getGameProfile().getName()));

                this.getPlayerList().stream().filter(p -> this.isOperator(p.getGameProfile())).forEach( p -> p.sendMessage(Text.literal("Started Recording Player %s".formatted(player.getGameProfile().getName())).formatted(Formatting.GOLD), false));
                PlayerRecorder recorder = new PlayerRecorder(connection);
                PlayerRecorder.playerRecorderMap.put(connection, recorder);
                recorder.onPacket(new LoginSuccessS2CPacket(player.getGameProfile()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "sendToAround", at=@At("HEAD"))
    private void sendToAround(@Nullable PlayerEntity player, double x, double y, double z, double distance, RegistryKey<World> worldKey, Packet<?> packet, CallbackInfo ci){
        if (player != null) {
            ReplayRecorder recorder = ((RecorderHolder)(((ServerPlayerEntity) player).networkHandler)).getRecorder();
            if (recorder != null) {
                recorder.onPacket(packet);
            }
        }
    }
}
