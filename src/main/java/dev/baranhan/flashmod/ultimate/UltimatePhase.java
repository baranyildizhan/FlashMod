package dev.baranhan.flashmod.ultimate;

import net.minecraft.util.Mth;

/**
 * Ultimate ("Dunya Turu Yumrugu") zaman cizelgesi: tek dogruluk kaynagi. Butun tick'ler mutlak ultimate tick'i.
 * Sunucu ve istemci ayni tablolari kullanir; durationScale yalnizca t hesaplanirken uygulanir.
 */
public enum UltimatePhase {
    ACTIVATE(0, 4, Space.ARENA),
    WINDUP(4, 22, Space.ARENA),
    DEPART(22, 40, Space.ARENA),
    VOID(40, 86, Space.SCENE),
    OCEAN(86, 120, Space.SCENE),
    ORBIT(120, 140, Space.SCENE),
    TUNNEL(140, 158, Space.SCENE),
    IMPACT(158, 166, Space.ARENA),
    LAUNCH(166, 188, Space.ARENA),
    RECOVER(188, 200, Space.ARENA);

    public enum Space { ARENA, SCENE }

    // ---------------------------------------------------------------- kritik olaylar
    public static final int DURATION = 200;
    public static final int BLINK = 1;
    public static final int HIDE_BODY = 22;
    public static final int HIT1 = 26;
    public static final int RETURN_TP = 157;
    public static final int HIT2 = 158;
    public static final int HITSTOP_END = 162;
    public static final int LAUNCH_T = 162;
    public static final int CRASH_END = 192;
    public static final float HITSTOP_FREEZE = 159F;
    /** Arena: caster -> hedef mesafesi (varsayilan). */
    public static final float ARENA_DISTANCE = 2.5F;

    public final int start, end;
    public final Space space;

    UltimatePhase(int start, int end, Space space) {
        this.start = start;
        this.end = end;
        this.space = space;
    }

    public float local(float t) {
        return Mth.clamp((t - start) / (float) (end - start), 0F, 1F);
    }

    public static UltimatePhase at(float t) {
        for (UltimatePhase p : values()) if (t < p.end) return p;
        return RECOVER;
    }

    /** Gorsel zaman: hit-stop (158-162) boyunca pozlar/parcaciklar 159'da donar. */
    public static float visual(float t) {
        return t >= HIT2 && t < HITSTOP_END ? Math.min(t, HITSTOP_FREEZE) : t;
    }

    /** Caster'in gercek govdesi dunyada gorunur mu (22-158 arasi gizli; proxy/sahne devralir). */
    public static boolean bodyHidden(float t) {
        return t >= HIDE_BODY && t < HIT2;
    }

    // ---------------------------------------------------------------- beyaz flas egrisi

    private static final float[][] FLASH = {
            {36, 40, 1}, {40, 44, -1}, {82, 86, 1}, {86, 89, -1}, {116, 120, 1}, {120, 123, -1},
            {138, 140, 1}, {140, 143, -1}, {155, 158, 1}, {158, 160, -1}};

    /** 0..1 tam ekran beyaz flas alfasi. Faz gecisleri hep alfa=1 aninda olur (kesmeler gorunmez). */
    public static float flash(float t) {
        for (float[] f : FLASH) {
            if (t >= f[0] && t < f[1]) {
                float u = (t - f[0]) / (f[1] - f[0]);
                if (f[2] > 0) return u * u;                    // IN_QUAD
                float v = 1F - u;
                return v * v * v;                              // OUT_CUBIC (1 -> 0)
            }
        }
        return 0F;
    }
}
