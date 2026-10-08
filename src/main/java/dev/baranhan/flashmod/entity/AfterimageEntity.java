package dev.baranhan.flashmod.entity;

import dev.baranhan.flashmod.FlashEntities;
import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SkillFxPacket;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Zaman kalintisi: hizcinin birakip kactigi, zamanda donmus parlak goruntusu. Yapay zekasi yok, hareket etmez;
 * cevredeki dusmanlar (sahibini hedefleyenler ve yakindaki hedefsiz dusmanlar) ona yonelir. Bir canli ona vurunca
 * parcalanir: cevresine statik bosalma (hasar + geri itme + yavaslatma). Suresi dolunca solar. Diske kaydedilmez.
 * Gorunum istemcide (AfterimageRenderer): sahibinin skin'i, donmus kosu ya da dovus durusu, simsekler.
 */
public class AfterimageEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AfterimageEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    /** 0 dovus durusu, 1 kosu. */
    private static final EntityDataAccessor<Byte> POSE_KIND = SynchedEntityData.defineId(AfterimageEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> CORE = SynchedEntityData.defineId(AfterimageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GLOW = SynchedEntityData.defineId(AfterimageEntity.class, EntityDataSerializers.INT);
    /** Solmaya basladigi tickCount (-1: canli). */
    private static final EntityDataAccessor<Integer> FADE_AT = SynchedEntityData.defineId(AfterimageEntity.class, EntityDataSerializers.INT);
    public static final int FADE_TICKS = 10;

    private int maxLife = 160;
    private boolean ended;

    public AfterimageEntity(EntityType<? extends AfterimageEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public static AfterimageEntity create(ServerPlayer owner, boolean running) {
        AfterimageEntity e = new AfterimageEntity(FlashEntities.AFTERIMAGE.get(), owner.level());
        float yaw = owner.yBodyRot;
        e.moveTo(owner.getX(), owner.getY(), owner.getZ(), yaw, 0F);
        e.setYHeadRot(owner.getYHeadRot());
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        e.entityData.set(POSE_KIND, (byte) (running ? 1 : 0));
        e.entityData.set(CORE, SpeedsterData.getCore(owner));
        e.entityData.set(GLOW, SpeedsterData.getGlow(owner));
        e.maxLife = FlashServerConfig.DECOY_LIFETIME.get() * 20;
        return e;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(POSE_KIND, (byte) 0);
        this.entityData.define(CORE, SpeedsterData.DEFAULT_CORE);
        this.entityData.define(GLOW, SpeedsterData.DEFAULT_GLOW);
        this.entityData.define(FADE_AT, -1);
    }

    @Nullable
    public UUID ownerId() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public boolean running() {
        return this.entityData.get(POSE_KIND) == 1;
    }

    public int core() {
        return this.entityData.get(CORE);
    }

    public int glow() {
        return this.entityData.get(GLOW);
    }

    public int fadeAt() {
        return this.entityData.get(FADE_AT);
    }

    @Nullable
    private Player owner() {
        UUID id = ownerId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        int fade = fadeAt();
        if (fade >= 0) {
            if (tickCount - fade >= FADE_TICKS) discard();
            return;
        }
        Player owner = owner();
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            if (!ended) shatter(null, false);
            return;
        }
        if (tickCount >= maxLife) {
            fadeOut();
            return;
        }
        if (tickCount % 5 == 1) lure(owner);
    }

    /** Sahibini hedefleyen dusmanlar ve yakindaki hedefsiz dusmanlar bu goruntuye yonelir. */
    private void lure(Player owner) {
        AABB box = getBoundingBox().inflate(24.0D);
        for (Mob m : level().getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
            LivingEntity t = m.getTarget();
            boolean hunting = t == owner;
            boolean idleEnemy = t == null && m instanceof Enemy && m.distanceToSqr(this) < 12.0D * 12.0D;
            if (!hunting && !idleEnemy) continue;
            m.setTarget(this);
            if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
                m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, this);
            }
        }
    }

    // ---------------------------------------------------------------- hasar / sonlanma

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // sadece bir canlinin (ya da onun firlattiginin) darbesi kirar; dusme, ates, bogulma vb. islemez
        return source.getEntity() == null || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || ended || isInvulnerableTo(source)) return false;
        Entity by = source.getEntity();
        if (by != null && by.getUUID().equals(ownerId())) return false; // sahibi kiramaz
        shatter(by, true);
        return true;
    }

    /** Statik bosalma: cevredeki canlilara hasar, disari itme, yavaslatma; parcalanma efekti. */
    private void shatter(@Nullable Entity by, boolean discharge) {
        ended = true;
        Player owner = owner();
        if (discharge) {
            float dmg = FlashServerConfig.DECOY_DISCHARGE.get().floatValue();
            AABB box = getBoundingBox().inflate(3.5D);
            for (LivingEntity le : level().getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x != this)) {
                if (le instanceof AfterimageEntity || (owner != null && le == owner)) continue;
                double dx = le.getX() - getX(), dz = le.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.5D) continue;
                if (dmg > 0F) {
                    DamageSource src = owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic();
                    le.invulnerableTime = 0;
                    le.hurt(src, dmg);
                }
                double k = (1.0D - d / 3.5D) * 0.9D + 0.35D;
                double nx = d > 1.0E-3 ? dx / d : 0, nz = d > 1.0E-3 ? dz / d : 1;
                le.setDeltaMovement(le.getDeltaMovement().add(nx * k, 0.32D, nz * k));
                le.hurtMarked = true;
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true));
            }
        }
        sendFx(discharge ? SkillFxPacket.DECOY_SHATTER : SkillFxPacket.DECOY_FADE);
        sound(FlashSounds.DECOY_SHATTER.get(), discharge ? 1.2F : 0.6F, discharge ? 1.0F : 1.35F);
        discard();
    }

    /** Suresi doldu: FADE_TICKS boyunca istemcide yukari dogru cozulur, sonra kaybolur. */
    private void fadeOut() {
        if (ended) return;
        ended = true;
        this.entityData.set(FADE_AT, tickCount);
        sendFx(SkillFxPacket.DECOY_FADE);
        sound(FlashSounds.DECOY_SHATTER.get(), 0.5F, 1.4F);
    }

    /** Disaridan (sahibi yeni bir kalinti biraktiginda): sessizce coz. */
    public void dissolve() {
        if (!ended) fadeOut();
    }

    private void sendFx(byte type) {
        UUID owner = ownerId();
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                SkillFxPacket.simple(type, owner == null ? new UUID(0, 0) : owner, getId(), getX(), getY(), getZ(),
                        0F, 1F, 0F, 1F, core(), glow()));
    }

    private void sound(SoundEvent ev, float vol, float pitch) {
        level().playSound(null, getX(), getY() + 1.0D, getZ(), ev, SoundSource.PLAYERS, vol, pitch);
    }

    // ---------------------------------------------------------------- canli davranisi (yok)

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !ended;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void tickDeath() {
        discard();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }
}
