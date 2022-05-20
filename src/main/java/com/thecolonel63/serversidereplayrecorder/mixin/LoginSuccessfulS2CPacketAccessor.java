package com.thecolonel63.serversidereplayrecorder.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LoginSuccessS2CPacket.class)
public interface LoginSuccessfulS2CPacketAccessor {
    @Accessor("profile")
    GameProfile getProfile();
}
