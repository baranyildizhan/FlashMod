package dev.baranhan.flashmod.ultimate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sunucuda tek bir ultimate kullaniminin durumu. */
public final class UltimateSession {
    public final int id;
    public final UUID casterId;
    public final int targetId;
    public final UUID targetUuid;
    public final ResourceKey<Level> dimension;
    public final ArenaFrame arena;
    public final long startGameTime;
    public final float scale;
    public final long seed;
    public final Vec3 startPos;
    public final boolean debug;
    public final List<UUID> audience = new ArrayList<>();

    public boolean blinked, hit1Done, returned, hit2Done, hit3Done, launched, landed, crashed, ended;
    public int soundMask;
    public Vec3 lockPos;
    public float lockYaw;
    // stasis
    public boolean stasis;
    public Vec3 freezePos;
    /** Ilk vurusta hedefin ileri kayma mesafesi (blok, carpismaya gore). */
    public float push;
    /** Final ucus olcegi (engellere gore) ve final geometrisi (UltimateScript). */
    public float fly = 1F;
    public UltimateScript.Path path;
    public boolean hadNoAi, mob;
    public long debrisAt = -1;
    public Vec3 debrisPos;
    public Vec3 lastTargetPos;

    public UltimateSession(int id, UUID casterId, int targetId, UUID targetUuid, ResourceKey<Level> dimension,
                           ArenaFrame arena, long startGameTime, float scale, long seed, Vec3 startPos, boolean debug) {
        this.id = id;
        this.casterId = casterId;
        this.targetId = targetId;
        this.targetUuid = targetUuid;
        this.dimension = dimension;
        this.arena = arena;
        this.startGameTime = startGameTime;
        this.scale = scale;
        this.seed = seed;
        this.startPos = startPos;
        this.debug = debug;
    }

    public float t(long gameTime) {
        return (gameTime - startGameTime) / scale;
    }
}
