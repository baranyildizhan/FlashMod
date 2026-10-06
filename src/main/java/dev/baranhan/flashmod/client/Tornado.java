package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.TornadoStatePacket;
import dev.baranhan.flashmod.network.TornadoSyncPacket;
import dev.baranhan.flashmod.speed.PhaseHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Yerel oyuncunun tornadosu (basili tut). Hareket istemci-otoriter:
 *  - Baslarken merkez oyuncunun sagina/soluna 3 blok konur, oyuncu bakis yonunde kosmaya baslar.
 *  - Basiliyken teget hiz seviyeye gore hizlanir (seviye 10 = Mach 1, ust sinir Mach 1).
 *  - Birakinca yavaslar; tam durmadan tekrar basarsan kaldigi hizdan devam eder. Durunca son yonde biraz kayar.
 *  - Kirisin uzunlugu en fazla 6 blok/tick -> sunucunun hiz kontrolune takilmaz.
 */
public final class Tornado {
    public static final float RADIUS = ClientSpeedsters.TORNADO_RADIUS;
    public static final float MACH1 = ClientSpeedsters.TORNADO_MACH1;
    private static final float DECEL = 0.45F;      // blok/tick^2, Mach 1'den ~2 sn'de durur
    private static final float STOP_SPEED = 0.3F;

    private static boolean active;
    private static double cx, cy, cz;
    private static float angle, prevAngle, speed;
    private static int dir = 1;
    private static int syncTimer;

    private Tornado() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3 center() {
        return new Vec3(cx, cy, cz);
    }

    private static float maxSpeed(int level) {
        return Math.min(MACH1, MACH1 * (0.3F + 0.07F * level));
    }

    private static float accel(int level) {
        return 0.15F + 0.06F * level;
    }

    /** LocalPlayer PlayerTick END'de cagrilir. true donerse bu tick tornado hareketi uygulandi. */
    public static boolean tick(LocalPlayer p, boolean held) {
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        boolean can = e != null && e.active && !p.isSpectator() && !p.isPassenger() && !p.isFallFlying()
                && !p.getAbilities().flying && !PhaseHelper.clientLocalPhasing && !p.isSleeping() && !e.blitz;
        if (!can) {
            if (active) end(p, e, false);
            return false;
        }
        if (held && !active) start(p, e);
        if (!active) return false;

        if (held) speed = Math.min(maxSpeed(e.level), speed + accel(e.level));
        else speed = Math.max(0F, speed - DECEL);
        if (!held && speed < STOP_SPEED) {
            end(p, e, true);
            return false;
        }

        prevAngle = angle;
        angle += dir * speed / RADIUS;
        if (Math.abs(angle) > 4096F) { // hassasiyet icin sarmala (ikisini birlikte kaydir)
            float shift = (float) (Math.floor(angle / (Math.PI * 2.0)) * Math.PI * 2.0);
            angle -= shift;
            prevAngle -= shift;
        }

        double tx = cx + Mth.cos(angle) * RADIUS, tz = cz + Mth.sin(angle) * RADIUS;
        double ty = p.getY();
        AABB box = p.getBoundingBox().move(tx - p.getX(), 0.0D, tz - p.getZ());
        if (!p.level().noCollision(p, box)) {
            if (p.level().noCollision(p, box.move(0.0D, 1.0D, 0.0D))) {
                ty += 1.0D; // tek blok basamak
            } else {
                angle = prevAngle; // engele carpti: sert yavasla
                speed *= 0.35F;
                if (speed < STOP_SPEED && !held) end(p, e, false);
                writeEntry(e);
                return true;
            }
        }
        p.setPos(tx, ty, tz);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(0.0D, v.y, 0.0D); // yatayda kaymasin, yercekimi normal
        p.resetFallDistance();
        cy = p.getY();

        writeEntry(e);
        if (--syncTimer <= 0) {
            syncTimer = 4;
            FlashNetwork.sendToServer(new TornadoStatePacket(true, cx, cy, cz, speed, dir));
        }
        return true;
    }

    private static void start(LocalPlayer p, ClientSpeedsters.Entry e) {
        float yaw = p.getYRot() * ((float) Math.PI / 180F);
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // bakis yonu
        double rx = -fz, rz = fx;                        // sag
        cx = p.getX() + rx * RADIUS;
        cy = p.getY();
        cz = p.getZ() + rz * RADIUS;
        angle = (float) Math.atan2(p.getZ() - cz, p.getX() - cx);
        prevAngle = angle;
        // Teget, oyuncunun bakis yonune bakacak sekilde donus yonunu sec
        double tdx = -Mth.sin(angle), tdz = Mth.cos(angle);
        dir = tdx * fx + tdz * fz >= 0 ? 1 : -1;
        speed = Math.max(0F, e.hSpeed);
        active = true;
        syncTimer = 0;
        e.nodes.clear();
    }

    private static void end(LocalPlayer p, ClientSpeedsters.Entry e, boolean carry) {
        active = false;
        if (carry) {
            float tdx = -Mth.sin(angle) * dir, tdz = Mth.cos(angle) * dir;
            float s = Math.min(speed, 1.2F);
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(tdx * s, v.y, tdz * s);
        }
        speed = 0F;
        if (e != null) {
            e.tornado = false;
            e.tSpeed = 0F;
            e.nodes.clear();
        }
        FlashNetwork.sendToServer(new TornadoStatePacket(false, cx, cy, cz, 0F, dir));
    }

    private static void writeEntry(ClientSpeedsters.Entry e) {
        e.tornado = true;
        e.tcx = cx;
        e.tcy = cy;
        e.tcz = cz;
        e.tSpeed = speed;
        e.tAngle = angle;
        e.tPrevAngle = prevAngle;
        e.tDir = dir;
    }

    public static void reset() {
        active = false;
        speed = 0F;
    }

    /** Diger oyuncularin tornadosu (sunucudan). Yerel oyuncu kendi durumunu hesapliyor, onu yok say. */
    public static void handleSync(TornadoSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (mc.player != null && mc.player.getUUID().equals(msg.player))) return;
        ClientSpeedsters.Entry e = ClientSpeedsters.getOrCreate(msg.player);
        if (!msg.active) {
            e.tornado = false;
            e.tSpeed = 0F;
            return;
        }
        if (!e.tornado) {
            Player p = mc.level.getPlayerByUUID(msg.player);
            float a = p != null ? (float) Math.atan2(p.getZ() - msg.cz, p.getX() - msg.cx) : 0F;
            e.tAngle = a;
            e.tPrevAngle = a;
        }
        e.tornado = true;
        e.tcx = msg.cx;
        e.tcy = msg.cy;
        e.tcz = msg.cz;
        e.tSpeed = msg.speed;
        e.tDir = msg.dir;
    }
}
