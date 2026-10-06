package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.speed.SpeedsterData;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Istemcideki tum hizcilarin gorsel durumu (iz noktalari, hiz, zamanlayicilar). */
public final class ClientSpeedsters {
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    /** Seviye carpani 1x iken sprint zemin hizi (blok/tick). */
    public static final float SPRINT_BPT = 0.286F;
    public static final float TORNADO_RADIUS = 3F;
    /** 343 m/s = 17.15 blok/tick */
    public static final float TORNADO_MACH1 = 17.15F;

    private ClientSpeedsters() {}

    /**
     * Iz omurgasi: oyuncunun her tick biraktigi nokta. side = hareket yonune dik yatay vektor.
     * odo double: astronomik hizlarda float odometre hassasiyetini kaybeder, dalgalar kirilirdi.
     */
    public record TrailNode(double x, double y, double z, float sideX, float sideY, float sideZ,
                            float upX, float upY, float upZ, double odo, float power, long tick, float height) {}

    public static final class Entry {
        public final long seed;
        public boolean synced;
        public boolean active;
        public boolean phasing, phasingPrev;
        public int level = SpeedsterData.DEFAULT_LEVEL;
        public int core = SpeedsterData.DEFAULT_CORE;
        public int glow = SpeedsterData.DEFAULT_GLOW;

        public final ArrayDeque<TrailNode> nodes = new ArrayDeque<>();
        public boolean hasLast;
        public double lastX, lastY, lastZ;
        /** blok/tick, yumusatilmis (3B ve yatay) */
        public float speed, prevSpeed, hSpeed;
        public double odometer;
        public float dirX = 1F, dirZ = 0F;
        /** Iz cercevesi: yan ve yukari vektorleri (yerde yatay yan + dunya yukarisi; duvarda N yukari). */
        public float sideX = 0F, sideY = 0F, sideZ = 1F, upX = 0F, upY = 1F, upZ = 0F;
        public float mdx = 1F, mdy = 0F, mdz = 0F;
        public boolean waterRunning;
        /** Bacak animasyonu icin kendi hiz degerimiz (vanilla 1'de kirpiyor). */
        public float limbSpeed;

        public long activatedAt = Long.MIN_VALUE / 2;
        public long deactivatedAt = Long.MIN_VALUE / 2;
        public long lastBoom = Long.MIN_VALUE / 2;
        public int boomStage;
        public float boomPower;
        public double boomX, boomY, boomZ;
        public float boomDirX, boomDirZ;
        public long phaseStartedAt = Long.MIN_VALUE / 2;

        /** Tornado: merkez, teget hiz (blok/tick), aci (radyan, sarmalanmis), donus yonu (+1/-1). */
        public boolean tornado, tornadoPrev;
        public double tcx, tcy, tcz;
        public float tSpeed, tAngle, tPrevAngle;
        public int tDir = 1;

        /** Blitz sinematigi (sunucudan): sahne cercevesi, baslangic tick'i, hedef. */
        public boolean blitz;
        public long blitzStart;
        public int blitzTarget = -1;
        public dev.baranhan.flashmod.speed.BlitzPath.Frame blitzFrame;
        public int blitzHitIdx, blitzKeyIdx;
        /** Yetenekler (sunucudan): enerji, mizrak sarji, agir cekim, duvarda kosma. */
        public float energy, charge;
        public boolean charging, slowmo;
        public long chargeStartAt = Long.MIN_VALUE / 2, throwAt = Long.MIN_VALUE / 2;
        public boolean wallRun;
        public float wallNx, wallNz = 1F;
        /** 0..1 duvara donme karisimi (tick basina guncellenir, render'da enterpole). */
        public float wallBlend, prevWallBlend;
        /** Duvarda govde yonu (yerel cercevede, yerdeki gibi hareket yonune doner). */
        public float wallBodyYaw;
        public boolean wallBodyInit;

        public float wallBlend(float pt) {
            float b = prevWallBlend + (wallBlend - prevWallBlend) * pt;
            return b * b * (3F - 2F * b);
        }

        /** Loop seslerinin konumu (kukla / tornado yayi dahil, SpeedFx her tick yazar). */
        public double soundX, soundY, soundZ;
        /** Darbe sarsintisi (kamera icin). */
        public long impactAt = Long.MIN_VALUE / 2;
        public float impactPower;

        public float tornadoAngle(float pt) {
            return tPrevAngle + (tAngle - tPrevAngle) * pt;
        }

        /** 0..1 tornado gucu (Mach 1 = 1). */
        public float tornadoPower() {
            return tornado ? Mth.clamp(tSpeed / TORNADO_MACH1, 0F, 1F) : 0F;
        }

        Entry(UUID id) {
            this(id.getMostSignificantBits() ^ (id.getLeastSignificantBits() * 31L));
        }

        Entry(long seed) {
            this.seed = seed;
        }

        /** Sentetik izler icin iz omru (tick); 0 = seviyeye gore. */
        public int trailLifeOverride;

        /** 0..1, ekran efektleri / FOV / aura icin. Seviye 10'un normal hizinda ~1. */
        public float intensity(float partialTick) {
            if (!active) return 0F;
            return Mth.clamp(Mth.lerp(partialTick, prevSpeed, speed) / 2.4F, 0F, 1F);
        }

        /** 0..1, logaritmik: 3 b/t -> 0, ~30 -> 0.4, ~300 -> 0.8, ~1000 -> 1. Overdrive hissi icin. */
        public float overdrive(float partialTick) {
            if (!active) return 0F;
            float s = Mth.lerp(partialTick, prevSpeed, speed);
            if (s <= 3F) return 0F;
            return Mth.clamp((float) Math.log10(s / 3F) / 2.5F, 0F, 1F);
        }

        /** Seviyenin normal sprint hizina gore kac kat hizli (HUD). */
        public float overdriveFactor() {
            double scale;
            try {
                scale = FlashServerConfig.SPEED_SCALE.get();
            } catch (IllegalStateException e) {
                scale = 1.0D; // config henuz yuklenmediyse
            }
            float base = SPRINT_BPT * (float) (1.0D + SpeedsterData.speedBonus(level) * scale);
            return hSpeed / base;
        }

        /** Ses duvari sarsintisi 0..~1.5, ~0.8 sn'de soner. */
        public float boomShake(long now, float partialTick) {
            float t = (now - lastBoom) + partialTick;
            if (t < 0F || t > 16F) return 0F;
            float k = 1F - t / 16F;
            return boomPower * k * k;
        }

        /** Iz omru (tick). Yuksek seviye = daha uzun kuyruk. */
        public int trailLife() {
            return trailLifeOverride > 0 ? trailLifeOverride : 10 + level;
        }
    }

    /** Hiz -> iz parlakligi. Yuruyuste bile hafif bir iz, kosarken tam guc. */
    public static float powerFor(float speed) {
        if (speed < 0.06F) return 0F;
        return Mth.clamp(0.3F + 0.7F * (speed - 0.15F) / 0.6F, 0.3F, 1F);
    }

    /**
     * Oyuncuya bagli olmayan, kayitsiz bir iz durumu (ultimate sahneleri, firlatilan hedef). Ayni iz cizicisiyle
     * cizilir; node'lari cagiran doldurur.
     */
    public static Entry synthetic(long seed, int core, int glow) {
        Entry e = new Entry(seed);
        e.synced = true;
        e.active = true;
        e.level = 10;
        e.core = core;
        e.glow = glow;
        e.speed = e.prevSpeed = 3F;
        return e;
    }

    public static Entry get(UUID id) {
        return ENTRIES.get(id);
    }

    public static Entry getOrCreate(UUID id) {
        return ENTRIES.computeIfAbsent(id, Entry::new);
    }

    public static Map<UUID, Entry> all() {
        return ENTRIES;
    }

    public static boolean isActive(UUID id) {
        Entry e = ENTRIES.get(id);
        return e != null && e.active;
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
