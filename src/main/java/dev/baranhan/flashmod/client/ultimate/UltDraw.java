package dev.baranhan.flashmod.client.ultimate;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/** Sahne cizimi icin aninda (immediate) quad/ucgen yardimcilari: vanilla core shader'lar, ozel GLSL yok. */
public final class UltDraw {
    public enum Blend { ALPHA, ADD, OPAQUE }

    private static BufferBuilder bb;
    private static boolean textured;

    private UltDraw() {}

    public static BufferBuilder begin(@Nullable ResourceLocation tex, Blend blend, boolean depthTest, boolean depthWrite,
                                      VertexFormat.Mode mode) {
        textured = tex != null;
        if (textured) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, tex);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        if (blend == Blend.OPAQUE) {
            RenderSystem.disableBlend();
        } else {
            RenderSystem.enableBlend();
            if (blend == Blend.ADD) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            else RenderSystem.defaultBlendFunc();
        }
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthWrite);
        RenderSystem.disableCull();
        bb = Tesselator.getInstance().getBuilder();
        bb.begin(mode, textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        return bb;
    }

    public static BufferBuilder begin(@Nullable ResourceLocation tex, Blend blend, boolean depthTest, boolean depthWrite) {
        return begin(tex, blend, depthTest, depthWrite, VertexFormat.Mode.QUADS);
    }

    public static void end() {
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    public static void v(BufferBuilder b, Matrix4f m, double x, double y, double z, float u, float vv, float r, float g,
                         float bl, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).uv(u, vv).color(sat(r), sat(g), sat(bl), sat(a)).endVertex();
    }

    public static void c(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float g, float bl, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).color(sat(r), sat(g), sat(bl), sat(a)).endVertex();
    }

    /** 0..1'e kirp: renk bayta cevrilirken 1'i asan deger tasar (parlak yerler koyu/yesil gorunur). */
    private static float sat(float v) {
        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    public static float r(int c) { return ((c >> 16) & 0xFF) / 255F; }
    public static float g(int c) { return ((c >> 8) & 0xFF) / 255F; }
    public static float b(int c) { return (c & 0xFF) / 255F; }

    public static int mix(int a, int b, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Kameraya bakan dokulu billboard: (x,y,z) merkez, sahne kamerasina goreli koordinatlar m icinde;
     * sag/yukari vektorleri kamera yonelimi (camRight, camUp) ile verilir.
     */
    public static void billboard(BufferBuilder b, Matrix4f m, double x, double y, double z, float[] right, float[] up,
                                 float w, float h, float rot, int rgb, float a) {
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float rx = right[0] * cs + up[0] * sn, ry = right[1] * cs + up[1] * sn, rz = right[2] * cs + up[2] * sn;
        float ux = up[0] * cs - right[0] * sn, uy = up[1] * cs - right[1] * sn, uz = up[2] * cs - right[2] * sn;
        float hw = w * 0.5F, hh = h * 0.5F;
        float cr = r(rgb), cg = g(rgb), cb = b(rgb);
        v(b, m, x - rx * hw - ux * hh, y - ry * hw - uy * hh, z - rz * hw - uz * hh, 0, 1, cr, cg, cb, a);
        v(b, m, x + rx * hw - ux * hh, y + ry * hw - uy * hh, z + rz * hw - uz * hh, 1, 1, cr, cg, cb, a);
        v(b, m, x + rx * hw + ux * hh, y + ry * hw + uy * hh, z + rz * hw + uz * hh, 1, 0, cr, cg, cb, a);
        v(b, m, x - rx * hw + ux * hh, y - ry * hw + uy * hh, z - rz * hw + uz * hh, 0, 0, cr, cg, cb, a);
    }
}
