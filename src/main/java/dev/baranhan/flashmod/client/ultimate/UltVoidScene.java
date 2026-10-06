package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.flashmod.client.render.Lightning;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * VOID (40-86): gri, sisli Speed Force boslugu. Kosucu comelmesi -> itis -> sprint -> isik cizgisine donusme.
 * Backdrop gradyan kure, radyal solan zemin, kayan sis bulutlari, yatay cizgi seritleri, 76'dan sonra isik bandi.
 */
public final class UltVoidScene implements UltScene.Scene {
    private static final Lightning.Rng R = new Lightning.Rng(404);

    static float speedFactor(float t) {
        if (t < 55F) return 0.05F;
        if (t < 66F) return 0.3F;
        if (t < 80F) return 0.3F + 13.7F * UltCamera.Ease.IN_EXPO.apply((t - 66F) / 14F);
        return 14F;
    }

    /** Kayma mesafesi = speedFactor integrali (sayisal, 0.25 tick adim). */
    static float travel(float t) {
        float d = 0F;
        for (float x = 40F; x < t; x += 0.25F) d += speedFactor(x) * 0.25F;
        return d;
    }

    static float casterZ(float t) {
        if (t < 55F) return 0F;
        if (t < 61F) return 0.4F * UltCamera.Ease.OUT_CUBIC.apply((t - 55F) / 6F);
        if (t < 66F) return 0.4F;
        return 0.4F + 1.2F * (float) (Math.exp(0.28 * (t - 66F)) - 1.0);
    }

    @Override
    public void backdrop(UltScene.Ctx c) {
        int top = 0x2A2D36, mid = 0xB9B6B8, low = 0x7A7476;
        if (c.t >= 66F) mid = UltDraw.mix(mid, c.glow, 0.15F * Math.min(1F, (c.t - 66F) / 6F));
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.OPAQUE, false, false);
        int seg = 24, rings = 12;
        float rad = 50F;
        for (int i = 0; i < rings; i++) {
            float a0 = -90F + 180F * i / rings, a1 = -90F + 180F * (i + 1) / rings;
            float y0 = Mth.sin(a0 * 0.017453292F), y1 = Mth.sin(a1 * 0.017453292F);
            float r0 = Mth.cos(a0 * 0.017453292F), r1 = Mth.cos(a1 * 0.017453292F);
            int c0 = sky(y0, top, mid, low), c1 = sky(y1, top, mid, low);
            for (int j = 0; j < seg; j++) {
                float b0 = 6.2832F * j / seg, b1 = 6.2832F * (j + 1) / seg;
                quadC(b, c.view, rad, r0, y0, r1, y1, b0, b1, c0, c1);
            }
        }
        UltDraw.end();
    }

    static void quadC(BufferBuilder b, Matrix4f m, float rad, float r0, float y0, float r1, float y1, float b0, float b1,
                      int c0, int c1) {
        UltDraw.c(b, m, Mth.cos(b0) * r0 * rad, y0 * rad, Mth.sin(b0) * r0 * rad, UltDraw.r(c0), UltDraw.g(c0), UltDraw.b(c0), 1F);
        UltDraw.c(b, m, Mth.cos(b1) * r0 * rad, y0 * rad, Mth.sin(b1) * r0 * rad, UltDraw.r(c0), UltDraw.g(c0), UltDraw.b(c0), 1F);
        UltDraw.c(b, m, Mth.cos(b1) * r1 * rad, y1 * rad, Mth.sin(b1) * r1 * rad, UltDraw.r(c1), UltDraw.g(c1), UltDraw.b(c1), 1F);
        UltDraw.c(b, m, Mth.cos(b0) * r1 * rad, y1 * rad, Mth.sin(b0) * r1 * rad, UltDraw.r(c1), UltDraw.g(c1), UltDraw.b(c1), 1F);
    }

    private static int sky(float y, int top, int mid, int low) {
        if (y >= 0F) return UltDraw.mix(mid, top, Mth.clamp(y / 0.6F, 0F, 1F));
        return UltDraw.mix(mid, low, Mth.clamp(-y / 0.4F, 0F, 1F));
    }

    @Override
    public void geometry(UltScene.Ctx c) {
        Matrix4f m = c.m;
        float t = c.vt, dist = travel(t);
        double cx = c.cam.x, cz = c.cam.z;
        // zemin: radyal solan disk
        BufferBuilder b = UltDraw.begin(null, UltDraw.Blend.ALPHA, true, true, VertexFormat.Mode.TRIANGLES);
        int seg = 48;
        float gr = UltDraw.r(0x8A8486), gg = UltDraw.g(0x8A8486), gb = UltDraw.b(0x8A8486);
        for (int ring = 0; ring < 6; ring++) {
            float r0 = ring * 18F, r1 = (ring + 1) * 18F;
            float a0 = 1F - ring / 6F, a1 = 1F - (ring + 1) / 6F;
            for (int j = 0; j < seg; j++) {
                float b0 = 6.2832F * j / seg, b1 = 6.2832F * (j + 1) / seg;
                UltDraw.c(b, m, cx + Mth.cos(b0) * r0, 0, cz + Mth.sin(b0) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r0, 0, cz + Mth.sin(b1) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r1, 0, cz + Mth.sin(b1) * r1, gr, gg, gb, a1);
                UltDraw.c(b, m, cx + Mth.cos(b0) * r0, 0, cz + Mth.sin(b0) * r0, gr, gg, gb, a0);
                UltDraw.c(b, m, cx + Mth.cos(b1) * r1, 0, cz + Mth.sin(b1) * r1, gr, gg, gb, a1);
                UltDraw.c(b, m, cx + Mth.cos(b0) * r1, 0, cz + Mth.sin(b0) * r1, gr, gg, gb, a1);
            }
        }
        UltDraw.end();
        // zemin uzerinde kayan soluk bulut dokusu
        b = UltDraw.begin(UltTextures.CLOUD, UltDraw.Blend.ALPHA, true, false);
        float sc = dist / 40F;
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 3; j++) {
            double x0 = cx + i * 20, z0 = cz + j * 20 - (dist % 20F);
            UltDraw.v(b, m, x0, 0.02, z0, 0, sc, 1, 1, 1, 0.12F);
            UltDraw.v(b, m, x0 + 20, 0.02, z0, 1, sc, 1, 1, 1, 0.12F);
            UltDraw.v(b, m, x0 + 20, 0.02, z0 + 20, 1, sc + 1, 1, 1, 1, 0.12F);
            UltDraw.v(b, m, x0, 0.02, z0 + 20, 0, sc + 1, 1, 1, 1, 0.12F);
        }
        UltDraw.end();

        // sis bulutlari (billboard), -Z'ye kayar, one sarar
        b = UltDraw.begin(UltTextures.CLOUD, UltDraw.Blend.ALPHA, true, false);
        R.seed(c.s.seed ^ 0xC10DL);
        int clouds = 10 + (int) (4 * c.quality);
        for (int i = 0; i < clouds; i++) {
            float x = R.signed() * 30F, y = R.next() * 10F, zb = 12F + R.next() * 33F;
            float w = 15F + R.next() * 25F, h = 6F + R.next() * 8F, a = 0.25F + R.next() * 0.3F;
            float z = (float) (cz - 20F + Math.floorMod((int) ((zb - dist * 0.6F) * 100), 8000) / 100F);
            UltDraw.billboard(b, m, x, y, z, c.right, c.up, w, h, 0F, 0xD8D4D6, a);
        }
        UltDraw.end();

        // yatay cizgi seritleri
        if (t >= 55F) {
            float k = Mth.clamp((t - 55F) / 25F, 0F, 1F);
            int n = (int) (200 * c.quality * k);
            boolean warm = t >= 66F;
            int col = warm ? UltDraw.mix(0xFFD9A0, c.glow, 0.4F) : 0xE0E0E0;
            b = UltDraw.begin(null, warm ? UltDraw.Blend.ADD : UltDraw.Blend.ALPHA, true, false);
            R.seed(c.s.seed ^ 0x57A7L);
            for (int i = 0; i < n; i++) {
                float y = 0.1F + R.next() * 2.5F, x = -4F + R.next() * 16F, len = 4F + R.next() * 26F;
                float hh = 0.02F + R.next() * 0.1F, sp = 0.8F + R.next() * 0.8F;
                float a = warm ? 0.3F + 0.5F * R.next() : 0.15F;
                float z = (float) (cz - 40F + Math.floorMod((int) ((R.next() * 120F - dist * sp) * 100), 12000) / 100F);
                UltDraw.c(b, m, x, y, z, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), 0F);
                UltDraw.c(b, m, x, y + hh, z, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), 0F);
                UltDraw.c(b, m, x, y + hh, z + len, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), a);
                UltDraw.c(b, m, x, y, z + len, UltDraw.r(col), UltDraw.g(col), UltDraw.b(col), a);
            }
            UltDraw.end();
        }

        // kalici yatay isik bandi (76-86)
        if (t >= 76F) {
            float k = Mth.clamp((t - 76F) / 6F, 0F, 1F);
            b = UltDraw.begin(null, UltDraw.Blend.ADD, true, false);
            float[][] layers = {{2.4F, 0.35F}, {1.0F, 0.6F}, {0.25F, 0.9F}};
            int[] cols = {UltDraw.mix(0xFFB070, c.glow, 0.4F), UltDraw.mix(0xFFE0B0, c.glow, 0.2F), 0xFFFFFF};
            double z0 = cz - 40, z1 = cz + 60;
            for (int i = 0; i < 3; i++) {
                float hh = layers[i][0] * 0.5F, a = layers[i][1] * k;
                int col = cols[i];
                float r = UltDraw.r(col), g = UltDraw.g(col), bl = UltDraw.b(col);
                UltDraw.c(b, m, 0, 1.0 - hh, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z0, r, g, bl, a);
                UltDraw.c(b, m, 0, 1.0, z1, r, g, bl, a);
                UltDraw.c(b, m, 0, 1.0 - hh, z1, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z0, r, g, bl, a);
                UltDraw.c(b, m, 0, 1.0 + hh, z0, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0 + hh, z1, r, g, bl, 0F);
                UltDraw.c(b, m, 0, 1.0, z1, r, g, bl, a);
            }
            UltDraw.end();
            b = UltDraw.begin(UltTextures.GLOW, UltDraw.Blend.ADD, true, false);
            UltDraw.billboard(b, m, 0, 1.0, cz + 18, c.right, c.up, 6F, 6F, 0F, UltDraw.mix(0xFFE0B0, c.glow, 0.3F), k);
            UltDraw.end();
        }
    }

    @Override
    public void model(UltScene.Ctx c) {
        float t = c.vt;
        if (t >= 80F) return;
        UltScene.Path path = tk -> new Vec3(0, 0, casterZ(tk));
        int ghosts;
        float spacing, a0;
        if (t < 55F) { ghosts = 3; spacing = 1.0F; a0 = 0.35F; }
        else if (t < 66F) { ghosts = 5; spacing = 0.6F; a0 = 0.45F; }
        else { ghosts = 8; spacing = 0.4F; a0 = t < 72F ? 0.55F : 0.9F * Math.max(0F, 1F - (t - 72F) / 6F); }
        ghosts = Math.max(2, (int) (ghosts * Math.max(0.5F, c.quality)));
        UltScene.drawCaster(c, path, 0F, t < 72F, ghosts, spacing, a0, 1.0F, 0F);
    }
}
