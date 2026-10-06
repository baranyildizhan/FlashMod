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
    /** Aparkat hazirligi: alcak, sag yumruk asagi-geride, sol kol on koruma, sol ayak onde. */
    static final float[] UPPER_COCK = p(20, -30, -0.20F, -14, 22, 0, 35, 0, 14, -75, 20, -4, 30, 0, 5, -48, 0, -5);
    /** Aparkat: govde yukari uzanir ve hafif geriye, yumruk yukari-ileri, parmak uclarinda. */
    static final float[] UPPERCUT = p(-8, 28, 0.06F, -32, -16, 0, -158, -8, -6, 42, 0, -16, 28, 0, 4, -30, 0, -4);
    /** Aparkatin devami: kol yukarida, bas ucan hedefi izler. */
    static final float[] UPPER_RISE = p(-6, 18, 0.03F, -44, -8, 0, -165, 0, -10, 30, 0, -20, 18, 0, 3, -22, 0, -3);
    /** Asili kalan hedefi izler: bas yukari, yumruk hala havada, agirlik geride. */
    static final float[] WATCH_UP = p(-5, 10, 0, -46, -6, 0, -150, 0, -18, 18, 0, 22, 6, 0, 2, -12, 0, -4);
    /** Sicramaya hazirlik: derin comelme, iki kol geride, gozler hedefte. */
    static final float[] CROUCH_SPRING = p(28, 0, -0.42F, -58, 0, 0, 48, 0, 16, 48, 0, -16, -68, 0, 7, -52, 0, -7);
    /** Havada, hedefin arkasinda: one egik, sag yumruk geride kurulu, sol kol hedefe nisan, sag diz toplu. */
    static final float[] AIR_COCK = p(36, -32, 0, 14, 28, 0, 58, 0, 22, -98, 16, -8, -58, 0, 6, 34, 0, -6);
    /** Yumruk ucus dogrusu boyunca asagi-geri; govde 50 derece one, sol kol geride, bacaklar ters. */
    static final float[] AIR_PUNCH = p(50, 30, 0, 4, -24, 0, -88, -8, -4, 62, 0, -18, 36, 0, 4, -36, 0, -4);
    /** Yumruk sonrasi: kol uzanik kalir, bas cakilan hedefi izler. */
    static final float[] AIR_FOLLOW = p(44, 16, 0, 22, -12, 0, -82, -4, 6, 40, 0, -26, 24, 0, 6, -28, 0, -6);
    /** Dusus: govde dik, kollar yanlara acik denge, sag diz yukarida, gozler yerde. */
    static final float[] AIR_DROP = p(10, 0, 0, 28, 0, 0, -38, 0, 72, -38, 0, -72, -42, 0, 4, 8, 0, -4);
    /** Kahraman inisi: tek diz yerde, sag yumruk yere dayali, sol kol geride acik, bas kraterde. */
    static final float[] LAND = p(42, -10, -0.58F, -26, 8, 0, -44, 0, -6, 46, 0, -26, -96, 0, 4, 52, 0, -4);
    /** Bitis: dik, hafif yan donuk, yerdeki rakibe bakar; kollar gevsek. */
    static final float[] FINISH = p(-2, 16, 0, 10, -10, 0, -18, 0, 16, 8, 0, -12, -6, 0, 2, 8, 0, -2);

    // ---------------------------------------------------------------- hedef (insansi modeller: oyuncu, zombi...)
    /** Aparkat: bas geriye savrulur, kollar acilir. */
    static final float[] T_HIT = p(0, 0, 0, -38, 0, 0, -50, 0, 40, -50, 0, -40, 20, 0, 6, -15, 0, -6);
    /** Ucus / asili kalma: kollar bas ustunde savrulmus, bir diz bukuk. */
    static final float[] T_FLY = p(0, 0, 0, -30, 0, 0, -150, 0, 30, -135, 0, -35, -35, 0, 10, 25, 0, -10);
    /** Sirttan yumruk: bas one kirilir, kollar ve bacaklar kamci gibi geride. */
    static final float[] T_STRUCK = p(0, 0, 0, 35, 0, 0, -175, 0, 12, -175, 0, -12, 25, 0, 14, 35, 0, -14);
    /** Yerde sirtustu, kollar ve bacaklar acik. */
    static final float[] T_LIE = p(0, 0, 0, 0, 0, 0, -10, 0, 70, -10, 0, -70, 0, 0, 18, 0, 0, -18);

    /** SPRINT isareti: tablo yerine dongu (kadans zamanla degisir, bkz. runPhase). */
    private static final float[] SPRINT = new float[N];

    /**
     * {start, end, blend} + poz (+ istege bagli TO: satir boyunca POSES -> TO yumusak gecis). blend: bir sonraki satira
     * baslangicindan once kac tick'te karisilir. Hit-stop'larda gorsel zaman donar (pozlar da donar).
     */
    private static final float[][] ROWS = {
            {0, 6, 0}, {6, 30, 3}, {30, 32, 1}, {32, 35, 1}, {35, 262, 2},
            {262, 270, 0}, {270, 276, 3}, {276, 290, 2}, {290, 294, 0}, {294, 300, 0},
            {300, 306, 3}, {306, 310, 0}, {310, 318, 2}, {318, 321, 0}, {321, 327, 0},
            {327, 331, 0}, {331, 332.5F, 0}, {332.5F, 337, 0}, {337, 342, 0}, {342, 346, 0}};
    private static final float[][] POSES = {
            IDLE_LOCK, CHARGE_ROAR, DASH_LEAN, DASH_STRIKE, SPRINT,
            SPRINT, CHARGE_CROUCH, UPPER_COCK, UPPERCUT, UPPERCUT,
            WATCH_UP, CROUCH_SPRING, AIR_COCK, AIR_PUNCH, AIR_PUNCH,
            AIR_FOLLOW, AIR_DROP, LAND, LAND, FINISH};
    private static final float[][] TO = {
            null, null, null, null, null,
            null, null, null, null, UPPER_RISE,
            null, null, null, null, AIR_FOLLOW,
            AIR_DROP, LAND, null, FINISH, null};
    /** Vanilla'ya donus penceresi (RECOVER sonu). */
    private static final float FADE_START = 346F, FADE_END = UltimatePhase.DURATION;

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
        if (smallTarget && POSES[r] == UPPERCUT && TO[r] == null) out[RAX] = -100F; // kucuk hedefe alcak aparkat
        return weight;
    }

    private static void rowPose(int r, float t, float[] out) {
        float[] src = POSES[r];
        if (src == SPRINT) {
            sprint(t, out);
            return;
        }
        if (TO[r] != null) {
            lerp(src, TO[r], smooth((t - ROWS[r][0]) / (ROWS[r][1] - ROWS[r][0])), out);
            return;
        }
        System.arraycopy(src, 0, out, 0, N);
    }

    /**
     * Hedefin uzuv pozu (insansi modeller); kok egilmesi/donusu UltRender'da. Donus: vanilla'ya karsi agirlik.
     * Aparkat -> ucus (yavas cekimde salinan kollar) -> sirttan yumruk -> yerde sirtustu -> kalkis.
     */
    public static float targetPose(float t, float[] out) {
        float vt = UltimatePhase.visual(t);
        if (vt < UltimatePhase.HIT2 || vt >= UltimatePhase.TARGET_FREE) return 0F;
        if (vt < UltimatePhase.LAUNCH_T) {
            lerp(IDLE_LOCK, T_HIT, smooth((vt - UltimatePhase.HIT2) / 0.8F), out);
        } else if (vt < UltimatePhase.HIT3) {
            lerp(T_HIT, T_FLY, smooth((vt - UltimatePhase.LAUNCH_T) / 6F), out);
            float sw = Mth.sin((vt - UltimatePhase.LAUNCH_T) * 0.22F); // yavas cekim salinimi
            out[RAX] += 14F * sw;
            out[LAX] -= 12F * sw;
            out[RLX] += 10F * sw;
            out[LLX] -= 8F * sw;
            out[HX] += 6F * sw;
        } else if (vt < UltimatePhase.SLAM_T) {
            lerp(T_FLY, T_STRUCK, smooth((vt - UltimatePhase.HIT3) / 0.8F), out);
        } else {
            lerp(T_STRUCK, T_LIE, smooth((vt - UltimatePhase.SLAM_T) / 2F), out);
        }
        float up = UltimatePhase.TARGET_FREE - 10F; // kalkis
        return vt < up ? 1F : 1F - smooth((vt - up) / 10F);
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
