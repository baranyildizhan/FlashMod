package dev.baranhan.flashmod.client;

import dev.baranhan.flashmod.FlashSounds;
import dev.baranhan.flashmod.network.FlashNetwork;
import dev.baranhan.flashmod.network.WallRunPacket;
import dev.baranhan.flashmod.speed.PhaseHelper;
import dev.baranhan.flashmod.speed.WallState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Duvar = zemin. Hizla bir duvara kosunca dunya duvar ekseni etrafinda 90 derece doner (bkz. WallState):
 *  - Fare yerdeki gibi calisir (yRot/xRot yerel cercevede), kamera 1. ve 3. sahista ayni donusu uygular.
 *  - WASD bakis yonune gore duvar uzerinde yurur/kosar (yerdeki hizla), Space yerel ziplama (duvardan disari),
 *    "yercekimi" duvara dogru. Cikintilara yerel adim yuksekligiyle tirmanir.
 *  - Duvarin ustune varinca kenardan cati/zemine gecer; zemine inince, duvardan dusunce ya da Shift ile biter.
 *  - Phasing, tornado, blitz, ucus, binek ve suyla cakismaz.
 * Hareket istemci-otoriter; durum ve normal sunucuya, oradan izleyenlere gider (kutu/nisan/goz iki tarafta ayni).
 */
public final class WallRun {
    private static final double GRAVITY = 0.08D, JUMP = 0.42D;
    /** Duvar boyunca asagi kayma hizi (blok/tick): dururken / tam hizla kosarken. */
    private static final double SLIP_IDLE = 0.14D, SLIP_MOVING = 0.04D;
    private static boolean active;
    private static float nx, nz;
    private static Vec3 vT = Vec3.ZERO;   // duvara teget hiz (dunya)
    private static double vN;             // normal hiz (+ duvardan disari)
    private static int airTicks, jumpCooldown, startTicks, regrab;
    private static float lastRoll;

    private WallRun() {}

    public static boolean isActive() {
        return active;
    }

    private static boolean allowed(LocalPlayer p, @Nullable ClientSpeedsters.Entry e) {
        return e != null && e.active && !PhaseHelper.clientLocalPhasing && !Tornado.isActive() && !e.blitz
                && !p.getAbilities().flying && !p.isPassenger() && !p.isFallFlying() && !p.isInWater() && !p.isSpectator();
    }

    // ---------------------------------------------------------------- baslama / bitis (PlayerTick END)

    public static void tick(LocalPlayer p, @Nullable ClientSpeedsters.Entry e) {
        if (active) {
            // olum/yeniden dogma/boyut degisimi (yeni oyuncu nesnesi) ya da izin verilmeyen durum -> birak
            if (!p.isAlive() || p.tickCount < 3 || !allowed(p, e) || p.isShiftKeyDown()) stop(p, e, false);
            return;
        }
        if (regrab > 0) regrab--;
        if (!allowed(p, e) || p.isShiftKeyDown() || regrab > 0) return;
        boolean air = !p.onGround();
        // Yerde: W'ya basarak hizla duvara kos. Havada (binadan binaya): duvara dogru gidiyorsan W sart degil,
        // hiz esigi daha dusuk ve duvara degmesen de 0.35 blok yakinindaysan yakalar.
        boolean pushing = p.input.forwardImpulse > 0.1F;
        if (!air && (!pushing || e.hSpeed < 0.45F || !p.horizontalCollision)) return;
        if (air && e.hSpeed < 0.3F && !pushing) return;

        // Gidis yonu: bakis + gercek hiz (havada momentum onemli). Duvar yonu bu yone en yakin ve temas eden
        // ana yon; artik sadece bakisin ana yonune bakmiyoruz (capraz gelince yakalayamiyordu).
        float yaw = p.getYRot() * ((float) Math.PI / 180F);
        double gx = -Mth.sin(yaw), gz = Mth.cos(yaw);
        double mx = p.getX() - p.xo, mz = p.getZ() - p.zo, ml = Math.sqrt(mx * mx + mz * mz);
        if (ml > 0.05D) {
            double w = air ? 0.7D : 0.35D;
            gx = gx * (1 - w) + mx / ml * w;
            gz = gz * (1 - w) + mz / ml * w;
        }
        AABB box = p.getBoundingBox();
        double reach = air ? 0.35D : 0.3D;
        double best = 0.25D;
        int bx = 0, bz = 0;
        for (int i = 0; i < 4; i++) {
            int cx = i == 0 ? 1 : i == 1 ? -1 : 0, cz = i == 2 ? 1 : i == 3 ? -1 : 0;
            double score = cx * gx + cz * gz;
            if (score <= best) continue;
            boolean feet = !p.level().noCollision(p, box.move(cx * reach, 0.0D, cz * reach));
            boolean mid = !p.level().noCollision(p, box.move(cx * reach, 0.9D, cz * reach));
            // yerde en az ~3 blok yuksek duvar; havada govde hizasinda temas yeter (ust kenara yakin gelirsen
            // baslar ve hemen catiya tirmanir)
            boolean ok = air ? (feet || mid) : (feet && !p.level().noCollision(p, box.move(cx * reach, 1.2D, cz * reach)));
            if (!ok) continue;
            best = score;
            bx = cx;
            bz = cz;
        }
        if (bx == 0 && bz == 0) return;
        if (air && !p.horizontalCollision) { // temas yok ama yakin: duvara yanas
            p.move(MoverType.SELF, new Vec3(bx * reach, 0.0D, bz * reach));
        }
        start(p, e, -bx, -bz);
    }

    private static void start(LocalPlayer p, ClientSpeedsters.Entry e, float nX, float nZ) {
        nx = nX;
        nz = nZ;
        // kutu duvar cercevesine gecmeden once yer var mi (pivot hizasinda, duvardan disari 1.8)
        AABB wb = WallState.box(p.position(), nx, nz, p.getBbWidth(), p.getBbHeight());
        if (!p.level().noCollision(p, wb.deflate(1.0E-3D))) return;
        active = true;
        startTicks = 0;
        airTicks = 0;
        jumpCooldown = 6;
        // Kosuya duvarda yukari devam; duvar boyunca yana giden kisim (capraz gelis, havadan konma) korunur.
        Vec3 dm = p.getDeltaMovement();
        double side = dm.x * -nz + dm.z * nx; // duvar boyunca yatay eksen (-nz, nx)
        vT = new Vec3(-nz * side, Math.max(0.6D, e.hSpeed * 0.95D), nx * side);
        vN = -GRAVITY;
        e.wallRun = true;
        e.wallNx = nx;
        e.wallNz = nz;
        WallState.setClient(p.getUUID(), true, nx, nz);
        p.refreshDimensions();
        FlashNetwork.sendToServer(new WallRunPacket(true, nx, nz));
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), FlashSounds.WALLRUN_START.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
    }

    /** hop=true: duvarin ustune varildi, kenardan ice dogru gec. */
    private static void stop(LocalPlayer p, @Nullable ClientSpeedsters.Entry e, boolean hop) {
        if (!active) return;
        active = false;
        regrab = 8; // ayrilinca ayni duvara hemen geri yapismasin
        if (e != null) e.wallRun = false;
        Vec3 world = vT.add(nx * vN, 0.0D, nz * vN);
        WallState.setClient(p.getUUID(), false, nx, nz);
        p.refreshDimensions();
        fitStanding(p);
        if (hop) {
            Vec3 along = vT.subtract(0.0D, vT.y, 0.0D); // duvar boyunca yatay kisim korunur
            p.setDeltaMovement(along.x - nx * 0.55D, Math.max(0.62D, Math.min(vT.y * 0.35D, 1.2D)), along.z - nz * 0.55D);
        } else {
            p.setDeltaMovement(world);
        }
        FlashNetwork.sendToServer(new WallRunPacket(false, nx, nz));
    }

    /**
     * Ayakta kutusu, duvar kutusunun durdugu yerde zemine/cikintiya gomulebilir (orn. duvardan asagi inip zemine
     * basinca kutu 0.6 blok asagida kalir). En yakin bos konuma kaydir: once yukari, sonra duvardan disari.
     */
    private static void fitStanding(LocalPlayer p) {
        if (p.level().noCollision(p, p.getBoundingBox())) return;
        Vec3 o = p.position();
        for (int i = 1; i <= 26; i++) {
            double up = i * 0.05D;
            if (p.level().noCollision(p, p.getBoundingBox().move(0.0D, up, 0.0D))) {
                p.setPos(o.x, o.y + up, o.z);
                return;
            }
        }
        for (int i = 1; i <= 16; i++) {
            double out = i * 0.1D;
            for (int j = 0; j <= 12; j++) {
                double up = j * 0.1D;
                if (p.level().noCollision(p, p.getBoundingBox().move(nx * out, up, nz * out))) {
                    p.setPos(o.x + nx * out, o.y + up, o.z + nz * out);
                    return;
                }
            }
        }
    }

    public static void reset() {
        active = false;
        lastRoll = 0F;
        vT = Vec3.ZERO;
        vN = 0;
    }

    // ---------------------------------------------------------------- hareket (Player.travel yerine)

    public static void travel(LocalPlayer p) {
        ClientSpeedsters.Entry e = ClientSpeedsters.get(p.getUUID());
        startTicks++;
        if (jumpCooldown > 0) jumpCooldown--;
        Vec3 n = new Vec3(nx, 0.0D, nz);
        AABB box = p.getBoundingBox();
        boolean onSurface = !p.level().noCollision(p, box.move(-nx * 0.06D, 0.0D, -nz * 0.06D));

        // yerel yon vektorleri (yerdeki gibi: yalniz yaw), dunyaya donmus
        float yaw = p.getYRot() * ((float) Math.PI / 180F);
        Vec3 fwd = WallState.toWorld(new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw)), nx, nz, 1.0D);
        Vec3 left = WallState.toWorld(new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw)), nx, nz, 1.0D);
        double f = p.input.forwardImpulse, s = p.input.leftImpulse;
        double len = Math.sqrt(f * f + s * s);
        if (len > 1.0D) { f /= len; s /= len; }
        if (f > 0.1D && !p.isSprinting() && (Minecraft.getInstance().options.keySprint.isDown() || e != null && e.active)) {
            p.setSprinting(true);
        }
        // yerdeki terminal hiz ~= hareket hizi niteligi * 2.92 (surtunme 0.546, ivme 0.216/f^3)
        double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.92D;
        Vec3 target = fwd.scale(f * speed).add(left.scale(s * speed));
        // Orumcek adam gibi yapisip kalmasin: duvar boyunca hafif asagi kayma. Dururken belirgin, kosarken az.
        double moving = Mth.clamp(target.length() / 0.6D, 0.0D, 1.0D);
        target = target.add(0.0D, -(SLIP_IDLE + (SLIP_MOVING - SLIP_IDLE) * moving), 0.0D);
        double accel = onSurface ? 0.5D : 0.06D;
        vT = vT.add(target.subtract(vT).scale(accel));

        if (onSurface && p.input.jumping && jumpCooldown == 0) {
            vN = JUMP;
            jumpCooldown = 10;
        } else if (onSurface && vN <= 0.0D) {
            vN = -GRAVITY;
        } else {
            vN = (vN - GRAVITY) * 0.98D;
        }

        Vec3 move = vT.add(n.scale(vN));
        double before = p.position().dot(n);
        moveWithStep(p, move, onSurface);
        double movedN = p.position().dot(n) - before;
        if (vN < 0.0D && movedN > vN + 1.0E-4D) vN = Math.max(vN, -GRAVITY); // duvara degdi
        // teget carpismada hizi kes (duvar/kose)
        if (p.horizontalCollision || p.verticalCollision) {
            Vec3 real = p.position().subtract(p.xo, p.yo, p.zo);
            Vec3 realT = real.subtract(n.scale(real.dot(n)));
            if (realT.lengthSqr() < vT.lengthSqr() * 0.25D) vT = realT;
        }
        p.resetFallDistance();
        p.calculateEntityAnimation(true);

        onSurface = !p.level().noCollision(p, p.getBoundingBox().move(-nx * 0.06D, 0.0D, -nz * 0.06D));
        airTicks = onSurface ? 0 : airTicks + 1;

        // --- bitis kosullari
        // Zemine degdin (duvar boyunca indin/kaydin): kutunun hemen alti dolu ve yukari gitmiyorsun.
        // (onGround'a guvenilmez: adim denemesindeki son hareket yatay oldugu icin bayragi siliyordu.)
        boolean floor = !p.level().noCollision(p, p.getBoundingBox().move(0.0D, -0.08D, 0.0D));
        if (startTicks > 3 && (floor || p.onGround()) && vT.y <= 0.08D) {
            stop(p, e, false);
            return;
        }
        if (!onSurface && vN <= 0.0D && vT.y > 0.12D && startTicks > 2
                && p.level().noCollision(p, p.getBoundingBox().expandTowards(-nx * 2.0D, 0.0D, -nz * 2.0D))) { // duvarin ustune vardin
            stop(p, e, true);
            return;
        }
        if (airTicks > 6 && vN <= 0.0D) {               // kenardan dustun / duvar bitti
            AABB below = p.getBoundingBox().expandTowards(-nx * 3.0D, 0.0D, -nz * 3.0D);
            if (p.level().noCollision(p, below)) stop(p, e, false);
        }
    }

    private static double tangentLen(Vec3 v) {
        double d = v.x * nx + v.z * nz;
        double tx = v.x - nx * d, tz = v.z - nz * d;
        return Math.sqrt(tx * tx + v.y * v.y + tz * tz);
    }

    /** Duvar cercevesinde adim yuksekligi: teget hareket takilirsa N yonunde yuksel, ilerle, geri in. */
    private static void moveWithStep(LocalPlayer p, Vec3 mv, boolean onSurface) {
        Vec3 start = p.position();
        p.move(MoverType.SELF, mv);
        if (!onSurface) return;
        double want = tangentLen(mv);
        Vec3 got = p.position().subtract(start);
        double gotT = tangentLen(got);
        if (want < 1.0E-4D || gotT >= want - 1.0E-3D) return;

        Vec3 normalTry = p.position();
        boolean hc = p.horizontalCollision, vc = p.verticalCollision;
        double step = Math.max(0.6D, p.getStepHeight());
        double d = mv.x * nx + mv.z * nz;
        Vec3 mvT = new Vec3(mv.x - nx * d, mv.y, mv.z - nz * d);
        p.setPos(start.x, start.y, start.z);
        Vec3 s0 = p.position();
        p.move(MoverType.SELF, new Vec3(nx * step, 0.0D, nz * step));
        double up = (p.position().x - s0.x) * nx + (p.position().z - s0.z) * nz;
        p.move(MoverType.SELF, mvT);
        p.move(MoverType.SELF, new Vec3(-nx * (up + 0.02D), 0.0D, -nz * (up + 0.02D)));
        double gotT2 = tangentLen(p.position().subtract(start));
        if (gotT2 <= gotT + 1.0E-3D) { // adim ise yaramadi
            p.setPos(normalTry.x, normalTry.y, normalTry.z);
            p.horizontalCollision = hc;
            p.verticalCollision = vc;
        }
    }

    // ---------------------------------------------------------------- kamera (1. ve 3. sahis)

    /** Kameraya eklenecek roll (ComputeCameraAngles'ta uygulanir). */
    public static float roll() {
        return lastRoll;
    }

    /**
     * Oyuncunun yerel bakisi (ve 3. sahista on/arka ayna acisi) duvar ekseni etrafinda 90*blend derece dondurulur,
     * yaw/pitch/roll'a cevrilir. Goz noktasi donmus kafaya kayar; 3. sahista kamera donmus bakisin arkasina
     * (duvar/blok carpismasiyla kisaltilarak) konur. Hepsi ayni yumusak karisimla -> ani donus yok.
     */
    @Nullable
    public static CameraModes.Result camera(Entity entity, float pt, Vec3 vanillaPos, float yawIgnored, float pitchIgnored) {
        lastRoll = 0F;
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return null;
        ClientSpeedsters.Entry e = ClientSpeedsters.get(mc.player.getUUID());
        if (e == null) return null;
        float b = e.wallBlend(pt);
        if (b <= 0.001F) return null;
        float wx = e.wallNx, wz = e.wallNz;
        CameraType type = mc.options.getCameraType();
        float yaw = entity.getViewYRot(pt), pitch = entity.getViewXRot(pt);
        if (type.isMirrored()) {
            yaw += 180F;
            pitch = -pitch;
        }
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90F, yaw);
        Vec3 l2 = WallState.toWorld(look, wx, wz, b), u2 = WallState.toWorld(up, wx, wz, b);

        float nyaw = (float) Math.toDegrees(Math.atan2(-l2.x, l2.z));
        float npitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(l2.y, -1.0D, 1.0D)));
        Vec3 u0 = Vec3.directionFromRotation(npitch - 90F, nyaw);
        Vec3 r0 = l2.cross(u0);
        lastRoll = (float) Math.toDegrees(Math.atan2(u2.dot(r0), u2.dot(u0)));

        Vec3 pos = entity.getPosition(pt);
        double eyeH = entity.getEyeHeight();
        Vec3 vanillaEye = new Vec3(pos.x, pos.y + eyeH, pos.z);
        Vec3 eye = vanillaEye.lerp(WallState.eye(pos, wx, wz, eyeH), b);
        if (type.isFirstPerson()) {
            return new CameraModes.Result(eye.x, eye.y, eye.z, nyaw, npitch, false);
        }
        double dist = 4.0D;
        Vec3 want = eye.subtract(l2.scale(dist));
        HitResult hit = mc.level.clip(new ClipContext(eye, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) dist = Math.max(0.3D, hit.getLocation().distanceTo(eye) - 0.2D);
        Vec3 cam = eye.subtract(l2.scale(dist));
        return new CameraModes.Result(cam.x, cam.y, cam.z, nyaw, npitch, true);
    }
}