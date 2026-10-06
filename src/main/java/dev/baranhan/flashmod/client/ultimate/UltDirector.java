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
    private static final float[] SHAKE_EVENTS = {16, 0.2F, 26, 0.45F, 55, 0.3F, 66, 0.25F, 93, 0.55F, 138, 0.3F,
            148, 0.35F, 158, 1.0F, 162, 0.6F};

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
        } else {
            s.crashes.add(new UltState.Crash(new Vec3(m.x, m.y, m.z), m.nx, m.ny, m.nz, m.type == UltimateEventPacket.SLAM,
                    mc.level.getGameTime()));
            if (s.full && mc.player != null) s.trauma = Math.min(1F, s.trauma + (mc.player.distanceToSqr(m.x, m.y, m.z) > 400 ? 0.3F : 0.6F));
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
            if (t > (s.preview ? 205F : 230F) || aborted || (!s.preview && s.caster() == null && t > 4F)) {
                UltSounds.stopAll(s);
                it.remove();
                continue;
            }
            if (s.abortAt < 0) UltSounds.tick(s, s.lastTickT, t);
            s.lastTickT = t;
        }
        UltState f = full();
        float ft = f == null ? -1F : f.t(0F);
        BlitzLock.ultimateLocked = (f != null && f.abortAt < 0 && ft >= 0F && ft < 200F && !freeCam) || stasisLocal && f != null;
    }

    /** Yerel oyuncunun hareketi kilitli mi (tam sinematik ya da stasis'teki hedef). */
    public static boolean inputLocked() {
        UltState f = full();
        if (f != null && f.abortAt < 0) {
            float t = f.t(0F);
            if (t >= 0F && t < 198F && !freeCam) return true;
        }
        for (UltState s : SESSIONS.values()) {
            if (s.skipped && s.abortAt < 0 && s.t(0F) < 200F) return true;
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
        return t >= 0F && t < 196F;
    }

    public static boolean controlsCamera() {
        UltState f = full();
        if (f == null || freeCam) return false;
        float t = f.t(0F);
        return t >= 0F && t < 200F;
    }

    /** Su an bir SCENE fazi mi oynuyor (dunya yerine sahne cizilecek). */
    @Nullable
    public static UltState scene(float pt) {
        UltState f = full();
        if (f == null || f.abortAt >= 0 || freeCam) return null;
        float t = f.t(pt);
        return t >= 40F && t < 158F ? f : null;
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
        if (t < 0F || t >= 200F) return null;
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

        UltCamera.State c = UltCamera.evaluate(s.tracks, Math.max(t, 4F));
        Vec3 pos, look;
        boolean arena = c.space() == UltimatePhase.Space.ARENA;
        if (arena) {
            pos = s.arena.toWorld(c.pos());
            look = resolveLook(s, c, t, pt, dt);
            pos = collide(s, look, pos, dt);
        } else {
            s.sceneCam = c.pos();
            pos = c.pos();
            look = c.look();
        }
        Vec3 dir = look.subtract(pos);
        float yaw = UltCamera.yawTo(dir), pitch = UltCamera.pitchTo(dir);
        float camFov = c.fov(), camRoll = c.roll();
        Vec3 out = arena ? pos : eye; // sahnede vanilla kamera gozde kalir (chunk'lar / ses dinleyicisi)

        if (t < 4F) { // oyun kamerasindan giris
            float u = UltCamera.Ease.IN_OUT_CUBIC.apply(t / 4F);
            out = s.snapPos.lerp(out, u);
            yaw = s.snapYaw + Mth.wrapDegrees(yaw - s.snapYaw) * u;
            pitch = Mth.lerp(u, s.snapPitch, pitch);
            camFov = Mth.lerp(u, s.snapFov, camFov);
            camRoll *= u;
        } else if (t >= 188F) { // canli birinci sahisa donus
            float u = UltCamera.Ease.IN_OUT_CUBIC.apply((t - 188F) / 12F);
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
        return new CameraModes.Result(out.x, out.y, out.z, yaw, Mth.clamp(pitch, -90F, 90F));
    }

    private static float baseTrauma(float t) {
        if (t >= 4F && t < 22F) return 0.10F + 0.35F * (t - 4F) / 18F;
        if (t >= 78F && t < 86F) return 0.3F;
        if (t >= 98F && t < 108F) return 0.25F;
        if (t >= 150F && t < 158F) return 0.4F + 0.5F * (t - 150F) / 8F;
        return 0F;
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
                if (s.springPos == null || t < 166.5F) { s.springPos = tp; s.springVel = Vec3.ZERO; }
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
        if (t < 0F || t >= 200F) return 0F;
        return UltPoses.pose(t, out, s.smallTarget());
    }
}
