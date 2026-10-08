package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.speed.BlitzPath;
import dev.baranhan.flashmod.speed.BlitzScript;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Blitz pozlari. Her eylem (kosu, yumruk, tekme, fren...) zamana bagli bir fonksiyon; eylemler arasi 1.6 tick
 * capraz gecis -> modelde hicbir an ani kopma yok. Kok donusumleri (egilme, alcalma, govde burulmasi, yatma)
 * BlitzRenderer'da, uzuv acilari burada (HumanoidModelMixin uzerinden) uygulanir.
 */
public final class BlitzAnim {
    public static final int HX = 0, HY = 1, HZ = 2, BX = 3, BY = 4, BZ = 5, RAX = 6, RAY = 7, RAZ = 8,
            LAX = 9, LAY = 10, LAZ = 11, RLX = 12, RLY = 13, RLZ = 14, LLX = 15, LLY = 16, LLZ = 17,
            LEAN = 18, LOWER = 19, TWIST = 20, ROLL = 21, N = 22;
    private static final float BLEND = 1.6F;
    private static final float DEG = (float) Math.PI / 180F;

    private BlitzAnim() {}

    // ---------------------------------------------------------------- yardimcilar

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static void lerp(float w, float[] a, float[] b, float[] out) {
        for (int i = 0; i < N; i++) out[i] = a[i] + (b[i] - a[i]) * w;
    }

    private static void zero(float[] p) {
        java.util.Arrays.fill(p, 0F);
    }

    // ---------------------------------------------------------------- temel pozlar

    private static void run(float bt, float[] p) {
        zero(p);
        float ph = bt * 2.6F;
        float c = Mth.cos(ph), s = Mth.sin(ph);
        p[RLX] = c * 1.15F;
        p[LLX] = -c * 1.15F;
        p[RAX] = -c * 1.05F - 0.2F;
        p[LAX] = c * 1.05F - 0.2F;
        p[RAZ] = 0.15F;
        p[LAZ] = -0.15F;
        p[HX] = -0.42F;          // one egik govdede kafa ileri bakar
        p[LEAN] = 28F;
        p[LOWER] = 0.05F + 0.05F * Math.abs(s);
    }

    private static void ready(float bt, float[] p) {
        zero(p);
        float br = Mth.sin(bt * 0.25F) * 0.03F;
        p[RAX] = -1.25F + br;
        p[RAY] = -0.3F;
        p[RAZ] = 0.12F;
        p[LAX] = -1.4F - br;
        p[LAY] = 0.35F;
        p[LAZ] = -0.1F;
        p[RLX] = 0.3F;
        p[RLZ] = 0.08F;
        p[LLX] = -0.35F;
        p[LLZ] = -0.08F;
        p[HX] = 0.08F;
        p[LEAN] = 9F;
        p[LOWER] = 0.1F;
    }

    private static void stand(float bt, float[] p) {
        zero(p);
        float br = Mth.sin(bt * 0.2F) * 0.025F;
        p[RAX] = 0.05F;
        p[RAZ] = 0.12F + br;
        p[LAX] = 0.05F;
        p[LAZ] = -0.12F - br;
        p[RLZ] = 0.07F;
        p[LLZ] = -0.07F;
        p[LEAN] = 3F;
        p[HX] = 0.05F;
    }

    private static void launch(float u, float[] p) {
        zero(p);
        p[RLX] = -0.95F;
        p[LLX] = 0.65F;
        p[RAX] = 0.75F;
        p[LAX] = -0.9F;
        p[HX] = -0.55F;
        p[LEAN] = 38F;
        p[LOWER] = 0.28F * (1F - 0.5F * smooth(u));
    }

    /** "Super kahraman" fren kaymasi: alcak, bir el yerde, govde yana donuk. */
    private static void brake(float u, float[] p) {
        zero(p);
        p[RLX] = -1.35F;
        p[RLZ] = 0.15F;
        p[LLX] = 0.95F;
        p[LLZ] = -0.1F;
        p[LAX] = -0.25F;
        p[LAZ] = -0.25F;
        p[RAX] = 0.95F;
        p[RAZ] = 0.55F;
        p[HX] = -0.45F;
        p[LEAN] = 26F;
        p[LOWER] = 0.44F + 0.07F * Mth.sin(Math.min(u * 4F, 1F) * (float) Math.PI);
        p[TWIST] = 32F;
        p[ROLL] = -6F;
    }

    private static final float[] T1 = new float[N];

    /** Yumruk: kurma -> vurus (kol tam ileri, govde burulur, one yuklenir) -> toparlanma. */
    private static void punch(float bt, float th, boolean right, float power, float[] p) {
        ready(bt, p);
        float s = bt - th, rest = -1.25F;
        float armX, armY, twist, lean, lower = 0.12F;
        if (s < -0.3F) {
            float w = smooth((s + 1.0F) / 0.7F);
            armX = Mth.lerp(w, rest, 0.45F);
            armY = Mth.lerp(w, -0.3F, 0.35F);
            twist = 14F * w;
            lean = 9F + 2F * w;
        } else if (s < 0.12F) {
            float k = smooth((s + 0.3F) / 0.42F);
            armX = Mth.lerp(k, 0.45F, -1.62F);
            armY = Mth.lerp(k, 0.35F, -0.06F);
            twist = Mth.lerp(k, 14F, -18F * power);
            lean = Mth.lerp(k, 11F, 18F * power);
            lower = Mth.lerp(k, 0.12F, 0.18F);
        } else {
            float r = smooth((s - 0.12F) / 0.85F);
            armX = Mth.lerp(r, -1.62F, rest);
            armY = Mth.lerp(r, -0.06F, -0.3F);
            twist = Mth.lerp(r, -18F * power, 0F);
            lean = Mth.lerp(r, 18F * power, 9F);
        }
        float g = (float) Math.exp(-(s / 0.6F) * (s / 0.6F)); // lunge
        p[RLX] = Mth.lerp(g, 0.3F, -0.6F);
        p[LLX] = Mth.lerp(g, -0.35F, 0.5F);
        float tw = right ? twist : -twist;
        if (right) {
            p[RAX] = armX;
            p[RAY] = armY - tw * DEG;   // govde burulsa da kol hedefe nisanli kalsin
            p[LAX] = -1.45F;
            p[LAY] = 0.4F - tw * DEG;
        } else {
            p[LAX] = armX;
            p[LAY] = -armY - tw * DEG;
            p[RAX] = -1.45F;
            p[RAY] = -0.4F - tw * DEG;
        }
        p[HY] = -tw * DEG;
        p[TWIST] = tw;
        p[LEAN] = lean;
        p[LOWER] = lower;
    }

    /** Asagidan yukari vurus (rakibi hafif kaldirir). */
    private static void uppercut(float bt, float th, float[] p) {
        ready(bt, p);
        float s = bt - th;
        if (s < -0.25F) {
            float w = smooth((s + 1.0F) / 0.75F);
            p[RAX] = Mth.lerp(w, -1.25F, 0.55F);
            p[LOWER] = Mth.lerp(w, 0.1F, 0.32F);
            p[LEAN] = Mth.lerp(w, 9F, 20F);
            p[TWIST] = 12F * w;
            p[RLX] = Mth.lerp(w, 0.3F, -0.7F);
            p[LLX] = Mth.lerp(w, -0.35F, 0.6F);
        } else if (s < 0.2F) {
            float k = smooth((s + 0.25F) / 0.45F);
            p[RAX] = Mth.lerp(k, 0.55F, -2.8F);
            p[LOWER] = Mth.lerp(k, 0.32F, -0.08F);
            p[LEAN] = Mth.lerp(k, 20F, -6F);
            p[TWIST] = Mth.lerp(k, 12F, -16F);
            p[RLX] = Mth.lerp(k, -0.7F, -0.2F);
            p[LLX] = Mth.lerp(k, 0.6F, 0.35F);
        } else {
            float r = smooth((s - 0.2F) / 1.2F);
            p[RAX] = Mth.lerp(r, -2.8F, -1.25F);
            p[LOWER] = Mth.lerp(r, -0.08F, 0.1F);
            p[LEAN] = Mth.lerp(r, -6F, 9F);
            p[TWIST] = Mth.lerp(r, -16F, 0F);
        }
        p[RAY] = -p[TWIST] * DEG;
        p[HY] = -p[TWIST] * DEG;
        p[HX] = -0.3F;
    }

    /** Havadan kafaya tekme. */
    private static void kick(float bt, float th, float[] p) {
        float s = bt - th;
        zero(p);
        if (s < -0.3F) {
            float w = smooth((s + 1.2F) / 0.9F);
            run(bt, T1);
            lerp(w, T1, p, p); // kosudan cik
            p[RLX] = Mth.lerp(w, T1[RLX], -1.0F);
            p[LLX] = Mth.lerp(w, T1[LLX], -0.6F);
            p[RAZ] = Mth.lerp(w, 0.15F, 0.9F);
            p[LAZ] = Mth.lerp(w, -0.15F, -0.9F);
            p[LEAN] = Mth.lerp(w, 28F, -5F);
            p[HX] = Mth.lerp(w, -0.42F, 0.2F);
        } else if (s < 0.4F) {
            float k = smooth((s + 0.3F) / 0.5F);
            p[RLX] = Mth.lerp(k, -1.0F, -2.05F);
            p[RLZ] = -0.1F * k;
            p[LLX] = Mth.lerp(k, -0.6F, 0.55F);
            p[RAX] = 0.4F * k;
            p[RAZ] = Mth.lerp(k, 0.9F, 1.2F);
            p[LAZ] = Mth.lerp(k, -0.9F, -1.2F);
            p[LEAN] = Mth.lerp(k, -5F, -30F);
            p[HX] = 0.35F;
        } else {
            float r = smooth((s - 0.4F) / 0.9F);
            run(bt, T1);
            p[RLX] = -2.05F;
            p[LLX] = 0.55F;
            p[RAX] = 0.4F;
            p[RAZ] = 1.2F;
            p[LAZ] = -1.2F;
            p[LEAN] = -30F;
            p[HX] = 0.35F;
            lerp(r, p, T1, p);
        }
    }

    /** Kosarken omuz/kol carpmasi. */
    private static void pass(float bt, float th, float[] p) {
        run(bt, p);
        float d = (bt - th) / 0.4F;
        float w = (float) Math.exp(-d * d);
        p[RAX] = Mth.lerp(w, p[RAX], -1.45F);
        p[RAZ] = Mth.lerp(w, p[RAZ], 1.0F);
        p[TWIST] = -18F * w;
    }

    /** Final: tam hizda dev yumruk; darbe aninda zaman donarken yumruk uzanmis kalir. */
    private static void finalPunch(float bt, float th, float[] p) {
        float s = bt - th;
        if (s < -0.6F) {
            run(bt, p);
            return;
        }
        float hold = BlitzScript.LAUNCH_T - th - 0.1F;           // donma suresi
        float local = s < 0.1F ? bt : th + 0.1F + Math.max(0F, s - 0.1F - hold);
        punch(local, th, true, 1.4F, p);
        if (s < 0.12F) {
            float into = smooth((s + 0.6F) / 0.35F);
            run(bt, T1);
            lerp(into, T1, p, p);
        }
        p[LEAN] = Math.max(p[LEAN], 16F);
    }

    private static void actPose(float[] act, float bt, float[] p) {
        int type = (int) act[2];
        float th = act[3], u = (bt - act[0]) / Math.max(0.01F, act[1] - act[0]);
        switch (type) {
            case BlitzScript.A_LAUNCH -> launch(u, p);
            case BlitzScript.A_RUN -> run(bt, p);
            case BlitzScript.A_PASS -> pass(bt, th, p);
            case BlitzScript.A_BRAKE -> brake(u, p);
            case BlitzScript.A_READY -> ready(bt, p);
            case BlitzScript.A_PUNCH_R -> punch(bt, th, true, 1F, p);
            case BlitzScript.A_PUNCH_L -> punch(bt, th, false, 1F, p);
            case BlitzScript.A_UPPER -> uppercut(bt, th, p);
            case BlitzScript.A_KICK -> kick(bt, th, p);
            case BlitzScript.A_FINAL -> finalPunch(bt, th, p);
            default -> stand(bt, p);
        }
    }

    private static final float[] PREV = new float[N];

    /** Hizcinin bt anindaki tam pozu (eylemler arasi capraz gecisli). */
    public static void speedsterPose(float bt, float[] out) {
        float[][] acts = BlitzScript.ACTS;
        int i = 0;
        while (i + 1 < acts.length && bt >= acts[i + 1][0]) i++;
        actPose(acts[i], bt, out);
        float into = bt - acts[i][0];
        if (i > 0 && into < BLEND) {
            actPose(acts[i - 1], bt, PREV);
            lerp(smooth(into / BLEND), PREV, out, out);
        }
    }

    // ---------------------------------------------------------------- yon (snap yok: hiz ve hedef yonu yumusak karisir)

    private static float stageYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    /** Hizcinin sahne yaw'i: hareket ediyorsa gidis yonu, yavasladikca hedefe doner. */
    public static float speedsterStageYaw(BlitzPath.Frame f, float bt) {
        double[] a = new double[3], b = new double[3], c = new double[3], tg = new double[6];
        BlitzPath.speedster(f, bt + 0.15F, a);
        BlitzPath.speedster(f, bt - 0.45F, b);
        BlitzPath.speedster(f, bt, c);
        BlitzPath.target(bt, tg);
        double vx = a[0] - b[0], vz = a[2] - b[2];
        float speed = (float) Math.sqrt(vx * vx + vz * vz) / 0.6F;
        float face = stageYaw(tg[0] - c[0], tg[2] - c[2]);
        if (speed < 1.0E-3F) return face;
        float move = stageYaw(vx, vz);
        float w = smooth((speed - 0.25F) / 1.0F);
        return face + Mth.wrapDegrees(move - face) * w;
    }

    // ---------------------------------------------------------------- hedef pozu

    /** Hedef (insansi ise) icin savrulma/cokme/ucus pozu. tg = BlitzPath.target ciktisi. */
    public static void targetPose(float bt, double[] tg, float[] p) {
        zero(p);
        float last = -100F;
        for (float[] h : BlitzScript.HITS) if (h[0] <= bt) last = h[0];
        float rec = (float) Math.exp(-(bt - last) / 3.0F);
        float roll = (float) tg[5], lean = (float) tg[4];
        float out = Mth.clamp(rec * 0.9F + Math.abs(roll) / 50F, 0F, 1.2F);
        p[RAZ] = 0.1F + 0.9F * out;
        p[LAZ] = -(0.1F + 0.9F * out);
        p[RAX] = -0.4F * out;
        p[LAX] = -0.25F * out;
        p[HX] = Mth.clamp(lean / 60F, -0.7F, 0.7F);
        p[HY] = rec * 0.5F * Math.signum(roll);
        float kneel = Mth.clamp((float) -tg[1] / 0.5F, 0F, 1F);
        p[RLX] = -1.35F * kneel;
        p[LLX] = -1.35F * kneel;
        p[RAX] = Mth.lerp(kneel, p[RAX], -0.7F);
        p[LAX] = Mth.lerp(kneel, p[LAX], -0.7F);
        if (bt > BlitzScript.FINAL_T) {
            float fl = smooth((bt - BlitzScript.FINAL_T) / 2F) * (1F - smooth((bt - 109F) / 3F));
            p[RAZ] = Mth.lerp(fl, p[RAZ], 1.6F);
            p[LAZ] = Mth.lerp(fl, p[LAZ], -1.6F);
            p[RLZ] = 0.5F * fl;
            p[LLZ] = -0.5F * fl;
        }
    }

    // ---------------------------------------------------------------- modele uygula

    private static final float[] POSE = new float[N];
    private static final double[] TG = new double[6];

    private static final float[] ULT_POSE = new float[dev.baranhan.flashmod.client.ultimate.UltPoses.N];

    public static void applyModel(LivingEntity entity, HumanoidModel<?> model) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.getFrameTime();
        float tw = dev.baranhan.flashmod.client.ultimate.UltDirector.targetPoseFor(entity, pt, ULT_POSE);
        if (tw > 0F) { // ultimate hedefi (insansi): firlatilma / cakilma uzuv pozu; kok donusu UltRender'da
            dev.baranhan.flashmod.client.ultimate.UltPoses.apply(model, ULT_POSE, tw);
            return;
        }
        if (entity instanceof Player pl) {
            float uw = dev.baranhan.flashmod.client.ultimate.UltDirector.poseFor(pl, pt, ULT_POSE);
            if (uw > 0F) { // ultimate pozu (uzuvlar); kok donusumu UltRender'da
                dev.baranhan.flashmod.client.ultimate.UltPoses.apply(model, ULT_POSE, uw);
                return;
            }
            ClientSpeedsters.Entry e = ClientSpeedsters.get(pl.getUUID());
            if (e == null) return;
            if (!e.blitz || e.blitzFrame == null) {
                // Blitz disi: mizrak sarji kol pozu, kinetik yumruk
                AbilityClient.applyArm(e, model, pt);
                dev.baranhan.flashmod.client.skill.SkillClient.applyPose(pl, model, pt);
                return;
            }
            speedsterPose(BlitzClient.renderTime(e, pt), POSE);
        } else {
            ClientSpeedsters.Entry e = BlitzClient.entryForTarget(entity.getId());
            if (e == null) return;
            float bt = BlitzClient.renderTime(e, pt);
            BlitzPath.target(bt, TG);
            targetPose(bt, TG, POSE);
        }
        model.head.xRot = POSE[HX];
        model.head.yRot = POSE[HY];
        model.head.zRot = POSE[HZ];
        model.body.xRot = POSE[BX];
        model.body.yRot = POSE[BY];
        model.body.zRot = POSE[BZ];
        model.rightArm.xRot = POSE[RAX];
        model.rightArm.yRot = POSE[RAY];
        model.rightArm.zRot = POSE[RAZ];
        model.leftArm.xRot = POSE[LAX];
        model.leftArm.yRot = POSE[LAY];
        model.leftArm.zRot = POSE[LAZ];
        model.rightLeg.xRot = POSE[RLX];
        model.rightLeg.yRot = POSE[RLY];
        model.rightLeg.zRot = POSE[RLZ];
        model.leftLeg.xRot = POSE[LLX];
        model.leftLeg.yRot = POSE[LLY];
        model.leftLeg.zRot = POSE[LLZ];
        model.hat.copyFrom(model.head);
    }
}
