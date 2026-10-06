package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Tum isik geometrisi burada. Her sey "yumusak kenarli" ciziliyor: merkez vertex alpha=a, kenar vertex alpha=0.
 * POSITION_COLOR + additive karisimda vertex renk enterpolasyonu dogal bir isik dusumu (falloff) verir.
 * Ayni cizgiyi farkli genislik/renklerde ust uste cizmek = shader'siz bloom.
 *
 * Koordinatlar kameraya goreli (kamera = 0,0,0). Boylece kameraya donuk seritler icin
 * "yan vektor = cross(teget, nokta)" yeterli.
 */
public final class GlowDraw {
    public static final Matrix4f IDENTITY = new Matrix4f();

    private static float[] SX = new float[256], SY = new float[256], SZ = new float[256];

    private GlowDraw() {}

    private static void ensure(int n) {
        if (SX.length < n) {
            int s = Math.max(n, SX.length * 2);
            SX = Arrays.copyOf(SX, s);
            SY = Arrays.copyOf(SY, s);
            SZ = Arrays.copyOf(SZ, s);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, Mth.clamp(a, 0F, 1F)).endVertex();
    }

    /**
     * Kameraya donuk, yumusak kenarli serit. line.w[i] * width = yari genislik.
     * flat=true: 2D (GUI) modu, yan vektor XY duzleminde.
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Polyline line, float width,
                              float r, float g, float b, float alphaMul, boolean flat) {
        int n = line.size;
        if (n < 2 || alphaMul <= 0.002F) return;
        ensure(n);
        float[] X = line.x, Y = line.y, Z = line.z;
        float psx = 0, psy = 0, psz = 0;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            float tx = X[i1] - X[i0], ty = Y[i1] - Y[i0], tz = Z[i1] - Z[i0];
            float sx, sy, sz;
            if (flat) {
                sx = -ty; sy = tx; sz = 0F;
            } else {
                // cross(t, p): p kameradan noktaya
                float px = X[i], py = Y[i], pz = Z[i];
                sx = ty * pz - tz * py;
                sy = tz * px - tx * pz;
                sz = tx * py - ty * px;
            }
            float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-6F) {
                sx = psx; sy = psy; sz = psz;
            } else {
                sx /= len; sy /= len; sz /= len;
                // isaret surekliligi: serit kendi uzerine kivrilmasin
                if (i > 0 && sx * psx + sy * psy + sz * psz < 0F) {
                    sx = -sx; sy = -sy; sz = -sz;
                }
            }
            SX[i] = sx; SY[i] = sy; SZ[i] = sz;
            psx = sx; psy = sy; psz = sz;
        }
        float[] A = line.a, W = line.w;
        for (int i = 0; i < n - 1; i++) {
            float a0 = A[i] * alphaMul, a1 = A[i + 1] * alphaMul;
            if (a0 <= 0.002F && a1 <= 0.002F) continue;
            float w0 = W[i] * width, w1 = W[i + 1] * width;
            float ox0 = SX[i] * w0, oy0 = SY[i] * w0, oz0 = SZ[i] * w0;
            float ox1 = SX[i + 1] * w1, oy1 = SY[i + 1] * w1, oz1 = SZ[i + 1] * w1;
            // ust yari
            v(vc, m, X[i] + ox0, Y[i] + oy0, Z[i] + oz0, r, g, b, 0F);
            v(vc, m, X[i], Y[i], Z[i], r, g, b, a0);
            v(vc, m, X[i + 1], Y[i + 1], Z[i + 1], r, g, b, a1);
            v(vc, m, X[i + 1] + ox1, Y[i + 1] + oy1, Z[i + 1] + oz1, r, g, b, 0F);
            // alt yari
            v(vc, m, X[i], Y[i], Z[i], r, g, b, a0);
            v(vc, m, X[i] - ox0, Y[i] - oy0, Z[i] - oz0, r, g, b, 0F);
            v(vc, m, X[i + 1] - ox1, Y[i + 1] - oy1, Z[i + 1] - oz1, r, g, b, 0F);
            v(vc, m, X[i + 1], Y[i + 1], Z[i + 1], r, g, b, a1);
        }
    }

    /** Ayni cizgiyi bloom katmanlariyla ciz: genis halo -> orta renk -> sicak beyaz cekirdek. */
    public static void layered(VertexConsumer vc, Matrix4f m, Polyline line, float scale,
                               int core, int glow, float alpha, float bloom, boolean flat) {
        if (bloom > 0.01F) {
            ribbon(vc, m, line, 0.19F * scale * (0.6F + 0.4F * bloom), cr(glow), cg(glow), cb(glow),
                    0.30F * alpha * bloom, flat);
        }
        int mid = mixRgb(core, glow, 0.35F);
        ribbon(vc, m, line, 0.05F * scale, cr(mid), cg(mid), cb(mid), 0.85F * alpha, flat);
        int hot = mixRgb(core, 0xFFFFFF, 0.45F);
        ribbon(vc, m, line, 0.017F * scale, cr(hot), cg(hot), cb(hot), alpha, flat);
    }

    /** Tek, yumusak kenarli cizgi parcasi. */
    public static void segment(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0,
                               float x1, float y1, float z1, float w, float r, float g, float b, float a0, float a1,
                               boolean flat) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float sx, sy, sz;
        if (flat) {
            sx = -dy; sy = dx; sz = 0F;
        } else {
            float mx = (x0 + x1) * 0.5F, my = (y0 + y1) * 0.5F, mz = (z0 + z1) * 0.5F;
            sx = dy * mz - dz * my;
            sy = dz * mx - dx * mz;
            sz = dx * my - dy * mx;
        }
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-7F) return;
        float k = w / len;
        sx *= k; sy *= k; sz *= k;
        v(vc, m, x0 + sx, y0 + sy, z0 + sz, r, g, b, 0F);
        v(vc, m, x0, y0, z0, r, g, b, a0);
        v(vc, m, x1, y1, z1, r, g, b, a1);
        v(vc, m, x1 + sx, y1 + sy, z1 + sz, r, g, b, 0F);
        v(vc, m, x0, y0, z0, r, g, b, a0);
        v(vc, m, x0 - sx, y0 - sy, z0 - sz, r, g, b, 0F);
        v(vc, m, x1 - sx, y1 - sy, z1 - sz, r, g, b, 0F);
        v(vc, m, x1, y1, z1, r, g, b, a1);
    }

    /** Kameraya donuk yumusak isik kuresi (dejenere quad'larla ucgen fan). */
    public static void orb(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius,
                           float r, float g, float b, float a) {
        if (a <= 0.002F || radius <= 0F) return;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        float fx, fy, fz;
        if (len < 1.0E-4F) { fx = 0; fy = 0; fz = 1; } else { fx = x / len; fy = y / len; fz = z / len; }
        // u = normalize(cross(f, up))
        float ux = -fz, uy = 0F, uz = fx;
        float ul = (float) Math.sqrt(ux * ux + uz * uz);
        if (ul < 1.0E-4F) { ux = 1; uy = 0; uz = 0; } else { ux /= ul; uz /= ul; }
        // v = cross(u, f)
        float vx = uy * fz - uz * fy, vy = uz * fx - ux * fz, vz = ux * fy - uy * fx;
        disc(vc, m, x, y, z, ux, uy, uz, vx, vy, vz, radius, r, g, b, a, 12);
    }

    /** Verilen iki eksenle tanimli duzlemde yumusak disk. */
    public static void disc(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                            float ux, float uy, float uz, float vx, float vy, float vz,
                            float radius, float r, float g, float b, float a, int seg) {
        float prevC = 1F, prevS = 0F;
        for (int i = 1; i <= seg; i++) {
            float ang = (float) (i * Math.PI * 2.0 / seg);
            float c = Mth.cos(ang), s = Mth.sin(ang);
            v(vc, m, x, y, z, r, g, b, a);
            v(vc, m, x, y, z, r, g, b, a);
            v(vc, m, x + (ux * prevC + vx * prevS) * radius, y + (uy * prevC + vy * prevS) * radius,
                    z + (uz * prevC + vz * prevS) * radius, r, g, b, 0F);
            v(vc, m, x + (ux * c + vx * s) * radius, y + (uy * c + vy * s) * radius,
                    z + (uz * c + vz * s) * radius, r, g, b, 0F);
            prevC = c;
            prevS = s;
        }
    }

    /** Yumusak halka (ic ve dis kenar alpha 0, ortasi a). u/v halkanin duzlemini tanimlar. */
    public static void ring(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                            float ux, float uy, float uz, float vx, float vy, float vz,
                            float radius, float thickness, float r, float g, float b, float a, int seg) {
        if (a <= 0.002F) return;
        float rIn = Math.max(0F, radius - thickness), rOut = radius + thickness;
        float pc = 1F, ps = 0F;
        for (int i = 1; i <= seg; i++) {
            float ang = (float) (i * Math.PI * 2.0 / seg);
            float c = Mth.cos(ang), s = Mth.sin(ang);
            float dx0 = ux * pc + vx * ps, dy0 = uy * pc + vy * ps, dz0 = uz * pc + vz * ps;
            float dx1 = ux * c + vx * s, dy1 = uy * c + vy * s, dz1 = uz * c + vz * s;
            // ic yari
            v(vc, m, x + dx0 * rIn, y + dy0 * rIn, z + dz0 * rIn, r, g, b, 0F);
            v(vc, m, x + dx0 * radius, y + dy0 * radius, z + dz0 * radius, r, g, b, a);
            v(vc, m, x + dx1 * radius, y + dy1 * radius, z + dz1 * radius, r, g, b, a);
            v(vc, m, x + dx1 * rIn, y + dy1 * rIn, z + dz1 * rIn, r, g, b, 0F);
            // dis yari
            v(vc, m, x + dx0 * radius, y + dy0 * radius, z + dz0 * radius, r, g, b, a);
            v(vc, m, x + dx0 * rOut, y + dy0 * rOut, z + dz0 * rOut, r, g, b, 0F);
            v(vc, m, x + dx1 * rOut, y + dy1 * rOut, z + dz1 * rOut, r, g, b, 0F);
            v(vc, m, x + dx1 * radius, y + dy1 * radius, z + dz1 * radius, r, g, b, a);
            pc = c;
            ps = s;
        }
    }

    /** 2D dortgen, kose basina alpha (GUI gradyanlari / kenar parlamasi). Sira: sol-ust, sol-alt, sag-alt, sag-ust. */
    public static void gradQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                                float r, float g, float b, float aTL, float aBL, float aBR, float aTR) {
        v(vc, m, x0, y0, 0F, r, g, b, aTL);
        v(vc, m, x0, y1, 0F, r, g, b, aBL);
        v(vc, m, x1, y1, 0F, r, g, b, aBR);
        v(vc, m, x1, y0, 0F, r, g, b, aTR);
    }

    /** Kenarlari yumusak dolu dortgen (icerde a, dista 0). */
    public static void softRect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float feather,
                                float r, float g, float b, float a) {
        float f = feather;
        gradQuad(vc, m, x0, y0, x1, y1, r, g, b, a, a, a, a);
        gradQuad(vc, m, x0, y0 - f, x1, y0, r, g, b, 0F, a, a, 0F);
        gradQuad(vc, m, x0, y1, x1, y1 + f, r, g, b, a, 0F, 0F, a);
        gradQuad(vc, m, x0 - f, y0, x0, y1, r, g, b, 0F, 0F, a, a);
        gradQuad(vc, m, x1, y0, x1 + f, y1, r, g, b, a, a, 0F, 0F);
        // koseler
        gradQuad(vc, m, x0 - f, y0 - f, x0, y0, r, g, b, 0F, 0F, a, 0F);
        gradQuad(vc, m, x1, y0 - f, x1 + f, y0, r, g, b, 0F, a, 0F, 0F);
        gradQuad(vc, m, x0 - f, y1, x0, y1 + f, r, g, b, 0F, 0F, 0F, a);
        gradQuad(vc, m, x1, y1, x1 + f, y1 + f, r, g, b, a, 0F, 0F, 0F);
    }

    // ---------- GUI (2D) additive ----------

    public static BufferBuilder beginGui() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    public static void endGui() {
        Tesselator.getInstance().end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ---------- renk yardimcilari ----------

    public static float cr(int c) { return ((c >> 16) & 0xFF) / 255F; }
    public static float cg(int c) { return ((c >> 8) & 0xFF) / 255F; }
    public static float cb(int c) { return (c & 0xFF) / 255F; }

    public static int mixRgb(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
