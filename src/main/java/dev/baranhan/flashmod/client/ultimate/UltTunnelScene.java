package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.render.BodyPoseCapture;
import dev.baranhan.flashmod.client.render.FlashRenderTypes;
import dev.baranhan.flashmod.client.render.GlowDraw;
import dev.baranhan.flashmod.client.render.Lightning;
import dev.baranhan.flashmod.client.render.Polyline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * TUNNEL (140-158): donus tuneli. Kahverengi-turuncu bantli, kameraya dogru kayan ve hafif spiral donen silindir,
 * uzak uctaki isik, kameraya akan kivilcimlar. Flash tunelden kosup gelir, comelir, gozleri parlar, yumrugunu
 * geri ceker (yumrukta simsek topu, govdeden disari isinlar).
 */
public final class UltTunnelScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(1400);
    private static final Polyline LINE = new Polyline();
    private static final float[] P = new float[3];

    static float casterZ(float t) {
        if (t < 148F) return Mth.lerp(UltCamera.Ease.OUT_EXPO.apply((t - 140F) / 8F), -120F, -2.4F);
        return -2.4F;
    }

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
        // duvar boyunca yakinsayan additive bantlar (daha hizli kayar)
        b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
        int warm = UltDraw.mix(0xFFC890, c.glow, 0.3F);
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * i / 12 + t * 0.02;
            double x = Math.cos(a) * 5.9, y = 1.2 + Math.sin(a) * 5.9;
            for (int k = 0; k < 6; k++) {
                double zs = 5 - ((t * 9.0 + k * 26 + i * 7) % 156.0), ze = zs - 14;
                UltDraw.c(b, m, x, y - 0.05, zs, UltDraw.r(warm), UltDraw.g(warm), UltDraw.b(warm), 0F);
                UltDraw.c(b, m, x, y + 0.05, zs, UltDraw.r(warm), UltDraw.g(warm), UltDraw.b(warm), 0F);
                UltDraw.c(b, m, x, y + 0.05, ze, UltDraw.r(warm), UltDraw.g(warm), UltDraw.b(warm), 0.5F);
                UltDraw.c(b, m, x, y - 0.05, ze, UltDraw.r(warm), UltDraw.g(warm), UltDraw.b(warm), 0.5F);
            }
        }
        UltDraw.end();
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
        int base = UltDraw.mix(UltDraw.mix(0x5A3A26, 0xC08050, 0.5F), 0xE0A070, far);
        UltDraw.v(b, m, Math.cos(a) * rad, 1.2 + Math.sin(a) * rad, z, u, v, UltDraw.r(base) * 1.4F, UltDraw.g(base) * 1.4F,
                UltDraw.b(base) * 1.4F, 1F);
    }

    @Override
    public void model(UltScene.Ctx c) {
        float t = c.vt;
        UltScene.Path path = tk -> new Vec3(0, 0, casterZ(tk));
        int ghosts = t < 148F ? Math.max(3, (int) (10 * c.quality)) : (t >= 150F ? 3 : 0);
        float spacing = t < 148F ? 0.35F : 0.15F, a0 = t < 148F ? 0.6F : 0.25F;
        float vein = t < 146F ? 0.8F : (t < 158F ? 1.0F + 0.4F * (t - 146F) / 12F : 1.4F);
        float eyes = t < 150F ? 0F : Math.min(1F, (t - 150F) / 4F);
        UltScene.drawCaster(c, path, 0F, true, ghosts, spacing, a0, vein, eyes);

        // inis radyal simsekleri, yumruk simsek topu, govdeden disari isinlar
        Minecraft mc = Minecraft.getInstance();
        Player p = c.s.caster();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        Vec3 feet = path.at(t).subtract(c.cam);
        if (t >= 148F && t < 152F) {
            float k = 1F - (t - 148F) / 4F;
            R.seed(c.s.seed ^ 148, (long) (t / 2), 1);
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
        if (body != null && t >= 150F) {
            Vec3 real = mc.gameRenderer.getMainCamera().getPosition();
            body.point(BodyPoseCapture.R_ARM, -1F, 10.5F, 0F, real, P);
            float hx = P[0], hy = P[1], hz = P[2];
            float k = Math.min(1F, (t - 150F) / 4F);
            GlowDraw.orb(vc, c.view, hx, hy, hz, 0.35F * k, GlowDraw.cr(c.glow), GlowDraw.cg(c.glow), GlowDraw.cb(c.glow), 0.6F);
            R.seed(c.s.seed ^ 0xF157L, (long) (t / 2), 2);
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
