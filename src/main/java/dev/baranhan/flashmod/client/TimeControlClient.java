package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.FlashMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Istemci tarafi zaman yavaslatma.
 *  - Zamanlayici (TimerMixin) sunucunun gonderdigi orana gore yavaslar -> butun dunya akici yavas akar.
 *  - Hizci dahil herkes ayni oranda yavaslar (ek alt adim yok: kollarin/izin takilmasi ve modelin yanip
 *    sonmesi o alt adimlardan geliyordu).
 *  - Sesler (muzik haric) agir cekimde biraz pesler.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class TimeControlClient {
    private static float serverRate = 1F;
    private static float rate = 1F;
    private static long lastNanos;

    private TimeControlClient() {}

    public static void setServerRate(float r) {
        serverRate = Mth.clamp(r, 0.05F, 1F);
    }

    /** Zamanlayicinin kullandigi oran (kare basina sunucu oranina yumusakca yaklasir). */
    public static float rate() {
        return rate;
    }

    /** 0..1 agir cekim gorsel/isitsel karisimi (1 = yapilandirilmis en yavas oran civari). */
    public static float amount() {
        return Mth.clamp((1F - rate) / 0.6F, 0F, 1F);
    }

    public static void reset() {
        serverRate = 1F;
        rate = 1F;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Mth.clamp((now - lastNanos) / 1.0E9F, 0F, 0.1F);
        lastNanos = now;
        if (Minecraft.getInstance().level == null) {
            reset();
            return;
        }
        rate += (serverRate - rate) * (1F - (float) Math.exp(-dt * 10F));
        if (Math.abs(serverRate - rate) < 0.002F) rate = serverRate;
    }

    /** Agir cekimde sesler (muzik/plak haric) pesler. Dongu/tick'li sesler sarmalanmaz (kendi perdelerini yonetir). */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        float a = amount();
        if (s == null || a <= 0.01F || s instanceof TickableSoundInstance) return;
        if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.RECORDS || s.getSource() == SoundSource.MASTER) return;
        event.setSound(new Pitched(s, 1F - 0.4F * a));
    }

    /** Perdesi carpanla degistirilmis ses (geri kalan her sey asil sese yonlendirilir). */
    private record Pitched(SoundInstance base, float mul) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return base.getLocation(); }
        @Nullable @Override public WeighedSoundEvents resolve(SoundManager m) { return base.resolve(m); }
        @Override public Sound getSound() { return base.getSound(); }
        @Override public SoundSource getSource() { return base.getSource(); }
        @Override public boolean isLooping() { return base.isLooping(); }
        @Override public boolean isRelative() { return base.isRelative(); }
        @Override public int getDelay() { return base.getDelay(); }
        @Override public float getVolume() { return base.getVolume(); }
        @Override public float getPitch() { return base.getPitch() * mul; }
        @Override public double getX() { return base.getX(); }
        @Override public double getY() { return base.getY(); }
        @Override public double getZ() { return base.getZ(); }
        @Override public Attenuation getAttenuation() { return base.getAttenuation(); }
        @Override public boolean canStartSilent() { return base.canStartSilent(); }
        @Override public boolean canPlaySound() { return base.canPlaySound(); }
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sound, boolean looping) {
            return base.getStream(lib, sound, looping);
        }
    }
}
