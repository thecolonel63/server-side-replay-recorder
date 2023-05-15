package com.thecolonel63.serversidereplayrecorder.mixin.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.thecolonel63.serversidereplayrecorder.command.ReplayCommand;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommandManager.class, priority = 10)
public abstract class CommandManagerMixin {
    @Shadow
    @Final
    private CommandDispatcher<ServerCommandSource> dispatcher;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;findAmbiguities(Lcom/mojang/brigadier/AmbiguityConsumer;)V", shift = At.Shift.BEFORE))
    private void fabric_addCommands(CommandManager.RegistrationEnvironment environment, CallbackInfo ci) {
        if (environment.dedicated) {
            ReplayCommand cmd = new ReplayCommand();
            cmd.register(dispatcher);
        }
    }
}