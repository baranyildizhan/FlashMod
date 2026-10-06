package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
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
 * ARENA fazlarinin dunya ici VFX'i (herkes gorur): blink izi, kilit halkasi, govdede simsek damarlari ve yaylar,
 * zemin catlaklari, DEPART hiz seridi / vurus / ses duvari, IMPACT patlamasi / isinlar / sok halkalari / arka izler,
 * LAUNCH hedef izi, carpma halkalari, goz parlamasi ve izleyiciler icin tepede donen yorunge simsegi.
 * Cizim bizim simsek altyapimizla (GlowDraw + Lightning, additive, kameraya goreli + BodyPoseCapture.view()).
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class UltWorldFx {
    private static final Polyline LINE = new Polyline();
    private static final Lightning.Rng R = new Lightning.Rng(77);
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
                if (t < 0F || t > 205F) continue;
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

    private static int regen() {
        return FlashClientConfig.ULT_REDUCE_FLASHES.get() ? 6 : 2;
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, float pt, Minecraft mc) {
        ArenaFrame a = s.arena;
        Player caster = s.caster();
        int core = s.core, glow = s.glow;
        float vt = UltimatePhase.visual(t);
        boolean far = caster != null && caster.distanceToSqr(cam) > 64 * 64;
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);

        // --- ACTIVATE: blink izi + kilit halkasi
        if (t >= 1F && t < 4F) {
            float k = 1F - (t - 1F) / 3F;
            Vec3 o = a.toWorld(0, 1.0, 0), sp = s.startPos.add(0, 1.0, 0);
            R.seed(s.seed, (long) (t * 4), 3);
            for (int i = 0; i < 3; i++) {
                LINE.clear();
                Lightning.jag(LINE, R, rel(sp.x, cam.x), rel(sp.y + R.signed() * 0.3, cam.y), rel(sp.z, cam.z),
                        rel(o.x, cam.x), rel(o.y + R.signed() * 0.3, cam.y), rel(o.z, cam.z), 0.12F, 5, false, k, k * 0.6F);
                GlowDraw.layered(vc, m, LINE, 1.6F, core, glow, k, 1F, false);
            }
            GlowDraw.orb(vc, m, rel(sp.x, cam.x), rel(sp.y, cam.y), rel(sp.z, cam.z), 0.8F, gr, gg, gb, 0.7F * k);
        }
        if (t >= 0F && t < 2F) {
            Vec3 tp = UltDirector.targetLive(s, pt);
            float u = t / 2F;
            GlowDraw.ring(vc, m, rel(tp.x, cam.x), rel(tp.y, cam.y), rel(tp.z, cam.z), 1, 0, 0, 0, 0, 1,
                    Mth.lerp(u, 0.6F, 1.4F), 0.05F, gr, gg, gb, 0.8F * (1F - u), 40);
        }

        // --- govde: damarlar, yaylar, zemin catlaklari
        BodyPoseCapture.Pose body = caster != null ? BodyPoseCapture.get(caster) : null;
        float vein = veinIntensity(vt);
        if (body != null && vein > 0.01F && !far) {
            veins(vc, m, cam, body, s, vt, vein, core, glow);
            if (vt >= 4F && vt < 22F) {
                long g = (long) (vt / regen());
                R.seed(s.seed ^ 0xA2C5L, g, 1);
                int n = 2 + (int) (R.next() * 3);
                body.point(BodyPoseCapture.BODY, 0, 6, 0, cam, P);
                for (int i = 0; i < n; i++) {
                    float dx = R.signed(), dy = R.signed() * 0.7F, dz = R.signed();
                    float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F, len = 0.5F + R.next();
                    LINE.clear();
                    Lightning.jag(LINE, R, P[0], P[1], P[2], P[0] + dx / l * len, P[1] + dy / l * len, P[2] + dz / l * len,
                            0.3F, 3, false, 1F, 0.2F);
                    GlowDraw.layered(vc, m, LINE, 0.6F, core, glow, 0.9F, 1F, false);
                }
                // zemin catlaklari (yer duzlemine yapisik)
                Vec3 f = caster.getPosition(pt);
                R.seed(s.seed ^ 0x5EEDL, g, 2);
                for (int i = 0; i < 4; i++) {
                    float ang = R.next() * 6.283F, len = 0.6F + 0.6F * R.next();
                    LINE.clear();
                    Lightning.jag(LINE, R, rel(f.x, cam.x), rel(f.y + 0.03, cam.y), rel(f.z, cam.z),
                            rel(f.x + Mth.cos(ang) * len, cam.x), rel(f.y + 0.03, cam.y), rel(f.z + Mth.sin(ang) * len, cam.z),
                            0.25F, 3, false, 0.9F, 0F);
                    GlowDraw.layered(vc, m, LINE, 0.45F, core, glow, 0.8F, 1F, false);
                }
            }
        }

        // --- DEPART: proxy arkasinda dikey hiz seridi, vurus, ses duvari
        if (vt >= 22F && vt < 40F && !far) {
            for (int i = 0; i < 12; i++) {
                float t0 = vt - i * 0.5F, t1 = vt - (i + 1) * 0.5F;
                if (t1 < 22F) break;
                Vec3 p0 = a.toWorld(UltRender.proxyArena(t0, a.d)), p1 = a.toWorld(UltRender.proxyArena(t1, a.d));
                float al0 = 0.55F * (1F - i / 12F), al1 = 0.55F * (1F - (i + 1) / 12F);
                for (float h = 0.1F; h < 1.5F; h += 0.35F) {
                    GlowDraw.segment(vc, m, rel(p0.x, cam.x), rel(p0.y + h, cam.y), rel(p0.z, cam.z),
                            rel(p1.x, cam.x), rel(p1.y + h, cam.y), rel(p1.z, cam.z), 0.22F, gr, gg, gb, al0, al1, false);
                }
            }
        }
        if (vt >= 26F && vt < 30F) {
            Vec3 c = UltDirector.targetLive(s, pt);
            float u = (vt - 26F) / 4F;
            GlowDraw.orb(vc, m, rel(c.x, cam.x), rel(c.y, cam.y), rel(c.z, cam.z), 0.9F * (1F - u * 0.5F), 1F, 1F, 1F, 1F - u);
            GlowDraw.ring(vc, m, rel(c.x, cam.x), rel(c.y, cam.y), rel(c.z, cam.z), 1, 0, 0, 0, 0, 1,
                    Mth.lerp(u, 0.5F, 2.0F), 0.04F, gr, gg, gb, 0.8F * (1F - u), 40);
            sparks(vc, m, cam, c, s.seed ^ 26, 6, 0.9F, 1F - u, core, glow);
        }
        if (vt >= 33F && vt < 39F) {
            Vec3 c = a.toWorld(UltRender.proxyArena(33F, a.d)).add(0, 1.0, 0);
            float u = (vt - 33F) / 6F;
            GlowDraw.ring(vc, m, rel(c.x, cam.x), rel(c.y, cam.y), rel(c.z, cam.z),
                    (float) a.rx(), 0, (float) a.rz(), 0, 1, 0, Mth.lerp(u, 0.5F, 3.0F), 0.08F * (1F - u) + 0.02F,
                    1F, 1F, 1F, 0.8F * (1F - u), 48);
        }

        // --- IMPACT
        if (t >= 158F && t < 172F) impact(vc, m, cam, s, t, vt, far, core, glow);

        // --- LAUNCH: hedefin arkasinda yatay izler
        if (t >= 162F && t < 188F && !far) {
            LivingEntity tg = s.target();
            if (tg != null && tg.isAlive()) {
                Vec3 p = tg.getPosition(pt).add(0, tg.getBbHeight() * 0.5, 0);
                Vec3 v = tg.getDeltaMovement();
                double sp = v.length();
                if (sp > 0.2) {
                    Vec3 back = v.scale(-1.0 / sp);
                    float al = Mth.clamp((188F - t) / 10F, 0F, 1F) * 0.6F;
                    R.seed(s.seed ^ 0x7A11L, 1, 1);
                    for (int i = 0; i < 6; i++) {
                        double oy = (R.next() - 0.5) * tg.getBbHeight() * 0.8, ox = (R.next() - 0.5) * 0.6;
                        float len = (float) Math.min(6.0, sp * 3.0) * (0.6F + 0.4F * R.next());
                        double sx = p.x + a.rx() * ox, sz = p.z + a.rz() * ox, sy = p.y + oy;
                        GlowDraw.segment(vc, m, rel(sx, cam.x), rel(sy, cam.y), rel(sz, cam.z),
                                rel(sx + back.x * len, cam.x), rel(sy + back.y * len, cam.y), rel(sz + back.z * len, cam.z),
                                0.05F, 0.8F, 0.9F, 1F, al, 0F, false);
                    }
                }
            }
        }

        // --- carpma halkalari
        if (mc.level != null) {
            for (UltState.Crash c : s.crashes) {
                float age = mc.level.getGameTime() - c.gameTime() + pt;
                if (age > 14F) continue;
                float[] ux = new float[3], vx = new float[3];
                basis(c.nx(), c.ny(), c.nz(), ux, vx);
                float x = rel(c.pos().x, cam.x), y = rel(c.pos().y, cam.y), z = rel(c.pos().z, cam.z);
                int rings = c.slam() ? 1 : 3;
                for (int i = 0; i < rings; i++) {
                    float u = (age - i * 2F) / 10F;
                    if (u < 0F || u > 1F) continue;
                    float rmax = (c.slam() ? 3F : 4F - i);
                    GlowDraw.ring(vc, m, x, y, z, ux[0], ux[1], ux[2], vx[0], vx[1], vx[2], Mth.lerp(u, 0.5F, rmax),
                            0.08F * (1F - u) + 0.02F, 0.85F, 0.92F, 1F, 0.9F * (1F - u), 48);
                }
                if (age < 6F) GlowDraw.orb(vc, m, x, y, z, 1.5F, 1F, 1F, 1F, 1F - age / 6F);
            }
        }

        // --- goz parlamasi (gercek govde, 158-175)
        if (body != null && t >= 158F && t < 175F) eyes(vc, m, cam, body, t < 162F ? 1F : 1F - (t - 162F) / 13F, core, glow);

        // --- izleyiciler: tepede donen yorunge simsegi
        if (!s.full && t >= 40F && t < 158F) orbitWorld(vc, m, cam, s, t, core, glow);
    }

    private static float veinIntensity(float vt) {
        if (vt >= 4F && vt < 22F) return 0.6F + 0.4F * (vt - 4F) / 18F;
        if (vt >= 22F && vt < 40F) return 1.0F;
        if (vt >= 158F && vt < 166F) return 1.2F;
        if (vt >= 166F && vt < 196F) return 0.5F * (1F - (vt - 166F) / 30F);
        return 0F;
    }

    /** Kemik cizgileri boyunca simsek damarlari (pozlanmis model parcalarindan), her 2 tick'te yeniden. */
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
            GlowDraw.orb(vc, m, P[0], P[1], P[2], 0.35F * k, gr, gg, gb, 0.6F * k);
            GlowDraw.orb(vc, m, P[0], P[1], P[2], 0.09F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), k);
            if (k > 0.7F) { // anamorfik flare taklidi
                body.point(BodyPoseCapture.HEAD, side * 2F + side * 9.6F, -4F, -4.1F, cam, Q);
                GlowDraw.segment(vc, m, P[0], P[1], P[2], Q[0], Q[1], Q[2], 0.025F, gr, gg, gb, (k - 0.7F) * 2F, 0F, false);
            }
        }
    }

    private static void impact(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, float vt, boolean far,
                               int core, int glow) {
        ArenaFrame a = s.arena;
        Vec3 c = s.contact();
        float x = rel(c.x, cam.x), y = rel(c.y, cam.y), z = rel(c.z, cam.z);
        float reduce = FlashClientConfig.ULT_REDUCE_FLASHES.get() ? 0.7F : 1F;
        int warm = GlowDraw.mixRgb(0xFFD9A0, glow, 0.5F);
        float size, alpha;
        if (t < 162F) { size = Mth.lerp((t - 158F) / 4F, 1.5F, 4.0F); alpha = 1F; }
        else { float u = Math.min(1F, (t - 162F) / 4F); size = Mth.lerp(u, 4.0F, 5.0F); alpha = 1F - u; }
        size *= reduce;
        if (alpha > 0F) {
            GlowDraw.orb(vc, m, x, y, z, size * 1.6F, GlowDraw.cr(warm), GlowDraw.cg(warm), GlowDraw.cb(warm), 0.7F * alpha);
            GlowDraw.orb(vc, m, x, y, z, size, 1F, 1F, 1F, alpha);
        }
        if (far) return;
        // isin patlamasi (ekran duzleminde, her 2 tick'te yeniden)
        if (t < 166F) {
            float[] ux = new float[3], vx = new float[3];
            float len = Mth.sqrt(x * x + y * y + z * z) + 1.0E-4F;
            basis(x / len, y / len, z / len, ux, vx);
            R.seed(s.seed ^ 0x1A2BL, (long) (t / regen()), 7);
            int n = (int) (32 * q());
            float ra = t < 162F ? 1F : 1F - (t - 162F) / 4F;
            for (int i = 0; i < n; i++) {
                float ang = R.next() * 6.283F, l = (3F + 6F * R.next()) * reduce;
                float dx = (Mth.cos(ang) * ux[0] + Mth.sin(ang) * vx[0]) * l, dy = (Mth.cos(ang) * ux[1] + Mth.sin(ang) * vx[1]) * l,
                        dz = (Mth.cos(ang) * ux[2] + Mth.sin(ang) * vx[2]) * l;
                GlowDraw.segment(vc, m, x, y, z, x + dx, y + dy, z + dz, 0.06F, GlowDraw.cr(warm), GlowDraw.cg(warm),
                        GlowDraw.cb(warm), 0.8F * ra, 0F, false);
            }
        }
        // dikey sok halkalari (normal = forward)
        for (int i = 0; i < 2; i++) {
            float u = (t - 158F - i * 2F) / 10F;
            if (u < 0F || u > 1F) continue;
            float r = Mth.lerp(u, 0.6F, 6.0F) * (i == 0 ? 1F : 0.7F);
            GlowDraw.ring(vc, m, x, y, z, (float) a.rx(), 0, (float) a.rz(), 0, 1, 0, r, Mth.lerp(u, 0.15F, 0.03F),
                    1F, 0.9F, 0.75F, 0.9F * (1F - u), 56);
        }
        // zemin halkasi
        Vec3 feet = UltDirector.targetLive(s, 0F).subtract(0, s.targetH * 0.5, 0);
        float ug = (t - 158F) / 12F;
        if (ug >= 0F && ug <= 1F) {
            GlowDraw.ring(vc, m, rel(feet.x, cam.x), rel(feet.y + 0.05, cam.y), rel(feet.z, cam.z), 1, 0, 0, 0, 0, 1,
                    Mth.lerp(ug, 0.5F, 8.0F), 0.12F * (1F - ug) + 0.03F, 1F, 0.95F, 0.85F, 0.8F * (1F - ug), 64);
        }
        // caster'in arkasindan gelen hiz izleri (-Z boyunca 12 blok)
        if (t < 166F) {
            float al = 1F - (t - 158F) / 8F;
            R.seed(s.seed ^ 0x99L, 3, 3);
            for (int i = 0; i < 10; i++) {
                double h = R.next() * 2.0, lx = -0.15 + R.signed() * 0.3;
                Vec3 p0 = a.toWorld(lx, h, a.d - 1.1), p1 = a.toWorld(lx, h, a.d - 13.1);
                GlowDraw.segment(vc, m, rel(p0.x, cam.x), rel(p0.y, cam.y), rel(p0.z, cam.z), rel(p1.x, cam.x),
                        rel(p1.y, cam.y), rel(p1.z, cam.z), 0.05F + 0.12F * R.next(), GlowDraw.cr(warm), GlowDraw.cg(warm),
                        GlowDraw.cb(warm), al, 0F, false);
            }
        }
        // kivilcim/simsek sacilmasi
        if (t >= 160F && t < 166F) sparks(vc, m, cam, c, s.seed ^ 158, (int) (20 * q()), 1.6F, 1F - (t - 162F) / 4F, core, glow);
    }

    private static void sparks(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, long seed, int n, float reach, float alpha,
                               int core, int glow) {
        if (alpha <= 0F) return;
        R.seed(seed, 1, 1);
        float x = rel(c.x, cam.x), y = rel(c.y, cam.y), z = rel(c.z, cam.z);
        for (int i = 0; i < n; i++) {
            float dx = R.signed(), dy = R.signed(), dz = R.signed();
            float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F, len = reach * (0.4F + 0.6F * R.next());
            LINE.clear();
            Lightning.jag(LINE, R, x, y, z, x + dx / l * len, y + dy / l * len, z + dz / l * len, 0.3F, 3, false, 1F, 0F);
            GlowDraw.layered(vc, m, LINE, 0.5F, core, glow, alpha, 1F, false);
        }
    }

    /** Izleyici versiyonu: arena merkezinin 14 blok ustunde 18 blok yaricapli cemberde donen altin simsek. */
    private static void orbitWorld(VertexConsumer vc, Matrix4f m, Vec3 cam, UltState s, float t, int core, int glow) {
        ArenaFrame a = s.arena;
        Vec3 center = a.toWorld(0, 14, a.d * 0.5);
        // tur suresi 40->80 arasi 30 tick'ten 8 tick'e: acinin analitik integrali
        double ang;
        if (t < 80F) {
            double u = (t - 40F) / 40.0;
            ang = 2 * Math.PI * 40.0 * (u / 30.0 + (1.0 / 8.0 - 1.0 / 30.0) * u * u / 2.0);
        } else {
            ang = 2 * Math.PI * 40.0 * (1.0 / 30.0 + (1.0 / 8.0 - 1.0 / 30.0) / 2.0) + 2 * Math.PI * (t - 80F) / 8.0;
        }
        double radius = 18, cy = center.y;
        double desc = t >= 138F ? Math.min(1.0, (t - 138F) / 20.0) : 0.0;
        int warm = GlowDraw.mixRgb(0xFFB040, glow, 0.5F);
        float wr = GlowDraw.cr(warm), wg = GlowDraw.cg(warm), wb = GlowDraw.cb(warm);
        LINE.clear();
        int seg = 24;
        for (int i = 0; i <= seg; i++) {
            double aa = ang - Math.toRadians(25.0) * (seg - i) / seg;
            double r = radius * (1.0 - desc * 0.6);
            double y = cy - desc * 13.0 * (1.0 - (double) (seg - i) / seg * 0.3);
            Vec3 back = a.toWorld(0, 0, -10).subtract(center);
            double px = center.x + Math.cos(aa) * r + back.x * desc, pz = center.z + Math.sin(aa) * r + back.z * desc;
            LINE.add(rel(px, cam.x), rel(y, cam.y), rel(pz, cam.z), (float) i / seg, 0.3F + 0.7F * i / seg);
        }
        GlowDraw.ribbon(vc, m, LINE, 0.9F, wr, wg, wb, 0.9F, false);
        GlowDraw.ribbon(vc, m, LINE, 0.3F, 1F, 0.97F, 0.85F, 1F, false);
        int h = LINE.size - 1;
        float hx = LINE.x[h], hy = LINE.y[h], hz = LINE.z[h];
        GlowDraw.orb(vc, m, hx, hy, hz, 1.6F, wr, wg, wb, 0.8F);
        // her turda %30 olasilikla asagi simsek
        long turn = (long) (ang / (2 * Math.PI));
        R.seed(s.seed ^ 0x0B17L, turn, 5);
        if (R.next() < 0.3F && (ang / (2 * Math.PI)) % 1.0 < 0.25) {
            Vec3 ground = a.toWorld(R.signed() * 4, 0.1, a.d * 0.5 + R.signed() * 4);
            LINE.clear();
            Lightning.jag(LINE, R, hx, hy, hz, rel(ground.x, cam.x), rel(ground.y, cam.y),
                    rel(ground.z, cam.z), 0.2F, 5, false, 1F, 0.5F);
            GlowDraw.layered(vc, m, LINE, 0.8F, core, glow, 0.9F, 1F, false);
        }
        // 150+: geri gelecegi yerde isik birikimi
        if (t >= 150F) {
            Vec3 b = a.toWorld(0, 0.8, -10);
            float k = (t - 150F) / 8F;
            GlowDraw.orb(vc, m, rel(b.x, cam.x), rel(b.y, cam.y), rel(b.z, cam.z), 0.5F + 2.5F * k, wr, wg, wb, 0.4F + 0.5F * k);
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

    private static float rel(double w, double c) {
        return (float) (w - c);
    }
}
