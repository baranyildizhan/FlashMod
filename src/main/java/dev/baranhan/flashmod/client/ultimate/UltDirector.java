package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.client.CameraModes;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.network.UltimateEventPacket;
import dev.baranhan.flashmod.network.UltimateStartPacket;
import dev.baranhan.flashmod.speed.BlitzLock;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Istemci yonetmeni (tekil): oturumlari tutar, zamani hesaplar, kamerayi (CameraModes uzerinden, bizim Blitz
 * kamerasiyla ayni yol) uretir, girdi/HUD kilitlerini ve sarsintiyi yonetir. Tek bir tam sinematik + istenen
 * sayida izleyici oturumu.
 */
public final class UltDirector {
    private static final Map<Integer, UltState> SESSIONS = new LinkedHashMap<>();
    private static boolean stasisLocal;
    private static boolean warned;
    private static long lastNanos;
    /** Debug: yonetmen kamerayi birakir (serbest kamera). */
    public static boolean freeCam, showInfo;
    private static float roll, fov = 70F;
    private static final float[] SHAKE_EVENTS = {22, 0.2F, UltimatePhase.HIT1, 0.5F, 53, 0.3F, 86, 0.25F, 112, 0.55F,
            126, 0.4F, 230, 0.2F, 272, 0.35F, UltimatePhase.HIT2, 1.0F, UltimatePhase.LAUNCH_T, 0.4F,
            UltimatePhase.AIR_BLINK, 0.25F, UltimatePhase.HIT3, 0.8F, UltimatePhase.SLAM_T, 1.0F, UltimatePhase.CASTER_LAND, 0.3F};
    /** Okyanus kompozisyonu: ufuk ekranin sol kenarinda alttan %6, sag kenarinda alttan %78 yukseklikte (su ~%42). */
    private static final float OCEAN_LEFT = 0.06F, OCEAN_RIGHT = 0.78F;

    private UltDirector() {}

    public static Iterable<UltState> sessions() {
        return SESSIONS.values();
    }

    @Nullable
    public static UltState full() {
        for (UltState s : SESSIONS.values()) if (s.full) return s;
        return null;
    }

    @Nullable
    public static UltState forCaster(int entityId) {
        for (UltState s : SESSIONS.values()) if (s.casterId == entityId) return s;
        return null;
    }

    @Nullable
    public static UltState forTarget(int entityId) {
        for (UltState s : SESSIONS.values()) if (s.targetId == entityId && s.targetId >= 0) return s;
        return null;
    }

    // ---------------------------------------------------------------- paketler

    public static void onStart(UltimateStartPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SESSIONS.containsKey(m.sessionId)) return;
        UltState s = new UltState(m);
        if (s.full && (!FlashClientConfig.ULT_CINEMATIC.get() || full() != null)) s.full = false;
        if (s.preview) s.previewT = -2F;
        SESSIONS.put(m.sessionId, s);
        if (s.full && !warned && !FlashClientConfig.ULT_REDUCE_FLASHES.get() && mc.player != null) {
            warned = true;
            mc.player.displayClientMessage(Component.translatable("flashmod.ult.flash_warning"), false);
        }
    }

    public static void onEvent(UltimateEventPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.type == UltimateEventPacket.STASIS_ON) { stasisLocal = true; return; }
        if (m.type == UltimateEventPacket.STASIS_OFF) { stasisLocal = false; return; }
        UltState s = SESSIONS.get(m.sessionId);
        if (s == null || mc.level == null) return;
        if (m.type == UltimateEventPacket.ABORT) {
            s.abortAt = mc.level.getGameTime();
            UltSounds.stopAll(s);
        } else if (!s.full && mc.player != null) { // izleyiciler: yakindaysa carpma sarsintisi (tam sinematikte tabloda)
            s.trauma = Math.min(1F, s.trauma + (mc.player.distanceToSqr(m.x, m.y, m.z) > 400 ? 0.3F : 0.6F));
        }
    }

    public static void clear() {
        for (UltState s : SESSIONS.values()) UltSounds.stopAll(s);
        SESSIONS.clear();
        stasisLocal = false;
        BlitzLock.ultimateLocked = false;
        freeCam = false;
    }

    // ---------------------------------------------------------------- tick

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { if (!SESSIONS.isEmpty()) clear(); return; }
        UltDebug.tick();
        Iterator<UltState> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            UltState s = it.next();
            if (s.preview && !s.paused) s.previewT += 1F;
            float t = s.t(0F);
            boolean aborted = s.abortAt >= 0 && mc.level.getGameTime() - s.abortAt > 10;
            if (t > UltimatePhase.DURATION + (s.preview ? 5F : 30F) || aborted || (!s.preview && s.caster() == null && t > 4F)) {
                UltSounds.stopAll(s);
                it.remove();
                continue;
            }
            if (s.abortAt < 0) UltSounds.tick(s, s.lastTickT, t);
            s.lastTickT = t;
            recordTargetTrail(s, t, mc.level.getGameTime());
        }
        UltState f = full();
        float ft = f == null ? -1F : f.t(0F);
        BlitzLock.ultimateLocked = (f != null && f.abortAt < 0 && ft >= 0F && ft < UltimatePhase.DURATION && !freeCam)
                || stasisLocal && f != null;
    }

    /** Yerel oyuncunun hareketi kilitli mi (tam sinematik ya da stasis'teki hedef). */
    public static boolean inputLocked() {
        UltState f = full();
        if (f != null && f.abortAt < 0) {
            float t = f.t(0F);
            if (t >= 0F && t < UltimatePhase.DURATION - 2 && !freeCam) return true;
        }
        for (UltState s : SESSIONS.values()) {
            if (s.skipped && s.abortAt < 0 && s.t(0F) < UltimatePhase.DURATION) return true;
        }
        return stasisLocal;
    }

    /** Gorsel sinematigi atla: izleyici versiyonuna gec (sunucu zaman cizelgesi degismez). */
    public static void skip() {
        UltState f = full();
        if (f == null || f.preview) return;
        f.full = false;
        f.skipped = true;
        UltSounds.stopAll(f);
        BlitzLock.ultimateLocked = false;
    }

    public static boolean hidesHud() {
        UltState f = full();
        if (f == null || f.abortAt >= 0 || freeCam) return false;
        float t = f.t(0F);
        return t >= 0F && t < UltimatePhase.DURATION - 4;
    }

    public static boolean controlsCamera() {
        UltState f = full();
        if (f == null || freeCam) return false;
        float t = f.t(0F);
        return t >= 0F && t < UltimatePhase.DURATION;
    }

    /** Bu oyuncu bir ultimate'in caster'i ve govdesi su an sahneye tasindi mi (dunyadaki normal izi cizilmesin). */
    public static boolean hidesWorldTrail(Player p, float pt) {
        UltState s = forCaster(p.getId());
        return s != null && s.abortAt < 0 && UltimatePhase.bodyHidden(s.t(pt));
    }

    /** Su an bir SCENE fazi mi oynuyor (dunya yerine sahne cizilecek). */
    @Nullable
    public static UltState scene(float pt) {
        UltState f = full();
        if (f == null || f.abortAt >= 0 || freeCam) return null;
        float t = f.t(pt);
        return t >= UltimatePhase.SCENE_START && t < UltimatePhase.SCENE_END ? f : null;
    }

    public static float roll() { return roll; }

    public static float fov() { return fov; }

    // ---------------------------------------------------------------- kamera

    @Nullable
    public static CameraModes.Result camera(Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        UltState s = full();
        roll = 0F;
        if (s == null || entity != mc.player || freeCam) return null;
        float t = s.t(pt);
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Mth.clamp((nanos - lastNanos) / 1.0E9F, 0F, 0.1F);
        lastNanos = nanos;
        if (t < 0F || t >= UltimatePhase.DURATION) return null;
        Player self = mc.player;
        Vec3 eye = self.getEyePosition(pt);
        float liveYaw = self.getViewYRot(pt), livePitch = self.getViewXRot(pt);
        float liveFov = mc.options.fov().get();

        if (!s.snapTaken) {
            s.snapTaken = true;
            s.snapPos = eye;
            s.snapYaw = liveYaw;
            s.snapPitch = livePitch;
            s.snapFov = liveFov;
        }

        float intro = UltimatePhase.WINDUP.start;
        UltCamera.State c = UltCamera.evaluate(s.tracks, Math.max(t, intro));
        Vec3 pos, look;
        boolean arena = c.space() == UltimatePhase.Space.ARENA;
        if (arena) {
            pos = s.arena.toWorld(c.pos());
            look = resolveLook(s, c, t, pt, dt);
            pos = collide(s, look, pos, dt);
        } else {
            pos = c.pos();
            look = c.look();
            if (c.follow()) { // kosucuyu takip (t - lag aninda)
                Vec3 r = UltScene.runner(t, t - c.lag());
                pos = pos.add(r);
                look = look.add(r);
            }
            s.sceneCam = pos;
        }
        Vec3 dir = look.subtract(pos);
        float yaw = UltCamera.yawTo(dir), pitch = UltCamera.pitchTo(dir);
        float camFov = c.fov(), camRoll = c.roll();
        if (UltimatePhase.at(t) == UltimatePhase.OCEAN) { // kompozisyon: egik ufuk, su sag-alt ~%40
            float[] pr = oceanComposition(camFov, mc);
            pitch = pr[0];
            camRoll = pr[1];
        }
        Vec3 out = arena ? pos : eye; // sahnede vanilla kamera gozde kalir (chunk'lar / ses dinleyicisi)

        if (t < intro) { // oyun kamerasindan giris
            float u = UltCamera.Ease.IN_OUT_CUBIC.apply(t / intro);
            out = s.snapPos.lerp(out, u);
            yaw = s.snapYaw + Mth.wrapDegrees(yaw - s.snapYaw) * u;
            pitch = Mth.lerp(u, s.snapPitch, pitch);
            camFov = Mth.lerp(u, s.snapFov, camFov);
            camRoll *= u;
        } else if (t >= UltimatePhase.RECOVER.start) { // canli birinci sahisa donus
            float u = UltCamera.Ease.IN_OUT_CUBIC.apply(UltimatePhase.RECOVER.local(t));
            out = out.lerp(eye, u);
            yaw = yaw + Mth.wrapDegrees(liveYaw - yaw) * u;
            pitch = Mth.lerp(u, pitch, livePitch);
            camFov = Mth.lerp(u, camFov, liveFov);
            camRoll *= 1F - u;
        }

        // sarsinti
        for (int i = 0; i < SHAKE_EVENTS.length; i += 2) {
            if (s.lastShakeT < SHAKE_EVENTS[i] && t >= SHAKE_EVENTS[i]) s.trauma = Math.min(1F, s.trauma + SHAKE_EVENTS[i + 1]);
        }
        s.lastShakeT = t;
        s.trauma = Math.max(0F, s.trauma - 1.6F * dt);
        float tr = Math.max(baseTrauma(t), s.trauma);
        float amp = tr * tr * FlashClientConfig.ULT_SHAKE.get().floatValue();
        if (amp > 0F) {
            float x = (nanos / 1.0E9F) * 22F;
            yaw += 3.5F * amp * UltCamera.noise(x + (s.seed & 31));
            pitch += 2.5F * amp * UltCamera.noise(x * 1.13F + 17.3F);
            camRoll += 4.5F * amp * UltCamera.noise(x * 0.87F + 41.9F);
        }

        // iptal: o anki kameradan canli kameraya 10 tick'te don
        if (s.abortAt >= 0 && mc.level != null) {
            float u = UltCamera.Ease.OUT_CUBIC.apply((mc.level.getGameTime() - s.abortAt + pt) / 10F);
            out = s.lastCamPos.lerp(eye, u);
            yaw = s.lastYaw + Mth.wrapDegrees(liveYaw - s.lastYaw) * u;
            pitch = Mth.lerp(u, s.lastPitch, livePitch);
            camFov = Mth.lerp(u, s.lastFov, liveFov);
            camRoll = s.lastRoll * (1F - u);
        } else {
            s.lastCamPos = out;
            s.lastYaw = yaw;
            s.lastPitch = pitch;
            s.lastFov = camFov;
            s.lastRoll = camRoll;
        }
        roll = camRoll;
        fov = camFov;
        // giris/cikista kamera goze cok yakinken govde cizilmesin (kafanin icini gormeyelim)
        boolean detached = arena ? out.distanceToSqr(eye) > 1.8 * 1.8 : true;
        return new CameraModes.Result(out.x, out.y, out.z, yaw, Mth.clamp(pitch, -90F, 90F), detached);
    }

    private static float baseTrauma(float t) {
        if (t >= 6F && t < 30F) return 0.08F + 0.32F * (t - 6F) / 24F;
        if (t >= 108F && t < 122F) return 0.3F;
        if (t >= 126F && t < 140F) return 0.2F;
        if (t >= 280F && t < 290F) return 0.4F + 0.5F * (t - 280F) / 10F;
        return 0F;
    }

    /**
     * Okyanus kompozisyonu: ufuk sol kenarda alttan OCEAN_LEFT, sag kenarda alttan OCEAN_RIGHT oraninda. Duz bir su
     * duzleminin ufku, pitch'e gore ekran merkezinden sabit uzakliktaki yatay bir cizgidir; roll onu merkez etrafinda
     * dondurur. Donus: {pitch (MC, + asagi), roll}.
     */
    static float[] oceanComposition(float fov, Minecraft mc) {
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float yl = 2F * OCEAN_LEFT - 1F, yr = 2F * OCEAN_RIGHT - 1F;         // NDC y (yari yukseklik birimi)
        double phi = Math.atan((yr - yl) / (2.0 * aspect));                    // ufkun egimi
        double y0 = (yl + yr) * 0.5;                                           // merkezdeki yukseklik (< 0: altta)
        double up = Math.atan(-y0 * Math.cos(phi) * Math.tan(Math.toRadians(fov) * 0.5));
        return new float[]{(float) -Math.toDegrees(up), (float) Math.toDegrees(phi)};
    }

    private static Vec3 resolveLook(UltState s, UltCamera.State c, float t, float pt, float dt) {
        switch (c.lookMode()) {
            case UltCamera.LOOK_CONTACT: {
                Vec3 off = c.look();
                return s.contact().add(s.arena.rx() * off.x + s.arena.fx * off.z, off.y, s.arena.rz() * off.x + s.arena.fz * off.z);
            }
            case UltCamera.LOOK_TARGET:
            case UltCamera.LOOK_MID: {
                Vec3 tp = targetLive(s, pt);
                if (s.springPos == null || t < UltimatePhase.LAUNCH.start + 0.5F) { s.springPos = tp; s.springVel = Vec3.ZERO; }
                // kritik sonumlu yay (omega = 10 rad/s)
                double w = 10.0, h = dt;
                Vec3 x = s.springPos.subtract(tp);
                Vec3 a = x.scale(-w * w).subtract(s.springVel.scale(2 * w));
                s.springVel = s.springVel.add(a.scale(h));
                s.springPos = s.springPos.add(s.springVel.scale(h));
                if (c.lookMode() == UltCamera.LOOK_TARGET) return s.springPos;
                Player caster = s.caster();
                Vec3 cp = caster != null ? caster.getPosition(pt).add(0, 1.2, 0) : s.arena.toWorld(0, 1.2, 0);
                return cp.lerp(s.springPos, 0.5);
            }
            default:
                return s.arena.toWorld(c.look());
        }
    }

    public static Vec3 targetLive(UltState s, float pt) {
        float tt = s.t(pt);
        if (dev.baranhan.flashmod.ultimate.UltimateScript.targetScripted(tt) && tt >= UltimatePhase.LAUNCH_T) {
            return s.targetScripted(tt).add(0, s.targetH * 0.5D, 0); // betikteki yer (onizlemede de dogru)
        }
        LivingEntity t = s.target();
        if (t != null && t.isAlive()) {
            s.lastTargetPos = t.getPosition(pt);
            return s.lastTargetPos.add(0, t.getBbHeight() * 0.5D, 0);
        }
        return s.lastTargetPos.add(0, s.targetH * 0.5D, 0);
    }

    /** ARENA kamera carpismasi: icerri aninda, disari yumusak (yari omur 0.15 s); pivot'a cok yakinsa yukari. */
    private static Vec3 collide(UltState s, Vec3 pivot, Vec3 desired, float dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return desired;
        Vec3 d = desired.subtract(pivot);
        double full = d.length();
        if (full < 1.0E-4) return desired;
        Vec3 dir = d.scale(1.0 / full);
        HitResult hit = mc.level.clip(new ClipContext(pivot, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        double allowed = hit.getType() == HitResult.Type.MISS ? full : Math.max(0.05, hit.getLocation().distanceTo(pivot) - 0.25);
        if (s.collDist < 0 || Math.abs(full - s.prevFull) > 1.0) s.collDist = allowed;
        else if (allowed < s.collDist) s.collDist = allowed;
        else s.collDist += (allowed - s.collDist) * (1.0 - Math.exp(-dt * 0.6931 / 0.15));
        s.prevFull = full;
        Vec3 p = pivot.add(dir.scale(Math.min(full, s.collDist)));
        if (Math.min(full, s.collDist) < 0.6) p = p.add(0, 0.4, 0);
        return p;
    }

    // ---------------------------------------------------------------- firlatilan hedefin izi

    /** Havaya kalkis ve cakilma boyunca hedefin betikteki konumu bizim iz sistemimize node olarak eklenir (UltWorldFx cizer). */
    private static void recordTargetTrail(UltState s, float t, long now) {
        dev.baranhan.flashmod.client.ClientSpeedsters.Entry e = s.targetTrail;
        LivingEntity tg = s.target();
        boolean on = s.abortAt < 0 && t >= UltimatePhase.LAUNCH_T && t <= UltimatePhase.SLAM_T && (tg != null || s.preview);
        if (on) {
            Vec3 p = s.targetScripted(t);
            double dx = 0, dz = 0, dy = 0;
            if (e.hasLast) { dx = p.x - e.lastX; dy = p.y - e.lastY; dz = p.z - e.lastZ; }
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz), h = Math.sqrt(dx * dx + dz * dz);
            if (h > 1.0E-3) { e.dirX = (float) (dx / h); e.dirZ = (float) (dz / h); }
            e.odometer += dist;
            e.lastX = p.x; e.lastY = p.y; e.lastZ = p.z;
            e.hasLast = true;
            e.prevSpeed = e.speed;
            e.speed = Math.max(1F, (float) dist);
            if (dist > 0.05) {
                e.nodes.addFirst(new dev.baranhan.flashmod.client.ClientSpeedsters.TrailNode(p.x, p.y, p.z, -e.dirZ, 0F, e.dirX,
                        0F, 1F, 0F, e.odometer, 1F, now, s.targetH / 1.8F));
            }
        }
        e.trailLifeOverride = 14;
        while (e.nodes.size() > 60) e.nodes.removeLast();
        while (!e.nodes.isEmpty() && now - e.nodes.peekLast().tick() > e.trailLife() + 1) e.nodes.removeLast();
    }

    // ---------------------------------------------------------------- poz (HumanoidModelMixin -> BlitzAnim)

    /** Bu oyuncu bir oturumda caster ise o anki pozu out'a yazar; vanilla'ya karsi agirlik doner (0 = yok). */
    public static float poseFor(Player p, float pt, float[] out) {
        if (UltRender.overridePose != null && UltRender.overrideEntity == p.getId()) {
            System.arraycopy(UltRender.overridePose, 0, out, 0, UltPoses.N);
            return 1F;
        }
        UltState s = forCaster(p.getId());
        if (s == null || s.abortAt >= 0) return 0F;
        float t = s.t(pt);
        if (t < 0F || t >= UltimatePhase.DURATION) return 0F;
        return UltPoses.pose(t, out, s.smallTarget());
    }
}
