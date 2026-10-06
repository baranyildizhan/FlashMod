package dev.baranhan.flashmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.client.particle.FlashParticles;
import dev.baranhan.flashmod.network.BlitzSyncPacket;
import dev.baranhan.flashmod.speed.BlitzLock;
import dev.baranhan.flashmod.speed.BlitzPath;
import dev.baranhan.flashmod.speed.BlitzScript;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Istemci tarafi Blitz: sahne senkronu, kukla/hedef konumu (BlitzPath), sabit sinematik kamera, darbe efektleri,
 * sinema seritleri ve ortam sesi.
 */
public final class BlitzClient {
    private static final Map<Integer, UUID> TARGETS = new HashMap<>();

    private BlitzClient() {}

    public static boolean localActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        return e != null && e.blitz;
    }

    /** Render aninin blitz zamani (entity enterpolasyonu ve iz noktalariyla ayni zaman tabani). */
    public static float renderTime(ClientSpeedsters.Entry e, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0F;
        return Mth.clamp((mc.level.getGameTime() - e.blitzStart) - 1 + pt, 0F, BlitzScript.DURATION);
    }

    @Nullable
    public static ClientSpeedsters.Entry entryForTarget(int entityId) {
        UUID id = TARGETS.get(entityId);
        if (id == null) return null;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(id);
        return e != null && e.blitz && e.blitzTarget == entityId ? e : null;
    }

    /** Senaryodaki yerinde cizilen varliklarin golgesi gizlenir (golge donmus gercek konumda kalirdi). */
    public static boolean hideShadow(Entity entity) {
        if (entryForTarget(entity.getId()) != null) return true;
        if (entity instanceof Player p) {
            dev.baranhan.flashmod.client.ultimate.UltState us = dev.baranhan.flashmod.client.ultimate.UltDirector.forCaster(p.getId());
            if (us != null) { // ultimate: govde proxy/sahnede ya da gizliyken golge gercek konumda kalirdi
                float ut = us.t(Minecraft.getInstance().getFrameTime());
                if (ut >= 22F && ut < 158F) return true;
            }
            ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
            return e != null && (e.blitz || e.tornado || e.wallRun || e.wallBlend > 0F); // duvarda: golge yerde kalirdi
        }
        return false;
    }

    public static void handleSync(BlitzSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.getOrCreate(msg.player);
        boolean self = mc.player != null && mc.player.getUUID().equals(msg.player);
        if (msg.active) {
            e.blitz = true;
            e.blitzStart = msg.start;
            e.blitzTarget = msg.targetId;
            e.blitzFrame = new BlitzPath.Frame(msg.ax, msg.ay, msg.az, msg.fx, msg.fz, msg.px, msg.py, msg.pz, msg.start);
            e.blitzHitIdx = 0;
            e.blitzKeyIdx = 0;
            e.nodes.clear();
            TARGETS.put(msg.targetId, msg.player);
            if (self) {
                BlitzLock.clientLocked = true;
                mc.getSoundManager().play(new Drone(msg.player));
                mc.level.playLocalSound(msg.px, msg.py, msg.pz, FlashSounds.BLITZ_START.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
            }
        } else {
            e.blitz = false;
            TARGETS.remove(e.blitzTarget);
            if (self) {
                BlitzLock.clientLocked = false;

            }
        }
    }

    public static void reset() {
        TARGETS.clear();
        BlitzLock.clientLocked = false;
    }

    // ------------------------------------------------------------------ hedef secimi

    /** Bakilan mob: once tam isin, olmazsa ~7 derecelik koni icindeki en yakin mob. */
    @Nullable
    public static LivingEntity pickTarget(Player p, double range) {
        Vec3 eye = p.getEyePosition(1F);
        Vec3 look = p.getViewVector(1F);
        BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double max = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(max));
        AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                en -> en instanceof net.minecraft.world.entity.Mob && en.isAlive(), max * max);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double cos = Math.cos(Math.toRadians(7.0D));
        for (Entity en : p.level().getEntities(p, box.inflate(3.0D))) {
            if (!(en instanceof net.minecraft.world.entity.Mob le) || !le.isAlive()) continue;
            Vec3 to = le.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > max + 1.0D || d < 1.0E-3D) continue;
            double c = to.dot(look) / d;
            if (c < cos) continue;
            double score = d * (2.0D - c);
            if (score < bestScore && p.hasLineOfSight(le)) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ kukla / hedef dunya konumu

    public static Vec3 puppet(Level level, ClientSpeedsters.Entry e, float bt, float framePt) {
        if (e.blitzFrame == null) return Vec3.ZERO;
        double[] w = new double[3];
        BlitzPath.speedsterWorld(e.blitzFrame, bt, w);
        return new Vec3(w[0], w[1], w[2]);
    }

    private static Vec3 stageToWorld(BlitzPath.Frame f, double sx, double sy, double sz) {
        return new Vec3(f.wx(sx, sz), f.wy(sy), f.wz(sx, sz));
    }

    // ------------------------------------------------------------------ kamera (sabit; fare etkilemez)

    @Nullable
    public static CameraModes.Result camera(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        if (e == null || !e.blitz || e.blitzFrame == null) return null;
        BlitzPath.Frame f = e.blitzFrame;
        float bt = renderTime(e, pt);
        float[][] shots = BlitzScript.SHOTS;
        int i = 0;
        while (i + 1 < shots.length && bt >= shots[i + 1][0]) i++;
        float[] s = shots[i];

        Vec3 cam, look;
        if ((int) s[1] == BlitzScript.MID) {
            double[] p = new double[3], tg = new double[6];
            BlitzPath.speedster(f, bt, p);
            BlitzPath.target(bt, tg);
            double mx = (p[0] + tg[0]) * 0.5D, mz = (p[2] + tg[2]) * 0.5D;
            cam = stageToWorld(f, mx + s[2], s[3], mz + s[4]);
            look = stageToWorld(f, mx, s[6], mz);
        } else if ((int) s[1] == BlitzScript.CHASE) {
            double[] p = new double[3], tg = new double[6];
            BlitzPath.speedster(f, bt, p);
            BlitzPath.target(bt, tg);
            cam = stageToWorld(f, p[0] + s[2], p[1] + s[3], p[2] + s[4]);
            Vec3 a = stageToWorld(f, p[0], p[1] + 1.1D, p[2]);
            Vec3 b = stageToWorld(f, tg[0], tg[1] + 1.2D, tg[2]);
            look = a.lerp(b, 0.6D);
        } else if ((int) s[1] == BlitzScript.DOLLY && s.length >= 11) {
            float tEnd = i + 1 < shots.length ? shots[i + 1][0] : BlitzScript.DURATION;
            float u = Mth.clamp((bt - s[0]) / Math.max(1F, tEnd - s[0]), 0F, 1F);
            u = u * u * (3F - 2F * u);
            cam = stageToWorld(f, Mth.lerp(u, s[2], s[8]), Mth.lerp(u, s[3], s[9]), Mth.lerp(u, s[4], s[10]));
            look = stageToWorld(f, s[5], s[6], s[7]);
        } else {
            cam = stageToWorld(f, s[2], s[3], s[4]);
            look = stageToWorld(f, s[5], s[6], s[7]);
        }
        // Darbe sarsintisi: cok kisa ve kucuk (kamera sabit kalir)
        float since = (mc.level.getGameTime() - e.impactAt) + pt;
        if (since >= 0F && since < 8F) {
            float amp = e.impactPower * (float) Math.exp(-since / 2.0F) * (e.impactPower > 1F ? 0.1F : 0.05F);
            float t = since * 9F;
            cam = cam.add(Mth.sin(t * 1.7F) * amp, Mth.sin(t * 2.3F + 1F) * amp, Mth.sin(t * 1.3F + 2F) * amp);
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(look, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 d = cam.subtract(look);
            double len = d.length();
            double back = Math.max(0.5D, hit.getLocation().distanceTo(look) - 0.3D);
            cam = look.add(d.scale(back / Math.max(len, 1.0E-4D)));
        }
        Vec3 dir = look.subtract(cam);
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, h));
        return new CameraModes.Result(cam.x, cam.y, cam.z, yaw, pitch);
    }

    // ------------------------------------------------------------------ sinema seritleri (GUI overlay)

    public static void renderBars(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float k = CameraModes.blitzAmount();
        if (k <= 0.001F) return;
        int bar = Math.round(Math.max(0F, (h - w / 2.39F) / 2F) * k);
        if (bar <= 0) return;
        RenderSystem.disableDepthTest();
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ olaylar (her tick, SpeedFx'ten)

    public static void tick(ClientLevel level, Player p, ClientSpeedsters.Entry e, long now) {
        if (e.blitzFrame == null) return;
        float bt = now - e.blitzStart;
        if (bt > BlitzScript.DURATION + 40) { // sunucudan bitis gelmediyse guvenlik
            e.blitz = false;
            TARGETS.remove(e.blitzTarget);
            if (p == Minecraft.getInstance().player) BlitzLock.clientLocked = false;
            return;
        }
        BlitzPath.Frame f = e.blitzFrame;
        RandomSource r = level.random;

        // Ekrana dalis "vin" sesleri: uzun hamlelerin basinda
        float[][] K = BlitzScript.RUN;
        while (e.blitzKeyIdx < K.length && K[e.blitzKeyIdx][0] <= bt) {
            int i = e.blitzKeyIdx++;
            if (i + 1 < K.length) {
                Vec3 a = puppet(level, e, K[i][0], 1F), b = puppet(level, e, K[i + 1][0], 1F);
                if (a.distanceTo(b) > 6.0D) {
                    level.playLocalSound(a.x, a.y, a.z, FlashSounds.BLITZ_WHOOSH.get(), SoundSource.PLAYERS,
                            0.8F, 0.95F + r.nextFloat() * 0.1F, false);
                }
            }
        }
        while (e.blitzHitIdx < BlitzScript.HITS.length && BlitzScript.HITS[e.blitzHitIdx][0] <= bt) {
            impact(level, e, f, BlitzScript.HITS[e.blitzHitIdx], now);
            e.blitzHitIdx++;
        }
        // Darbe donmasinin sonu: ses-duvari benzeri sok dalgasi ve rakip firlar
        if (bt >= BlitzScript.LAUNCH_T && bt - 1F < BlitzScript.LAUNCH_T) launchBoom(level, e, f, now);

        // Fren: ayaklardan kivilcim ve toz
        for (float[] br : BlitzScript.BRAKES) {
            if (bt < br[0] || bt > br[1]) continue;
            float k = 1F - (bt - br[0]) / (br[1] - br[0]);
            Vec3 pos = puppet(level, e, bt, 1F);
            int n = (int) (6 * k * Math.max(FlashParticles.factor(), 0.35F)) + 1;
            for (int i = 0; i < n; i++) {
                FlashParticles.spark(level, pos.x + (r.nextDouble() - 0.5D) * 0.6D, pos.y + 0.05D,
                        pos.z + (r.nextDouble() - 0.5D) * 0.6D, (r.nextDouble() - 0.5D) * 0.5D,
                        0.1D + r.nextDouble() * 0.25D, (r.nextDouble() - 0.5D) * 0.5D,
                        e.core, e.glow, 6 + r.nextInt(8), 0.8F);
            }
            if (r.nextFloat() < k) {
                level.addParticle(ParticleTypes.CLOUD, pos.x, pos.y + 0.1D, pos.z,
                        (r.nextDouble() - 0.5D) * 0.1D, 0.04D, (r.nextDouble() - 0.5D) * 0.1D);
            }
            if (bt - br[0] < 1F) {
                level.playLocalSound(pos.x, pos.y, pos.z, FlashSounds.BRAKE.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
            }
        }
    }

    private static void impact(ClientLevel level, ClientSpeedsters.Entry e, BlitzPath.Frame f, float[] h, long now) {
        int kind = (int) h[1];
        float th = h[0];
        double[] tg = new double[6], sp = new double[3];
        BlitzPath.target(th, tg);
        BlitzPath.speedster(f, th, sp);
        Entity t = level.getEntity(e.blitzTarget);
        double hgt = t != null ? t.getBbHeight() : 1.8D;
        double cy = kind == BlitzScript.KICK ? hgt * 0.9D : kind == BlitzScript.LIFT ? hgt * 0.45D : hgt * 0.65D;
        // temas noktasi: hedef govdesi, hizciya dogru biraz kaymis
        double dx = sp[0] - tg[0], dz = sp[2] - tg[2], dl = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
        Vec3 c = stageToWorld(f, tg[0] + dx / dl * 0.3D, tg[1] + cy, tg[2] + dz / dl * 0.3D);
        Vec3 away = stageToWorld(f, tg[0] - dx / dl, 0, tg[2] - dz / dl).subtract(stageToWorld(f, tg[0], 0, tg[2]));

        boolean fin = kind == BlitzScript.FINISH;
        switch (kind) {
            case BlitzScript.PASS -> sound(level, c, FlashSounds.BLITZ_PUNCH.get(), 0.7F, 1.15F);
            case BlitzScript.KICK -> sound(level, c, FlashSounds.BLITZ_KICK.get(), 1.0F, 1.0F);
            case BlitzScript.FINISH -> sound(level, c, FlashSounds.BLITZ_FINAL.get(), 1.4F, 1.0F);
            default -> sound(level, c, FlashSounds.BLITZ_PUNCH.get(), 1.0F, 0.95F + level.random.nextFloat() * 0.1F);
        }
        e.impactAt = now;
        e.impactPower = fin ? 1.4F : kind == BlitzScript.KICK || kind == BlitzScript.LIFT ? 0.7F : 0.4F;

        RandomSource r = level.random;
        float factor = Math.max(FlashParticles.factor(), 0.35F);
        int n = (int) ((fin ? 70 : 20) * factor);
        for (int i = 0; i < n; i++) {
            double spd = fin ? 0.5D + r.nextDouble() * 0.6D : 0.2D + r.nextDouble() * 0.3D;
            double vx = away.x * spd + (r.nextDouble() - 0.5D) * (fin ? 0.8D : 0.45D);
            double vy = 0.05D + r.nextDouble() * (fin ? 0.45D : 0.25D);
            double vz = away.z * spd + (r.nextDouble() - 0.5D) * (fin ? 0.8D : 0.45D);
            FlashParticles.spark(level, c.x, c.y, c.z, vx, vy, vz, e.core, e.glow, 7 + r.nextInt(9), fin ? 1.3F : 0.9F);
        }
        if (fin) { // yerden kalkan toz halkasi (ses patlamasi degil, darbe tozu)
            Vec3 feet = stageToWorld(f, tg[0], tg[1], tg[2]);
            for (int i = 0; i < 20; i++) {
                float a = i / 20F * (float) (Math.PI * 2.0);
                level.addParticle(ParticleTypes.CLOUD, feet.x, feet.y + 0.1D, feet.z, Mth.cos(a) * 0.25D, 0.03D, Mth.sin(a) * 0.25D);
            }
        }
    }

    /** Final darbenin dondugu an biter: halka sok dalgalari + parlama (SpeedTrailRenderer.drawBoom), toz, ses. */
    private static void launchBoom(ClientLevel level, ClientSpeedsters.Entry e, BlitzPath.Frame f, long now) {
        double[] tg = new double[6], sp = new double[3];
        BlitzPath.target(BlitzScript.LAUNCH_T, tg);
        BlitzPath.speedster(f, BlitzScript.LAUNCH_T, sp);
        Vec3 c = stageToWorld(f, tg[0], tg[1] + 1.1D, tg[2]);
        double[] tg2 = new double[6];
        BlitzPath.target(BlitzScript.LAUNCH_T + 3F, tg2);
        Vec3 dir = stageToWorld(f, tg2[0], 0, tg2[2]).subtract(stageToWorld(f, tg[0], 0, tg[2])).normalize(); // firlama yonu
        e.lastBoom = now;
        e.boomPower = 1.5F;
        e.boomX = c.x;
        e.boomY = c.y;
        e.boomZ = c.z;
        e.boomDirX = (float) dir.x;
        e.boomDirZ = (float) dir.z;
        e.impactAt = now;
        e.impactPower = 1.5F;
        // Ses duvari ile ayni vanilla katmanlar (FlashSounds.SONIC_BOOM kayitli ama kullanilmiyor)
        sound(level, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.9F, 0.35F);
        sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.2F);
        sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.6F);
        RandomSource r = level.random;
        Vec3 feet = stageToWorld(f, tg[0], tg[1], tg[2]);
        for (int i = 0; i < 36; i++) {
            float a = i / 36F * (float) (Math.PI * 2.0);
            level.addParticle(ParticleTypes.CLOUD, feet.x, feet.y + 0.1D, feet.z,
                    Mth.cos(a) * 0.55D, 0.02D + r.nextDouble() * 0.05D, Mth.sin(a) * 0.55D);
        }
        int n = (int) (90 * Math.max(FlashParticles.factor(), 0.35F));
        for (int i = 0; i < n; i++) {
            double spd = 0.6D + r.nextDouble() * 0.9D;
            FlashParticles.spark(level, c.x, c.y, c.z, dir.x * spd + (r.nextDouble() - 0.5D) * 1.2D,
                    0.05D + r.nextDouble() * 0.6D, dir.z * spd + (r.nextDouble() - 0.5D) * 1.2D,
                    e.core, e.glow, 10 + r.nextInt(12), 1.5F);
        }
    }

    private static void sound(ClientLevel level, Vec3 at, SoundEvent ev, float vol, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }

    /** Sinematik boyunca alcak, "zaman yavasladi" ugultusu (sadece kendi blitz'inde). */
    private static final class Drone extends AbstractTickableSoundInstance {
        private final UUID player;
        private int age;

        Drone(UUID player) {
            super(FlashSounds.BLITZ_DRONE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.0F;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            ClientSpeedsters.Entry e = ClientSpeedsters.get(player);
            if (e == null || !e.blitz) {
                stop();
                return;
            }
            age++;
            this.volume = Math.min(0.8F, age * 0.06F);
        }
    }
}
