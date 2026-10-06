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
    /** SPRINT isareti: tablo yerine dongu (kadans zamanla degisir, bkz. runPhase). */
    private static final float[] SPRINT = new float[N];

    /** {start, end, blend} + poz. Zamanlar UltimatePhase'e goreli. */
    private static final float[][] ROWS = {
            {0, 6, 0}, {6, 30, 3}, {30, 34, 2}, {34, 38, 1}, {38, 262, 2},
            {262, 270, 0}, {270, 276, 3}, {276, 290, 3}, {290, 294, 1}, {294, 330, 6}};
    private static final float[][] POSES = {
            IDLE_LOCK, CHARGE_ROAR, DASH_LEAN, DASH_STRIKE, SPRINT,
            SPRINT, CHARGE_CROUCH, PUNCH_COCK, PUNCH_RELEASE, FINISH_POSE};
    /** Vanilla'ya donus penceresi (RECOVER sonu). */
    private static final float FADE_START = 330F, FADE_END = UltimatePhase.DURATION;

    /**
     * Kosu kadansi (adim/sn) dugumleri {t, hz}; aralarda dogrusal. DEPART'ta hizli kacis, VOID'de surekli kosu ve
     * hizlanma (bekleme/comelme yok), okyanusta sabit, tunelde en hizli.
     */
    private static final float[][] CADENCE = {
            {30, 5.5F}, {52, 5.0F}, {70, 6.5F}, {96, 10.5F}, {122, 9.0F}, {262, 9.0F}, {270, 11F}};

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
            if (vt >= FADE_START && vt < FADE_END) { // vanilla'ya don
                r = ROWS.length - 1;
                weight = 1F - smooth((vt - FADE_START) / (FADE_END - FADE_START));
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
        if (smallTarget && r == 8) out[RAX] = -60F;
        return weight;
    }

    private static void rowPose(int r, float t, float[] out) {
        float[] src = POSES[r];
        if (src == SPRINT) {
            sprint(t, out);
            return;
        }
        System.arraycopy(src, 0, out, 0, N);
    }

    /** Kosu fazi (radyan): kadansin analitik integrali -> adimlar hic sicramaz, geri sarmaya uygun. */
    public static double runPhase(float t) {
        double ph = 0;
        float[][] K = CADENCE;
        if (t <= K[0][0]) return Math.PI * 2 * K[0][1] * (t - K[0][0]) / 20.0;
        for (int i = 0; i + 1 < K.length; i++) {
            float t0 = K[i][0], t1 = K[i + 1][0];
            if (t <= t0) break;
            float te = Math.min(t, t1);
            float u = (te - t0) / (t1 - t0);
            float hzEnd = K[i][1] + (K[i + 1][1] - K[i][1]) * u;
            ph += Math.PI * 2 * (K[i][1] + hzEnd) * 0.5 * (te - t0) / 20.0;
        }
        float[] last = K[K.length - 1];
        if (t > last[0]) ph += Math.PI * 2 * last[1] * (t - last[0]) / 20.0;
        return ph;
    }

    /** Analitik kosu dongusu; govde one egik, kollar/bacaklar genis acili (hizli kosu). */
    public static void sprint(float t, float[] out) {
        double ph = runPhase(t);
        float s = (float) Math.sin(ph);
        java.util.Arrays.fill(out, 0F);
        out[RP] = 32F + 4F * (float) Math.sin(2 * ph);
        out[RY] = -0.04F + 0.06F * Math.abs(s);
        out[HX] = -28F;
        out[RLX] = 72F * s;
        out[LLX] = -72F * s;
        out[RAX] = -88F * s;
        out[LAX] = 88F * s;
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
