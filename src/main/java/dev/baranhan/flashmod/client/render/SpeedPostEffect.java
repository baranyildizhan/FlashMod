package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.ClientSpeedsters;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;

/**
 * Hiz post-processing'i. Shader pack gerektirmez: mod kendi core shader'ini yukler, dunya cizildikten sonra
 * (AFTER_LEVEL, el ve GUI'den once) ana ekrani bir kopyaya alir ve tam ekran tek bir pass ile geri yazar.
 *
 *  - Kenarlara dogru artan radyal hiz bulanikligi (merkez net kalir -> goz yormaz)
 *  - Hafif renk sapmasi (chromatic aberration), hiz ve overdrive ile artar
 *  - Ses duvarinda kisa goruntu sarsintisi + sapma darbesi
 *  - Phasing'de yatay titreyen ghosting
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class SpeedPostEffect {
    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static TextureTarget copy;
    private static float smoothIntensity, smoothPhase;
    private static long lastFrameNanos;

    private SpeedPostEffect() {}

    public static void setShader(ShaderInstance s) {
        shader = s;
    }

    /** LOW: iz renderer'i (HIGH) once cizilsin, efekt onlara da uygulansin. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance sh = shader;
        if (sh == null || mc.player == null || mc.level == null || !FlashClientConfig.POST_PROCESSING.get()) return;
        // Not: zaman yavaslamasinin mavi tonu hizci olmayan oyuncularda da calisir (asagida 'slow').

        float pt = event.getPartialTick();
        long now = mc.level.getGameTime();
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        float target = 0F, phase = 0F, shake = 0F;
        float cine = 0F; // Blitz: ekranda renk/ton efekti yok (sadece sinematik seritler, BlitzClient.renderBars)
        float slow = dev.baranhan.flashmod.client.TimeControlClient.amount(); // zaman yavaslamasi: herkeste hafif mavi ton
        float rewind = dev.baranhan.flashmod.client.skill.SkillClient.rewindAmount(pt); // kendi geri sarman
        if (e != null && e.active && !e.blitz) {
            float run = Mth.clamp((e.intensity(pt) - 0.15F) / 0.85F, 0F, 1F);
            target = run * 0.65F + e.overdrive(pt) * 0.35F;
            phase = PhaseHelper.clientLocalPhasing ? 1F : 0F;
            shake = Math.min(e.boomShake(now, pt), 1.5F);
        }

        // Kare hizindan bagimsiz yumusatma
        long nanos = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016F : Mth.clamp((nanos - lastFrameNanos) / 1.0E9F, 0F, 0.1F);
        lastFrameNanos = nanos;
        float k = 1F - (float) Math.exp(-dt * 6.0F);
        smoothIntensity += (target - smoothIntensity) * k;
        smoothPhase += (phase - smoothPhase) * (1F - (float) Math.exp(-dt * 12.0F));

        float strength = FlashClientConfig.POST_STRENGTH.get().floatValue();
        if (cine > 0.001F || slow > 0.001F || rewind > 0.001F) strength = Math.max(strength, 0.6F); // renk ayarlari her zaman
        if (strength <= 0F || (smoothIntensity < 0.005F && smoothPhase < 0.005F && shake < 0.005F && cine < 0.005F
                && slow < 0.005F && rewind < 0.005F)) return;

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }

        // Ana goruntuyu kopyala (GPU blit, CPU'ya inmez)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        sh.safeGetUniform("Intensity").set(smoothIntensity * strength);
        sh.safeGetUniform("Phase").set(smoothPhase * Math.min(strength, 1.2F));
        sh.safeGetUniform("Shake").set(shake * strength);
        sh.safeGetUniform("Cine").set(cine);
        sh.safeGetUniform("Slow").set(slow);
        sh.safeGetUniform("Rewind").set(rewind);
        sh.safeGetUniform("Time").set(((now % 24000L) + pt) / 20.0F);
        sh.safeGetUniform("ScreenSize").set((float) w, (float) h);

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        buf.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        buf.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        buf.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
