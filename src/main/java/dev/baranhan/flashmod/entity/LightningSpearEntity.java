package dev.baranhan.flashmod.entity;

import dev.baranhan.flashmod.FlashEntities;
import dev.baranhan.flashmod.FlashSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Simsek mizragi: yercekimsiz, hizli. Isabette alan hasari, atese verme, sersemletme ve geri itme; patlama gorseli
 * istemcide bizim simseklerimizle (AbilityClient.spearImpact). Gucu (charge 0..100) istemcilere senkron, gorsel buna gore buyur.
 */
public class LightningSpearEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(LightningSpearEntity.class, EntityDataSerializers.FLOAT);
    private static final byte EVENT_IMPACT = 3;
    /** Istemci: renderer'in parcacik kisma sayaci. */
    public int clientLastFx = -1;

    public LightningSpearEntity(EntityType<? extends LightningSpearEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public LightningSpearEntity(Level level, LivingEntity owner, float charge) {
        super(FlashEntities.LIGHTNING_SPEAR.get(), owner, level);
        this.setNoGravity(true);
        this.entityData.set(CHARGE, charge);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CHARGE, 0F);
    }

    public float getCharge() {
        return this.entityData.get(CHARGE);
    }

    @Override
    protected float getGravity() {
        return 0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 80) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() == getOwner()) return;
        super.onHitEntity(result);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult er && er.getEntity() == getOwner()) return;
        super.onHit(result);
        if (!level().isClientSide) impact(result.getLocation());
    }

    private void impact(Vec3 at) {
        float k = Math.max(0F, Math.min(1F, getCharge() / 100F));
        double radius = 2.5D + 3.5D * k;
        float damage = 4F + 16F * k;
        Entity owner = getOwner();
        AABB box = new AABB(at, at).inflate(radius);
        for (LivingEntity le : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner)) {
            double d = le.position().add(0, le.getBbHeight() * 0.5D, 0).distanceTo(at);
            if (d > radius) continue;
            float f = (float) (1D - d / radius * 0.6D);
            le.hurt(damageSources().indirectMagic(this, owner), damage * f);
            le.setSecondsOnFire((int) (2 + 4 * k));
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (30 + 50 * k), 4));
            Vec3 push = le.position().subtract(at).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4D) push = push.normalize();
            le.push(push.x * (0.5D + k), 0.25D + 0.35D * k, push.z * (0.5D + k));
            le.hurtMarked = true;
        }
        level().playSound(null, at.x, at.y, at.z, FlashSounds.SPEAR_IMPACT.get(), SoundSource.PLAYERS, 1.2F + k, 1.0F);
        setPos(at);
        level().broadcastEntityEvent(this, EVENT_IMPACT);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_IMPACT) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.baranhan.flashmod.client.AbilityClient.spearImpact(this));
            return;
        }
        super.handleEntityEvent(id);
    }
}
