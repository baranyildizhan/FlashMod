package dev.baranhan.flashmod.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;

/** Kayit gerektirmeyen istemci parcaciklari: dogrudan ParticleEngine'e eklenir, additive cizilir. */
public final class FlashParticles {
    private FlashParticles() {}

    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "flashmod:additive";
        }
    };

    /** Parcacik ayarina gore 1.0 / 0.5 / 0.15. */
    public static float factor() {
        ParticleStatus s = Minecraft.getInstance().options.particles().get();
        return switch (s) {
            case ALL -> 1F;
            case DECREASED -> 0.5F;
            case MINIMAL -> 0.15F;
        };
    }

    public static void spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             int core, int glow, int life, float size) {
        Minecraft.getInstance().particleEngine.add(new SparkParticle(level, x, y, z, vx, vy, vz, core, glow, life, size));
    }
}
