package dev.baranhan.flashmod.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.render.GlowDraw;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Hiz yonunde uzayan, yercekimli, sekebilen kivilcim cizgisi. */
public class SparkParticle extends Particle {
    private final int core;
    private final int glow;
    private final float size;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            int core, int glow, int life, float size) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.core = core;
        this.glow = glow;
        this.size = size;
        this.lifetime = Math.max(2, life);
        this.gravity = 0.35F;
        this.friction = 0.92F;
        this.hasPhysics = true;
        this.setSize(0.05F, 0.05F);
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(pt, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(pt, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(pt, zo, this.z) - cam.z);
        float life = 1F - (age + pt) / lifetime;
        if (life <= 0F) return;
        float k = 1.6F; // kuyruk uzunlugu (tick cinsinden hiz)
        float tx = x - (float) xd * k, ty = y - (float) yd * k, tz = z - (float) zd * k;
        float a = life * life;
        float glowA = 0.35F * a;
        GlowDraw.segment(vc, GlowDraw.IDENTITY, tx, ty, tz, x, y, z, 0.06F * size,
                GlowDraw.cr(glow), GlowDraw.cg(glow), GlowDraw.cb(glow), 0F, glowA, false);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.4F);
        GlowDraw.segment(vc, GlowDraw.IDENTITY, tx, ty, tz, x, y, z, 0.014F * size,
                GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 0F, a, false);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FlashParticles.ADDITIVE;
    }
}
