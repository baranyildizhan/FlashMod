package dev.baranhan.flashmod.client.skill;

import dev.baranhan.flashmod.client.ultimate.UltPoses;

/**
 * Yeteneklerin donmus pozlari (UltPoses bicimi: derece, kok + uzuvlar; UltRender.drawModel ile cizilir).
 * Zaman kalintisi ve geri sarma yankilari: hizcinin o anki hali, zamanda donmus.
 */
public final class SkillPoses {
    /** Dovus durusu: hafif one egik, iki yumruk onde, ayaklar acik (Blitz'deki "hazir" durusu). */
    public static final float[] STANCE = pose(9, -10, -0.06F, 4, 8, 0, -72, -17, 7, -80, 20, -6, 17, 0, 5, -20, 0, -5);
    /** Kosu karesi: govde one, kollar ve bacaklar genis acili (UltPoses.sprint'in en dinamik ani). */
    public static final float[] RUN = pose(34, 0, 0.01F, -28, 0, 0, -75, 0, 8, 75, 0, -8, 61, 0, 0, -61, 0, 0);
    /** Kosu karesinin ayna hali (yankilarda adim adim degissin). */
    public static final float[] RUN_B = pose(33, 0, 0.02F, -27, 0, 0, 72, 0, 8, -72, 0, -8, -58, 0, 0, 58, 0, 0);

    private SkillPoses() {}

    private static float[] pose(float rp, float ryaw, float ry, float hx, float hy, float hz, float rax, float ray, float raz,
                                float lax, float lay, float laz, float rlx, float rly, float rlz, float llx, float lly, float llz) {
        float[] o = new float[UltPoses.N];
        o[UltPoses.RP] = rp;
        o[UltPoses.RYAW] = ryaw;
        o[UltPoses.RY] = ry;
        o[UltPoses.HX] = hx;
        o[UltPoses.HY] = hy;
        o[UltPoses.HZ] = hz;
        o[UltPoses.RAX] = rax;
        o[UltPoses.RAY] = ray;
        o[UltPoses.RAZ] = raz;
        o[UltPoses.LAX] = lax;
        o[UltPoses.LAY] = lay;
        o[UltPoses.LAZ] = laz;
        o[UltPoses.RLX] = rlx;
        o[UltPoses.RLY] = rly;
        o[UltPoses.RLZ] = rlz;
        o[UltPoses.LLX] = llx;
        o[UltPoses.LLY] = lly;
        o[UltPoses.LLZ] = llz;
        return o;
    }
}
