package dev.baranhan.flashmod.ultimate;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * ARENA uzayi (sunucu + istemci): caster -> hedef yonune hizali yerel cerceve.
 * forward = yatay birim, up = (0,1,0), right = forward x up = (-fz, 0, fx).
 * Caster (0,0,0)'da +Z'ye, hedef (0,0,D)'de -Z'ye bakar.
 */
public final class ArenaFrame {
    public final double ox, oy, oz;
    public final double fx, fz;
    public final float d;

    public ArenaFrame(double ox, double oy, double oz, double fx, double fz, float d) {
        double l = Math.sqrt(fx * fx + fz * fz);
        if (l < 1.0E-6) { fx = 0; fz = 1; l = 1; }
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.fx = fx / l;
        this.fz = fz / l;
        this.d = d;
    }

    public double rx() { return -fz; }
    public double rz() { return fx; }

    public Vec3 toWorld(double x, double y, double z) {
        return new Vec3(ox + rx() * x + fx * z, oy + y, oz + rz() * x + fz * z);
    }

    public Vec3 toWorld(Vec3 p) {
        return toWorld(p.x, p.y, p.z);
    }

    public Vec3 toArena(Vec3 w) {
        double dx = w.x - ox, dy = w.y - oy, dz = w.z - oz;
        return new Vec3(dx * rx() + dz * rz(), dy, dx * fx + dz * fz);
    }

    /** Dunya yaw'i (derece), arena'da +Z yonunden saat yonunun tersine 'arenaYaw' kadar donuk bakis icin. */
    public float forwardYaw() {
        return (float) Math.toDegrees(Math.atan2(-fx, fz));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(ox);
        buf.writeDouble(oy);
        buf.writeDouble(oz);
        buf.writeDouble(fx);
        buf.writeDouble(fz);
        buf.writeFloat(d);
    }

    public static ArenaFrame read(FriendlyByteBuf buf) {
        return new ArenaFrame(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat());
    }
}
