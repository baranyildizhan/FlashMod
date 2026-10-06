package dev.baranhan.flashmod.client.ultimate;

import dev.baranhan.flashmod.network.UltimateStartPacket;
import dev.baranhan.flashmod.ultimate.ArenaFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Istemcideki bir ultimate oturumu (tam sinematik ya da izleyici). */
public final class UltState {
    public final int id, casterId, targetId;
    public final long startGameTime, seed;
    public final float scale;
    public final ArenaFrame arena;
    public final Vec3 startPos;
    public final int core, glow;
    public final boolean youAreTarget, preview;
    public boolean full;
    /** Sinematik atlandi: kamera serbest, ama sunucu kilidi suruyor (hareket kilitli kalir). */
    public boolean skipped;
    public final UltCamera.Track[] tracks;
    public final float targetW, targetH, contactY;
    /** Ilk vurusta hedefin ileri kayma mesafesi; hedefin baslangic konumu (gorsel itme duzeltmesi icin). */
    public final float push;
    public final Vec3 targetBase;
    /** Final geometrisi (sunucuyla ayni betik). */
    public final dev.baranhan.flashmod.ultimate.UltimateScript.Path path;
    public Vec3 lastTargetPos;
    /** Bizim iz sistemimizle cizilen sentetik izler: sahnedeki kosucu ve firlatilan hedef. */
    public final dev.baranhan.flashmod.client.ClientSpeedsters.Entry sceneTrail, targetTrail;

    // zaman: yerel tick sayaci (sunucu saat paketleri her saniye oyun zamanini +-1 tick oynatir; animasyon
    // ona bagli olsaydi kamera saniyede bir takilirdi). Sunucu zamanindan kalici sapma yavasca duzeltilir.
    private long clock;
    private int skewTicks;
    public float previewT;
    public boolean paused;
    public float lastTickT = -999F;
    public long abortAt = -1;

    // kamera
    public boolean snapTaken;
    public Vec3 snapPos = Vec3.ZERO;
    public float snapYaw, snapPitch, snapFov = 70F;
    public float trauma, lastShakeT = -999F;
    public Vec3 springPos, springVel = Vec3.ZERO;
    public double collDist = -1, prevFull = -1;
    public Vec3 lastCamPos = Vec3.ZERO;
    public float lastYaw, lastPitch, lastFov = 70F, lastRoll;
    /** SCENE fazinda sahne kamera konumu (sahne uzayi) ve bakisi. */
    public Vec3 sceneCam = Vec3.ZERO;

    public UltState(UltimateStartPacket m) {
        this.id = m.sessionId;
        this.casterId = m.casterId;
        this.targetId = m.targetId;
        this.startGameTime = m.startGameTime;
        this.seed = m.seed;
        this.scale = m.scale <= 0F ? 1F : m.scale;
        this.arena = m.arena;
        this.startPos = new Vec3(m.sx, m.sy, m.sz);
        this.core = m.core;
        this.glow = m.glow;
        this.youAreTarget = m.youAreTarget;
        this.preview = m.preview;
        this.full = m.fullCinematic;
        Minecraft mc = Minecraft.getInstance();
        this.clock = mc.level != null ? mc.level.getGameTime() - startGameTime : 0L;
        LivingEntity t = target();
        this.targetW = t != null ? t.getBbWidth() : 0.6F;
        this.targetH = t != null ? t.getBbHeight() : 1.8F;
        this.contactY = targetH > 2.2F ? targetH * 0.6F : (targetH < 1.0F ? Math.max(0.4F, targetH * 0.6F) : 1.35F);
        this.push = m.push;
        this.path = new dev.baranhan.flashmod.ultimate.UltimateScript.Path(push, m.fly, targetH);
        this.tracks = UltCamera.build(arena.d, path, targetW);
        this.lastTargetPos = t != null ? t.position() : arena.toWorld(0, 0, arena.d);
        this.targetBase = lastTargetPos;
        this.sceneTrail = dev.baranhan.flashmod.client.ClientSpeedsters.synthetic(seed ^ 0x51A7L, core, glow);
        this.targetTrail = dev.baranhan.flashmod.client.ClientSpeedsters.synthetic(seed ^ 0x7A26L, core, glow);
    }

    @Nullable
    public Player caster() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(casterId);
        return e instanceof Player p ? p : null;
    }

    @Nullable
    public LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || targetId < 0) return null;
        Entity e = mc.level.getEntity(targetId);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Ultimate tick'i (ondalikli). Onizlemede yerel saat, yoksa sunucu zamanina kilitli yerel sayac. */
    public float t(float pt) {
        if (preview) return previewT + (paused ? 0F : pt);
        return (clock + pt) / scale;
    }

    /** Her istemci tick'inde bir: yerel sayaci ilerletir; sunucu zamanindan >3 tick saparsa atlar, kucuk kalici sapmayi 10 tick'te bir 1 tick duzeltir. */
    public void advanceClock(long gameTime) {
        clock++;
        long d = (gameTime - startGameTime) - clock;
        if (Math.abs(d) > 3) {
            clock += d;
            skewTicks = 0;
        } else if (d != 0) {
            if (++skewTicks >= 10) { clock += Long.signum(d); skewTicks = 0; }
        } else {
            skewTicks = 0;
        }
    }

    /** Hedefin bu t'deki arena mesafesi (ilk vurusla ileri kayar). */
    public float dAt(float t) {
        return arena.d + push * dev.baranhan.flashmod.ultimate.UltimatePhase.pushEase(t);
    }

    /** Ikinci vurusun temas noktasi (dunya): itilmis hedefin gogsu. */
    public Vec3 contact() {
        return arena.toWorld(0D, contactY, arena.d + push - 0.25D);
    }

    /** Hedefin betikteki dunya konumu (ayaklar): itme, havaya kalkis, asili kalma, cakilma. */
    public Vec3 targetScripted(float t) {
        return targetBase.add(path.targetOffset(arena.fx, arena.fz, t));
    }

    /** Flash'in havadaki/inisteki dunya konumu (AIR_BLINK'ten itibaren). */
    public Vec3 casterAir(float t) {
        return targetBase.add(path.casterOffset(arena.fx, arena.fz, t));
    }

    /** t anindaki temas noktasi (ilk vurusta itme oncesi/sirasi icin). */
    public Vec3 contact(float t) {
        return arena.toWorld(0D, contactY, dAt(t) - 0.25D);
    }

    public boolean smallTarget() {
        return targetH < 1.0F;
    }
}
