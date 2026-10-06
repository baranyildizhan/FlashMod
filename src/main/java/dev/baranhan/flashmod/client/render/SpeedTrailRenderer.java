package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.ClientSpeedsters.Entry;
import dev.baranhan.flashmod.client.ClientSpeedsters.TrailNode;
import dev.baranhan.flashmod.config.FlashClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * CW tarzi elektrik izi.
 *
 * Cizim asamasi AFTER_LEVEL: bulutlar, hava durumu ve (Fabulous'ta) seffaflik birlestirmesi bittikten SONRA.
 * Eskiden AFTER_PARTICLES'ta ciziliyordu; bulutlar ondan sonra geldigi ve iz derinlik yazmadigi icin
 * bulutlar izin ustune boyaniyordu. Simdi derinlik testi gercek sahneye karsi yapiliyor
 * (fancy bulutlar derinlik yazar -> sadece gercekten onunde olan bulut izi kapatir).
 *
 * 1) Oyuncunun her tick biraktigi noktalardan omurga, Catmull-Rom ile yumusatilir (en fazla MAX_PATH blok).
 * 2) Her iplik omurga etrafinda kendi yukseklik/yan ofset/dalgasiyla; dalgalar odometreye sabit.
 * 3) Her iplik 3 katman: genis halo, orta renk, beyaz-sicak cekirdek (additive = sahte bloom).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpeedTrailRenderer {
    private static final int MAX_SAMPLES = 320;
    /** Astronomik hizlarda iz binlerce blok uzamasin. */
    private static final float MAX_PATH = 140F;
    private static final float TAU = (float) (Math.PI * 2.0);

    private static float[] cx = new float[96], cy = new float[96], cz = new float[96], csx = new float[96],
            csz = new float[96], cage = new float[96], cpow = new float[96], ch = new float[96],
            csy = new float[96], cux = new float[96], cuy = new float[96], cuz = new float[96];
    private static double[] codo = new double[96];
    private static int cn;

    private static final float[] sx = new float[MAX_SAMPLES + 1], sy = new float[MAX_SAMPLES + 1], sz = new float[MAX_SAMPLES + 1],
            ssx = new float[MAX_SAMPLES + 1], ssz = new float[MAX_SAMPLES + 1],
            ssy = new float[MAX_SAMPLES + 1], sux = new float[MAX_SAMPLES + 1], suy = new float[MAX_SAMPLES + 1],
            suz = new float[MAX_SAMPLES + 1],
            sage = new float[MAX_SAMPLES + 1], spow = new float[MAX_SAMPLES + 1], sh = new float[MAX_SAMPLES + 1];
    private static final double[] sodo = new double[MAX_SAMPLES + 1];
    /** Kafadan itibaren yol boyunca kumulatif mesafe (govdeye baglanma gecisi icin). */
    private static final float[] sdist = new float[MAX_SAMPLES + 1];
    private static int sn;

    /** renderPlayer suresince gecerli: o oyuncunun bu karedeki govde pozu (yoksa null). */
    @javax.annotation.Nullable
    private static BodyPoseCapture.Pose bodyPose;
    private static Vec3 bodyCam = Vec3.ZERO;

    private static final float[] PT3 = new float[3];
    private static final float[] PT3B = new float[3];

    private static final Lightning.Rng W = new Lightning.Rng(4);

    /** Uzuvlarin iplik sirasi: her 6'lik blok 6 uzvun hepsini icerir, govde (en buyuk yuzey) biraz daha sik. */
    private static final int[] PART_ORDER = {
            BodyPoseCapture.BODY, BodyPoseCapture.HEAD, BodyPoseCapture.R_ARM, BodyPoseCapture.L_LEG,
            BodyPoseCapture.L_ARM, BodyPoseCapture.R_LEG,
            BodyPoseCapture.BODY, BodyPoseCapture.L_ARM, BodyPoseCapture.R_LEG, BodyPoseCapture.HEAD,
            BodyPoseCapture.R_ARM, BodyPoseCapture.L_LEG,
            BodyPoseCapture.BODY, BodyPoseCapture.HEAD, BodyPoseCapture.R_ARM, BodyPoseCapture.L_ARM};

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Ipligin 'hop' numarali cikis noktasi: kendi hucresinin (home +- radius) icinde, arka yuze yakin. */
    private static void wanderPoint(long seed, long hop, float[] box, float homeX, float homeY, float radius, float[] out) {
        W.seed(seed, hop, 0x3A11);
        out[0] = Mth.clamp(homeX + W.signed() * radius, box[0] + 0.3F, box[3] - 0.3F);
        out[1] = Mth.clamp(homeY + W.signed() * radius, box[1] + 0.3F, box[4] - 0.3F);
        out[2] = Mth.lerp(0.7F + 0.3F * W.next(), box[2], box[5]);
    }

    private static int pickPart(float r) {
        float acc = 0F;
        for (int i = 0; i < BodyPoseCapture.PARTS; i++) {
            acc += BodyPoseCapture.WEIGHT[i];
            if (r < acc) return i;
        }
        return BodyPoseCapture.BODY;
    }

    /** Uzvun kupunden rastgele bir nokta (piksel), yuzeye itilmis -> arklar derinin ustunde gezinir. */
    private static void randomSurface(Lightning.Rng rng, int part, float[] out) {
        float[] b = BodyPoseCapture.BOX[part];
        out[0] = Mth.lerp(rng.next(), b[0], b[3]);
        out[1] = Mth.lerp(rng.next(), b[1], b[4]);
        out[2] = Mth.lerp(rng.next(), b[2], b[5]);
        int axis = (int) (rng.next() * 3F) % 3;
        out[axis] = rng.next() < 0.5F ? b[axis] - 0.3F : b[axis + 3] + 0.3F;
    }

    private static final Polyline LINE = new Polyline();
    private static final Polyline FORK = new Polyline();
    private static final Lightning.Rng P = new Lightning.Rng(1);
    private static final Lightning.Rng T = new Lightning.Rng(2);

    private SpeedTrailRenderer() {}

    /** HIGH: post-processing (LOW) bunlari da bulaniklastirsin diye once cizilir. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientSpeedsters.all().isEmpty()) return;

        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f pose = new Matrix4f(BodyPoseCapture.view());
        long now = mc.level.getGameTime();
        float bloom = FlashClientConfig.BLOOM.get().floatValue();
        int strands = FlashClientConfig.STRANDS.get();

        // AFTER_LEVEL'da RenderSystem'in model-view matrisi her zaman birim degil; kamera donusu iki kez
        // uygulaniyor ve efektler kafayi cevirince yanlis yerlere kayiyordu. Cizim suresince birime sabitliyoruz,
        // tum donusum yukaridaki 'pose' matrisinde.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
            for (Player p : mc.level.players()) {
                Entry e = ClientSpeedsters.get(p.getUUID());
                if (e == null) continue;
                if (e.nodes.isEmpty() && !e.active && now - e.deactivatedAt > 20) continue;
                if (p.isSpectator() || p.isInvisibleTo(mc.player)) continue;
                if (p.distanceToSqr(cam) > 200 * 200 && e.nodes.isEmpty()) continue;
                if (dev.baranhan.flashmod.client.ultimate.UltDirector.hidesWorldTrail(p, pt)) continue; // sahne devraldi
                boolean selfFp = p == mc.player && !camera.isDetached();
                renderPlayer(vc, pose, cam, p, e, pt, now, bloom, strands, selfFp);
            }
            buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderPlayer(VertexConsumer vc, Matrix4f m, Vec3 cam, Player p, Entry e, float pt, long now,
                                     float bloom, int strands, boolean selfFp) {
        float hx = (float) (Mth.lerp(pt, p.xo, p.getX()) - cam.x);
        float hy = (float) (Mth.lerp(pt, p.yo, p.getY()) - cam.y);
        float hz = (float) (Mth.lerp(pt, p.zo, p.getZ()) - cam.z);
        float hScale = p.getBbHeight() / 1.8F;
        float intensity = e.intensity(pt);
        float wb = e.wallBlend(pt);
        if (wb > 0F) { // duvarda: kafa noktasi ayak pivotunda, boy olcegi ayakta boyu
            hx -= e.wallNx * 0.3F * wb;
            hy += 0.9F * wb;
            hz -= e.wallNz * 0.3F * wb;
            hScale = Mth.lerp(wb, hScale, 1F);
        }

        if (e.tornado) {
            // Kosucu cemberin YAYI uzerinde (kiris enterpolasyonu degil)
            float a = e.tornadoAngle(pt);
            hx = (float) (e.tcx + Mth.cos(a) * ClientSpeedsters.TORNADO_RADIUS - cam.x);
            hz = (float) (e.tcz + Mth.sin(a) * ClientSpeedsters.TORNADO_RADIUS - cam.z);
            drawTornado(vc, m, e, cam, pt, now, bloom);
        }

        // Bu karede gercekten cizilen modelin konumu varsa, tahmin yerine onu kullan (tam yapisik).
        BodyPoseCapture.Pose body = BodyPoseCapture.get(p);
        if (body != null) {
            hx = (float) (body.originX() - cam.x);
            hy = (float) (body.originY() - cam.y);
            hz = (float) (body.originZ() - cam.z);
        }
        bodyPose = body;
        bodyCam = cam;

        if (buildPath(e, cam, hx, hy, hz, hScale, pt, now) && sn >= 2) {
            drawHaze(vc, m, e, bloom);
            for (int s = 0; s < strands; s++) drawStrand(vc, m, e, s, strands, now, pt, bloom, selfFp);
        }
        if (!selfFp) {
            drawBody(vc, m, p.onGround(), e, hx, hy, hz, hScale, intensity, now, bloom);
            if (e.phasing && !e.tornado) drawPhaseStreaks(vc, m, p, e, hx, hy, hz, hScale, pt, bloom);
        }
        drawActivation(vc, m, p, e, hx, hy, hz, hScale, pt, now, bloom);
        dev.baranhan.flashmod.client.AbilityClient.drawCharge(vc, m, p, e, bodyPose, cam, selfFp, bloom);
        drawBoom(vc, m, e, cam, pt, now, bloom);
    }

    /**
     * Sentetik iz: oyuncuya bagli olmayan bir yol (ultimate sahneleri, firlatilan hedef). Normal izle birebir ayni
     * omurga/iplik/katman kodu kullanilir. Node'lar 'cam'e, govde noktalari 'bodyCamera'ya gore cevrilir
     * (sahnede govde gercek kamerayla yakalanir, node'lar sahne kamerasina goredir). h* = kameraya goreli kafa.
     */
    public static void drawSynthetic(VertexConsumer vc, Matrix4f m, Entry e, Vec3 cam, float hx, float hy, float hz,
                                     float hScale, @javax.annotation.Nullable BodyPoseCapture.Pose body, Vec3 bodyCamera,
                                     long now, float pt, float bloom, int strands, boolean bodyArcs, boolean onGround) {
        bodyPose = body;
        bodyCam = bodyCamera;
        try {
            if (buildPath(e, cam, hx, hy, hz, hScale, pt, now) && sn >= 2) {
                drawHaze(vc, m, e, bloom);
                for (int s = 0; s < strands; s++) drawStrand(vc, m, e, s, strands, now, pt, bloom, false);
            }
            if (bodyArcs) drawBody(vc, m, onGround, e, hx, hy, hz, hScale, e.intensity(pt), now, bloom);
        } finally {
            bodyPose = null;
        }
    }

    // ------------------------------------------------------------------ omurga

    private static void ensureCp(int n) {
        if (cx.length >= n) return;
        int s = n * 2;
        cx = Arrays.copyOf(cx, s); cy = Arrays.copyOf(cy, s); cz = Arrays.copyOf(cz, s);
        csx = Arrays.copyOf(csx, s); csz = Arrays.copyOf(csz, s); codo = Arrays.copyOf(codo, s);
        cage = Arrays.copyOf(cage, s); cpow = Arrays.copyOf(cpow, s); ch = Arrays.copyOf(ch, s);
        csy = Arrays.copyOf(csy, s); cux = Arrays.copyOf(cux, s); cuy = Arrays.copyOf(cuy, s); cuz = Arrays.copyOf(cuz, s);
    }

    private static void addCp(float x, float y, float z, float sideX, float sideY, float sideZ, float upX, float upY,
                              float upZ, double odo, float age, float pow, float h) {
        ensureCp(cn + 1);
        cx[cn] = x; cy[cn] = y; cz[cn] = z; csx[cn] = sideX; csy[cn] = sideY; csz[cn] = sideZ;
        cux[cn] = upX; cuy[cn] = upY; cuz[cn] = upZ;
        codo[cn] = odo; cage[cn] = age; cpow[cn] = pow; ch[cn] = h;
        cn++;
    }

    private static boolean buildPath(Entry e, Vec3 cam, float hx, float hy, float hz, float hScale, float pt, long now) {
        cn = 0;
        sn = 0;
        if (e.nodes.isEmpty()) return false;
        float life = e.trailLife();
        float pathLen = 0F;

        boolean first = true;
        for (TrailNode n : e.nodes) {
            // Kafanin cizildigi an = now - 1 + pt (entity enterpolasyonu ile ayni). Once long cikarma.
            float age = ((now - n.tick()) - 1 + pt) / life;
            if (age <= 0F) continue;
            float x = (float) (n.x() - cam.x), y = (float) (n.y() - cam.y), z = (float) (n.z() - cam.z);
            if (first) {
                first = false;
                float dx = x - hx, dy = y - hy, dz = z - hz;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float headPow = e.active ? ClientSpeedsters.powerFor(Mth.lerp(pt, e.prevSpeed, e.speed)) : 0F;
                addCp(hx, hy, hz, e.sideX, e.sideY, e.sideZ, e.upX, e.upY, e.upZ, n.odo() + d, 0F,
                        Math.max(headPow, n.power() * 0.6F), hScale);
            }
            float px = cx[cn - 1], py = cy[cn - 1], pz = cz[cn - 1];
            float seg = (float) Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz));
            if (pathLen + seg > MAX_PATH) {
                // son noktayi MAX_PATH'e kirp ve orada sonlandir (alpha ~0 olacak sekilde yasli say)
                float t = seg > 1.0E-4F ? (MAX_PATH - pathLen) / seg : 0F;
                addCp(px + (x - px) * t, py + (y - py) * t, pz + (z - pz) * t, n.sideX(), n.sideY(), n.sideZ(),
                        n.upX(), n.upY(), n.upZ(), codo[cn - 1] - (codo[cn - 1] - n.odo()) * t, 1F, n.power(), n.height());
                break;
            }
            pathLen += seg;
            addCp(x, y, z, n.sideX(), n.sideY(), n.sideZ(), n.upX(), n.upY(), n.upZ(), n.odo(), Math.min(age, 1F),
                    n.power(), n.height());
            if (age >= 1F) break;
        }
        if (cn < 2) return false;

        for (int i = 0; i < cn - 1 && sn < MAX_SAMPLES; i++) {
            int i0 = Math.max(0, i - 1), i2 = i + 1, i3 = Math.min(cn - 1, i + 2);
            float dx = cx[i2] - cx[i], dy = cy[i2] - cy[i], dz = cz[i2] - cz[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = Mth.clamp(Mth.ceil(len / 0.3F), 1, 48);
            for (int k = 0; k < steps && sn < MAX_SAMPLES; k++) {
                sample(i0, i, i2, i3, k / (float) steps);
            }
        }
        if (sn < MAX_SAMPLES) sample(Math.max(0, cn - 3), cn - 2, cn - 1, cn - 1, 1F);
        return true;
    }

    private static final float[] CR_OUT = new float[3];

    /**
     * Centripetal Catmull-Rom (alpha 0.5). Duz Catmull-Rom esit olmayan aralikli noktalarda (kafaya cok yakin
     * ilk nokta + 30 blok uzaktaki sonraki nokta) tasarak ileri/geri sarkiyordu; centripetal tasma yapmaz.
     */
    private static final float[][] CP4 = new float[4][3];

    private static void centripetal(int i0, int i1, int i2, int i3, float t, float[] out) {
        float[][] q = CP4;
        load(q[1], i1);
        load(q[2], i2);
        if (i0 == i1) reflect(q[0], q[1], q[2]); else load(q[0], i0);   // uclarda yansitilmis sanal nokta
        if (i3 == i2) reflect(q[3], q[2], q[1]); else load(q[3], i3);
        float t1 = knot(q[0], q[1]), t2 = t1 + knot(q[1], q[2]), t3 = t2 + knot(q[2], q[3]);
        float tt = t1 + (t2 - t1) * t;
        for (int axis = 0; axis < 3; axis++) {
            float p0 = q[0][axis], p1 = q[1][axis], p2 = q[2][axis], p3 = q[3][axis];
            float a1 = ((t1 - tt) * p0 + tt * p1) / t1;
            float a2 = ((t2 - tt) * p1 + (tt - t1) * p2) / (t2 - t1);
            float a3 = ((t3 - tt) * p2 + (tt - t2) * p3) / (t3 - t2);
            float b1 = ((t2 - tt) * a1 + tt * a2) / t2;
            float b2 = ((t3 - tt) * a2 + (tt - t1) * a3) / (t3 - t1);
            out[axis] = ((t2 - tt) * b1 + (tt - t1) * b2) / (t2 - t1);
        }
    }

    private static void load(float[] o, int i) {
        o[0] = cx[i];
        o[1] = cy[i];
        o[2] = cz[i];
    }

    private static void reflect(float[] o, float[] a, float[] b) {
        o[0] = 2F * a[0] - b[0];
        o[1] = 2F * a[1] - b[1];
        o[2] = 2F * a[2] - b[2];
    }

    private static float knot(float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return (float) Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) + 1.0E-3F;
    }

    private static void sample(int i0, int i1, int i2, int i3, float t) {
        centripetal(i0, i1, i2, i3, t, CR_OUT);
        sx[sn] = CR_OUT[0];
        sy[sn] = CR_OUT[1];
        sz[sn] = CR_OUT[2];
        float sxx = Mth.lerp(t, csx[i1], csx[i2]), syy = Mth.lerp(t, csy[i1], csy[i2]), szz = Mth.lerp(t, csz[i1], csz[i2]);
        float l = (float) Math.sqrt(sxx * sxx + syy * syy + szz * szz);
        if (l < 1.0E-4F) { sxx = csx[i1]; syy = csy[i1]; szz = csz[i1]; } else { sxx /= l; syy /= l; szz /= l; }
        ssx[sn] = sxx;
        ssy[sn] = syy;
        ssz[sn] = szz;
        float ux = Mth.lerp(t, cux[i1], cux[i2]), uy = Mth.lerp(t, cuy[i1], cuy[i2]), uz = Mth.lerp(t, cuz[i1], cuz[i2]);
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-4F) { ux = 0F; uy = 1F; uz = 0F; } else { ux /= ul; uy /= ul; uz /= ul; }
        sux[sn] = ux;
        suy[sn] = uy;
        suz[sn] = uz;
        sodo[sn] = codo[i1] + (codo[i2] - codo[i1]) * t;
        sage[sn] = Mth.lerp(t, cage[i1], cage[i2]);
        spow[sn] = Mth.lerp(t, cpow[i1], cpow[i2]);
        sh[sn] = Mth.lerp(t, ch[i1], ch[i2]);
        if (sn == 0) {
            sdist[0] = 0F;
        } else {
            float dx = sx[sn] - sx[sn - 1], dy = sy[sn] - sy[sn - 1], dz = sz[sn] - sz[sn - 1];
            sdist[sn] = sdist[sn - 1] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        sn++;
    }

    /** Kameraya cok yakin noktalari sondur (genel: tornado hunisi, halkalar vb.; kamera onlarin icinde olabilir). */
    private static float camFade(float x, float y, float z) {
        float d = (float) Math.sqrt(x * x + y * y + z * z);
        float t = Mth.clamp((d - 0.35F) / 1.4F, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * Kosu izi icin daha guclu sonme. Ucuncu sahista kamera goz hizasinda ~4 blok arkada; kafa ipligi de goz
     * hizasinda arkaya aktigi icin objektifin icinden gecip ekranda savruluyordu. 0.9 blokta gorunmez, 3.2'de tam.
     * Sadece iz ipliklerinde kullanilir: tornado kamerasi hunisinin icinde/yakininda durdugu icin genel sonmede
     * kullanilirsa hunisi silinip ortada sadece isik sutunu kaliyordu.
     */
    private static float trailCamFade(float x, float y, float z) {
        float d = (float) Math.sqrt(x * x + y * y + z * z);
        float t = Mth.clamp((d - CAM_FADE_NEAR) / (CAM_FADE_FAR - CAM_FADE_NEAR), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    private static final float CAM_FADE_NEAR = 0.9F, CAM_FADE_FAR = 3.2F;

    /** Dunyaya sabit sinus: faz double'da hesaplanir, buyuk odometrede bozulmaz. */
    /** Periyodik olmayan yumusak kivrim (-1..1 civari): iki oktav deger gurultusu, odometreye sabit. */
    private static float meander(long seed, double odo, float freq) {
        return Lightning.noise(seed, odo * freq) * 0.75F + Lightning.noise(seed + 977, odo * freq * 2.3D) * 0.35F;
    }

    private static float wave(double odo, float freq, float phase) {
        double c = odo * freq;
        c -= Math.floor(c);
        return Mth.sin((float) c * TAU + phase);
    }

    // ------------------------------------------------------------------ katmanlar

    private static void drawHaze(VertexConsumer vc, Matrix4f m, Entry e, float bloom) {
        if (bloom <= 0.01F) return;
        Polyline line = LINE.clear();
        for (int j = 0; j < sn; j++) {
            float a = sage[j];
            float k = (1F - a);
            float hh = 0.95F * sh[j];
            float x = sx[j] + sux[j] * hh, y = sy[j] + suy[j] * hh, z = sz[j] + suz[j] * hh;
            line.add(x, y, z, k * k * spow[j] * trailCamFade(x, y, z), 1F + a * 1.2F);
        }
        float r = GlowDraw.cr(e.glow), g = GlowDraw.cg(e.glow), b = GlowDraw.cb(e.glow);
        GlowDraw.ribbon(vc, m, line, 0.62F * bloom, r, g, b, 0.085F * bloom, false);
        GlowDraw.ribbon(vc, m, line, 0.28F * bloom, r, g, b, 0.08F * bloom, false);
    }

    private static void drawStrand(VertexConsumer vc, Matrix4f m, Entry e, int s, int strands, long now, float pt, float bloom,
                                   boolean selfFp) {
        P.seed(e.seed, s, 0x5EED);
        // Dengeli dagilim: uzuvlar sirayla dagitilir (ilk 6 iplik 6 uzvun hepsine birer tane), ayni uzuvdaki
        // iplikler de o uzvun yuzeyinde dusuk-tutarsizlik dizisiyle (R2) birbirinden esit uzakliga yerlestirilir.
        // Her ipligin kendi "hucresi" var; gezinme sadece o hucrenin icinde -> iplikler ust uste yigilmaz.
        int part = PART_ORDER[s % PART_ORDER.length];
        int slot = 0, slots = 0;
        for (int i = 0; i < strands; i++) {
            if (PART_ORDER[i % PART_ORDER.length] != part) continue;
            if (i < s) slot++;
            slots++;
        }
        float[] box = BodyPoseCapture.BOX[part];
        float rot = Lightning.hash(e.seed, part); // oyuncuya ozgu kaydirma, dagilim yine esit kalir
        float gu = slots == 1 ? 0.5F + (rot - 0.5F) * 0.3F : frac(rot + 0.5F + slot * 0.7548777F);
        float gv = slots == 1 ? 0.5F : frac(rot * 1.7F + 0.5F + slot * 0.5698403F);
        float w = box[3] - box[0], h = box[4] - box[1];
        float lx = box[0] + w * (0.12F + 0.76F * gu);
        float ly = box[1] + h * (0.08F + 0.84F * gv);
        float lz = box[2] + (box[5] - box[2]) * 0.85F; // arka yuze yakin (iz arkaya akar)
        // Hucre yaricapi: uzuv alani bu uzuvdaki iplik sayisina bolunur
        float cell = 0.5F * (float) Math.sqrt(w * h / Math.max(1, slots));
        float homeX = lx, homeY = ly;
        // Kalici iz ofsetleri ayakta duruştaki konumdan turetilir -> iplikler govdeden ciktiklari yerde yayilir,
        // hepsi tek noktaya toplanmaz. Sag = +yan.
        float[] piv = BodyPoseCapture.PIVOT[part];
        float side0 = -(piv[0] + lx) / 16F * 0.9375F;
        float anchor = Mth.clamp((24F - (piv[1] + ly)) / 16F * 0.9375F, 0.05F, 1.9F);
        // Yelpaze hep disari dogru acilir (iplik cikis tarafindan karsi tarafa gecmez -> uclarda capraz/burgulu
        // gorunmez); her ipligin kucuk kendi sapmasi kalir.
        float spread = side0 * 1.1F + P.signed() * 0.18F;
        float vSpread = (anchor - 0.95F) * 0.45F + P.signed() * 0.12F;
        float waveAmp = P.range(0.07F, 0.26F), waveFreq = P.range(0.3F, 0.85F), phase = P.next() * TAU;
        float vAmp = P.range(0.05F, 0.2F), vFreq = P.range(0.25F, 0.8F), phase2 = P.next() * TAU;
        float lengthFrac = P.range(0.55F, 1.0F);
        float widthMul = P.range(0.8F, 1.25F);
        long nSeed = e.seed * 31L + s * 7919L;
        BodyPoseCapture.Pose body = bodyPose;
        float bx = 0, by = 0, bz = 0;
        if (body != null) {
            // Hafif rastgelelik: izin kendisi (yukaridaki kalici ofsetler) sabit kalir, sadece CIKIS noktasi
            // ayni uzvun yuzeyinde zaman zaman yeni bir yere kayar. Her ipligin kendi ritmi var (18-48 tick),
            // gecis periyodun son %30'unda yumusakca olur; govdeye baglanma bolgesi farki emer, iz ziplamaz.
            float period = 18F + Lightning.hash(nSeed, 1) * 30F;
            double clock = (now + pt) / period + Lightning.hash(nSeed, 2);
            long hop = (long) Math.floor(clock);
            float f = (float) (clock - hop);
            float u = Mth.clamp((f - 0.7F) / 0.3F, 0F, 1F);
            u = u * u * (3F - 2F * u);
            wanderPoint(nSeed, hop, box, homeX, homeY, cell * 0.6F, PT3);
            wanderPoint(nSeed, hop + 1, box, homeX, homeY, cell * 0.6F, PT3B);
            lx = Mth.lerp(u, PT3[0], PT3B[0]);
            ly = Mth.lerp(u, PT3[1], PT3B[1]);
            lz = Mth.lerp(u, PT3[2], PT3B[2]);
            body.point(part, lx, ly, lz, bodyCam, PT3);
            bx = PT3[0];
            by = PT3[1];
            bz = PT3[2];
        }

        T.seed(e.seed ^ 0xF1A5L, s, now);
        if (T.next() < 0.07F) return;
        float flicker = T.range(0.75F, 1.15F);
        long tickSeed = nSeed ^ (now * 0x9E3779B9L);
        double gapShift = (now / 3) * 1.7D;

        Polyline line = LINE.clear();
        for (int j = 0; j < sn; j++) {
            float a = sage[j] / lengthFrac;
            if (a >= 1F) break;
            double o = sodo[j];
            float fan = (float) Math.pow(a, 0.8);
            // Kivrim zarfi: govdeden cikinca hizla buyur (ilk ~%20), sonra kuyruga dogru belirgin azalir.
            float rise = Math.min(1F, a / 0.2F);
            float fade = a <= 0.25F ? 0F : (a - 0.25F) / 0.75F;
            fade = fade * fade * (3F - 2F * fade);
            float env = (0.35F + 0.65F * rise) * (1F - 0.7F * fade);   // uclarda ~%30 kalir
            // Uzun izde uclar ayrica sakinlesir (kafadan ~5 bloktan sonra), seyrek orneklemede diken olusmasin.
            float far = Mth.clamp((sdist[j] - 5F) / 13F, 0F, 1F);
            far = far * far * (3F - 2F * far);
            float nCalm = (1F - 0.82F * far) * (1F - 0.48F * fade);  // ince titreme ve kirilmalar
            float wCalm = (1F - 0.6F * far) * env;                    // genis kivrimlar
            // Kendini tekrar etmesin: periyodik sinus yerine odometreye sabit, iki katmanli yumusak gurultu
            float lat = side0 * (1F + fan) + spread * fan
                    + waveAmp * wCalm * meander(nSeed + 31, o, waveFreq)
                    + Lightning.noise(nSeed, o * 3.1D) * 0.085F * nCalm
                    + Lightning.noise(tickSeed, o * 5.3D) * 0.045F * nCalm;
            float ver = anchor * sh[j] + vSpread * fan
                    + vAmp * wCalm * meander(nSeed + 57, o, vFreq)
                    + Lightning.noise(nSeed + 99, o * 3.4D) * 0.08F * nCalm
                    + Lightning.noise(tickSeed + 99, o * 5.9D) * 0.045F * nCalm;
            float x = sx[j] + ssx[j] * lat + sux[j] * ver;
            float y = sy[j] + ssy[j] * lat + suy[j] * ver;
            float z = sz[j] + ssz[j] * lat + suz[j] * ver;
            if (body != null) {
                // Ilk ~1.6 blokta iplik modelin gercek uzvundan cikar, sonra iz formuna karisir
                float bl = Mth.clamp(sdist[j] / 1.6F, 0F, 1F);
                bl = bl * bl * (3F - 2F * bl);
                float jit = (1F - bl) * 0.04F;
                x = Mth.lerp(bl, bx + Lightning.noise(tickSeed + 11, j * 0.7D) * jit, x);
                y = Mth.lerp(bl, by + Lightning.noise(tickSeed + 13, j * 0.7D) * jit, y);
                z = Mth.lerp(bl, bz + Lightning.noise(tickSeed + 17, j * 0.7D) * jit, z);
            }

            float k = 1F - a;
            float gap = Mth.clamp((Lightning.noise(nSeed + 7, o * 0.22D + gapShift) + 0.8F) * 3F, 0F, 1F);
            float alpha = k * k * spow[j] * flicker * gap * trailCamFade(x, y, z);
            line.add(x, y, z, alpha, widthMul * (1F + a * 0.7F));
        }
        if (line.size < 2) return;
        GlowDraw.layered(vc, m, line, 1F, e.core, e.glow, 1F, bloom, false);

        int forks = (selfFp ? 1 : 2) + (int) (e.intensity(1F) * 2);
        for (int f = 0; f < forks; f++) {
            if (T.next() > 0.45F) continue;
            int j = (int) (T.next() * (line.size - 1));
            float la = line.a[j];
            if (la < 0.08F || sdist[Math.min(j, sn - 1)] > 7F) continue; // catallar sadece ize yakin kisimda
            float x0 = line.x[j], y0 = line.y[j], z0 = line.z[j];
            float len = T.range(0.3F, 1.0F) * (0.6F + sage[Math.min(j, sn - 1)]);
            float dx = T.signed(), dy = T.range(-0.9F, 0.4F), dz = T.signed();
            float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
            FORK.clear();
            Lightning.jag(FORK, T, x0, y0, z0, x0 + dx / dl * len, y0 + dy / dl * len, z0 + dz / dl * len,
                    0.22F, 3, false, la, 0F);
            GlowDraw.layered(vc, m, FORK, 0.7F, e.core, e.glow, 0.9F, bloom, false);
        }
        if (T.next() < 0.5F) {
            int j = (int) (T.next() * (line.size - 1));
            float la = line.a[j];
            GlowDraw.orb(vc, m, line.x[j], line.y[j], line.z[j], 0.16F * (0.5F + bloom * 0.5F),
                    GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0.35F * la);
            int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.5F);
            GlowDraw.orb(vc, m, line.x[j], line.y[j], line.z[j], 0.045F,
                    GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.9F * la);
        }
    }

    // ------------------------------------------------------------------ govde

    private static void drawBody(VertexConsumer vc, Matrix4f m, boolean onGround, Entry e, float hx, float hy, float hz,
                                 float hScale, float intensity, long now, float bloom) {
        if (!e.active) return;
        float h = 1.8F * hScale;
        float cyy = hy + h * 0.53F;
        float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
        float ax = hx, az = hz;
        if (bodyPose != null) {
            bodyPose.point(BodyPoseCapture.BODY, 0F, 4F, 0F, bodyCam, PT3);
            ax = PT3[0];
            cyy = PT3[1];
            az = PT3[2];
        }

        if (bloom > 0.01F) {
            GlowDraw.orb(vc, m, ax, cyy, az, (0.85F + 0.45F * intensity) * hScale, gr, gg, gb,
                    (0.05F + 0.1F * intensity) * bloom);
        }
        if (onGround) {
            GlowDraw.disc(vc, m, hx, hy + 0.03F, hz, 1, 0, 0, 0, 0, 1, 0.7F + 0.7F * intensity, gr, gg, gb,
                    (0.06F + 0.12F * intensity) * Math.max(bloom, 0.3F), 14);
        }

        T.seed(e.seed ^ 0xB0D7L, now, 3);
        int arcs = 1 + (int) (intensity * 4F) + (e.phasing ? 3 : 0);
        BodyPoseCapture.Pose body = bodyPose;
        if (body != null) {
            // Govdenin her yerinde: rastgele bir uzvun yuzeyinden ya ayni uzvun baska noktasina, ya komsu uzva,
            // ya da deriden disari kisa bir kivilcim. Model hareket ettikce birebir takip eder.
            float[] q = new float[3];
            for (int i = 0; i < arcs + 2; i++) {
                if (intensity < 0.05F && !e.phasing && T.next() > 0.3F) continue;
                int p0 = pickPart(T.next());
                randomSurface(T, p0, q);
                body.point(p0, q[0], q[1], q[2], bodyCam, PT3);
                float x0 = PT3[0], y0 = PT3[1], z0 = PT3[2];
                float x1, y1, z1;
                float mode = T.next();
                if (mode < 0.3F) { // deriden disari
                    x1 = x0 + T.signed() * 0.4F;
                    y1 = y0 + T.signed() * 0.4F;
                    z1 = z0 + T.signed() * 0.4F;
                } else {
                    int p1 = mode < 0.65F ? p0 : pickPart(T.next());
                    randomSurface(T, p1, q);
                    body.point(p1, q[0], q[1], q[2], bodyCam, PT3B);
                    x1 = PT3B[0];
                    y1 = PT3B[1];
                    z1 = PT3B[2];
                }
                FORK.clear();
                Lightning.jag(FORK, T, x0, y0, z0, x1, y1, z1, 0.22F, 3, false, 1F, 0.7F);
                GlowDraw.layered(vc, m, FORK, 0.5F, e.core, e.glow, 0.85F, bloom, false);
            }
            return;
        }
        float rx = 0.42F * hScale, ry = h * 0.52F;
        for (int i = 0; i < arcs; i++) {
            if (intensity < 0.05F && !e.phasing && T.next() > 0.3F) continue;
            float th1 = T.next() * TAU, ph1 = T.signed() * 1.3F;
            float th2 = th1 + T.range(0.5F, 1.5F) * (T.next() < 0.5F ? -1 : 1), ph2 = ph1 + T.signed() * 0.9F;
            float x0 = hx + Mth.cos(th1) * Mth.cos(ph1) * rx, y0 = cyy + Mth.sin(ph1) * ry, z0 = hz + Mth.sin(th1) * Mth.cos(ph1) * rx;
            float x1 = hx + Mth.cos(th2) * Mth.cos(ph2) * rx, y1 = cyy + Mth.sin(ph2) * ry, z1 = hz + Mth.sin(th2) * Mth.cos(ph2) * rx;
            FORK.clear();
            Lightning.jag(FORK, T, x0, y0, z0, x1, y1, z1, 0.28F, 3, false, 1F, 0.7F);
            GlowDraw.layered(vc, m, FORK, 0.6F, e.core, e.glow, 0.85F, bloom, false);
        }
    }

    /**
     * Phasing: kafa/govde hizasinda yatay, ince beyaz hiz cizgileri (titresimin "hareket bulaniklgi").
     * Her karede yeniden rastgele -> 60 Hz titreme. Hayalet kopyalar PhaseRenderer'da.
     */
    private static void drawPhaseStreaks(VertexConsumer vc, Matrix4f m, Player p, Entry e, float hx, float hy, float hz,
                                         float hScale, float pt, float bloom) {
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * ((float) Math.PI / 180F);
        float sideX = Mth.cos(yaw), sideZ = Mth.sin(yaw);       // govdenin sag-sol ekseni
        float fwdX = -Mth.sin(yaw), fwdZ = Mth.cos(yaw);         // bakis yonu
        long frame = Util.getMillis() / 16L;
        T.seed(e.seed ^ 0x9A5EL, frame, 5);
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.75F);
        float hr = GlowDraw.cr(hot), hg = GlowDraw.cg(hot), hb = GlowDraw.cb(hot);
        int n = 3 + (int) (T.next() * 4);
        for (int i = 0; i < n; i++) {
            boolean head = i < 2;
            float y = hy + (head ? T.range(1.45F, 1.8F) : T.range(0.5F, 1.4F)) * hScale;
            float f = T.range(0.05F, 0.35F);
            float c = T.signed() * 0.25F;
            float len = T.range(0.35F, 0.9F);
            float x0 = hx + fwdX * f + sideX * (c - len), z0 = hz + fwdZ * f + sideZ * (c - len);
            float x1 = hx + fwdX * f + sideX * (c + len), z1 = hz + fwdZ * f + sideZ * (c + len);
            float a = T.range(0.35F, 0.85F);
            GlowDraw.segment(vc, m, x0, y, z0, x1, y, z1, 0.012F, hr, hg, hb, 0F, a, false);
            GlowDraw.segment(vc, m, x1, y, z1, x0, y, z0, 0.012F, hr, hg, hb, 0F, a * 0.6F, false);
            GlowDraw.segment(vc, m, x0, y, z0, x1, y, z1, 0.05F * Math.max(bloom, 0.4F),
                    GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0F, a * 0.25F, false);
        }
    }

    // ------------------------------------------------------------------ tornado

    /**
     * Girdap: yerde parlayan iz halkasi, kosucunun arkasindan cemberi saran simsek halkalari (3 yukseklik),
     * yukari dogru genisleyen ve donen simsek hunisi, merkezde soluk bir isik sutunu.
     */
    private static void drawTornado(VertexConsumer vc, Matrix4f m, Entry e, Vec3 cam, float pt, long now, float bloom) {
        float k = e.tornadoPower();
        float kk = Mth.clamp(e.tSpeed / 5F, 0F, 1F); // gorsel daha erken dolsun
        if (kk < 0.02F) return;
        float R = ClientSpeedsters.TORNADO_RADIUS;
        float cx = (float) (e.tcx - cam.x), cy = (float) (e.tcy - cam.y), cz = (float) (e.tcz - cam.z);
        float theta = e.tornadoAngle(pt);
        float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.45F);
        float b = Math.max(bloom, 0.35F);

        // Yerdeki iz halkasi
        GlowDraw.ring(vc, m, cx, cy + 0.04F, cz, 1, 0, 0, 0, 0, 1, R, 0.55F, gr, gg, gb, 0.32F * kk * b, 48);
        GlowDraw.ring(vc, m, cx, cy + 0.05F, cz, 1, 0, 0, 0, 0, 1, R, 0.07F,
                GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.65F * kk, 48);
        GlowDraw.disc(vc, m, cx, cy + 0.03F, cz, 1, 0, 0, 0, 0, 1, R * 1.9F, gr, gg, gb, 0.09F * kk * b, 24);

        // Kosucunun arkasindan uzanan simsek halkalari
        float span = Math.min((float) (Math.PI * 2.0) * 1.05F, 0.7F + kk * (float) (Math.PI * 2.0));
        float[] heights = {0.25F, 0.95F, 1.65F};
        for (int layer = 0; layer < heights.length; layer++) {
            T.seed(e.seed ^ 0x70A2L, now, layer);
            long ns = e.seed * 17L + layer * 131L + now;
            Polyline line = LINE.clear();
            int n = 56;
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n;
                float a = theta - e.tDir * span * u;
                float r = R + Lightning.noise(ns, u * 22D) * 0.16F;
                float y = cy + heights[layer] + Lightning.noise(ns + 5, u * 19D) * 0.14F;
                float px = cx + Mth.cos(a) * r, pz = cz + Mth.sin(a) * r;
                float al = (float) Math.pow(1F - u, 1.4F) * kk;
                line.add(px, y, pz, al * camFade(px, y, pz), 1F + u * 0.6F);
            }
            GlowDraw.layered(vc, m, line, 0.9F, e.core, e.glow, 1F, bloom, false);
        }

        // Donen simsek hunisi
        if (k > 0.05F) {
            int strands = 7;
            float height = 3F + 9F * k;
            double time = (now + pt) * 0.05D;
            for (int s = 0; s < strands; s++) {
                long ns = e.seed * 29L + s * 977L + (now / 2);
                Polyline line = LINE.clear();
                int n = 28;
                for (int i = 0; i <= n; i++) {
                    float t = i / (float) n;
                    float r = R * (0.85F + 1.6F * t * t);
                    float a = s * (float) (Math.PI * 2.0) / strands + theta * 0.2F
                            + e.tDir * (t * 2.6F + (float) (time * (1.0 + 2.0 * k)));
                    float wob = Lightning.noise(ns, t * 9D) * 0.25F;
                    float px = cx + Mth.cos(a) * (r + wob), pz = cz + Mth.sin(a) * (r + wob);
                    float py = cy + 0.2F + t * height + Lightning.noise(ns + 3, t * 7D) * 0.2F;
                    float al = (float) Math.pow(1F - t, 1.3F) * Mth.clamp(t * 6F, 0F, 1F) * k * 0.75F;
                    line.add(px, py, pz, al * camFade(px, py, pz), 0.8F + t * 1.2F);
                }
                GlowDraw.layered(vc, m, line, 0.6F, e.core, e.glow, 1F, bloom, false);
            }
            // Merkez isik sutunu
            GlowDraw.orb(vc, m, cx, cy + 1.2F, cz, 2.2F + 2.5F * k, gr, gg, gb, 0.07F * k * b);
            GlowDraw.orb(vc, m, cx, cy + height * 0.5F, cz, 3.0F + 3.0F * k, gr, gg, gb, 0.05F * k * b);
        }
    }

    // ------------------------------------------------------------------ acilis efekti

    private static void drawActivation(VertexConsumer vc, Matrix4f m, Player p, Entry e, float hx, float hy, float hz,
                                       float hScale, float pt, long now, float bloom) {
        float t = (now - e.activatedAt) + pt;
        if (t < 0F || t > 24F) return;
        float k = t / 24F;
        float ease = 1F - (1F - k) * (1F - k) * (1F - k);
        float radius = 0.5F + ease * 7F;
        float a = (float) Math.pow(1F - k, 1.5);
        float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.4F);

        GlowDraw.ring(vc, m, hx, hy + 0.06F, hz, 1, 0, 0, 0, 0, 1, radius, 0.35F + radius * 0.06F, gr, gg, gb,
                0.55F * a * Math.max(bloom, 0.4F), 40);
        GlowDraw.ring(vc, m, hx, hy + 0.07F, hz, 1, 0, 0, 0, 0, 1, radius, 0.07F, GlowDraw.cr(hot), GlowDraw.cg(hot),
                GlowDraw.cb(hot), 0.9F * a, 40);

        if (t < 8F) {
            float f = 1F - t / 8F;
            float cyy = hy + 1.0F * hScale;
            GlowDraw.orb(vc, m, hx, cyy, hz, 1.4F + t * 0.25F, gr, gg, gb, 0.45F * f * f * Math.max(bloom, 0.4F));
            T.seed(e.seed ^ 0xAC71L, now, 11);
            for (int i = 0; i < 3; i++) {
                float ox = T.signed() * 2.5F, oz = T.signed() * 2.5F;
                FORK.clear();
                Lightning.jag(FORK, T, hx + ox, cyy + 7F + T.next() * 3F, hz + oz, hx, cyy, hz, 0.12F, 5, false,
                        0.3F * f, f);
                GlowDraw.layered(vc, m, FORK, 1.3F, e.core, e.glow, 1F, bloom, false);
            }
        }
    }

    // ------------------------------------------------------------------ ses duvari

    private static void drawBoom(VertexConsumer vc, Matrix4f m, Entry e, Vec3 cam, float pt, long now, float bloom) {
        float t = (now - e.lastBoom) + pt;
        if (t < 0F || t > 18F) return;
        float x = (float) (e.boomX - cam.x), y = (float) (e.boomY - cam.y), z = (float) (e.boomZ - cam.z);
        float ux = -e.boomDirZ, uz = e.boomDirX;
        float size = 0.6F + e.boomPower * 0.6F;
        int hot = GlowDraw.mixRgb(e.core, 0xFFFFFF, 0.5F);
        if (e.boomPower >= 1.4F) {
            // Guclu darbe (Blitz finali): yerde genisleyen halka + kisa beyaz parlama
            float k = Mth.clamp(t / 18F, 0F, 1F);
            float a = (1F - k) * (1F - k);
            float gr = GlowDraw.cr(e.glow), gg = GlowDraw.cg(e.glow), gb = GlowDraw.cb(e.glow);
            float gy = y - 1.05F;
            float rr = 0.8F + (1F - (1F - k) * (1F - k)) * 9F;
            GlowDraw.ring(vc, m, x, gy, z, 1, 0, 0, 0, 0, 1, rr, 0.5F + rr * 0.06F, gr, gg, gb, 0.5F * a * Math.max(bloom, 0.4F), 48);
            GlowDraw.ring(vc, m, x, gy + 0.01F, z, 1, 0, 0, 0, 0, 1, rr, 0.08F,
                    GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.85F * a, 48);
            if (t < 6F) {
                float fl = 1F - t / 6F;
                GlowDraw.orb(vc, m, x, y, z, 1.2F + t * 0.6F, 1F, 1F, 1F, 0.55F * fl * fl);
                GlowDraw.orb(vc, m, x, y, z, 2.5F + t * 0.9F, gr, gg, gb, 0.35F * fl * Math.max(bloom, 0.4F));
            }
        }
        for (int i = 0; i < 3; i++) {
            float tt = t - i * 2.5F;
            if (tt < 0F || tt > 14F) continue;
            float k = tt / 14F;
            float a = (1F - k) * (1F - k);
            float r = (0.6F + tt * 0.42F) * size;
            float px = x - e.boomDirX * tt * 0.25F * i, pz = z - e.boomDirZ * tt * 0.25F * i;
            GlowDraw.ring(vc, m, px, y, pz, ux, 0, uz, 0, 1, 0, r, 0.3F + r * 0.08F,
                    GlowDraw.cr(e.glow), GlowDraw.cg(e.glow), GlowDraw.cb(e.glow), 0.45F * a * Math.max(bloom, 0.4F), 36);
            GlowDraw.ring(vc, m, px, y, pz, ux, 0, uz, 0, 1, 0, r, 0.05F,
                    GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0.8F * a, 36);
        }
    }
}
