package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.AbilityClient;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.entity.LightningSpearEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Simsek mizragi: ucan, titreyen, sarjla kalinlasip uzayan bir simsek + parlayan uc. Sahibinin renklerinde. */
public class LightningSpearRenderer extends EntityRenderer<LightningSpearEntity> {
    private static final Polyline LINE = new Polyline();
    private static final Lightning.Rng R = new Lightning.Rng(21);

    public LightningSpearRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(LightningSpearEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        int core = 0xFFF3C4, glow = 0xFF9A1A;
        Entity owner = e.getOwner();
        if (owner instanceof Player p) {
            ClientSpeedsters.Entry en = ClientSpeedsters.get(p.getUUID());
            if (en != null) { core = en.core; glow = en.glow; }
        }
        if (e.clientLastFx != e.tickCount) {
            e.clientLastFx = e.tickCount;
            AbilityClient.spearTrailFx(e, core, glow);
        }
        float k = Mth.clamp(e.getCharge() / 100F, 0F, 1F);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 pos = e.getPosition(pt);
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = v.lengthSqr() > 1.0E-6 ? v.normalize() : new Vec3(0, 0, 1);
        float len = 2.0F + 2.5F * k;
        float hx = (float) (pos.x - cam.x), hy = (float) (pos.y - cam.y), hz = (float) (pos.z - cam.z);
        float tx = hx - (float) dir.x * len, ty = hy - (float) dir.y * len, tz = hz - (float) dir.z * len;

        // Kameraya goreli koordinatlar + gorus matrisi (GlowDraw kameraya donuk seritleri buna gore hesaplar)
        Matrix4f m = BodyPoseCapture.view();
        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        float bloom = 1F;
        R.seed(e.getId(), e.tickCount, 0x5EA7);
        for (int s = 0; s < 2 + (int) (k * 2); s++) {
            LINE.clear();
            Lightning.jag(LINE, R, tx, ty, tz, hx, hy, hz, 0.08F + 0.05F * s, 4, false, 0.15F, 1F);
            GlowDraw.layered(vc, m, LINE, (0.9F + 0.9F * k) * (s == 0 ? 1F : 0.6F), core, glow, 1F, bloom, false);
        }
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.6F);
        GlowDraw.orb(vc, m, hx, hy, hz, 0.35F + 0.5F * k, GlowDraw.cr(glow), GlowDraw.cg(glow), GlowDraw.cb(glow), 0.45F);
        GlowDraw.orb(vc, m, hx, hy, hz, 0.1F + 0.12F * k, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), 1F);
    }

    @Override
    public ResourceLocation getTextureLocation(LightningSpearEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
