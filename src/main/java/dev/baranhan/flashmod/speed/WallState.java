package dev.baranhan.flashmod.speed;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duvar = zemin cercevesi (iki tarafta ortak). Duvarda kosan oyuncu icin dunya, duvar ekseni etrafinda
 * 90 derece donmus kabul edilir: yerel "yukari" = duvar normali N (duvardan disari), yerel yatay duzlem = duvar.
 *
 *  - R: yerelden dunyaya donus (dunya yukarisi -> N, -N -> dunya yukarisi), eksen = cross(yukari, N) = (nz, 0, -nx).
 *  - Oyuncunun yRot/xRot'u yerel cercevede yorumlanir (fare yerdeki gibi calisir); bakis vektoru R ile doner.
 *  - Carpisma kutusu: 0.6 x 0.6 kesitli, N boyunca 1.8 uzun (ayaklar duvarda, govde disari) -> hala AABB.
 *  - Pivot (ayak noktasi): pozisyon - N*0.3 + (0, 0.9, 0). Goz: pivot + N*gozYuksekligi.
 */
public final class WallState {
    public static final double HALF_W = 0.3D, PIVOT_Y = 0.9D;
    private static final Map<UUID, float[]> CLIENT = new ConcurrentHashMap<>();

    private WallState() {}

    // ---------------------------------------------------------------- durum

    public static void setClient(UUID id, boolean on, float nx, float nz) {
        if (on) CLIENT.put(id, new float[]{nx, nz});
        else CLIENT.remove(id);
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    /** {nx, nz} veya null (duvarda degil). */
    @Nullable
    public static float[] normal(Player p) {
        if (p.level().isClientSide) return CLIENT.get(p.getUUID());
        return AbilityLogic.wallNormal(p);
    }

    public static boolean is(Player p) {
        return normal(p) != null;
    }

    // ---------------------------------------------------------------- geometri

    /** Rodrigues: v'yi yatay eksen (ax, 0, az) etrafinda ang radyan dondur. */
    public static Vec3 rotate(Vec3 v, double ax, double az, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        double dot = v.x * ax + v.z * az;
        double cx = -az * v.y, cy = az * v.x - ax * v.z, cz = ax * v.y;
        return new Vec3(v.x * c + cx * s + ax * dot * (1 - c),
                v.y * c + cy * s,
                v.z * c + cz * s + az * dot * (1 - c));
    }

    /** Yerel -> dunya, karisim b (0..1) ile: 90*b derece. */
    public static Vec3 toWorld(Vec3 v, float nx, float nz, double b) {
        return rotate(v, nz, -nx, Math.PI * 0.5D * b);
    }

    /** Dunya -> yerel (tam donus). */
    public static Vec3 toLocal(Vec3 v, float nx, float nz) {
        return rotate(v, nz, -nx, -Math.PI * 0.5D);
    }

    public static Vec3 pivot(Vec3 pos, float nx, float nz) {
        return new Vec3(pos.x - nx * HALF_W, pos.y + PIVOT_Y, pos.z - nz * HALF_W);
    }

    public static Vec3 eye(Vec3 pos, float nx, float nz, double eyeHeight) {
        Vec3 pv = pivot(pos, nx, nz);
        return new Vec3(pv.x + nx * eyeHeight, pv.y, pv.z + nz * eyeHeight);
    }

    /** Duvardaki carpisma kutusu: duvara bakan yuzu ayakta kutusuyla ayni, N boyunca boy kadar uzanir. */
    public static AABB box(Vec3 pos, float nx, float nz, double width, double height) {
        double w = width * 0.5D, cy = pos.y + PIVOT_Y;
        if (Math.abs(nx) > Math.abs(nz)) {
            double a = pos.x - nx * w, b = pos.x + nx * (height - w);
            return new AABB(Math.min(a, b), cy - w, pos.z - w, Math.max(a, b), cy + w, pos.z + w);
        }
        double a = pos.z - nz * w, b = pos.z + nz * (height - w);
        return new AABB(pos.x - w, cy - w, Math.min(a, b), pos.x + w, cy + w, Math.max(a, b));
    }
}
