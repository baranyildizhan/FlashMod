package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.flashmod.client.skill.SkillPoses;
import dev.baranhan.flashmod.client.ultimate.UltPoses;
import dev.baranhan.flashmod.client.ultimate.UltRender;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.entity.AfterimageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Zaman kalintisi: sahibinin skin'iyle, zamanda donmus yari saydam parlak model (kosu ya da dovus durusu), iki yana
 * kaymis renk-sapmasi kopyalari, govdenin ustunde gezinen simsekler, yukari suzulen isik zerreleri.
 * Dogusta asiri pozlanmis beyaz bir an ve ayaktan basa tarayan "donma" halkasi; solarken yukari dogru cozulur.
 */
public class AfterimageRenderer extends EntityRenderer<AfterimageEntity> {
    private static final Lightning.Rng R = new Lightning.Rng(71);
    private static final Polyline LINE = new Polyline();
    private final PlayerModel<AbstractClientPlayer> wide, slim;
    private final float[] pose = new float[UltPoses.N];

    public AfterimageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(AfterimageEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(1.5D));
    }

    @Override
    public void render(AfterimageEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float age = e.tickCount + pt;
        int fadeAt = e.fadeAt();
        float fade = fadeAt < 0 ? 1F : Mth.clamp(1F - (e.tickCount - fadeAt + pt) / AfterimageEntity.FADE_TICKS, 0F, 1F);
        if (fade <= 0.01F) return;
        float birth = Mth.clamp(age / 6F, 0F, 1F);
        int core = e.core(), glow = e.glow();

        // skin ve model: sahibi yakindaysa onun gorunumu (kostum dahil), yoksa varsayilan
        UUID owner = e.ownerId();
        Player op = owner == null ? null : mc.level.getPlayerByUUID(owner);
        ResourceLocation skin;
        boolean isSlim;
        if (op instanceof AbstractClientPlayer acp) {
            skin = acp.getSkinTextureLocation();
            isSlim = "slim".equals(acp.getModelName());
        } else {
            UUID id = owner != null ? owner : e.getUUID();
            skin = DefaultPlayerSkin.getDefaultSkin(id);
            isSlim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        }
        PlayerModel<AbstractClientPlayer> model = isSlim ? slim : wide;
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0F;
        model.setAllVisible(true);

        System.arraycopy(e.running() ? SkillPoses.RUN : SkillPoses.STANCE, 0, pose, 0, UltPoses.N);
        // donmus ama "canli": cok kucuk, hizli zaman titremesi
        float glitch = (((int) (age * 2.5F)) % 7 == 0) ? 1F : 0F;
        pose[UltPoses.HY] += glitch * 6F;
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);

        float flicker = 0.9F + 0.1F * Mth.sin(age * 1.7F) * Mth.sin(age * 0.53F);
        float overexpose = 1F - birth;                 // dogus: asiri parlak beyaz
        int tint = GlowDraw.mixRgb(UltRender.mixWhite(glow, 0.55F), 0xFFFFFF, overexpose);
        float alpha = (0.38F + 0.4F * overexpose) * fade * flicker;
        // cozulurken model asagidan yukari silinmez (vanilla model kesilemez); yerine saydamlasir ve yukari kayar
        double lift = (1F - fade) * 0.35;

        VertexConsumer vc = buffers.getBuffer(FlashRenderTypes.ghost(skin));
        UltRender.drawModel(model, ps, vc, LightTexture.FULL_BRIGHT, 0, lift, 0, bodyYaw, pose, alpha, tint);
        float yr = bodyYaw * Mth.DEG_TO_RAD;
        float rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        float off = 0.055F + 0.035F * Mth.sin(age * 0.8F) + glitch * 0.06F;
        UltRender.drawModel(model, ps, vc, LightTexture.FULL_BRIGHT, rx * off, lift, rz * off, bodyYaw, pose,
                0.2F * fade * flicker, core);
        UltRender.drawModel(model, ps, vc, LightTexture.FULL_BRIGHT, -rx * off, lift, -rz * off, bodyYaw, pose,
                0.2F * fade * flicker, glow);
        // isiktan kabuk: ayni model eklemeli -> govde kendinden parlar (zamandan kopmus enerji)
        VertexConsumer add = buffers.getBuffer(FlashRenderTypes.glowGhost(skin));
        UltRender.drawModel(model, ps, add, LightTexture.FULL_BRIGHT, 0, lift, 0, bodyYaw, pose,
                (0.42F + 0.5F * overexpose) * fade * flicker, UltRender.mixWhite(glow, 0.25F));

        // --- isik: kameraya goreli koordinat + gorus matrisi (ghost tamponundan sonra alinir)
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 pos = e.getPosition(pt);
        float x = (float) (pos.x - cam.x), y = (float) (pos.y + lift - cam.y), z = (float) (pos.z - cam.z);
        Matrix4f m = BodyPoseCapture.view();
        VertexConsumer g = buffers.getBuffer(FlashRenderTypes.ADDITIVE_GLOW);
        float bloom = Math.max(0.6F, FlashClientConfig.BLOOM.get().floatValue());
        float gr = GlowDraw.cr(glow), gg = GlowDraw.cg(glow), gb = GlowDraw.cb(glow);
        int hot = GlowDraw.mixRgb(core, 0xFFFFFF, 0.55F);

        // govdeyi saran yumusak hale
        GlowDraw.orb(g, m, x, y + 1.0F, z, 1.15F, gr, gg, gb, 0.1F * fade * bloom);

        // dogus: ayaktan basa tarayan donma halkasi
        if (age < 7F) {
            float u = age / 7F;
            float hy = y + 0.05F + u * 1.95F;
            float a = (1F - u) * fade;
            GlowDraw.ring(g, m, x, hy, z, 1, 0, 0, 0, 0, 1, 0.55F, 0.12F, gr, gg, gb, 0.5F * a, 28);
            GlowDraw.ring(g, m, x, hy, z, 1, 0, 0, 0, 0, 1, 0.55F, 0.025F, GlowDraw.cr(hot), GlowDraw.cg(hot),
                    GlowDraw.cb(hot), 0.95F * a, 28);
        }

        // govdenin ustunde gezinen kisa simsekler
        long frame = (long) (age / 2.5F);
        R.seed(e.getId() * 7919L, frame, 3);
        int arcs = 4 + (int) (3 * overexpose);
        for (int i = 0; i < arcs; i++) {
            float a0 = R.next() * Mth.TWO_PI, h0 = 0.15F + R.next() * 1.65F;
            float a1 = a0 + R.signed() * 1.4F, h1 = Mth.clamp(h0 + R.signed() * 0.6F, 0.05F, 1.85F);
            float r0 = h0 > 1.45F ? 0.27F : 0.33F, r1 = h1 > 1.45F ? 0.27F : 0.33F;
            LINE.clear();
            Lightning.jag(LINE, R, x + Mth.cos(a0) * r0, y + h0, z + Mth.sin(a0) * r0, x + Mth.cos(a1) * r1, y + h1,
                    z + Mth.sin(a1) * r1, 0.32F, 3, false, 0.9F * fade, 0.3F * fade);
            GlowDraw.layered(g, m, LINE, 0.46F, core, glow, fade, bloom, false);
        }

        // yukari suzulen isik zerreleri (kalinti zamanin disinda: zerreler yavas, dalgali)
        for (int i = 0; i < 7; i++) {
            float ph = (age * 0.018F + i / 7F) % 1F;
            R.seed(e.getId() * 31L + i, 0, 9);
            float ang = R.next() * Mth.TWO_PI + age * 0.02F, rad = 0.25F + R.next() * 0.35F;
            float mx = x + Mth.cos(ang) * rad, mz = z + Mth.sin(ang) * rad, my = y + 0.1F + ph * 2.1F;
            float a = Mth.sin(ph * Mth.PI) * 0.8F * fade;
            GlowDraw.orb(g, m, mx, my, mz, 0.05F, GlowDraw.cr(hot), GlowDraw.cg(hot), GlowDraw.cb(hot), a);
            GlowDraw.orb(g, m, mx, my, mz, 0.16F, gr, gg, gb, 0.25F * a);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AfterimageEntity e) {
        return DefaultPlayerSkin.getDefaultSkin(e.getUUID());
    }
}
