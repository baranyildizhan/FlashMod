package dev.baranhan.flashmod.ultimate;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Finalin hareket betigi (sunucu + istemci ortak, deterministik): ilk vurustaki itme, ikinci vurustan sonra hedefin
 * yavas cekimde havaya yukselip asili kalmasi, havadaki yumrukla yere cakilmasi ve Flash'in havadaki konumu.
 * Sunucu hedefi/caster'i bu konumlara tasir; istemci ayni egrileri yumusak cizer (ag gecikmesi gorunmez).
 * Butun ofsetler hedefin baslangic konumuna (stasis tabani) gore dunya koordinatinda.
 */
public final class UltimateScript {
    /** Hedefin havaya yukselecegi yukseklik (blok) ve asili kalirken ek suzulme. */
    public static final double RISE = 7.0, HOVER = 0.6;
    /** Havadayken hedefin ileri kaymasi; arkadan gelen yumrukla cakilirken biraz geri (caster tarafina degil) gelir. */
    private static final double DRIFT_AIR = 0.8, DRIFT_SLAM = 0.3;
    /** Flash'in hedefe gore havadaki yeri: hedefin arkasinda (ileri yonde) ve ustunde. */
    public static final double BEHIND = 1.3, ABOVE = 2.0;
    private static final float HANG_T = 306F;

    private UltimateScript() {}

    private static float smooth(float u) {
        u = Mth.clamp(u, 0F, 1F);
        return u * u * (3F - 2F * u);
    }

    private static float outCubic(float u) {
        u = Mth.clamp(u, 0F, 1F);
        float v = 1F - u;
        return 1F - v * v * v;
    }

    /** Hedef bu t'de betikle mi hareket ediyor (stasis: ilk vurustan yere carpana kadar). */
    public static boolean targetScripted(float t) {
        return t >= UltimatePhase.HIT1 && t <= UltimatePhase.SLAM_T;
    }

    /** Hedefin yerden yuksekligi: hizli kalkis (OUT_CUBIC), yavas cekim gibi asili kalma, yumrukla cakilma. */
    public static double targetUp(float t) {
        if (t < UltimatePhase.LAUNCH_T) return 0.0;
        if (t < HANG_T) return RISE * outCubic((t - UltimatePhase.LAUNCH_T) / (HANG_T - UltimatePhase.LAUNCH_T));
        double top = RISE + HOVER * smooth((t - HANG_T) / (UltimatePhase.HIT3 - HANG_T));
        if (t < UltimatePhase.HITSTOP3_END) return top;
        float u = Mth.clamp((t - UltimatePhase.HITSTOP3_END) / (float) (UltimatePhase.SLAM_T - UltimatePhase.HITSTOP3_END), 0F, 1F);
        return (RISE + HOVER) * (1.0 - u * u); // IN_QUAD dusus
    }

    /** Hedefin ileri kaymasi (itme dahil). */
    public static double targetAhead(float t, float push) {
        double a = push * UltimatePhase.pushEase(t);
        if (t < UltimatePhase.LAUNCH_T) return a;
        if (t < UltimatePhase.HITSTOP3_END) return a + DRIFT_AIR * outCubic((t - UltimatePhase.LAUNCH_T) / 24F);
        float u = Mth.clamp((t - UltimatePhase.HITSTOP3_END) / (float) (UltimatePhase.SLAM_T - UltimatePhase.HITSTOP3_END), 0F, 1F);
        double air = DRIFT_AIR * outCubic((UltimatePhase.HITSTOP3_END - UltimatePhase.LAUNCH_T) / 24F);
        return a + Mth.lerp(u, air, DRIFT_SLAM);
    }

    /** Hedefin taban konumuna gore ofseti (fx, fz: arena ileri yonu). */
    public static Vec3 targetOffset(double fx, double fz, float push, float t) {
        double a = targetAhead(t, push);
        return new Vec3(fx * a, targetUp(t), fz * a);
    }

    /**
     * Flash'in havadaki/inisteki konumu (hedef tabanina gore ofset). AIR_BLINK'ten itibaren gecerli: hedefin
     * arkasinda ve ustunde asili, yumruktan sonra hedefin arkasina iner (CASTER_LAND).
     */
    public static Vec3 casterOffset(double fx, double fz, float push, float t) {
        float hold = Math.min(t, UltimatePhase.HITSTOP3_END);
        Vec3 tg = targetOffset(fx, fz, push, hold);
        Vec3 air = tg.add(fx * BEHIND, ABOVE, fz * BEHIND);
        if (t < UltimatePhase.HITSTOP3_END) {
            // belirince hafifce suzulerek yaklasir
            float k = 1F - outCubic((t - UltimatePhase.AIR_BLINK) / 6F);
            return air.add(fx * 0.6 * k, 0.4 * k, fz * 0.6 * k);
        }
        double land = targetAhead(UltimatePhase.SLAM_T, push) + BEHIND;
        Vec3 ground = new Vec3(fx * land, 0.0, fz * land);
        float u = Mth.clamp((t - UltimatePhase.HITSTOP3_END) / (float) (UltimatePhase.CASTER_LAND - UltimatePhase.HITSTOP3_END), 0F, 1F);
        double y = air.y * (1.0 - u * u);
        return new Vec3(Mth.lerp(u, air.x, ground.x), y, Mth.lerp(u, air.z, ground.z));
    }
}
