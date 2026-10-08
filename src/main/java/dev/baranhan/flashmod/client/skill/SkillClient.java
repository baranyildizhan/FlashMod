package dev.baranhan.flashmod.client.skill;

import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.BlitzClient;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.FlashKeys;
import dev.baranhan.flashmod.client.Tornado;
import dev.baranhan.flashmod.client.WallRun;
import dev.baranhan.flashmod.client.particle.FlashParticles;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.SkillCastPacket;
import dev.baranhan.flashmod.network.SkillFxPacket;
import dev.baranhan.flashmod.network.SkillSyncPacket;
import dev.baranhan.flashmod.speed.SkillLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Istemci tarafi yetenekler: senkron (kinetik yuk, bekleme sureleri), gorsel olay listeleri (SkillRender/SkillGhosts
 * cizer), tuslar, yerel oyuncunun atilmasi ve geri sarma oynatimi (hareket istemci-otoriter), kinetik yumruk pozu.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SkillClient {
    /** Bir hizcinin yetenek durumu (istemci). */
    public static final class Info {
        public float kinetic;
        public long decoyReadyAt, rewindReadyAt, blitzReadyAt, ultReadyAt;
        public int decoyTotal, rewindTotal, blitzTotal = 1, ultTotal = 1;
        public long punchAt = Long.MIN_VALUE / 2;
        public float punchPower;
    }

    /** Kinetik yumruk isabeti. */
    public static final class Hit {
        public final double x, y, z;
        public final float dx, dz, power;
        public final long at, seed;
        public final int core, glow;

        Hit(SkillFxPacket m, long at) {
            this.x = m.x; this.y = m.y; this.z = m.z;
            this.dx = m.dx; this.dz = m.dz; this.power = m.power;
            this.at = at;
            this.seed = Double.doubleToLongBits(m.x * 31 + m.z) ^ at;
            this.core = m.core; this.glow = m.glow;
        }
    }

    /** Tek seferlik patlama/cozulme (kalinti parcalanmasi, solmasi, geri sarma bitisi) ve kalinti -> hizci simsegi. */
    public static final class Burst {
        public final byte type;
        public final UUID player;
        public final double x, y, z;
        public final float dx, dz;
        public final long at, seed;
        public final int core, glow;

        Burst(byte type, UUID player, Vec3 at, float power, long time, int core, int glow) {
            this.type = type;
            this.player = player;
            this.x = at.x; this.y = at.y; this.z = at.z;
            this.dx = power; this.dz = 0F;
            this.at = time;
            this.seed = Double.doubleToLongBits(at.x * 13 + at.z) ^ (time * 37L);
            this.core = core; this.glow = glow;
        }

        Burst(SkillFxPacket m, long at) {
            this.type = m.type;
            this.player = m.player;
            this.x = m.x; this.y = m.y; this.z = m.z;
            this.dx = m.dx; this.dz = m.dz;
            this.at = at;
            this.seed = Double.doubleToLongBits(m.x * 17 + m.y * 7 + m.z) ^ (at * 31L);
            this.core = m.core; this.glow = m.glow;
        }
    }

    /** Bir hizcinin geri sarmasi: yol (baslangic konumuna goreli, yeniden eskiye), sure, renkler. */
    public static final class Rewind {
        public final UUID player;
        public final double ox, oy, oz;
        public final float[] path;
        public final int count, ticks;
        public final long start;
        public final int core, glow;
        public long endAt = -1;
        /** Yerel oynatim sayaci (sadece kendi geri sarman). */
        int localTick;

        Rewind(SkillFxPacket m, long start) {
            this.player = m.player;
            this.ox = m.x; this.oy = m.y; this.oz = m.z;
            this.path = m.path;
            this.count = Math.max(1, m.path.length / 6);
            this.ticks = Math.max(1, m.ticks);
            this.start = start;
            this.core = m.core; this.glow = m.glow;
        }

        /** 0..1 zamandan yol ilerlemesine: hafif yumusak, tepe hiz ortalamanin ~1.15 kati (cok oyunculu hiz kontrolu). */
        public static float ease(float u) {
            u = Mth.clamp(u, 0F, 1F);
            return 0.7F * u + 0.3F * u * u * (3F - 2F * u);
        }

        /** Ilerleme 0..1 (zamana gore). */
        public float progress(float t) {
            return ease((t - start) / ticks);
        }

        /** Yol uzerinde kesirli indeks -> dunya konumu ve bakis. out = {x, y, z, yaw, pitch, hiz}. */
        public void sample(float idx, double[] out) {
            idx = Mth.clamp(idx, 0F, count - 1);
            int i = Math.min(count - 2, (int) idx);
            if (count < 2) i = 0;
            int j = Math.min(count - 1, i + 1);
            float f = count < 2 ? 0F : idx - i;
            out[0] = ox + Mth.lerp(f, path[i * 6], path[j * 6]);
            out[1] = oy + Mth.lerp(f, path[i * 6 + 1], path[j * 6 + 1]);
            out[2] = oz + Mth.lerp(f, path[i * 6 + 2], path[j * 6 + 2]);
            float y0 = path[i * 6 + 3], y1 = path[j * 6 + 3];
            out[3] = y0 + Mth.wrapDegrees(y1 - y0) * f;
            out[4] = Mth.lerp(f, path[i * 6 + 4], path[j * 6 + 4]);
            out[5] = Mth.lerp(f, path[i * 6 + 5], path[j * 6 + 5]);
        }

        public boolean ended() {
            return endAt >= 0;
        }
    }

    private static final Map<UUID, Info> INFO = new HashMap<>();
    public static final List<Hit> HITS = new ArrayList<>();
    public static final List<Burst> BURSTS = new ArrayList<>();
    public static final Map<UUID, Rewind> REWINDS = new HashMap<>();

    // yerel oyuncu
    @Nullable private static Rewind local;
    private static int dashTick = -1;
    private static double dashX, dashZ;
    private static float dashDist;
    private static long dashAt = Long.MIN_VALUE / 2;
    /** Geri sarma bitisinin beyaz parlamasi icin (yerel). */
    private static long localRewindEndAt = Long.MIN_VALUE / 2;
    /** Yerel kamera sarsintisi (kinetik yumruk, geri sarma bitisi). */
    private static long shakeAt = Long.MIN_VALUE / 2;
    private static float shakePower;
    private static final float[] DASH_PROFILE = {0.34F, 0.30F, 0.22F, 0.14F};
    private static final double[] TMP = new double[6];

    private SkillClient() {}

    public static Info info(UUID id) {
        return INFO.computeIfAbsent(id, k -> new Info());
    }

    @Nullable
    public static Info infoOrNull(UUID id) {
        return INFO.get(id);
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    // ---------------------------------------------------------------- paketler

    public static void handleSync(SkillSyncPacket m) {
        Info i = info(m.player);
        long now = now();
        i.kinetic = m.kinetic;
        i.decoyReadyAt = now + m.decoyLeft;
        i.decoyTotal = Math.max(1, m.decoyTotal);
        i.rewindReadyAt = now + m.rewindLeft;
        i.rewindTotal = Math.max(1, m.rewindTotal);
        i.blitzReadyAt = now + m.blitzLeft;
        i.blitzTotal = Math.max(1, m.blitzTotal);
        i.ultReadyAt = now + m.ultLeft;
        i.ultTotal = Math.max(1, m.ultTotal);
    }

    public static void handleFx(SkillFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        boolean self = mc.player != null && mc.player.getUUID().equals(m.player);
        switch (m.type) {
            case SkillFxPacket.KINETIC_HIT -> {
                HITS.add(new Hit(m, now));
                Info i = info(m.player);
                i.punchAt = now;
                i.punchPower = m.power;
                if (self) { shakeAt = now; shakePower = 0.35F + 0.9F * m.power; }
                hitSparks(level, m);
            }
            case SkillFxPacket.DECOY_CAST -> {
                BURSTS.add(new Burst(m, now));
                castSparks(level, m);
                if (self) {
                    dashTick = 0;
                    dashX = m.dx;
                    dashZ = m.dz;
                    dashDist = m.power;
                    dashAt = now;
                }
            }
            case SkillFxPacket.DECOY_SHATTER, SkillFxPacket.DECOY_FADE -> {
                BURSTS.add(new Burst(m, now));
                endSparks(level, m, m.type == SkillFxPacket.DECOY_SHATTER);
            }
            case SkillFxPacket.REWIND_START -> {
                Rewind r = new Rewind(m, now);
                REWINDS.put(m.player, r);
                if (self) local = r;
            }
            case SkillFxPacket.REWIND_END -> {
                Rewind r = REWINDS.get(m.player);
                if (r != null) r.endAt = now;
                BURSTS.add(new Burst(m, now));
                if (self) {
                    local = null;
                    localRewindEndAt = now;
                    shakeAt = now;
                    shakePower = 0.8F;
                }
            }
            default -> {}
        }
    }

    /** Simsek mizragi isabeti: bizim simsek patlamamiz (eskiden vanilla yildirim dusuyordu). dx = sarj 0..1. */
    public static final byte SPEAR = 20;

    public static void spearBurst(Vec3 at, float charge, int core, int glow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BURSTS.add(new Burst(SPEAR, new UUID(0, 0), at, charge, mc.level.getGameTime(), core, glow));
    }

    // ---------------------------------------------------------------- parcaciklar

    private static void hitSparks(ClientLevel level, SkillFxPacket m) {
        RandomSource r = level.random;
        float k = m.power;
        int n = (int) ((14 + 46 * k) * Math.max(FlashParticles.factor(), 0.35F));
        for (int i = 0; i < n; i++) { // darbe yonunde koni
            double sp = 0.25 + r.nextDouble() * (0.4 + 0.9 * k);
            double sx = (r.nextDouble() - 0.5) * 0.9, sy = (r.nextDouble() - 0.3) * 0.8, sz = (r.nextDouble() - 0.5) * 0.9;
            FlashParticles.spark(level, m.x, m.y, m.z, (m.dx + sx) * sp, sy * sp + 0.05, (m.dz + sz) * sp,
                    m.core, m.glow, 6 + r.nextInt(10), 0.9F + 0.6F * k);
        }
    }

    private static void castSparks(ClientLevel level, SkillFxPacket m) {
        RandomSource r = level.random;
        int n = (int) (26 * Math.max(FlashParticles.factor(), 0.35F));
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, sp = 0.05 + r.nextDouble() * 0.2;
            FlashParticles.spark(level, m.x + Math.cos(a) * 0.3, m.y + r.nextDouble() * 1.8, m.z + Math.sin(a) * 0.3,
                    Math.cos(a) * sp - m.dx * 0.2, (r.nextDouble() - 0.4) * 0.12, Math.sin(a) * sp - m.dz * 0.2,
                    m.core, m.glow, 6 + r.nextInt(8), 0.8F);
        }
    }

    private static void endSparks(ClientLevel level, SkillFxPacket m, boolean shatter) {
        RandomSource r = level.random;
        int n = (int) ((shatter ? 90 : 45) * Math.max(FlashParticles.factor(), 0.35F));
        for (int i = 0; i < n; i++) {
            double px = m.x + (r.nextDouble() - 0.5) * 0.7, py = m.y + r.nextDouble() * 1.85, pz = m.z + (r.nextDouble() - 0.5) * 0.7;
            double vx, vy, vz;
            if (shatter) { // govdeden her yone savrulan parcalar
                double dx = px - m.x, dz = pz - m.z, l = Math.sqrt(dx * dx + dz * dz) + 0.05;
                double sp = 0.25 + r.nextDouble() * 0.55;
                vx = dx / l * sp;
                vz = dz / l * sp;
                vy = (r.nextDouble() - 0.2) * 0.45;
            } else { // goruntu yukari dogru cozulur
                vx = (r.nextDouble() - 0.5) * 0.04;
                vz = (r.nextDouble() - 0.5) * 0.04;
                vy = 0.04 + r.nextDouble() * 0.08;
            }
            FlashParticles.spark(level, px, py, pz, vx, vy, vz, m.core, m.glow, (shatter ? 8 : 14) + r.nextInt(10),
                    shatter ? 1.1F : 0.7F);
        }
    }

    // ---------------------------------------------------------------- tuslar

    /** ClientEvents'ten her istemci tick'inde (ekran kapaliyken). */
    public static void pollKeys(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        while (FlashKeys.DECOY.consumeClick()) {
            if (canCast(p)) {
                double[] d = inputDir(p);
                FlashNetwork.sendToServer(new SkillCastPacket(SkillLogic.DECOY, (float) d[0], (float) d[1]));
            }
        }
        while (FlashKeys.REWIND.consumeClick()) {
            if (canCast(p) && !WallRun.isActive()) FlashNetwork.sendToServer(new SkillCastPacket(SkillLogic.REWIND, 0F, 0F));
        }
    }

    private static boolean canCast(LocalPlayer p) {
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        return e != null && e.active && !e.blitz && !BlitzClient.localActive() && !Tornado.isActive() && local == null
                && !dev.baranhan.flashmod.client.ultimate.UltDirector.inputLocked();
    }

    /** Girdi yonu (WASD, dunya yatayi); girdi yoksa {0,0} -> sunucu bakisin sagina atar. */
    private static double[] inputDir(LocalPlayer p) {
        float fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
        if (Math.abs(fwd) + Math.abs(left) < 0.01F) return new double[]{0, 0};
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double ix = left * Mth.cos(yaw) - fwd * Mth.sin(yaw);
        double iz = fwd * Mth.cos(yaw) + left * Mth.sin(yaw);
        double l = Math.sqrt(ix * ix + iz * iz);
        return l < 1.0E-4 ? new double[]{0, 0} : new double[]{ix / l, iz / l};
    }

    // ---------------------------------------------------------------- yerel hareket (atilma, geri sarma)

    /** LOWEST: diger hareket mantigindan (hava momentumu, su ustu, duvar) sonra son soz bizde. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer p)) return;
        Rewind r = local;
        if (r != null) {
            r.localTick++;
            float u = Math.min(1F, r.localTick / (float) r.ticks);
            r.sample(Rewind.ease(u) * (r.count - 1), TMP);
            p.setPos(TMP[0], TMP[1], TMP[2]);
            p.setYRot((float) TMP[3]);
            p.setXRot((float) TMP[4]);
            p.setYHeadRot((float) TMP[3]);
            p.yBodyRot = (float) TMP[3];
            p.setDeltaMovement(Vec3.ZERO);
            p.resetFallDistance();
            if (r.localTick >= r.ticks + 10) local = null; // sunucu bitisi gelmediyse (kayip paket) birak
            return;
        }
        if (dashTick >= 0) {
            if (dashTick < DASH_PROFILE.length) {
                double sp = DASH_PROFILE[dashTick] * dashDist;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(dashX * sp, Math.max(v.y, 0D), dashZ * sp);
                dashTick++;
            } else {
                Vec3 v = p.getDeltaMovement(); // atilma bitti: hizin cogunu kaybet (kayip gitmesin)
                p.setDeltaMovement(v.x * 0.35, v.y, v.z * 0.35);
                dashTick = -1;
            }
        }
    }

    /** Geri sarma ve atilma sirasinda yuruyus girdisi yok. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (local == null && dashTick < 0) return;
        Input in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (local != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Geri sarmada hafif uzaklasan FOV, atilmada kisa bir itki. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getPlayer() != mc.player || mc.level == null) return;
        float pt = mc.getFrameTime();
        float k = 1F + 0.12F * rewindAmount(pt);
        float d = (mc.level.getGameTime() - dashAt + pt) / 6F;
        if (d >= 0F && d < 1F) k += 0.1F * Mth.sin(d * Mth.PI);
        if (k != 1F) event.setNewFovModifier(event.getNewFovModifier() * k);
    }

    /** Geri sarmada kamera hafif sallanir (bant geri sarilirken titreyen goruntu); yumruk/bitis darbesinde sarsilir. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        float t = (float) ((now() + pt) * 1.3);
        float a = rewindAmount(pt);
        if (a > 0.001F) event.setRoll(event.getRoll() + (Mth.sin(t * 1.9F) * 1.6F + Mth.sin(t * 5.3F) * 0.5F) * a);
        float s = now() + pt - shakeAt;
        if (s >= 0F && s < 10F) {
            float k = (1F - s / 10F);
            float amp = shakePower * k * k;
            event.setPitch(event.getPitch() + Mth.sin(s * 5.1F) * 1.6F * amp);
            event.setYaw(event.getYaw() + Mth.sin(s * 3.7F + 1F) * 1.1F * amp);
            event.setRoll(event.getRoll() + Mth.sin(s * 4.3F + 2F) * 2.2F * amp);
        }
    }

    // ---------------------------------------------------------------- gorsel durum sorgulari

    /** 0..1 yerel geri sarma ekran efekti (shader + ekran ustu), kenarlarda 4 tick'te acilip kapanir. */
    public static float rewindAmount(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0F;
        Rewind r = REWINDS.get(mc.player.getUUID());
        if (r == null) return 0F;
        float t = mc.level.getGameTime() + pt;
        float in = Mth.clamp((t - r.start) / 4F, 0F, 1F);
        float out = r.ended() ? 1F - Mth.clamp((t - r.endAt) / 5F, 0F, 1F) : 1F;
        return in * out;
    }

    /** Yerel geri sarma bitisinin parlamasi 0..1. */
    public static float rewindFlash(float pt) {
        float t = now() + pt - localRewindEndAt;
        if (t < 0F || t > 6F) return 0F;
        float k = 1F - t / 6F;
        return k * k;
    }

    public static boolean rewinding(UUID id) {
        Rewind r = REWINDS.get(id);
        return r != null && !r.ended();
    }

    @Nullable
    public static Rewind localRewind() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : REWINDS.get(mc.player.getUUID());
    }

    // ---------------------------------------------------------------- poz (HumanoidModelMixin -> BlitzAnim)

    /**
     * Kinetik yumruk: kol bir an geriye kurulur, sonra omuz one donerken tam ileri firlar, kisa sure uzanik kalir ve
     * toparlanir; govde vurusla birlikte burulur. Vanilla saldiri salinimini ezer.
     */
    public static void applyPose(Player p, HumanoidModel<?> model, float pt) {
        Info i = INFO.get(p.getUUID());
        Minecraft mc = Minecraft.getInstance();
        if (i == null || mc.level == null) return;
        float s = mc.level.getGameTime() + pt - i.punchAt;
        if (s < 0F || s >= 9F) return;
        boolean left = p.getMainArm() == HumanoidArm.LEFT;
        ModelPart arm = left ? model.leftArm : model.rightArm, off = left ? model.rightArm : model.leftArm;
        float side = left ? -1F : 1F;
        float k = 0.6F + 0.4F * i.punchPower;
        float ax, twist, w;
        if (s < 1.2F) {            // vurus: kol ileri firlar
            float u = s / 1.2F;
            u = 1F - (1F - u) * (1F - u);
            ax = Mth.lerp(u, -0.4F, -1.62F);
            twist = Mth.lerp(u, -0.15F, 0.42F) * k;
            w = 1F;
        } else if (s < 4F) {       // uzanik kalir (darbe)
            ax = -1.62F + 0.06F * Mth.sin((s - 1.2F) * 6F) * i.punchPower;
            twist = 0.42F * k;
            w = 1F;
        } else {                   // toparlanma
            float u = (s - 4F) / 5F;
            w = 1F - u * u * (3F - 2F * u);
            ax = -1.62F;
            twist = 0.42F * k;
        }
        arm.xRot = Mth.lerp(w, arm.xRot, ax);
        arm.yRot = Mth.lerp(w, arm.yRot, -0.12F * side);
        arm.zRot = Mth.lerp(w, arm.zRot, 0.05F * side);
        off.xRot = Mth.lerp(w, off.xRot, 0.55F);
        off.zRot = Mth.lerp(w, off.zRot, -0.15F * side);
        model.body.yRot = Mth.lerp(w, model.body.yRot, twist * side);
        model.head.yRot = Mth.lerp(w * 0.5F, model.head.yRot, -twist * 0.4F * side);
        model.hat.copyFrom(model.head);
    }

    // ---------------------------------------------------------------- temizlik

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = now();
        HITS.removeIf(h -> now - h.at > 20);
        BURSTS.removeIf(b -> now - b.at > 24);
        Iterator<Rewind> it = REWINDS.values().iterator();
        while (it.hasNext()) {
            Rewind r = it.next();
            if ((r.ended() && now - r.endAt > 20) || now - r.start > r.ticks + 60) it.remove();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INFO.clear();
        HITS.clear();
        BURSTS.clear();
        REWINDS.clear();
        local = null;
        dashTick = -1;
    }
}
