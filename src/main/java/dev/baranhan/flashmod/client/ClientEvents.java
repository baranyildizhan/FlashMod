package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.FlashMod;
import dev.baranhan.flashmod.client.gui.FlashColorScreen;
import dev.baranhan.flashmod.client.render.PhaseRenderer;
import dev.baranhan.flashmod.config.FlashClientConfig;
import dev.baranhan.flashmod.config.FlashServerConfig;
import dev.baranhan.flashmod.network.AbilityInputPacket;
import dev.baranhan.flashmod.network.BlitzStartPacket;
import dev.baranhan.flashmod.network.ChangeLevelPacket;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.PhaseInputPacket;
import dev.baranhan.flashmod.network.SetColorsPacket;
import dev.baranhan.flashmod.network.ToggleSpeedPacket;
import dev.baranhan.flashmod.speed.AbilityLogic;
import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean lastPhaseHeld = false;
    /** Yerel oyuncunun son yerdeki yatay hizi (blok/tick), havada korunur. */
    private static double airGroundSpeed = 0.0D;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.screen == null) {
            while (FlashKeys.TOGGLE.consumeClick()) FlashNetwork.sendToServer(new ToggleSpeedPacket());
            while (FlashKeys.LEVEL_UP.consumeClick()) FlashNetwork.sendToServer(new ChangeLevelPacket(1));
            while (FlashKeys.LEVEL_DOWN.consumeClick()) FlashNetwork.sendToServer(new ChangeLevelPacket(-1));
            while (FlashKeys.COLORS.consumeClick()) mc.setScreen(new FlashColorScreen(null));
            while (FlashKeys.WARDROBE.consumeClick()) mc.setScreen(new dev.baranhan.flashmod.client.gui.SkinWardrobeScreen(null));
            while (FlashKeys.AERIAL.consumeClick()) CameraModes.aerial = !CameraModes.aerial;
            while (FlashKeys.BLITZ.consumeClick()) tryBlitz(mc);
            while (FlashKeys.ULTIMATE.consumeClick()) {
                if (!dev.baranhan.flashmod.client.ultimate.UltDirector.inputLocked() && !BlitzClient.localActive() && !Tornado.isActive())
                    FlashNetwork.sendToServer(new dev.baranhan.flashmod.network.UltimateRequestPacket());
            }
        }

        updateLocalPhasing(mc);
        updateAbilityKeys(mc);
        if (!mc.isPaused()) SpeedFx.tickAll(mc);
    }

    private static boolean lastThrowHeld;

    /** Mizrak (basili tut/birak; sadece degisimde) ve agir cekim (ac/kapa; her basista) tuslarini sunucuya bildir. */
    private static void updateAbilityKeys(Minecraft mc) {
        boolean free = mc.screen == null && !BlitzClient.localActive();
        boolean t = free && FlashKeys.THROW.isDown();
        if (t != lastThrowHeld) {
            lastThrowHeld = t;
            FlashNetwork.sendToServer(new AbilityInputPacket(AbilityLogic.THROW, t));
        }
        while (FlashKeys.SLOWMO.consumeClick()) {
            if (free) FlashNetwork.sendToServer(new AbilityInputPacket(AbilityLogic.SLOWMO, true));
        }
    }

    /**
     * Yuklenmemis chunk'a dalma korumasi (yerel oyuncu). Vanilla, oyuncu istemcide yuklu olmayan bir chunk'a girince
     * oyuncunun tick'ini tamamen durdurur -> hizci "takilip" donar. Gidis yonunde birkac tick ilerisi yuklu degilse
     * hiz, yuklu sinira kadar yumusakca kisilir; chunk gelince kendiliginden tam hiza doner.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChunkGuard(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.player != mc.player || mc.level == null) return;
        LocalPlayer p = mc.player;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.active || e.blitz || Tornado.isActive() || WallRun.isActive()) return;
        Vec3 v = p.getDeltaMovement();
        double sp = Math.sqrt(v.x * v.x + v.z * v.z);
        if (sp < 0.6D) return;
        double ux = v.x / sp, uz = v.z / sp;
        double look = sp * 4.0D + 8.0D; // ~4 tick ilerisi + pay
        double free = -1.0D;
        for (double d = 4.0D; d <= look; d += 4.0D) {
            int cx = Mth.floor(p.getX() + ux * d) >> 4;
            int cz = Mth.floor(p.getZ() + uz * d) >> 4;
            if (!mc.level.hasChunk(cx, cz)) {
                free = d;
                break;
            }
        }
        if (free < 0.0D) return;
        double allowed = Math.max(0.15D, (free - 3.0D) * 0.35D);
        if (sp > allowed) {
            double k = allowed / sp;
            p.setDeltaMovement(v.x * k, v.y, v.z * k);
            airGroundSpeed = Math.min(airGroundSpeed, allowed);
        }
    }

    /** Duvarda kosarken birinci sahis kamera roll'u (WallRun.camera hesaplar). Diger sarsintilardan once. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onWallRunRoll(ViewportEvent.ComputeCameraAngles event) {
        float r = WallRun.roll();
        if (r != 0F) event.setRoll(event.getRoll() + r);
    }

    /** Baktigin canliya Blitz. Hedef secimi istemcide (aim yardimiyla), dogrulama sunucuda. */
    private static void tryBlitz(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        if (e == null || !e.active || e.blitz || Tornado.isActive() || WallRun.isActive()) return; // duvarda Blitz yok
        LivingEntity target = BlitzClient.pickTarget(p, 32.0D);
        if (target == null) {
            p.displayClientMessage(Component.translatable("msg.flashmod.no_target"), true);
            return;
        }
        FlashNetwork.sendToServer(new BlitzStartPacket(target.getId()));
    }

    /**
     * Phasing istemcide gecikmesiz hesaplanir (hareket istemci-otoriter), sunucu ayni kurali uygular.
     * Tus birakilsa bile bir blogun icindeysen disari cikana kadar phasing devam eder.
     */
    private static void updateLocalPhasing(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean held = mc.screen == null && FlashKeys.PHASE.isDown();
        if (held != lastPhaseHeld) {
            lastPhaseHeld = held;
            FlashNetwork.sendToServer(new PhaseInputPacket(held));
        }
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        boolean enabled;
        try {
            enabled = FlashServerConfig.PHASING.get();
        } catch (IllegalStateException ex) {
            enabled = true;
        }
        boolean was = PhaseHelper.clientLocalPhasing;
        boolean now = e != null && e.active && enabled && !p.isSpectator() && !p.isPassenger() && !Tornado.isActive()
                && !WallRun.isActive()
                && (held || (was && PhaseHelper.insideSolid(p)));
        PhaseHelper.clientLocalPhasing = now;
        if (now && !was && p.onGround()) PhaseHelper.setFloor(p, p.getY());
        if (!now) PhaseHelper.clearClientFloor();
        if (e != null) e.phasing = now;
    }

    /**
     * Su ustunde kosma (seviye 3+). Hareket istemci-otoriter oldugu icin sadece yerel oyuncuda yapilir;
     * sunucu suyu carpisma saymadigi icin "moved wrongly" uretmez. Egilince batarsin.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer p)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean tornadoHeld = mc.screen == null && FlashKeys.TORNADO.isDown() && !WallRun.isActive(); // duvarda tornado yok
        if (BlitzClient.localActive()) { // sinematik: gercek beden yerinde donar
            Vec3 bv = p.getDeltaMovement();
            p.setDeltaMovement(0.0D, Math.min(0.0D, bv.y), 0.0D);
            airGroundSpeed = 0.0D;
            return;
        }
        if (Tornado.tick(p, tornadoHeld)) {
            airGroundSpeed = 0.0D;
            return;
        }
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        WallRun.tick(p, e); // duvarda kosma baslat/bitir (hareketin kendisi Player.travel yerine, PlayerTravelMixin)
        if (WallRun.isActive()) {
            airGroundSpeed = 0.0D;
            return;
        }
        if (e == null || !e.active) {
            airGroundSpeed = 0.0D;
            return;
        }
        airMomentum(p);
        if (e.level < 3) return;
        if (p.isShiftKeyDown() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying()) return;
        Vec3 v = p.getDeltaMovement();
        if (v.horizontalDistance() < 0.3D) return;

        BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.05D, p.getZ());
        FluidState fluid = p.level().getFluidState(feet);
        if (!fluid.is(FluidTags.WATER)) return;
        if (p.level().getFluidState(feet.above()).is(FluidTags.WATER)) return;
        double surface = feet.getY() + fluid.getHeight(p.level(), feet);
        if (p.getY() < surface - 0.6D || p.getY() > surface + 0.05D) return;

        p.setPos(p.getX(), surface, p.getZ());
        p.setDeltaMovement(v.x, Math.max(0.0D, v.y), v.z);
        p.setOnGround(true);
        p.resetFallDistance();
    }

    /**
     * Havada hiz kaybi yok: vanilla havada movement_speed yerine sabit 0.02 ivme ve 0.91 surtunme kullanir,
     * bu yuzden ziplayinca hiz hizla erirdi. Yerdeki hizi koruyoruz (WASD ile yon degistirilebilir) ve hiza bagli
     * ekstra yercekimi ekliyoruz: ne kadar hizliysan o kadar cabuk yere inersin (dev sicramalar olmaz).
     */
    private static void airMomentum(LocalPlayer p) {
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (p.onGround()) {
            airGroundSpeed = moved;
            return;
        }
        if (p.isShiftKeyDown() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying()
                || p.isInWater() || p.isInLava() || p.onClimbable() || airGroundSpeed < 0.35D) {
            return;
        }
        if (p.horizontalCollision) { // duvara carpti: hizi kaybet
            airGroundSpeed = moved;
            return;
        }
        Vec3 v = p.getDeltaMovement();
        double hv = Math.sqrt(v.x * v.x + v.z * v.z);
        double dirX, dirZ;
        if (hv > 1.0E-4D) { dirX = v.x / hv; dirZ = v.z / hv; }
        else if (moved > 1.0E-4D) { dirX = dx / moved; dirZ = dz / moved; }
        else return;

        // Havada yon kontrolu (vanilla getInputVector ile ayni donusum)
        float fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
        if (Math.abs(fwd) + Math.abs(left) > 0.01F) {
            float yaw = p.getYRot() * ((float) Math.PI / 180F);
            double ix = left * Mth.cos(yaw) - fwd * Mth.sin(yaw);
            double iz = fwd * Mth.cos(yaw) + left * Mth.sin(yaw);
            double il = Math.sqrt(ix * ix + iz * iz);
            if (il > 1.0E-4D) {
                dirX = dirX * 0.8D + ix / il * 0.2D;
                dirZ = dirZ * 0.8D + iz / il * 0.2D;
                double l = Math.sqrt(dirX * dirX + dirZ * dirZ);
                dirX /= l;
                dirZ /= l;
            }
        }
        airGroundSpeed *= 0.997D;
        double speed = Math.max(hv, airGroundSpeed);
        double extraGravity = 0.08D * Math.min(9.0D, Math.sqrt(airGroundSpeed) * 0.8D);
        p.setDeltaMovement(dirX * speed, v.y - extraGravity, dirZ * speed);
    }

    /** Tornado sirasinda yuruyus girdisi tamamen iptal (sprint de baslamaz -> FOV degismez). Ziplama serbest. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!Tornado.isActive() && !BlitzClient.localActive()) return;
        Input in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.shiftKeyDown = false;
        event.getEntity().setSprinting(false);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if ((Tornado.isActive() || BlitzClient.localActive()) && event.getPlayer() == Minecraft.getInstance().player) {
            event.setNewFovModifier(1.0F); // tornadoda FOV hic oynamasin
            return;
        }
        ClientSpeedsters.Entry e = ClientSpeedsters.get(event.getPlayer().getUUID());
        if (e == null || !e.active || event.getPlayer().isUsingItem()) return;
        // Vanilla hiz-FOV'u attribute'a bagli ve overdrive'da ekrani "patlatir"; yerine kontrollu bir deger.
        float pt = Minecraft.getInstance().getFrameTime();
        float f = 1F + (event.getPlayer().isSprinting() ? 0.08F : 0F);
        if (!CameraModes.detachedByUs()) { // kus bakisi / tornado kamerasinda FOV patlamasin
            f += FlashClientConfig.FOV_BOOST.get().floatValue() * e.intensity(pt) + 0.12F * e.overdrive(pt);
        }
        if (event.getPlayer().getAbilities().flying) f *= 1.1F;
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(Mth.lerp(scale, 1F, f));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !FlashClientConfig.CAMERA_SHAKE.get()) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        if (e == null || !e.active) return;
        float pt = (float) event.getPartialTick();
        float t = (mc.player.tickCount + pt) * 0.9F;
        boolean fp = mc.options.getCameraType() == CameraType.FIRST_PERSON && !CameraModes.detachedByUs();

        // Yuksek hizda hafif yol titremesi (sadece birinci sahis)
        float k = fp ? Mth.clamp((e.intensity(pt) - 0.3F) / 0.7F, 0F, 1F) : 0F;
        float amp = k * k * 0.5F + e.overdrive(pt) * 0.35F * (fp ? 1F : 0F);
        // Ses duvari darbesi (her kamera modunda)
        float boom = e.boomShake(mc.level.getGameTime(), pt);
        amp += boom * 2.2F;
        if (amp <= 0.001F) return;
        event.setRoll(event.getRoll() + (Mth.sin(t * 1.7F) * 0.6F + Mth.sin(t * 4.3F) * 0.4F) * amp);
        event.setPitch(event.getPitch() + (Mth.sin(t * 2.9F + 1.3F) * 0.5F + Mth.sin(t * 7.1F) * 0.5F) * amp * 0.5F);
        event.setYaw(event.getYaw() + Mth.sin(t * 5.3F + 0.7F) * boom * 0.8F);
    }

    /** Blitz sinematiginde HUD tamamen gizli; sadece sinema seritleri kalir. */
    @SubscribeEvent
    public static void onGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (CameraModes.blitzAmount() <= 0.01F) return;
        if (!event.getOverlay().id().getPath().equals("blitz_bars")) event.setCanceled(true);
    }

    /** Sinematik sirasinda saldiri/kullanma tuslari bos. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (BlitzClient.localActive()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Duvarin icindeyken blok dokusu ekrani kaplamasin. */
    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK && PhaseHelper.clientLocalPhasing) {
            event.setCanceled(true);
        }
    }

    /** Birinci sahista titreyen eller (ana el once cizilir, kayma iki ele de uygulanir). */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (CameraModes.detachedByUs()) { // kamera govdeden ayrildiysa ekranda el olmasin
            event.setCanceled(true);
            return;
        }
        if (!PhaseHelper.clientLocalPhasing || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        event.getPoseStack().translate(PhaseRenderer.jitter(mc.player.getId(), 0) * 0.07F, 0.0F, 0.0F);
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Sadece sunucuda kayitli renk yoksa uygulanir (kayitli renkler oyuncu NBT'sinde, her giriste korunur)
        FlashNetwork.sendToServer(new SetColorsPacket(FlashClientConfig.core(), FlashClientConfig.glow(), true));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSpeedsters.clear();
        PhaseHelper.clientLocalPhasing = false;
        PhaseHelper.clearClientFloor();
        lastPhaseHeld = false;
        lastThrowHeld = false;
        TimeControlClient.reset();
        WallRun.reset();
        dev.baranhan.flashmod.speed.WallState.clearClient();
        dev.baranhan.flashmod.client.skin.SkinManager.clear();
        Tornado.reset();
        BlitzClient.reset();
        SpeedSounds.clear();
        CameraModes.aerial = false;
    }
}
