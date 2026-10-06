package dev.baranhan.flashmod.speed;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Sunucu tarafi kalici veri (player persistent data -> ForgeData, kayitta saklanir). */
public final class SpeedsterData {
    public static final String ROOT = "flashmod";
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;
    public static final int DEFAULT_LEVEL = 5;
    /** CW tarzi: neredeyse beyaz sari cekirdek + turuncu-altin parilti. */
    public static final int DEFAULT_CORE = 0xFFF3C4;
    public static final int DEFAULT_GLOW = 0xFF9A1A;

    /** MULTIPLY_TOTAL bonusu (sprint hizinin ustune). Seviye 10 ~ 11x sprint. */
    private static final double[] SPEED_TABLE = {0.5, 1.0, 1.6, 2.3, 3.1, 4.0, 5.1, 6.4, 8.0, 10.0};

    private SpeedsterData() {}

    private static CompoundTag tag(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(ROOT, Tag.TAG_COMPOUND)) pd.put(ROOT, new CompoundTag());
        return pd.getCompound(ROOT);
    }

    public static boolean isActive(Player p) { return tag(p).getBoolean("active"); }
    public static void setActive(Player p, boolean v) { tag(p).putBoolean("active", v); }

    public static int getLevel(Player p) {
        CompoundTag t = tag(p);
        return t.contains("level", Tag.TAG_INT) ? Mth.clamp(t.getInt("level"), MIN_LEVEL, MAX_LEVEL) : DEFAULT_LEVEL;
    }
    public static void setLevel(Player p, int level) { tag(p).putInt("level", Mth.clamp(level, MIN_LEVEL, MAX_LEVEL)); }

    public static int getCore(Player p) {
        CompoundTag t = tag(p);
        return t.contains("core", Tag.TAG_INT) ? t.getInt("core") & 0xFFFFFF : DEFAULT_CORE;
    }
    public static int getGlow(Player p) {
        CompoundTag t = tag(p);
        return t.contains("glow", Tag.TAG_INT) ? t.getInt("glow") & 0xFFFFFF : DEFAULT_GLOW;
    }
    public static void setColors(Player p, int core, int glow) {
        CompoundTag t = tag(p);
        t.putInt("core", core & 0xFFFFFF);
        t.putInt("glow", glow & 0xFFFFFF);
    }

    /** Bu oyuncu icin sunucuda kayitli renk var mi (yoksa istemcinin varsayilani ilk giriste yazilir). */
    public static boolean hasColors(Player p) {
        CompoundTag t = tag(p);
        return t.contains("core", Tag.TAG_INT) && t.contains("glow", Tag.TAG_INT);
    }

    // ---------------------------------------------------------------- skin (PNG baytlari, oyuncu NBT'sinde)

    public static byte[] getSkin(Player p) {
        CompoundTag t = tag(p);
        return t.contains("skin", Tag.TAG_BYTE_ARRAY) ? t.getByteArray("skin") : new byte[0];
    }

    public static boolean getSkinSlim(Player p) { return tag(p).getBoolean("skinSlim"); }

    public static String getSkinName(Player p) { return tag(p).getString("skinName"); }

    public static void setSkin(Player p, byte[] png, boolean slim, String name) {
        CompoundTag t = tag(p);
        if (png == null || png.length == 0) {
            t.remove("skin");
            t.remove("skinSlim");
            t.remove("skinName");
            return;
        }
        t.putByteArray("skin", png);
        t.putBoolean("skinSlim", slim);
        t.putString("skinName", name == null ? "" : name);
    }

    /** Ultimate bekleme suresinin bittigi oyun zamani (olumde sifirlanmaz; Clone kokteki veriyi kopyalar). */
    public static long getUltCooldown(Player p) { return tag(p).getLong("ultCdUntil"); }

    public static void setUltCooldown(Player p, long until) { tag(p).putLong("ultCdUntil", until); }

    public static double speedBonus(int level) {
        return SPEED_TABLE[Mth.clamp(level, MIN_LEVEL, MAX_LEVEL) - 1];
    }
}
