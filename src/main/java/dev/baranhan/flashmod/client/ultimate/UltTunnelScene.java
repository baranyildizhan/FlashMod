package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import dev.baranhan.flashmod.ultimate.UltimatePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * TUNNEL: donus tuneli. Koyu, uzak ucu parlayan, kameraya dogru kayan ve hafif spiral donen silindir; duvarlar
 * boyunca sarmal donerek akan simsekler (bizim simseklerimiz), uzak uctaki isik, kameraya akan kivilcimlar. Flash tunelden kosup gelir, comelir, gozleri parlar, yumrugunu
 * geri ceker (yumrukta simsek topu, govdeden disari isinlar).
 */
public final class UltTunnelScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(1400);
    private static final Polyline LINE = new Polyline();
    private static final float[] P = new float[3];

    private static final float T0 = UltimatePhase.TUNNEL.start, LAND = T0 + 12F, CROUCH = T0 + 8F, COCK = T0 + 14F;

    static float casterZ(float t) {
        if (t < LAND) return Mth.lerp(UltCamera.Ease.OUT_EXPO.apply((t - T0) / (LAND - T0)), -140F, -2.4F);
        return -2.4F;
    }

    static final UltScene.TrailPath PATH = new UltScene.TrailPath() {
        @Override
        public Vec3 at(float t) {
            return new Vec3(0, 0, t < T0 ? -140F - (T0 - t) * 80F : casterZ(t)); // OUT_EXPO'nun baslangic hizi
        }

        @Override
        public double odo(float t) {
            return at(t).z;
        }
    };

    @Override
    public void backdrop(UltScene.Ctx c) {
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        UltOrbitScene.cube(b, c.view, 100F, 0.16F, 0.1F, 0.06F);
        UltDraw.end();
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.vt;
        int slices = Math.max(16, (int) (48 * c.quality)), rings = Math.max(12, (int) (40 * c.quality));
        double rad = 6.0, z0 = 5.0, z1 = -150.0;
        float vScroll = t * 0.35F, uSpin = t * 0.01F;
        BufferBuilder b = UltDraw.begin(UltTextures.TUNNEL, UltDraw.Blend.OPAQUE, true, true);
        for (int j = 0; j < rings; j++) {
            double za = Mth.lerp((double) j / rings, z0, z1), zb = Mth.lerp((double) (j + 1) / rings, z0, z1);
            for (int i = 0; i < slices; i++) {
                double a0 = 2 * Math.PI * i / slices, a1 = 2 * Math.PI * (i + 1) / slices;
                tv(b, m, rad, a0, za, (float) i / slices + uSpin, (float) (za / 20.0) + vScroll);
                tv(b, m, rad, a1, za, (float) (i + 1) / slices + uSpin, (float) (za / 20.0) + vScroll);
                tv(b, m, rad, a1, zb, (float) (i + 1) / slices + uSpin, (float) (zb / 20.0) + vScroll);
                tv(b, m, rad, a0, zb, (float) i / slices + uSpin, (float) (zb / 20.0) + vScroll);
            }
        }
        UltDraw.end();
        // duvar boyunca kameraya akan simsekler (bizim simsek altyapimiz): sarmal donerek yaklasir
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        float bloom = Math.max(0.6F, dev.baranhan.flashmod.config.FlashClientConfig.BLOOM.get().floatValue());
        int bolts = Math.max(8, (int) (18 * c.quality));
        long frame = (long) (t / UltWorldFx.regen());
        for (int i = 0; i < bolts; i++) {
            R.seed(c.s.seed ^ 0x7B0L, i, 0);
            double a = 2 * Math.PI * R.next() + t * 0.03, sp = 7.0 + R.next() * 6.0, len = 10.0 + R.next() * 14.0;
            double zs = 5 - ((R.next() * 156.0 + t * sp) % 156.0), ze = zs - len;
            double r = 5.4 + R.next() * 0.4, twist = 0.25 + 0.2 * R.next();
            float fade = (float) Mth.clamp((zs + 150.0) / 30.0, 0.0, 1.0) * (float) Mth.clamp((5.0 - zs) / 6.0, 0.0, 1.0);
            LINE.clear();
            R.seed(c.s.seed ^ 0x7B1L, frame, i);
            float bx0 = (float) (Math.cos(a) * r - c.cam.x), by0 = (float) (1.2 + Math.sin(a) * r - c.cam.y), bz0 = (float) (zs - c.cam.z);
            float bx1 = (float) (Math.cos(a + twist) * r - c.cam.x), by1 = (float) (1.2 + Math.sin(a + twist) * r - c.cam.y);
            float bz1 = (float) (ze - c.cam.z);
            Lightning.jag(LINE, R, bx0, by0, bz0, bx1, by1, bz1, 0.06F, 5, false, fade, fade * 0.2F);
            GlowDraw.layered(vc, c.view, LINE, 1.6F, c.core, c.glow, 0.9F, bloom, false);
        }
        buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
        // uc isigi + kameraya akan kivilcimlar
        b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, true, false);
        UltDraw.billboard(b, m, 0, 1.2, -148, c.right, c.up, 30F, 30F, 0F, 0xFFC890, 0.9F);
        R.seed(c.s.seed ^ 0x7E11L);
        int n = (int) (80 * c.quality);
        for (int i = 0; i < n; i++) {
            double r = R.next() * 5.5, a = R.next() * Math.PI * 2, sp = 3 + R.next() * 3;
            double z = 5 - ((R.next() * 155 + t * sp) % 155.0);
            UltDraw.billboard(b, m, Math.cos(a) * r, 1.2 + Math.sin(a) * r, z, c.right, c.up, 0.12F, 0.12F, 0F, 0xFFF0D8, 0.8F);
        }
        UltDraw.end();
    }

    private static void tv(BufferBuilder b, Matrix4f m, double rad, double a, double z, float u, float v) {
        float far = (float) Mth.clamp((-z - 80.0) / 70.0, 0.0, 1.0);
        int base = UltDraw.mix(0x4A2E1E, 0xE6A86A, far * far);
        UltDraw.v(b, m, Math.cos(a) * rad, 1.2 + Math.sin(a) * rad, z, u, v, UltDraw.r(base) * 1.25F, UltDraw.g(base) * 1.25F,
                UltDraw.b(base) * 1.25F, 1F);
    }

    @Override
    public void model(UltScene.Ctx c) {
        float t = c.vt;
        UltScene.Path path = PATH;
        int ghosts = t < LAND ? Math.max(3, (int) (8 * c.quality)) : (t >= COCK ? 3 : 0);
        float spacing = t < LAND ? 0.35F : 0.15F, a0 = t < LAND ? 0.5F : 0.25F;
        float vein = t < CROUCH ? 0.8F : 1.0F + 0.4F * Math.min(1F, (t - CROUCH) / 20F);
        float eyes = t < COCK ? 0F : Math.min(1F, (t - COCK) / 4F);
        UltScene.drawCaster(c, path, 0F, true, ghosts, spacing, a0, vein, eyes);
        // geldigi yol boyunca bizim simsek izi (inince kisalip soner)
        UltScene.drawTrail(c, PATH, T0 - 20F, t < LAND ? 14 : 8, 1F, true, t < LAND + 6F ? 1F : Math.max(0F, 1F - (t - LAND - 6F) / 6F));

        // inis radyal simsekleri, yumruk simsek topu, govdeden disari isinlar
        Minecraft mc = Minecraft.getInstance();
        Player p = c.s.caster();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        Vec3 feet = path.at(t).subtract(c.cam);
        if (t >= LAND && t < LAND + 5F) { // inis: ayaklardan yere yayilan simsekler
            float k = 1F - (t - LAND) / 5F;
            R.seed(c.s.seed ^ 148, (long) (t / UltWorldFx.regen()), 1);
            for (int i = 0; i < 8; i++) {
                float ang = 6.283F * i / 8 + R.signed() * 0.3F;
                LINE.clear();
                Lightning.jag(LINE, R, (float) feet.x, (float) feet.y + 0.05F, (float) feet.z,
                        (float) feet.x + Mth.cos(ang) * 1.5F, (float) feet.y + 0.05F, (float) feet.z + Mth.sin(ang) * 1.5F,
                        0.3F, 3, false, k, 0F);
                GlowDraw.layered(vc, c.view, LINE, 0.6F, c.core, c.glow, k, 1F, false);
            }
        }
        BodyPoseCapture.Pose body = p != null ? BodyPoseCapture.get(p) : null;
        if (body != null && t >= COCK) {
            Vec3 real = mc.gameRenderer.getMainCamera().getPosition();
            body.point(BodyPoseCapture.R_ARM, -1F, 10.5F, 0F, real, P);
            float hx = P[0], hy = P[1], hz = P[2];
            float k = Math.min(1F, (t - COCK) / 4F);
            GlowDraw.orb(vc, c.view, hx, hy, hz, 0.35F * k, GlowDraw.cr(c.glow), GlowDraw.cg(c.glow), GlowDraw.cb(c.glow), 0.6F);
            R.seed(c.s.seed ^ 0xF157L, (long) (t / UltWorldFx.regen()), 2);
            for (int i = 0; i < 5; i++) {
                float dx = R.signed(), dy = R.signed(), dz = R.signed();
                float l = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F, len = 0.3F + 0.3F * R.next();
                LINE.clear();
                Lightning.jag(LINE, R, hx, hy, hz, hx + dx / l * len, hy + dy / l * len, hz + dz / l * len, 0.3F, 2, false, 1F, 0F);
                GlowDraw.layered(vc, c.view, LINE, 0.4F, c.core, c.glow, k, 1F, false);
            }
            // govdeden disari isinlar (arkada)
            body.point(BodyPoseCapture.BODY, 0F, 6F, 2F, real, P);
            int warm = GlowDraw.mixRgb(0xFFC890, c.glow, 0.4F);
            R.seed(c.s.seed ^ 0x2A75L, 1, 1);
            for (int i = 0; i < 8; i++) {
                float ang = 6.283F * i / 8 + 0.2F;
                float ex = P[0] + Mth.cos(ang) * 3.5F, ey = P[1] + Mth.sin(ang) * 3.5F, ez = P[2] - 1.5F;
                GlowDraw.segment(vc, c.view, P[0], P[1], P[2], ex, ey, ez, 0.18F, GlowDraw.cr(warm), GlowDraw.cg(warm),
                        GlowDraw.cb(warm), 0.45F * k, 0F, false);
            }
        }
        buffers.endBatch(FlashRenderTypes.ADDITIVE_GLOW);
    }
}
