package com.thecolonel63.serversidereplayrecorder.mixin.experimental.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Vibration;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements RegionRecorderWorld {

    private final Set<RegionRecorder> recorders = new LinkedHashSet<>();
    @Override
    public Set<RegionRecorder> getRegionRecorders() {
        return recorders;
    }

    private final Map<ChunkPos, Set<RegionRecorder>> recorders_by_chunk = new ConcurrentHashMap<>();

    private final Map<ChunkPos, Set<RegionRecorder>> recorders_by_expanded_chunk = new ConcurrentHashMap<>();

    @Override
    public Map<ChunkPos, Set<RegionRecorder>> getRegionRecordersByChunk() {
        return this.recorders_by_chunk;
    }

    @Override
    public Map<ChunkPos, Set<RegionRecorder>> getRegionRecordersByExpandedChunk() {
        return this.recorders_by_expanded_chunk;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToDimension(Lnet/minecraft/network/Packet;Lnet/minecraft/util/registry/RegistryKey;)V"))
    void handleWheater(PlayerManager instance, Packet<?> packet, RegistryKey<World> dimension){
        instance.sendToDimension(packet, dimension);
        getRegionRecorders().forEach( r -> r.onPacket(packet));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/Packet;)V"))
    void handleWheater(PlayerManager instance, Packet<?> packet){
        instance.sendToAll(packet);
        getRegionRecorders().forEach( r -> r.onPacket(packet));
    }

    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"))
    void handleBreaking(int entityId, BlockPos pos, int progress, CallbackInfo ci){
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(pos.getX(),pos.getY(),pos.getZ()))).forEach(
                r -> r.onPacket(new BlockBreakingProgressS2CPacket(entityId, pos, progress))
        );
    }

    @Inject(method = "playSound", at = @At("HEAD"))
    private void handleSound(PlayerEntity except, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, CallbackInfo ci) {
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch))
        );
    }

    @Inject(method = "syncWorldEvent", at = @At("HEAD"))
    private void handleLevelEvent(PlayerEntity player, int eventId, BlockPos pos, int data, CallbackInfo ci) {
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(pos.getX(),pos.getY(),pos.getZ()))).forEach(
                r -> r.onPacket(new WorldEventS2CPacket(eventId, pos, data, false))
        );
    }

    @Inject(method = "createExplosion", at = @At(value = "TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void handleExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, Explosion.DestructionType destructionType, CallbackInfoReturnable<Explosion> cir, Explosion explosion) {
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(new ExplosionS2CPacket(x, y, z, power, explosion.getAffectedBlocks(), Vec3d.ZERO))
        );
    }

    @Inject(method = "processSyncedBlockEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAround(Lnet/minecraft/entity/player/PlayerEntity;DDDDLnet/minecraft/util/registry/RegistryKey;Lnet/minecraft/network/Packet;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void handleBlockEvent(CallbackInfo ci, BlockEvent blockEvent) {
        BlockPos pos = blockEvent.getPos();
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(pos.getX(),pos.getY(),pos.getZ()))).forEach(
                r -> r.onPacket(new BlockEventS2CPacket(blockEvent.getPos(), blockEvent.getBlock(), blockEvent.getType(), blockEvent.getData()))
        );
    }

    @Inject(method = "sendVibrationPacket", at = @At(value = "HEAD"))
    private void handleVibration(Vibration vibration, CallbackInfo ci) {
        BlockPos pos = vibration.getOrigin();
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(pos.getX(),pos.getY(),pos.getZ()))).forEach(
                r -> r.onPacket(new VibrationS2CPacket(vibration))
        );
    }

    @Inject(method = "spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I", at = @At(value = "HEAD"))
    private <T extends ParticleEffect> void handleParticles(T particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed, CallbackInfoReturnable<Integer> cir) {
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(new ParticleS2CPacket(particle, false, x, y, z, (float)deltaX, (float)deltaY, (float)deltaZ, (float)speed, count))
        );
    }

}
