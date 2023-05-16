package com.thecolonel63.serversidereplayrecorder.mixin.region;

import com.thecolonel63.serversidereplayrecorder.recorder.RegionRecorder;
import com.thecolonel63.serversidereplayrecorder.util.interfaces.RegionRecorderWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.VibrationS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Vibration;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"))
    void handleBreaking(int entityId, BlockPos pos, int progress, CallbackInfo ci){
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(pos.getX(),pos.getY(),pos.getZ()))).forEach(
                r -> r.onPacket(new BlockBreakingProgressS2CPacket(entityId, pos, progress))
        );
    }
    @Inject(method = "createExplosion", at = @At(value = "HEAD"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void handleExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, Explosion.DestructionType destructionType, CallbackInfoReturnable<Explosion> cir, Explosion explosion) {
        getRegionRecorders().stream().filter(r -> r.region.isInBox(new Vec3d(x,y,z))).forEach(
                r -> r.onPacket(new ExplosionS2CPacket(x, y, z, power, explosion.getAffectedBlocks(), Vec3d.ZERO))
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
