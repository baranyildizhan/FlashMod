package dev.baranhan.flashmod.ultimate;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Finalin hareket betigi (sunucu + istemci ortak, deterministik). Ilk vurusta hedef ileri kayar; ikinci vurus
 * (aparkat) hedefi yukari-ileri bir dogru boyunca firlatir, hedef yavas cekimde asili kalir; Flash onun ARKASINDA
 * (dogrunun uzantisinda, ustte) belirir ve yumrukla hedefi neredeyse ayni dogru boyunca geri, yere cakar; sonra
 * kendisi de krater yanina iner.
 * Butun konumlar hedefin baslangic konumuna (stasis tabani) gore {ileri, yukari} (arena ileri yonu boyunca).
 * Sunucu hedefi/caster'i bu konumlara tasir; istemci ayni egrileri yumusak cizer (ag gecikmesi gorunmez).
 */
public final class UltimateScript {
    /** fly = 1 iken hedefin asili kaldigi nokta (itilmis konuma gore ileri, yukari). */
    public static final double FLY_FWD = 5.4, FLY_UP = 6.9;
    /** Cakilma noktasi: itilmis konumun biraz gerisi (Flash'in durdugu yer; bos oldugu bilinir). */
    public static final double SLAM_BACK = 0.4;
    /** Flash'in inis noktasi (itilmis konuma gore ileri): kraterin ote yaninda, kratere bakar. */
    public static final double LAND_FWD = 2.2;
    /** Engel varsa ucus olcegi bu degerlerle kucultulur (tavan, agac). */
    private static final float[] FLY_TRY = {1F, 0.85F, 0.7F, 0.55F, 0.4F};

    private UltimateScript() {}

    /** Bir oturumun final geometrisi (push: ilk vurus itmesi, fly: ucus olcegi, h: hedef boyu). */
    public static final class Path {
        public final float push, fly, h;
        /** Asili kalma (HIT3) noktasi, cakilma noktasi (ileri; yukari 0). */
        public final double pa, py, ga;
        /** Cakilma noktasindan asili kalma noktasina birim yon {ileri, yukari}. */
        public final double ux, uy;
        /** Flash: yumruk anindaki, kurulmus ve belirdigi andaki ayak konumlari; inis noktasi. */
        public final double hitA, hitY, cockA, cockY, appearA, appearY, landA;

        public Path(float push, float fly, float h) {
            this.push = push;
            this.fly = fly;
            this.h = h;
            pa = push + FLY_FWD * fly;
            py = FLY_UP * fly;
            ga = push - SLAM_BACK;
            double dx = pa - ga, dy = py, l = Math.sqrt(dx * dx + dy * dy);
            ux = dx / l;
            uy = dy / l;
            // yumruk hedefin sirtina (merkezin dogru uzantisindaki yuzu); govde 50 derece one egik, kol dogru
            // boyunca: yumruk, ayaklara gore ~(0.86 geri, 0.64 yukari)
            double cx = pa + ux * 0.3, cy = py + h * 0.5 + uy * 0.3;
            hitA = cx + 0.86;
            hitY = cy - 0.64;
            cockA = hitA + ux * 0.55;
            cockY = hitY + uy * 0.55;
            appearA = cockA + ux * 0.6;
            appearY = cockY + uy * 0.6 + 0.25;
            landA = push + LAND_FWD;
        }

        /** Hedefin {ileri, yukari} ofseti. */
        public double[] target(float t) {
            if (t < UltimatePhase.HIT1) return new double[]{0, 0};
            if (t < UltimatePhase.LAUNCH_T) return new double[]{push * UltimatePhase.pushEase(t), 0};
            if (t < UltimatePhase.HITSTOP3_END) {
                double s = flyEase(Math.min(t, UltimatePhase.HIT3));
                return new double[]{Mth.lerp(s, push, pa), py * s};
            }
            if (t < UltimatePhase.SLAM_T) {
                double w = (t - UltimatePhase.HITSTOP3_END) / (double) (UltimatePhase.SLAM_T - UltimatePhase.HITSTOP3_END);
                double k = 0.55 * w + 0.45 * w * w; // yumrukla hizli baslar, hizlanarak cakilir
                return new double[]{Mth.lerp(k, pa, ga), Mth.lerp(k, py, 0.0)};
            }
            return new double[]{ga, 0};
        }

        /** 0..1 firlatma ilerlemesi: hizli kalkis, uzun yavas cekim asili kalma (hafif suzulme surer). */
        public static double flyEase(float t) {
            double u = Mth.clamp((t - UltimatePhase.LAUNCH_T) / (double) (UltimatePhase.HIT3 - UltimatePhase.LAUNCH_T), 0, 1);
            double v = 1 - u;
            return 0.94 * (1 - v * v * v * v) + 0.06 * u;
        }

        /** Flash'in {ileri, yukari} ofseti (AIR_BLINK'ten itibaren gecerli). */
        public double[] caster(float t) {
            if (t < UltimatePhase.AIR_BLINK + 4F) { // belirir, kurulu konuma suzulur
                double k = outCubic((t - UltimatePhase.AIR_BLINK) / 4F);
                return new double[]{Mth.lerp(k, appearA, cockA), Mth.lerp(k, appearY, cockY)};
            }
            if (t < UltimatePhase.HIT3 - 2F) { // kurulu bekler (hafif nefes)
                double b = 0.03 * Math.sin((t - UltimatePhase.AIR_BLINK) * 0.9);
                return new double[]{cockA, cockY + b};
            }
            if (t < UltimatePhase.HIT3) { // atilma
                double k = (t - (UltimatePhase.HIT3 - 2F)) / 2.0;
                k *= k;
                return new double[]{Mth.lerp(k, cockA, hitA), Mth.lerp(k, cockY, hitY)};
            }
            if (t < UltimatePhase.HITSTOP3_END) return new double[]{hitA, hitY};
            if (t < UltimatePhase.SLAM_T) return follow(t);
            double[] f = follow(UltimatePhase.SLAM_T);
            float w = Mth.clamp((t - UltimatePhase.SLAM_T) / (float) (UltimatePhase.CASTER_LAND - UltimatePhase.SLAM_T), 0F, 1F);
            double sw = w * w * (3 - 2 * w);
            return new double[]{Mth.lerp(sw, f[0], landA), f[1] * (1.0 - w * w)}; // yercekimi gibi hizlanan dusus
        }

        /** Yumruktan sonra: kol dogru boyunca biraz daha gider, havada kisa bir an asili kalir. */
        private double[] follow(float t) {
            double k = outCubic((t - UltimatePhase.HITSTOP3_END) / 5F);
            double sink = 0.3 * smooth((t - UltimatePhase.HITSTOP3_END) / 6F);
            return new double[]{hitA - ux * 0.7 * k, hitY - uy * 0.7 * k - sink};
        }

        public Vec3 targetOffset(double fx, double fz, float t) {
            double[] p = target(t);
            return new Vec3(fx * p[0], p[1], fz * p[0]);
        }

        public Vec3 casterOffset(double fx, double fz, float t) {
            double[] p = caster(t);
            return new Vec3(fx * p[0], p[1], fz * p[0]);
        }
    }

    /** Hedef bu t'de betikle mi tutuluyor (stasis: ilk vurustan, yere cakilip yattigi yerden kalkana kadar). */
    public static boolean targetScripted(float t) {
        return t >= UltimatePhase.HIT1 && t < UltimatePhase.TARGET_FREE;
    }

    /**
     * Sunucu: ucus yolu (hedefin kutusu dogru boyunca, Flash'in havadaki yeri) bos olan en buyuk olcek.
     * Hicbiri bos degilse en kucugu (gecici olarak bloklarin icinden gecer; sinematik bozulmaz).
     */
    public static float flyScale(LivingEntity target, double fx, double fz, float push) {
        AABB box = target.getBoundingBox();
        AABB flash = new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        for (float fly : FLY_TRY) {
            Path p = new Path(push, fly, target.getBbHeight());
            boolean ok = true;
            for (int i = 1; i <= 10 && ok; i++) {
                double k = i / 10.0, a = Mth.lerp(k, push, p.pa), y = p.py * k;
                ok = target.level().noCollision(target, box.move(fx * a, y, fz * a));
            }
            if (ok) {
                Vec3 base = target.position();
                ok = target.level().noCollision(flash.move(base.x + fx * p.cockA, base.y + p.cockY, base.z + fz * p.cockA));
            }
            if (ok) return fly;
        }
        return FLY_TRY[FLY_TRY.length - 1];
    }

    private static double smooth(float u) {
        u = Mth.clamp(u, 0F, 1F);
        return u * u * (3F - 2F * u);
    }

    private static double outCubic(float u) {
        u = Mth.clamp(u, 0F, 1F);
        float v = 1F - u;
        return 1F - v * v * v;
    }
}
