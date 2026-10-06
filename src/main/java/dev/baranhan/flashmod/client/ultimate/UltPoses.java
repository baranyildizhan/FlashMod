package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * Ultimate poz sistemi (bizim Blitz yaklasimimizla ayni): uzuvlar HumanoidModelMixin -> setupAnim TAIL'de,
 * tum govde egilmesi/alcalmasi renderer'da PoseStack kok donusumu ile (UltRender). Acilar derece.
 * Konvansiyon: kol/bacak xRot negatif = one/yukari; rootPitch pozitif = one egilme (kalca pivotu).
 */
public final class UltPoses {
    public static final int RP = 0, RYAW = 1, RROLL = 2, RY = 3, RZ = 4,
            HX = 5, HY = 6, HZ = 7, RAX = 8, RAY = 9, RAZ = 10, LAX = 11, LAY = 12, LAZ = 13,
            RLX = 14, RLY = 15, RLZ = 16, LLX = 17, LLY = 18, LLZ = 19, N = 20;

    private static float[] p(float rp, float ryaw, float ry, float hx, float hy, float hz, float rax, float ray, float raz,
                             float lax, float lay, float laz, float rlx, float rly, float rlz, float llx, float lly, float llz) {
        return new float[]{rp, ryaw, 0, ry, 0, hx, hy, hz, rax, ray, raz, lax, lay, laz, rlx, rly, rlz, llx, lly, llz};
    }

    // ---------------------------------------------------------------- katalog (rehber 8.4)
    static final float[] IDLE_LOCK = p(0, 0, 0, 0, 0, 0, 0, 0, 5, 0, 0, -5, 0, 0, 0, 0, 0, 0);
    static final float[] CHARGE_ROAR = p(14, -10, -0.12F, -18, 0, 0, 45, 0, 12, -55, 25, 0, 18, 0, 4, -22, 0, -4);
    static final float[] DASH_LEAN = p(38, 0, -0.05F, -35, 0, 0, 70, 0, 8, -75, 0, -8, 55, 0, 0, -60, 0, 0);
    static final float[] DASH_STRIKE = p(30, 25, -0.05F, -30, -20, 0, -90, -15, 0, 50, 0, -10, 50, 0, 0, -55, 0, 0);
    static final float[] SPRINTER_CROUCH = p(58, 0, -0.52F, -42, 0, 0, -58, 0, 6, -58, 0, -6, -72, 0, 4, 38, 0, -4);
    static final float[] SPRINTER_SET = p(48, 0, -0.30F, -38, 0, 0, -50, 0, 6, -50, 0, -6, -55, 0, 0, 55, 0, 0);
    static final float[] PUSH_OFF = p(42, 0, -0.18F, -30, 0, 0, 60, 0, 10, -80, 0, -6, 40, 0, 0, -75, 0, 0);
    static final float[] CHARGE_CROUCH = p(34, -12, -0.35F, -8, 10, 0, -30, 0, -15, -60, 0, 10, -50, 0, 6, 30, 0, -6);
    static final float[] PUNCH_COCK = p(12, -28, -0.10F, -6, 18, 0, -145, 0, 28, -65, 20, 0, -25, 0, 6, 25, 0, -6);
    static final float[] PUNCH_RELEASE = p(18, 24, -0.12F, -10, -18, 0, -95, -12, 0, 45, 0, -12, 32, 0, 0, -40, 0, 0);
    static final float[] FINISH_POSE = p(-4, 30, 0, 5, -20, 0, -150, 0, -22, 22, 0, 28, 8, 0, 0, -62, 0, -6);
    /** SPRINT isareti: tablo yerine dongu. */
    private static final float[] SPRINT = new float[N];
    private static final float[] SPRINT_FAST = new float[N];

    /** {start, end, blend} + poz. */
    private static final float[][] ROWS = {
            {0, 4, 0}, {4, 22, 3}, {22, 25, 2}, {25, 28, 1}, {28, 40, 2}, {40, 55, 0}, {55, 61, 2}, {61, 66, 0},
            {66, 82, 2}, {140, 146, 0}, {146, 150, 3}, {150, 158, 3}, {158, 162, 1}, {162, 196, 6}};
    private static final float[][] POSES = {
            IDLE_LOCK, CHARGE_ROAR, DASH_LEAN, DASH_STRIKE, DASH_LEAN, SPRINTER_CROUCH, PUSH_OFF, SPRINTER_SET,
            SPRINT, SPRINT_FAST, CHARGE_CROUCH, PUNCH_COCK, PUNCH_RELEASE, FINISH_POSE};

    private static final float[] A = new float[N], B = new float[N];

    private UltPoses() {}

    /**
     * t aninda caster pozu -> out. Donus: vanilla'ya karsi agirlik (1 = tam bizim poz, 0 = poz yok).
     * Hit-stop'ta gorsel zaman donar. smallTarget: kucuk hedeflerde asagi vurus.
     */
    public static float pose(float t, float[] out, boolean smallTarget) {
        float vt = UltimatePhase.visual(t);
        int r = -1;
        for (int i = 0; i < ROWS.length; i++) if (vt >= ROWS[i][0] && vt < ROWS[i][1]) r = i;
        float weight = 1F;
        if (r < 0) {
            if (vt >= 196F && vt < 200F) { // vanilla'ya 4 tick'te don
                r = ROWS.length - 1;
                weight = 1F - smooth((vt - 196F) / 4F);
            } else if (vt >= 82F && vt < 140F) {
                r = 8; // sahnede model yok (okyanus/yorunge); son kosu pozu
            } else {
                return 0F;
            }
        }
        rowPose(r, vt, out);
        if (r + 1 < ROWS.length) {
            float ns = ROWS[r + 1][0], bl = ROWS[r + 1][2];
            if (bl > 0F && vt >= ns - bl && vt < ns) {
                rowPose(r + 1, ns, B);
                System.arraycopy(out, 0, A, 0, N);
                lerp(A, B, smooth((vt - (ns - bl)) / bl), out);
            }
        }
        if (smallTarget && r == 12) out[RAX] = -60F;
        return weight;
    }

    private static void rowPose(int r, float t, float[] out) {
        float[] src = POSES[r];
        if (src == SPRINT || src == SPRINT_FAST) {
            sprint(t, src == SPRINT_FAST, out);
            return;
        }
        System.arraycopy(src, 0, out, 0, N);
        if (r == 5) out[RY] += 0.01F * Mth.sin((t - 40F) / 20F * 1.5F * ((float) (Math.PI * 2.0))); // comelmede nefes
    }

    /** Analitik kosu dongusu (geri sarmaya uygun): VOID'da 66->78 arasi 2.5 Hz'den 9 Hz'e, tunelde 10 Hz. */
    public static void sprint(float t, boolean fast, float[] out) {
        double ph;
        if (fast) {
            ph = Math.PI * 2 * 10.0 * Math.max(0F, t - 140F) / 20.0;
        } else {
            double tau = Math.max(0F, t - 66F) / 20.0, T = 0.6, f0 = 2.5, f1 = 9.0;
            ph = tau < T ? Math.PI * 2 * (f0 * tau + (f1 - f0) * tau * tau / (2 * T))
                    : Math.PI * 2 * (f0 * T + (f1 - f0) * T / 2 + f1 * (tau - T));
        }
        float s = (float) Math.sin(ph);
        java.util.Arrays.fill(out, 0F);
        out[RP] = 30F + 4F * (float) Math.sin(2 * ph);
        out[RY] = -0.04F + 0.05F * Math.abs(s);
        out[HX] = -25F;
        out[RLX] = 70F * s;
        out[LLX] = -70F * s;
        out[RAX] = -85F * s;
        out[LAX] = 85F * s;
        out[RAZ] = 8F;
        out[LAZ] = -8F;
    }

    public static void lerp(float[] a, float[] b, float u, float[] out) {
        for (int i = 0; i < N; i++) out[i] = a[i] + (b[i] - a[i]) * u;
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0F, 1F);
        return u * u * (3F - 2F * u);
    }

    /** Uzuv pozunu modele uygula (vanilla ile 'w' kadar karisim). Katmanlari (sapka) yeniden kopyalar. */
    public static void apply(HumanoidModel<?> m, float[] pz, float w) {
        float d = ((float) Math.PI / 180F);
        set(m.head, pz[HX] * d, pz[HY] * d, pz[HZ] * d, w);
        set(m.rightArm, pz[RAX] * d, pz[RAY] * d, pz[RAZ] * d, w);
        set(m.leftArm, pz[LAX] * d, pz[LAY] * d, pz[LAZ] * d, w);
        set(m.rightLeg, pz[RLX] * d, pz[RLY] * d, pz[RLZ] * d, w);
        set(m.leftLeg, pz[LLX] * d, pz[LLY] * d, pz[LLZ] * d, w);
        if (w >= 0.999F) { // vanilla'nin comelme vb. ofsetlerini temizle
            m.body.xRot = 0F;
            m.body.y = 0F;
            m.rightArm.y = 2F;
            m.leftArm.y = 2F;
            m.rightLeg.z = 0.1F;
            m.leftLeg.z = 0.1F;
            m.rightLeg.y = 12F;
            m.leftLeg.y = 12F;
            m.head.y = 0F;
        }
        m.hat.copyFrom(m.head);
    }

    private static void set(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }
}
