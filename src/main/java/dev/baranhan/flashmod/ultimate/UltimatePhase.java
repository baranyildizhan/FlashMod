package dev.baranhan.flashmod.ultimate;

import net.minecraft.util.Mth;

/**
 * Ultimate ("Dunya Turu Yumrugu") zaman cizelgesi: tek dogruluk kaynagi. Butun tick'ler mutlak ultimate tick'i.
 * Sunucu ve istemci ayni tablolari kullanir; durationScale yalnizca t hesaplanirken uygulanir.
 * Baska siniflar sabit sayi yerine buradaki faz baslangiclarina goreli zaman kullanir.
 */
public enum UltimatePhase {
    ACTIVATE(0, 6, Space.ARENA),
    WINDUP(6, 30, Space.ARENA),
    DEPART(30, 52, Space.ARENA),
    VOID(52, 122, Space.SCENE),
    OCEAN(122, 182, Space.SCENE),
    ORBIT(182, 262, Space.SCENE),
    TUNNEL(262, 290, Space.SCENE),
    IMPACT(290, 294, Space.ARENA),
    /** Aparkatla hedef yukari-ileri ucar, yavas cekimde asili kalir. */
    LAUNCH(294, 310, Space.ARENA),
    /** Flash hedefin arkasinda (ucus dogrusunun uzantisinda) belirir, yumrukla ayni dogru boyunca geri yere cakar. */
    AIR(310, 327, Space.ARENA),
    /** Yere carpma simsek patlamasi, Flash'in inisi. */
    SLAM(327, 340, Space.ARENA),
    RECOVER(340, 354, Space.ARENA);

    public enum Space { ARENA, SCENE }

    // ---------------------------------------------------------------- kritik olaylar
    public static final int DURATION = 354;
    public static final int BLINK = 1;
    /** Caster'in gercek govdesi bu andan itibaren proxy yolunda (DEPART). */
    public static final int HIDE_BODY = 30;
    /** Ilk vurus: hedef PUSH_END'e kadar ileri kayar. */
    public static final int HIT1 = 33;
    public static final int PUSH_END = 43;
    /** Sahneler (dunya yerine overlay) bu aralikta. */
    public static final int SCENE_START = 52, SCENE_END = 290;
    public static final int RETURN_TP = 289;
    public static final int HIT2 = 290;
    public static final int HITSTOP_END = 294;
    /** Hedef yukari-ileri ucar (sunucu ve istemci ayni betikle: UltimateScript). */
    public static final int LAUNCH_T = 294;
    /** Flash havada, hedefin arkasinda belirir. */
    public static final int AIR_BLINK = 310;
    /** Havadaki yumruk; HITSTOP3_END'e kadar donma, sonra hedef yere cakilir. */
    public static final int HIT3 = 318;
    public static final int HITSTOP3_END = 321;
    /** Hedef yere carpar: simsek patlamasi, stasis biter. */
    public static final int SLAM_T = 327;
    /** Flash yere iner. */
    public static final int CASTER_LAND = 332;
    /** Yerde yatan hedef kalkar; stasis biter. */
    public static final int TARGET_FREE = 346;
    public static final float HITSTOP_FREEZE = 291F, HITSTOP3_FREEZE = 319F;
    /** Arena: caster -> hedef mesafesi (varsayilan). */
    public static final float ARENA_DISTANCE = 2.5F;
    /** Ilk vurusta hedefin en fazla ileri kaymasi (blok); sunucu carpismaya gore kisaltir. */
    public static final float PUSH_MAX = 1.6F;

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

    /** Gorsel zaman: iki hit-stop boyunca pozlar/parcaciklar donar. */
    public static float visual(float t) {
        if (t >= HIT2 && t < HITSTOP_END) return Math.min(t, HITSTOP_FREEZE);
        if (t >= HIT3 && t < HITSTOP3_END) return Math.min(t, HITSTOP3_FREEZE);
        return t;
    }

    /** Caster'in gercek govdesi dunyada gorunur mu (sahneler boyunca gizli; sahne/yorunge devralir). */
    public static boolean bodyHidden(float t) {
        return t >= SCENE_START && t < HIT2;
    }

    /** 0..1 ilk vurus itisi (OUT_CUBIC): hedef HIT1 -> PUSH_END arasi ileri kayar. */
    public static float pushEase(float t) {
        float u = Mth.clamp((t - HIT1) / (float) (PUSH_END - HIT1), 0F, 1F);
        float v = 1F - u;
        return 1F - v * v * v;
    }

    // ---------------------------------------------------------------- beyaz flas egrisi

    private static final float[][] FLASH = {
            {48, 52, 1}, {52, 56, -1}, {118, 122, 1}, {122, 125, -1}, {178, 182, 1}, {182, 185, -1},
            {258, 262, 1}, {262, 265, -1}, {287, 290, 1}, {290, 292, -1}};

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
