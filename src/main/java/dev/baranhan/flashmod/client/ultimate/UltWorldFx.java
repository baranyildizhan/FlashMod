package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import dev.baranhan.flashmod.client.render.SpeedTrailRenderer;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.ultimate.ArenaFrame;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * ARENA fazlarinin dunya ici VFX'i (herkes gorur), tamami bizim simsek/iz altyapimizla (Lightning + GlowDraw +
 * SpeedTrailRenderer): blink simsekleri ve acilis patlamasi, sarjda govdeden tasan simsekler ve zemin catlaklari,
 * ilk vurus patlamasi, ses duvari, IMPACT patlamasi (radyal dallanan simsekler + zemine yayilan catlaklar),
 * firlatilan hedefin izi, carpma patlamalari, goz parlamasi ve izleyiciler icin tepede donen kosucu izi.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltWorldFx {
    private static final Polyline LINE = new Polyline(), FORK = new Polyline();
    private static final Lightning.Rng R = new Lightning.Rng(77), B = new Lightning.Rng(78);
    private static final float[] P = new float[3], Q = new float[3];

    private UltWorldFx() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !UltDirector.sessions().iterator().hasNext()) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f(BodyPoseCapture.view());
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
            for (UltState s : UltDirector.sessions()) {
                if (s.abortAt >= 0) continue;
                float t = s.t(pt);
                if (t < 0F || t > UltimatePhase.DURATION + 5F) continue;
                if (s.full && UltDirector.scene(pt) == s) continue; // sahne ekrani kapliyor
                draw(vc, m, cam, s, t, pt, mc);
            }
            buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static float q() {
        return FlashClientConfig.ULT_QUALITY.get().mul;
    }

    /** Simsek sekillerinin yenilenme araligi (tick); isiga duyarli modda daha yavas titrer. */
    static int regen() {
        return FlashClientConfig.ULT_REDUCE_FLASHES.get() ? 6 : 2;
    }

    private static float bloom() {
        return Math.max(0.6F, FlashClientConfig.BLOOM.get().floatValue());
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, float pt, Minecraft mc) {
        ArenaFrame a = s.arena;
        Player caster = s.caster();
        int core = s.core, glow = s.glow;
        float vt = UltimatePhase.visual(t);
        boolean far = caster != null && caster.distanceToSqr(cam) > 64 * 64;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        long frame = (long) (vt / regen());

        // --- ACTIVATE: baslangic noktasindan arenaya blink simsekleri + iki uctaki simsek patlamalari
        if (t >= 1F && t < 6F) {
            float k = 1F - (t - 1F) / 5F;
            Vec3 o = a.toWorld(0, 1.0, 0), sp = s.startPos.add(0, 1.0, 0);
            R.seed(s.seed, frame, 3);
            for (int i = 0; i < 4; i++) {
                LINE.clear();
                Lightning.jag(LINE, R, rel(sp.x, cam.x), rel(sp.y + R.signed() * 0.4, cam.y), rel(sp.z, cam.z),
                        rel(o.x, cam.x), rel(o.y + R.signed() * 0.4, cam.y), rel(o.z, cam.z), 0.14F, 5, false, k, k * 0.6F);
                GlowDraw.layered(vc, m, LINE, 1.5F, core, glow, k, bloom(), false);
            }
        }
        if (t >= 1F && t < 6F && s.startPos.distanceToSqr(a.toWorld(0, 0, 0)) > 1.0) {
            Vec3 sp = s.startPos;
            burst(vc, m, cam, sp.add(0, 1.0, 0), sp.y + 0.03, 1.4F, (t - 1F) / 5F, (int) (8 * q()) + 4, s.seed ^ 0x51A7L,
                    frame, core, glow, 0.8F);
        }
        if (t >= 1F && t < 12F && !far) { // acilis patlamasi: caster'dan her yone dallanan simsekler
            Vec3 o = a.toWorld(0, 0, 0);
            burst(vc, m, cam, o.add(0, 1.1, 0), o.y + 0.03, 3.4F, (t - 1F) / 11F, (int) (18 * q()) + 6, s.seed ^ 0xB0057L,
                    frame, core, glow, 1F);
        }
        if (t >= 0F && t < 3F) { // hedef kilidi
            Vec3 tp = UltDirector.targetLive(s, pt);
            float u = t / 3F;
            GlowDraw.ring(vc, m, rel(tp.x, cam.x), rel(tp.y, cam.y), rel(tp.z, cam.z), 1, 0, 0, 0, 0, 1,
                    Mth.lerp(u, 0.6F, 1.4F), 0.05F, gr, gg, gb, 0.8F * (1F - u), 40);
        }

        // --- govde: damarlar; sarjda govdeden tasan simsekler ve zemin catlaklari (sarj ilerledikce artar)
        BodyPoseCapture.Pose body = caster != null ? BodyPoseCapture.get(caster) : null;
        float vein = veinIntensity(vt);
        if (body != null && vein > 0.01F && !far) {
            veins(vc, m, cam, body, s, vt, vein, core, glow);
            UltimatePhase w = UltimatePhase.WINDUP;
            if (vt >= w.start && vt < w.end) {
                float c = w.local(vt);
                R.seed(s.seed ^ 0xA2C5L, frame, 1);
                int n = 2 + (int) (c * 6F + R.next() * 2F);
                body.point(BodyPoseCapture.BODY, 0, 6, 0, cam, P);
                for (int i = 0; i < n; i++) {
                    float dx = R.signed(), dy = R.signed() * 0.6F - 0.25F, dz = R.signed();
                    float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F, len = (0.5F + R.next()) * (0.8F + 1.4F * c);
                    LINE.clear();
                    Lightning.jag(LINE, R, P[0], P[1], P[2], P[0] + dx / l * len, P[1] + dy / l * len, P[2] + dz / l * len,
                            0.3F, 4, false, 1F, 0.1F);
                    GlowDraw.layered(vc, m, LINE, 0.6F + 0.4F * c, core, glow, 0.9F, bloom(), false);
                }
                Vec3 f = caster.getPosition(pt);
                groundCracks(vc, m, cam, f, 4 + (int) (c * 4F), 0.7F + 1.6F * c, s.seed ^ 0x5EEDL, frame, core, glow, 0.9F);
                if (vt >= 24F) { // sarjin zirvesi: kucuk bir patlama
                    burst(vc, m, cam, f.add(0, 1.0, 0), f.y + 0.03, 2.2F, (vt - 24F) / 6F, (int) (10 * q()) + 4,
                            s.seed ^ 0x24L, frame, core, glow, 0.8F);
                }
            }
        }

        // --- ilk vurus: temas noktasinda kucuk patlama + halka, hedefin altinda catlaklar
        if (vt >= UltimatePhase.HIT1 && vt < UltimatePhase.HIT1 + 9F) {
            float u = (vt - UltimatePhase.HIT1) / 9F;
            Vec3 c = s.contact(vt);
            burst(vc, m, cam, c, Double.NaN, 1.6F, u, (int) (10 * q()) + 4, s.seed ^ 0x36L, frame, core, glow, 1F);
            GlowDraw.ring(vc, m, rel(c.x, cam.x), rel(c.y, cam.y), rel(c.z, cam.z), (float) a.rx(), 0, (float) a.rz(), 0, 1, 0,
                    Mth.lerp(u, 0.4F, 2.2F), 0.05F, gr, gg, gb, 0.7F * (1F - u), 40);
            Vec3 feet = a.toWorld(0, 0, s.dAt(vt));
            feet = new Vec3(feet.x, s.targetBase.y, feet.z);
            groundCracks(vc, m, cam, feet, 5, 1.3F * (0.4F + 0.6F * Math.min(1F, u * 3F)), s.seed ^ 0x3636L, frame, core, glow,
                    1F - u);
        }
        // ses duvari: kacarken
        if (vt >= 44F && vt < 50F) {
            Vec3 c = a.toWorld(UltRender.proxyArena(44F, a.d)).add(0, 1.0, 0);
            float u = (vt - 44F) / 6F;
            GlowDraw.ring(vc, m, rel(c.x, cam.x), rel(c.y, cam.y), rel(c.z, cam.z),
                    (float) a.rx(), 0, (float) a.rz(), 0, 1, 0, Mth.lerp(u, 0.5F, 3.0F), 0.08F * (1F - u) + 0.02F,
                    1F, 1F, 1F, 0.8F * (1F - u), 48);
        }

        // --- IMPACT
        if (t >= UltimatePhase.HIT2 && t < UltimatePhase.HIT2 + 16F) impact(vc, m, cam, s, t, vt, frame, far, core, glow);

        // --- LAUNCH: firlatilan hedefin izi (bizim iz sistemimiz; node'lar UltDirector'da her tick)
        if (t >= UltimatePhase.LAUNCH_T && !far && !s.targetTrail.nodes.isEmpty()) {
            LivingEntity tg = s.target();
            Vec3 head = tg != null && tg.isAlive() ? tg.getPosition(pt) : s.lastTargetPos;
            long now = mc.level.getGameTime();
            float h = tg != null ? tg.getBbHeight() / 1.8F : 1F;
            SpeedTrailRenderer.drawSynthetic(vc, m, s.targetTrail, cam, rel(head.x, cam.x), rel(head.y, cam.y),
                    rel(head.z, cam.z), h, null, cam, now, pt, bloom(), 6, false, false);
        }

        // --- carpma patlamalari
        for (UltState.Crash c : s.crashes) {
            float age = mc.level.getGameTime() - c.gameTime() + pt;
            if (age > 14F) continue;
            float[] ux = new float[3], vx = new float[3];
            basis(c.nx(), c.ny(), c.nz(), ux, vx);
            float x = rel(c.pos().x, cam.x), y = rel(c.pos().y, cam.y), z = rel(c.pos().z, cam.z);
            int rings = c.slam() ? 1 : 2;
            for (int i = 0; i < rings; i++) {
                float u = (age - i * 2F) / 10F;
                if (u < 0F || u > 1F) continue;
                float rmax = (c.slam() ? 3F : 3.5F - i);
                GlowDraw.ring(vc, m, x, y, z, ux[0], ux[1], ux[2], vx[0], vx[1], vx[2], Mth.lerp(u, 0.5F, rmax),
                        0.06F * (1F - u) + 0.02F, 0.85F, 0.92F, 1F, 0.8F * (1F - u), 48);
            }
            burst(vc, m, cam, c.pos(), Double.NaN, 2.0F, age / 14F, (int) (10 * q()) + 4, s.seed ^ c.gameTime(),
                    (long) (age / regen()), core, glow, 0.9F);
        }

        // --- goz parlamasi (gercek govde, darbeden sonra)
        if (body != null && t >= UltimatePhase.HIT2 && t < UltimatePhase.HIT2 + 17F) {
            eyes(vc, m, cam, body, t < UltimatePhase.HITSTOP_END ? 1F : 1F - (t - UltimatePhase.HITSTOP_END) / 13F, core, glow);
        }

        // --- izleyiciler: tepede donen kosucu izi
        if (!s.full && t >= UltimatePhase.SCENE_START && t < UltimatePhase.SCENE_END) orbitWorld(vc, m, cam, s, t, pt, core, glow);
    }

    private static float veinIntensity(float vt) {
        if (vt >= UltimatePhase.WINDUP.start && vt < UltimatePhase.WINDUP.end) return 0.6F + 0.4F * UltimatePhase.WINDUP.local(vt);
        if (vt >= UltimatePhase.DEPART.start && vt < UltimatePhase.DEPART.end) return 1.0F;
        if (vt >= UltimatePhase.HIT2 && vt < UltimatePhase.LAUNCH.start) return 1.2F;
        float fadeEnd = UltimatePhase.LAUNCH.start + 30F;
        if (vt >= UltimatePhase.LAUNCH.start && vt < fadeEnd) return 0.5F * (1F - (vt - UltimatePhase.LAUNCH.start) / 30F);
        return 0F;
    }

    // ---------------------------------------------------------------- simsek patlamasi

    /**
     * Bizim simseklerle patlama: merkezden her yone dallanan zikzak simsekler (boy hizla buyur, sonra soner),
     * zemine yayilan catlak simsekleri (groundY NaN degilse), kisa bir sicak cekirdek ve ince bir sok halkasi.
     * Yonler oturum tohumuyla sabit (patlama "titremez"), kirilmalar her regen tick'te yenilenir (canli cirpinma).
     * age 0..1; center dunya koordinati.
     */
    public static void burst(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 center, double groundY, float radius, float age,
                             int bolts, long seed, long frame, int core, int glow, float alpha) {
        if (age < 0F || age >= 1F || alpha <= 0.01F) return;
        float x = rel(center.x, cam.x), y = rel(center.y, cam.y), z = rel(center.z, cam.z);
        float grow = UltCamera.Ease.OUT_EXPO.apply(Math.min(1F, age / 0.3F));
        float fade = age < 0.25F ? 1F : 1F - (age - 0.25F) / 0.75F;
        fade = fade * fade * alpha;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.6F);
        boolean ground = !Double.isNaN(groundY);
        float bl = Math.min(0.8F, bloom()); // cok sayida simsek ust uste binince beyaza doymasin
        float scale = 0.7F + radius * 0.12F;
        for (int i = 0; i < bolts; i++) {
            B.seed(seed, i, 0x0B);
            // dengeli kure dagilimi (altin aci spirali) + kucuk sapma
            float zz = 1F - 2F * (i + 0.5F) / bolts;
            float rr = Mth.sqrt(Math.max(0F, 1F - zz * zz)), th = i * 2.39996F + B.signed() * 0.4F;
            float dx = Mth.cos(th) * rr, dy = zz, dz = Mth.sin(th) * rr;
            if (ground && dy < -0.25F) dy = -0.25F + (dy + 0.25F) * 0.2F; // yere gomulmesin
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
            float len = radius * (0.55F + 0.45F * B.next()) * grow;
            float ex = x + dx / l * len, ey = y + dy / l * len, ez = z + dz / l * len;
            R.seed(seed ^ 0x5A5AL, frame, i);
            LINE.clear();
            // merkezden biraz disaridan baslar: yuzlerce halo ayni noktada ust uste binip beyaz leke olmasin
            float sx = x + dx / l * radius * 0.08F, sy = y + dy / l * radius * 0.08F, sz = z + dz / l * radius * 0.08F;
            Lightning.jag(LINE, R, sx, sy, sz, ex, ey, ez, 0.26F, 4, false, fade, fade * 0.1F);
            GlowDraw.layered(vc, m, LINE, scale, core, glow, 1F, bl, false);
            if (R.next() < 0.6F && LINE.size > 6) { // dal
                int j = 4 + (int) (R.next() * (LINE.size - 8));
                float fx = LINE.x[j], fy = LINE.y[j], fz = LINE.z[j], fa = LINE.a[j];
                float bx = dx / l + R.signed() * 0.8F, by = dy / l + R.signed() * 0.8F, bz = dz / l + R.signed() * 0.8F;
                float bl2 = Mth.sqrt(bx * bx + by * by + bz * bz) + 1.0E-4F, blen = len * (0.25F + 0.25F * R.next());
                FORK.clear();
                Lightning.jag(FORK, R, fx, fy, fz, fx + bx / bl2 * blen, fy + by / bl2 * blen, fz + bz / bl2 * blen,
                        0.3F, 3, false, fa, 0F);
                GlowDraw.layered(vc, m, FORK, scale * 0.7F, core, glow, 1F, bl, false);
            }
        }
        if (ground) {
            groundCracks(vc, m, cam, new Vec3(center.x, groundY, center.z), Math.max(4, bolts / 2), radius * 1.3F * grow,
                    seed ^ 0x6A0DL, frame, core, glow, fade);
            float u = Math.min(1F, age / 0.6F);
            GlowDraw.ring(vc, m, x, rel(groundY + 0.04, cam.y), z, 1, 0, 0, 0, 0, 1, radius * (0.3F + 1.5F * u),
                    0.04F + 0.1F * (1F - u), gr, gg, gb, 0.55F * (1F - u) * alpha, 56);
        }
        float flash = Math.max(0F, 1F - age / 0.18F);
        if (flash > 0F) { // kisa sicak cekirdek (ekrani kaplamaz)
            GlowDraw.orb(vc, m, x, y, z, radius * 0.22F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), flash * alpha * 0.8F);
        }
        GlowDraw.orb(vc, m, x, y, z, radius * 0.8F, gr, gg, gb, 0.12F * fade);
    }

    /** Zemine yapisik catlak simsekleri: merkezden disari, y sabit. */
    static void groundCracks(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 at, int n, float reach, long seed, long frame,
                             int core, int glow, float alpha) {
        if (alpha <= 0.01F || reach <= 0.05F) return;
        float x = rel(at.x, cam.x), y = rel(at.y + 0.03, cam.y), z = rel(at.z, cam.z);
        for (int i = 0; i < n; i++) {
            B.seed(seed, i, 0x6C);
            float ang = (i + B.next() * 0.8F) / n * 6.2832F, len = reach * (0.6F + 0.4F * B.next());
            R.seed(seed, frame, i);
            LINE.clear();
            Lightning.jag(LINE, R, x, y, z, x + Mth.cos(ang) * len, y, z + Mth.sin(ang) * len, 0.22F, 4, false, alpha, 0F);
            for (int j = 0; j < LINE.size; j++) LINE.y[j] = y; // zemine yapistir
            GlowDraw.layered(vc, m, LINE, 0.55F, core, glow, 0.9F, bloom(), false);
        }
    }

    /** Kemik cizgileri boyunca simsek damarlari (pozlanmis model parcalarindan), her regen tick'te yeniden. */
    public static void veins(VertexConsumer vc, Matrix4f m, Vec3 cam, BodyPoseCapture.Pose body, UltState s, float vt,
                             float intensity, int core, int glow) {
        R.seed(s.seed ^ 0xBE11L, (long) (vt / regen()), 0);
        for (int part = 0; part < BodyPoseCapture.PARTS; part++) {
            float[] b = BodyPoseCapture.BOX[part];
            float cx = (b[0] + b[3]) * 0.5F, cz = (b[2] + b[5]) * 0.5F;
            body.point(part, cx, b[1], cz, cam, P);
            body.point(part, cx, b[4], cz, cam, Q);
            LINE.clear();
            Lightning.jag(LINE, R, P[0], P[1], P[2], Q[0], Q[1], Q[2], 0.18F, 3, false, 1F, 0.8F);
            GlowDraw.layered(vc, m, LINE, 0.32F * intensity, core, glow, Math.min(1F, intensity), 1F, false);
            if (R.next() < 0.25F) { // dal
                int i = 2 + (int) (R.next() * 4);
                i = Math.min(i, LINE.size - 1);
                float bx = LINE.x[i], by = LINE.y[i], bz = LINE.z[i];
                LINE.clear();
                Lightning.jag(LINE, R, bx, by, bz, bx + R.signed() * 0.25F, by + R.signed() * 0.25F, bz + R.signed() * 0.25F,
                        0.3F, 2, false, 1F, 0F);
                GlowDraw.layered(vc, m, LINE, 0.22F * intensity, core, glow, Math.min(1F, intensity), 1F, false);
            }
        }
    }

    public static void eyes(VertexConsumer vc, Matrix4f m, Vec3 cam, BodyPoseCapture.Pose body, float k, int core, int glow) {
        if (k <= 0.01F) return;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.6F);
        for (int side = -1; side <= 1; side += 2) {
            body.point(BodyPoseCapture.HEAD, side * 2F, -4F, -4.1F, cam, P);
            GlowDraw.orb(vc, m, P[0], P[1], P[2], 0.3F * k, gr, gg, gb, 0.5F * k);
            GlowDraw.orb(vc, m, P[0], P[1], P[2], 0.08F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), k);
            if (k > 0.7F) { // anamorfik flare taklidi
                body.point(BodyPoseCapture.HEAD, side * 2F + side * 9.6F, -4F, -4.1F, cam, Q);
                GlowDraw.segment(vc, m, P[0], P[1], P[2], Q[0], Q[1], Q[2], 0.025F, gr, gg, gb, (k - 0.7F) * 2F, 0F, false);
            }
        }
    }

    private static void impact(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, float vt, long frame,
                               boolean far, int core, int glow) {
        ArenaFrame a = s.arena;
        Vec3 c = s.contact();
        float reduce = FlashClientConfig.ULT_REDUCE_FLASHES.get() ? 0.7F : 1F;
        int warm = GlowDraw.mixRgb(0xFFD9A0, glow, 0.5F);
        // hit-stop boyunca patlama "donmus" kalir (vt), sonra 12 tick'te acilip soner
        float age = (vt - UltimatePhase.HIT2) / 14F;
        Vec3 feet = UltDirector.targetLive(s, 0F).subtract(0, s.targetH * 0.5, 0);
        if (!far) {
            burst(vc, m, cam, c, feet.y, 4.6F * reduce, age, (int) (14 * q()) + 6, s.seed ^ 0x1A2BL, frame, core, glow, 1F);
            groundCracks(vc, m, cam, feet, 8, 7.5F * Math.min(1F, age * 4F) * reduce, s.seed ^ 0x6E0L, frame, core, glow,
                    Math.max(0F, 1F - age));
        } else {
            float x = rel(c.x, cam.x), y = rel(c.y, cam.y), z = rel(c.z, cam.z);
            GlowDraw.orb(vc, m, x, y, z, 3F, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.6F * (1F - age));
            return;
        }
        float x = rel(c.x, cam.x), y = rel(c.y, cam.y), z = rel(c.z, cam.z);
        // dikey sok halkalari (normal = forward)
        for (int i = 0; i < 2; i++) {
            float u = (t - UltimatePhase.HIT2 - i * 2F) / 10F;
            if (u < 0F || u > 1F) continue;
            float r = Mth.lerp(u, 0.6F, 5.0F) * (i == 0 ? 1F : 0.7F);
            GlowDraw.ring(vc, m, x, y, z, (float) a.rx(), 0, (float) a.rz(), 0, 1, 0, r, Mth.lerp(u, 0.12F, 0.03F),
                    1F, 0.9F, 0.75F, 0.7F * (1F - u), 56);
        }
        // caster'in arkasindan gelen varis simsekleri (tunelden donus): -Z boyunca sonen zikzaklar
        if (t < UltimatePhase.HIT2 + 8F) {
            float al = 1F - (t - UltimatePhase.HIT2) / 8F;
            float d = a.d + s.push;
            for (int i = 0; i < 6; i++) {
                B.seed(s.seed ^ 0x99L, i, 3);
                double h = 0.3 + B.next() * 1.5, lx = -0.15 + B.signed() * 0.35;
                Vec3 p0 = a.toWorld(lx, h, d - 1.1), p1 = a.toWorld(lx + B.signed() * 0.6, h + B.signed() * 0.5, d - 7.0 - B.next() * 5.0);
                R.seed(s.seed ^ 0x99L, frame, i);
                LINE.clear();
                Lightning.jag(LINE, R, rel(p0.x, cam.x), rel(p0.y, cam.y), rel(p0.z, cam.z), rel(p1.x, cam.x),
                        rel(p1.y, cam.y), rel(p1.z, cam.z), 0.12F, 5, false, al, 0F);
                GlowDraw.layered(vc, m, LINE, 0.9F, core, glow, 1F, bloom(), false);
            }
        }
    }

    /**
     * Izleyici versiyonu: arena merkezinin 14 blok ustunde, hizlanan bir cemberde kosan isik. Iz bizim iz sistemimiz;
     * sonda geri gelecegi noktaya dogru alcalir. Tur suresi ilk 40 tick'te 30'dan 8 tick'e iner.
     */
    private static void orbitWorld(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, float pt, int core, int glow) {
        ArenaFrame a = s.arena;
        Vec3 center = a.toWorld(0, 14, a.d * 0.5);
        Vec3 back = a.toWorld(0, 0.8, -10);
        float t0 = UltimatePhase.SCENE_START, descStart = UltimatePhase.SCENE_END - 20F;
        UltScene.TrailPath path = new UltScene.TrailPath() {
            private double ang(float tt) {
                double x = Math.max(0F, tt - t0);
                if (x < 40.0) return 2 * Math.PI * (x / 30.0 + (1.0 / 8.0 - 1.0 / 30.0) * x * x / 80.0);
                return 2 * Math.PI * (40.0 / 30.0 + (1.0 / 8.0 - 1.0 / 30.0) * 20.0) + 2 * Math.PI * (x - 40.0) / 8.0;
            }

            @Override
            public Vec3 at(float tt) {
                double desc = tt >= descStart ? Math.min(1.0, (tt - descStart) / 20.0) : 0.0;
                double r = 18 * (1.0 - desc * 0.6), an = ang(tt);
                Vec3 p = center.add(Math.cos(an) * r, -desc * 13.0, Math.sin(an) * r);
                return p.add(back.subtract(center).multiply(desc, 0, desc));
            }

            @Override
            public double odo(float tt) {
                return ang(tt) * 18.0;
            }
        };
        float vt = t;
        long now = (long) Math.floor(vt);
        UltScene.fillNodes(s.sceneTrail, path, vt, t0, 10, 1F, 1F);
        if (s.sceneTrail.nodes.isEmpty()) return;
        Vec3 head = path.at(vt);
        SpeedTrailRenderer.drawSynthetic(vc, m, s.sceneTrail, cam, rel(head.x, cam.x), rel(head.y, cam.y), rel(head.z, cam.z),
                1F, null, cam, now, vt - now, bloom(), 6, false, false);
        int warm = GlowDraw.mixRgb(0xFFB040, glow, 0.5F);
        GlowDraw.orb(vc, m, rel(head.x, cam.x), rel(head.y, cam.y), rel(head.z, cam.z), 1.4F, GlowDraw.cr(warm),
                GlowDraw.cg(warm), GlowDraw.cb(warm), 0.6F);
        // her turda bir kez asagi simsek
        long turn = (long) (path.odo(vt) / (2 * Math.PI * 18.0));
        R.seed(s.seed ^ 0x0B17L, turn, 5);
        if (R.next() < 0.35F && (path.odo(vt) / (2 * Math.PI * 18.0)) % 1.0 < 0.25) {
            Vec3 ground = a.toWorld(R.signed() * 4, 0.1, a.d * 0.5 + R.signed() * 4);
            LINE.clear();
            Lightning.jag(LINE, R, rel(head.x, cam.x), rel(head.y, cam.y), rel(head.z, cam.z), rel(ground.x, cam.x),
                    rel(ground.y, cam.y), rel(ground.z, cam.z), 0.2F, 5, false, 1F, 0.5F);
            GlowDraw.layered(vc, m, LINE, 0.8F, core, glow, 0.9F, bloom(), false);
        }
        // donusten once geri gelecegi yerde isik birikimi
        if (t >= UltimatePhase.SCENE_END - 8F) {
            float k = (t - (UltimatePhase.SCENE_END - 8F)) / 8F;
            GlowDraw.orb(vc, m, rel(back.x, cam.x), rel(back.y, cam.y), rel(back.z, cam.z), 0.5F + 2.5F * k,
                    GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.4F + 0.5F * k);
        }
    }

    /** n'ye dik iki birim vektor. */
    public static void basis(float nx, float ny, float nz, float[] u, float[] v) {
        float ax = Math.abs(ny) < 0.9F ? 0F : 1F, ay = Math.abs(ny) < 0.9F ? 1F : 0F, az = 0F;
        float ux = ay * nz - az * ny, uy = az * nx - ax * nz, uz = ax * ny - ay * nx;
        float l = Mth.sqrt(ux * ux + uy * uy + uz * uz) + 1.0E-6F;
        u[0] = ux / l; u[1] = uy / l; u[2] = uz / l;
        v[0] = ny * u[2] - nz * u[1];
        v[1] = nz * u[0] - nx * u[2];
        v[2] = nx * u[1] - ny * u[0];
    }

    static float rel(double w, double c) {
        return (float) (w - c);
    }
}
