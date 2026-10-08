package dev.baranhan.flashmod.speed;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.entity.LightningSpearEntity;
import dev.baranhan.flashmod.network.AbilitySyncPacket;
import dev.baranhan.flashmod.network.FlashNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sunucu tarafi yetenekler:
 *  - Speed Force enerjisi (0..100): kosarak/tornadoyla dolar.
 *  - Simsek mizragi: tus basiliyken kinetik yuk (SkillLogic) elde biriktirilir (sarj), birakinca firlatilir.
 *  - Agir cekim (ac/kapa): acikken enerji harcar ve TimeControl ile butun oyunu (sunucu + tum istemciler)
 *    yavaslatir. Enerji bitince ya da guc kapaninca kendiliginden kapanir.
 *  - Duvarda kosma durumu istemciden gelir, izleyenlere yayilir (render icin).
 */
public final class AbilityLogic {
    public static final int THROW = 0, SLOWMO = 1;
    public static final float MAX_ENERGY = 100F;
    public static final float MIN_THROW = 15F;

    private static final class State {
        float energy, charge;
        boolean throwHeld, slowToggle, slowActive;
        boolean wallRun;
        float nx, nz = 1F;
        float[] wallN = {0F, 1F};
        double lx, ly, lz;
        boolean hasLast;
        // son gonderilen
        float sEnergy = -1F, sCharge = -1F;
        boolean sCharging, sSlow, sWall;
        int syncCooldown;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private AbilityLogic() {}

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static float energy(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0F : s.energy;
    }

    /** Enerji harca (yeterliyse true). Ultimate gibi tek seferlik bedeller icin. */
    public static boolean consumeEnergy(ServerPlayer p, float amount) {
        State s = state(p);
        if (s.energy + 1.0E-3F < amount) return false;
        s.energy = Math.max(0F, s.energy - amount);
        sync(p, s, true);
        return true;
    }

    /** Herhangi bir hizcinin agir cekimi acik mi (TimeControl hedef orani). */
    public static boolean anySlowActive() {
        for (State s : STATES.values()) if (s.slowActive) return true;
        return false;
    }

    /** Simsek mizragi sarj ediliyor mu (kinetik yuk bu sirada sonmez). */
    public static boolean isCharging(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.throwHeld;
    }

    public static boolean isWallRunning(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.wallRun;
    }

    /** {nx, nz} veya null (duvarda degil). WallState/mixinler icin. */
    public static float[] wallNormal(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.wallRun ? s.wallN : null;
    }

    private static void setWall(ServerPlayer p, State s, boolean on) {
        if (s.wallRun == on) return;
        s.wallRun = on;
        s.wallN = new float[]{s.nx, s.nz};
        p.refreshDimensions(); // kutu duvar cercevesine gecer / geri doner
    }

    // ---------------------------------------------------------------- girdiler

    public static void input(ServerPlayer p, int ability, boolean held) {
        State s = state(p);
        if (held && dev.baranhan.flashmod.ultimate.UltimateManager.involved(p)) return; // ultimate sirasinda yok
        if (ability == THROW) {
            if (held) {
                s.throwHeld = SpeedsterData.isActive(p) && !BlitzLogic.isActive(p);
            } else if (s.throwHeld) {
                s.throwHeld = false;
                release(p, s);
            }
        } else if (ability == SLOWMO && held) { // ac/kapa (istemci sadece basista gonderir)
            if (s.slowToggle) {
                s.slowToggle = false;
            } else if (SpeedsterData.isActive(p) && s.energy > 2F && !BlitzLogic.isActive(p)) {
                s.slowToggle = true;
            }
        }
    }

    public static void wallRun(ServerPlayer p, boolean on, float nx, float nz) {
        State s = state(p);
        float l = (float) Math.sqrt(nx * nx + nz * nz);
        if (l > 1.0E-3F) {
            s.nx = nx / l;
            s.nz = nz / l;
        }
        setWall(p, s, on && SpeedsterData.isActive(p));
        sync(p, s, true);
    }

    private static void release(ServerPlayer p, State s) {
        float c = s.charge;
        s.charge = 0F;
        if (c < MIN_THROW || !SpeedsterData.isActive(p)) {
            SkillLogic.giveKinetic(p, c); // yetersiz sarj: kinetik yuke iade
            return;
        }
        float k = c / 100F;
        Vec3 look = p.getLookAngle();
        LightningSpearEntity spear = new LightningSpearEntity(p.level(), p, c);
        Vec3 eye = p.getEyePosition();
        spear.setPos(eye.x + look.x * 0.6D, eye.y - 0.1D + look.y * 0.6D, eye.z + look.z * 0.6D);
        double speed = 2.2D + 2.6D * k;
        spear.setDeltaMovement(look.scale(speed));
        p.level().addFreshEntity(spear);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), FlashSounds.THROW_RELEASE.get(), SoundSource.PLAYERS,
                1.0F + 0.5F * k, 1.1F - 0.2F * k);
        sync(p, s, true);
    }

    // ---------------------------------------------------------------- tick

    public static void serverTick(ServerPlayer p) {
        State s = state(p);
        boolean active = SpeedsterData.isActive(p) && !p.isSpectator();
        double dx = 0, dz = 0;
        if (s.hasLast) {
            dx = p.getX() - s.lx;
            dz = p.getZ() - s.lz;
        }
        s.lx = p.getX();
        s.ly = p.getY();
        s.lz = p.getZ();
        s.hasLast = true;
        double h = Math.min(Math.sqrt(dx * dx + dz * dz), 50.0D);

        if (!active) {
            if (s.charge > 0F) { SkillLogic.giveKinetic(p, s.charge); s.charge = 0F; }
            s.throwHeld = false;
            s.slowActive = false;
            s.slowToggle = false;
            setWall(p, s, false);
        } else {
            // enerji: kostukca / tornado sirasinda dolar
            if (TornadoLogic.isActive(p)) s.energy += 2.5F;
            else if (h > 0.4D) s.energy += (float) Math.min(h, 6.0D) * 0.55F;
            s.energy = Math.min(MAX_ENERGY, s.energy);

            // mizrak sarji: kostukca biriken kinetik yukten (Speed Force enerjisi degil)
            if (s.throwHeld && s.charge < 100F) {
                s.charge += SkillLogic.takeKinetic(p, Math.min(4F, 100F - s.charge));
            }

            // agir cekim: gercek zamanda sabit enerji tuketimi (tick'ler yavasladikca tick basina daha fazla)
            boolean was = s.slowActive;
            if (BlitzLogic.isActive(p)) s.slowToggle = false;
            if (s.slowToggle && s.energy <= 0.5F) s.slowToggle = false; // enerji bitti
            s.slowActive = s.slowToggle;
            if (s.slowActive) {
                float perTick = FlashServerConfig.SLOWMO_DRAIN.get().floatValue() / 20F / Math.max(0.05F, TimeControl.rate());
                s.energy = Math.max(0F, s.energy - perTick);
            }
            if (was != s.slowActive) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                        s.slowActive ? FlashSounds.SLOWMO_START.get() : FlashSounds.SLOWMO_END.get(),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        sync(p, s, false);
    }

    // ---------------------------------------------------------------- senkron

    private static void sync(ServerPlayer p, State s, boolean force) {
        boolean charging = s.throwHeld;
        boolean changed = s.sCharging != charging || s.sSlow != s.slowActive || s.sWall != s.wallRun;
        boolean valueChanged = Math.abs(s.sEnergy - s.energy) >= 1F || Math.abs(s.sCharge - s.charge) >= 1F;
        if (s.syncCooldown > 0) s.syncCooldown--;
        if (!force && !changed && !(valueChanged && s.syncCooldown == 0)) return;
        s.sEnergy = s.energy;
        s.sCharge = s.charge;
        s.sCharging = charging;
        s.sSlow = s.slowActive;
        s.sWall = s.wallRun;
        s.syncCooldown = 2;
        FlashNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new AbilitySyncPacket(p.getUUID(), s.energy, s.charge, charging, s.slowActive, s.wallRun, s.nx, s.nz));
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
