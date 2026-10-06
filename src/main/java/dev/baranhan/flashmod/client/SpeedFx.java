package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.speed.WallState;
import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.client.particle.FlashParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tick bazli istemci efektleri: iz noktasi kaydi, hiz olcumu, bacak animasyonu, kivilcimlar, ses duvarlari. */
public final class SpeedFx {
    /**
     * Ses duvari esikleri (blok/tick). Ilk patlama 500 km/h (~6.94 b/t), sonra Mach 1, 3, 10, 30, 100.
     * 1 Mach = 343 m/s = 17.15 blok/tick; km/h -> blok/tick: / 3.6 / 20.
     */
    public static final float[] BOOM_THRESHOLDS = {500F / 72F, 17.15F, 51.45F, 171.5F, 514.5F, 1715F};
    private static final int MAX_NODES = 80;

    private SpeedFx() {}

    public static void tickAll(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        for (Player p : level.players()) {
            ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
            if (e != null) tickPlayer(mc, level, p, e, now);
        }
        if (now % 100 == 0) {
            Set<UUID> present = new HashSet<>();
            for (Player p : level.players()) present.add(p.getUUID());
            ClientSpeedsters.all().keySet().removeIf(id -> !present.contains(id));
        }
    }

    private static void tickPlayer(Minecraft mc, ClientLevel level, Player p, ClientSpeedsters.Entry e, long now) {
        // Blitz/tornado'da model gercek konumundan uzakta cizilir; gercek konum kadraj disindayken frustum
        // elemesi modeli (ve onu izleyen simsekleri) yok etmesin.
        dev.baranhan.flashmod.client.ultimate.UltState ult = dev.baranhan.flashmod.client.ultimate.UltDirector.forCaster(p.getId());
        float ut = ult != null && ult.abortAt < 0 ? ult.t(0F) : -1F;
        boolean ultActive = ut >= 0F && ut < dev.baranhan.flashmod.ultimate.UltimatePhase.DURATION;
        p.noCulling = e.blitz || e.tornado || ultActive;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        e.prevSpeed = e.speed;
        if (!e.hasLast) {
            e.lastX = x; e.lastY = y; e.lastZ = z;
            e.hasLast = true;
        }
        double dx = x - e.lastX, dy = y - e.lastY, dz = z - e.lastZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Teleport / boyut degisimi: mevcut hizla aciklanamayan sicrama -> izi kopar.
        if (dist > 16.0D && dist > e.speed * 3.0D + 16.0D) {
            e.nodes.clear();
            e.boomStage = 0;
            dist = 0; dx = 0; dz = 0;
        }
        double horiz = Math.sqrt(dx * dx + dz * dz);
        e.speed += ((float) dist - e.speed) * 0.5F;
        e.hSpeed += ((float) horiz - e.hSpeed) * 0.5F;
        if (horiz > 0.01D) {
            e.dirX = (float) (dx / horiz);
            e.dirZ = (float) (dz / horiz);
        }
        e.odometer += dist;
        e.lastX = x; e.lastY = y; e.lastZ = z;

        // --- Tornado: olculen (kiris) hiz yerine gercek teget hiz, iz noktasi kaydi yok ---
        boolean local = p == mc.player;
        if (e.tornado) {
            if (!local) { // digerleri icin aci istemcide ilerletilir (sadece gorsel)
                e.tPrevAngle = e.tAngle;
                e.tAngle += e.tDir * e.tSpeed / ClientSpeedsters.TORNADO_RADIUS;
                if (Math.abs(e.tAngle) > 4096F) {
                    float sh = (float) (Math.floor(e.tAngle / (Math.PI * 2.0)) * Math.PI * 2.0);
                    e.tAngle -= sh;
                    e.tPrevAngle -= sh;
                }
            }
            e.speed = e.prevSpeed + (e.tSpeed - e.prevSpeed) * 0.5F;
            e.hSpeed = e.speed;
            e.dirX = -Mth.sin(e.tAngle) * e.tDir;
            e.dirZ = Mth.cos(e.tAngle) * e.tDir;
            x = e.tcx + Mth.cos(e.tAngle) * ClientSpeedsters.TORNADO_RADIUS;
            z = e.tcz + Mth.sin(e.tAngle) * ClientSpeedsters.TORNADO_RADIUS;
            dist = 0; // kiris noktalari ize eklenmesin
        }
        if (e.tornado != e.tornadoPrev) {
            e.nodes.clear();
            if (e.tornado) {
                mc.getSoundManager().play(new TornadoSound(p.getUUID(), e.tcx, e.tcy + 1.0D, e.tcz));
                level.playLocalSound(e.tcx, e.tcy, e.tcz, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 0.8F, 1.2F, false);
            }
            e.tornadoPrev = e.tornado;
        }

        // --- Blitz: konum/hiz/yon kuklanin senaryodaki yolundan, iz noktalari da oradan ---
        if (e.blitz) {
            float bt = now - e.blitzStart;
            Vec3 a = BlitzClient.puppet(level, e, bt, 1F), b = BlitzClient.puppet(level, e, bt - 1F, 1F);
            x = a.x; y = a.y; z = a.z;
            dx = a.x - b.x; dy = a.y - b.y; dz = a.z - b.z;
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double h2 = Math.sqrt(dx * dx + dz * dz);
            e.speed = e.prevSpeed + ((float) dist - e.prevSpeed) * 0.6F;
            e.hSpeed = (float) h2;
            if (h2 > 0.01D) {
                e.dirX = (float) (dx / h2);
                e.dirZ = (float) (dz / h2);
            }
            e.odometer += dist;
            BlitzClient.tick(level, p, e, now);
        }

        // --- Ultimate: DEPART'ta govde proxy yolunda cizilir, iz noktalari da oradan; sahnelerde (govde gizli) iz yok
        boolean ultHidden = ultActive && dev.baranhan.flashmod.ultimate.UltimatePhase.bodyHidden(ut);
        if (ultActive && ut >= dev.baranhan.flashmod.ultimate.UltimatePhase.DEPART.start
                && ut < dev.baranhan.flashmod.ultimate.UltimatePhase.DEPART.end) {
            float step = 1F / ult.scale;
            Vec3 a = ult.arena.toWorld(dev.baranhan.flashmod.client.ultimate.UltRender.proxyArena(ut, ult.arena.d));
            Vec3 b = ult.arena.toWorld(dev.baranhan.flashmod.client.ultimate.UltRender.proxyArena(ut - step, ult.arena.d));
            x = a.x; y = a.y; z = a.z;
            dx = a.x - b.x; dy = a.y - b.y; dz = a.z - b.z;
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double h2 = Math.sqrt(dx * dx + dz * dz);
            e.speed = e.prevSpeed + ((float) dist - e.prevSpeed) * 0.6F;
            e.hSpeed = (float) h2;
            if (h2 > 0.01D) {
                e.dirX = (float) (dx / h2);
                e.dirZ = (float) (dz / h2);
            }
            e.odometer += dist;
        }

        // Duvarda kosma: donus karisimi (render'da enterpole) ve bacak hizi (yatay hiz ~0, dikey hiz kullanilir)
        e.prevWallBlend = e.wallBlend;
        e.wallBlend = Mth.clamp(e.wallBlend + (e.wallRun ? 0.2F : -0.2F), 0F, 1F);
        if (e.wallRun) {
            e.hSpeed = e.speed;                       // butun hareket duvar duzleminde
            wallBodyYaw(p, e, dx, dy, dz);
        } else {
            e.wallBodyInit = false;
        }
        // Iz cercevesi: yukari = dunya yukarisinin duvar donusuyle dondurulmus hali, yan = hareket x yukari
        float wb = e.wallBlend;
        wb = wb * wb * (3F - 2F * wb);
        if (dist > 0.01D) {
            e.mdx = (float) (dx / dist);
            e.mdy = (float) (dy / dist);
            e.mdz = (float) (dz / dist);
        }
        if (wb <= 0.001F) {
            e.upX = 0F; e.upY = 1F; e.upZ = 0F;
            e.sideX = -e.dirZ; e.sideY = 0F; e.sideZ = e.dirX;
        } else {
            float th = wb * (float) Math.PI * 0.5F;
            e.upX = e.wallNx * Mth.sin(th);
            e.upY = Mth.cos(th);
            e.upZ = e.wallNz * Mth.sin(th);
            float cx = e.mdy * e.upZ - e.mdz * e.upY, cy = e.mdz * e.upX - e.mdx * e.upZ, cz = e.mdx * e.upY - e.mdy * e.upX;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl > 1.0E-3F) { e.sideX = cx / cl; e.sideY = cy / cl; e.sideZ = cz / cl; }
        }

        e.soundX = x;
        e.soundY = y;
        e.soundZ = z;
        SpeedSounds.tick(mc, p.getUUID(), e);

        updateLimbs(p, e);

        if (e.phasing && !e.phasingPrev) e.phaseStartedAt = now;
        e.phasingPrev = e.phasing;

        boolean visible = !p.isSpectator() && (mc.player == null || !p.isInvisibleTo(mc.player));

        if (e.active && visible && dist > 0.05D && !ultHidden) {
            float power = ClientSpeedsters.powerFor(e.speed);
            // duvarda: omurga ayak noktasindan (pivot) gecer
            double ox = -e.wallNx * 0.3D * wb, oy = 0.9D * wb, oz = -e.wallNz * 0.3D * wb;
            e.nodes.addFirst(new ClientSpeedsters.TrailNode(x + ox, y + oy, z + oz, e.sideX, e.sideY, e.sideZ,
                    e.upX, e.upY, e.upZ, e.odometer, power, now, e.wallRun ? 1F : p.getBbHeight() / 1.8F));
            while (e.nodes.size() > MAX_NODES) e.nodes.removeLast();
        }
        int life = e.trailLife();
        while (!e.nodes.isEmpty() && now - e.nodes.peekLast().tick() > life + 1) e.nodes.removeLast();

        BlockPos below = BlockPos.containing(x, y - 0.2D, z);
        e.waterRunning = e.active && e.speed > 0.3F && level.getFluidState(below).is(FluidTags.WATER)
                && !p.isInWater();

        if (!e.active) {
            e.boomStage = 0;
            return;
        }
        if (!visible) return;

        RandomSource rand = level.random;
        float factor = FlashParticles.factor();
        boolean selfFp = local && !mc.gameRenderer.getMainCamera().isDetached();
        if (selfFp) factor *= 0.35F;
        float intensity = e.intensity(1F);

        // --- Ses duvarlari (kademeli) ---
        if (e.tornado || e.blitz || ultActive) {
            // Tornadoda / Blitz sinematiginde otomatik patlama yok; gecilen esikleri sessizce isaretle ki tornado bitince ani bir patlama olmasin.
            while (e.boomStage < BOOM_THRESHOLDS.length && e.speed >= BOOM_THRESHOLDS[e.boomStage]) e.boomStage++;
        } else if (e.boomStage < BOOM_THRESHOLDS.length && e.speed >= BOOM_THRESHOLDS[e.boomStage]) {
            sonicBoom(level, p, e, now, e.boomStage);
            e.boomStage++;
        } else if (e.boomStage > 0 && e.speed < BOOM_THRESHOLDS[e.boomStage - 1] * 0.6F) {
            e.boomStage--;
        }

        // --- Kivilcimlar ---
        float rate = (0.4F + intensity * 3.2F) * factor;
        if (e.speed < 0.08F) rate = 0.12F * factor; // dururken ara sira citirti
        if (e.phasing) rate += 1.5F * factor;      // titresirken govdeden kivilcim
        int count = (int) rate + (rand.nextFloat() < rate - (int) rate ? 1 : 0);
        float h = p.getBbHeight();
        // Bu tick'ten sonraki karelerde model pos(T-1) ile pos(T) arasinda cizilir. Kivilcimi pos(T)'de dogurmak
        // yuksek hizda onu modelin ONUNE koyuyordu; bir-iki tick geriye (pos(T-1)..pos(T-2)) yerlestiriyoruz.
        double mvx = e.tornado ? 0.0D : dx, mvy = e.tornado ? 0.0D : dy, mvz = e.tornado ? 0.0D : dz;
        for (int i = 0; i < count; i++) {
            double u = 1.0D + rand.nextFloat();
            double px = x - mvx * u + (rand.nextFloat() - 0.5F) * 0.6F;
            double py = y - mvy * u + rand.nextFloat() * h;
            double pz = z - mvz * u + (rand.nextFloat() - 0.5F) * 0.6F;
            double vx = -e.dirX * (0.05 + 0.12 * intensity) + (rand.nextFloat() - 0.5F) * 0.22F;
            double vy = 0.04 + rand.nextFloat() * 0.16F;
            double vz = -e.dirZ * (0.05 + 0.12 * intensity) + (rand.nextFloat() - 0.5F) * 0.22F;
            FlashParticles.spark(level, px, py, pz, vx, vy, vz, e.core, e.glow, 6 + rand.nextInt(9),
                    0.7F + rand.nextFloat() * 0.7F);
        }

        if (e.tornado) tornadoParticles(level, e, rand);

        // --- Phasing baslangic sesi (loop SpeedSounds'ta) ---
        if (e.phasing && now == e.phaseStartedAt) {
            level.playLocalSound(x, y, z, FlashSounds.PHASE_START.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
        }

        // --- Su ustunde kosarken sicrama ---
        if (e.waterRunning) {
            int splash = (int) (4 * FlashParticles.factor()) + 1;
            for (int i = 0; i < splash; i++) {
                level.addParticle(ParticleTypes.SPLASH, x + (rand.nextFloat() - 0.5F) * 0.8F, y + 0.05D,
                        z + (rand.nextFloat() - 0.5F) * 0.8F, -e.dirX * 0.3F, 0.25D, -e.dirZ * 0.3F);
            }
            if (rand.nextFloat() < 0.5F) {
                level.addParticle(ParticleTypes.CLOUD, x - e.dirX * 0.6F, y + 0.1D, z - e.dirZ * 0.6F,
                        -e.dirX * 0.05F, 0.03D, -e.dirZ * 0.05F);
            }
        }
    }

    /** Girdap: yerden kalkan toz/blok parcalari, cember boyunca savrulan kivilcimlar, yukari sarmal bulutlar. */
    private static void tornadoParticles(ClientLevel level, ClientSpeedsters.Entry e, RandomSource rand) {
        float k = e.tornadoPower();
        if (k < 0.03F) return;
        float factor = FlashParticles.factor();
        float R = ClientSpeedsters.TORNADO_RADIUS;
        double cx = e.tcx, cy = e.tcy, cz = e.tcz;

        BlockState ground = level.getBlockState(BlockPos.containing(cx, cy - 0.5D, cz));
        int dust = (int) ((2 + 10 * k) * factor);
        for (int i = 0; i < dust; i++) {
            float a = rand.nextFloat() * (float) (Math.PI * 2.0);
            float r = R + (rand.nextFloat() - 0.5F) * 1.2F;
            double px = cx + Mth.cos(a) * r, pz = cz + Mth.sin(a) * r;
            double tx = -Mth.sin(a) * e.tDir, tz = Mth.cos(a) * e.tDir;
            double sp = 0.25D + 0.6D * k;
            double up = 0.08D + rand.nextFloat() * 0.3D * k;
            if (!ground.isAir() && rand.nextFloat() < 0.6F) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), px, cy + 0.1D, pz,
                        tx * sp, up + 0.1D, tz * sp);
            } else {
                level.addParticle(ParticleTypes.CLOUD, px, cy + rand.nextFloat() * 0.6D, pz,
                        tx * sp * 0.6D, up, tz * sp * 0.6D);
            }
        }
        // Sarmal yukselen bulutlar (huni)
        int swirl = (int) ((1 + 5 * k) * factor);
        for (int i = 0; i < swirl; i++) {
            float h = rand.nextFloat();
            float a = rand.nextFloat() * (float) (Math.PI * 2.0);
            float r = R * (0.8F + 1.5F * h * h);
            double px = cx + Mth.cos(a) * r, pz = cz + Mth.sin(a) * r;
            double tx = -Mth.sin(a) * e.tDir, tz = Mth.cos(a) * e.tDir;
            level.addParticle(ParticleTypes.POOF, px, cy + h * (3.0D + 8.0D * k), pz,
                    tx * 0.35D * k, 0.05D + 0.1D * k, tz * 0.35D * k);
        }
        // Kosucudan cember disina firlayan kivilcimlar
        int sparks = (int) ((1 + 4 * k) * factor);
        double rx = cx + Mth.cos(e.tAngle) * R, rz = cz + Mth.sin(e.tAngle) * R;
        for (int i = 0; i < sparks; i++) {
            double ox = Mth.cos(e.tAngle), oz = Mth.sin(e.tAngle);
            FlashParticles.spark(level, rx, cy + rand.nextFloat() * 1.8D, rz,
                    e.dirX * 0.4D * k + ox * (0.1D + rand.nextFloat() * 0.3D), 0.05D + rand.nextFloat() * 0.2D,
                    e.dirZ * 0.4D * k + oz * (0.1D + rand.nextFloat() * 0.3D),
                    e.core, e.glow, 6 + rand.nextInt(8), 0.8F + rand.nextFloat() * 0.6F);
        }
    }

    private static void sonicBoom(ClientLevel level, Player p, ClientSpeedsters.Entry e, long now, int stage) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        e.lastBoom = now;
        e.boomPower = Math.min(0.55F + stage * 0.25F, 1.5F);
        e.boomX = x; e.boomY = y + p.getBbHeight() * 0.5D; e.boomZ = z;
        e.boomDirX = e.dirX; e.boomDirZ = e.dirZ;
        // Ses duvari: vanilla ses katmanlari (FlashSounds.SONIC_BOOM kayitli ama kullanilmiyor)
        float pitch = Math.max(0.5F - stage * 0.08F, 0.25F);
        level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.3F + stage * 0.3F, pitch, false);
        level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7F + stage * 0.15F, 1.5F - stage * 0.15F, false);
        if (stage >= 2) {
            level.playLocalSound(x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F, false);
        }
        RandomSource rand = level.random;
        int n = (int) ((36 + stage * 12) * Math.max(FlashParticles.factor(), 0.3F));
        float sx = -e.dirZ, sz = e.dirX;
        float sp = 0.45F + stage * 0.12F;
        for (int i = 0; i < n; i++) {
            float ang = (float) (i * Math.PI * 2 / n);
            float c = Mth.cos(ang), s = Mth.sin(ang);
            double vx = sx * c * sp - e.dirX * 0.1, vy = s * sp, vz = sz * c * sp - e.dirZ * 0.1;
            FlashParticles.spark(level, e.boomX, e.boomY, e.boomZ, vx, vy, vz, e.core, e.glow,
                    10 + rand.nextInt(8), 1.2F);
        }
    }

    /**
     * Bacak animasyonu hizla orantili. Vanilla WalkAnimationState hizi 1'de kirpiyor (bacaklar 5 blok/sn'de de
     * 500 blok/sn'de de ayni hizda sallaniyor). Biz pozisyon artisini kendi degerimizle degistiriyoruz:
     * vanilla bu tick 'cur' kadar ilerletti, biz farki ekleyip hizi kendi degerimize ayarliyoruz -> enterpolasyon
     * kesintisiz kalir. Renderer genligi zaten 1'de kirpiyor, yani sadece frekans artar.
     */
    /**
     * Duvarda govde yonu, vanilla'nin yerdeki kurali ile ama yerel cercevede: hareket yonune dogru donmeye calisir
     * (geri giderken ters cevirir), kafadan en fazla 50 derece ayrilir. Vanilla dunya-yatay hareketten
     * hesapladigi icin her tick kendi degerimizi yaziyoruz (yBodyRotO bir onceki degerimiz -> akici).
     */
    private static void wallBodyYaw(Player p, ClientSpeedsters.Entry e, double dx, double dy, double dz) {
        float head = p.getYHeadRot();
        if (!e.wallBodyInit) {
            e.wallBodyYaw = p.yBodyRot;
            e.wallBodyInit = true;
        }
        Vec3 loc = WallState.toLocal(new Vec3(dx, dy, dz), e.wallNx, e.wallNz);
        double h = Math.sqrt(loc.x * loc.x + loc.z * loc.z);
        float target = head;
        if (h > 0.03D) {
            float mv = (float) Math.toDegrees(Math.atan2(-loc.x, loc.z));
            float diff = Math.abs(Mth.wrapDegrees(head - mv));
            if (diff > 95F && diff < 265F) mv += 180F; // geri geri
            target = mv;
        }
        float body = e.wallBodyYaw + Mth.wrapDegrees(target - e.wallBodyYaw) * 0.3F;
        float off = Mth.wrapDegrees(head - body);
        if (off > 50F) body = head - 50F;
        else if (off < -50F) body = head + 50F;
        e.wallBodyYaw = body;
        p.yBodyRot = body;
    }

    private static void updateLimbs(Player p, ClientSpeedsters.Entry e) {
        WalkAnimationState walk = p.walkAnimation;
        if (!e.active || p.isPassenger() || p.isFallFlying() || p.isSwimming() || p.isSleeping()) {
            e.limbSpeed = walk.speed();
            return;
        }
        float vanillaTarget = Math.min(e.hSpeed * 4F, 1F);
        float mul = 1F + 1.6F * (float) Math.log(1.0D + Math.max(0F, e.hSpeed - 0.28F) * 1.5D);
        float target = vanillaTarget * Math.min(mul, 4.5F);
        e.limbSpeed += (target - e.limbSpeed) * 0.4F;
        float cur = walk.speed();
        float delta = e.limbSpeed - cur;
        if (Math.abs(delta) < 1.0E-4F) return;
        walk.update(delta, 1F);   // position += delta, speedOld = cur
        walk.setSpeed(e.limbSpeed);
    }

    /** Guc acildiginda: govdeden disari patlayan kivilcim halkasi. */
    public static void activationBurst(Player p, ClientSpeedsters.Entry e) {
        if (!(p.level() instanceof ClientLevel level)) return;
        RandomSource rand = level.random;
        int n = (int) (48 * Math.max(FlashParticles.factor(), 0.3F));
        double cy = p.getY() + p.getBbHeight() * 0.55D;
        for (int i = 0; i < n; i++) {
            float yaw = rand.nextFloat() * (float) (Math.PI * 2.0);
            float pitch = (rand.nextFloat() - 0.3F) * 1.4F;
            float sp = 0.25F + rand.nextFloat() * 0.35F;
            double vx = Mth.cos(yaw) * Mth.cos(pitch) * sp, vy = Mth.sin(pitch) * sp + 0.1, vz = Mth.sin(yaw) * Mth.cos(pitch) * sp;
            FlashParticles.spark(level, p.getX(), cy, p.getZ(), vx, vy, vz, e.core, e.glow, 10 + rand.nextInt(12),
                    0.9F + rand.nextFloat() * 0.8F);
        }
    }

    /** Guc kapandiginda: sonen kivilcimlar + hafif duman. */
    public static void fizzle(Player p, ClientSpeedsters.Entry e) {
        if (!(p.level() instanceof ClientLevel level)) return;
        RandomSource rand = level.random;
        for (int i = 0; i < 18; i++) {
            double px = p.getX() + (rand.nextFloat() - 0.5F) * 0.7F;
            double py = p.getY() + rand.nextFloat() * p.getBbHeight();
            double pz = p.getZ() + (rand.nextFloat() - 0.5F) * 0.7F;
            FlashParticles.spark(level, px, py, pz, (rand.nextFloat() - 0.5F) * 0.1F, -0.02F,
                    (rand.nextFloat() - 0.5F) * 0.1F, e.core, e.glow, 8 + rand.nextInt(8), 0.6F);
            if (i % 3 == 0) level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.02, 0);
        }
    }
}
