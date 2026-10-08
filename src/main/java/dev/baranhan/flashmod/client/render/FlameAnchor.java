package dev.baranhan.flashmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.flashmod.FlashMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Yanan varliklarin vanilla alev katmani, modelin o karede GERCEKTEN cizildigi yerde.
 * Vanilla alevi EntityRenderDispatcher, renderer'dan sonra varligin gercek konumunda cizer; Blitz/ultimate/tornado
 * gibi modeli RenderLivingEvent.Pre'de baska yere tasiyan cizimlerde alev geride kaliyordu.
 * Ayni dispatcher.render cagrisinda: model cizilince ayaklarin PoseStack-uzayi konumu yakalanir, alev oraya kaydirilir;
 * model gizlendiyse (Pre iptal) alev de cizilmez.
 */
@Mod.EventBusSubscriber(modid = FlashMod.MODID, value = Dist.CLIENT)
public final class FlameAnchor {
    private static final Map<Integer, Vector4f> ANCHOR = new HashMap<>();
    private static final Set<Integer> HIDDEN = new HashSet<>();
    private static final Matrix4f INV = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static int pushed;

    private FlameAnchor() {}

    /** Dispatcher.render basi: bu cizime ait taze bilgi gelecek. */
    public static void begin(Entity e) {
        if (ANCHOR.isEmpty() && HIDDEN.isEmpty()) return;
        ANCHOR.remove(e.getId());
        HIDDEN.remove(e.getId());
    }

    /** Model cizildikten hemen sonra (PoseStack tam model donusumunde). */
    public static void capture(LivingEntity e, PoseStack ps) {
        if (!e.displayFireAnimation()) return;
        // translate(0,-1.501,0) geri alininca model uzayinda (0, 1.501, 0) = ayaklar
        ANCHOR.put(e.getId(), ps.last().pose().transform(new Vector4f(0F, 1.501F, 0F, 1F)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() && event.getEntity().displayFireAnimation()) HIDDEN.add(event.getEntity().getId());
    }

    /** renderFlame basi: false -> alev cizilmez. */
    public static boolean beforeFlame(PoseStack ps, Entity e) {
        pushed = 0;
        if (HIDDEN.remove(e.getId())) return false;
        Vector4f a = ANCHOR.remove(e.getId());
        if (a == null) return true;
        ps.last().pose().invert(INV).transform(TMP.set(a));
        if (TMP.lengthSquared() < 1.0E-6F) return true;
        ps.pushPose();
        ps.translate(TMP.x(), TMP.y(), TMP.z());
        pushed = 1;
        return true;
    }

    /** renderFlame sonu. */
    public static void afterFlame(PoseStack ps) {
        if (pushed == 1) ps.popPose();
        pushed = 0;
    }
}
