package dev.baranhan.flashmod.speed;

/**
 * Ortak bayrak: yerel oyuncu Blitz sinematiginde mi? (istemci yazar, ortak EntityMixin okur -> fare kilidi).
 * Ortak pakette cunku mixin sunucuda da yuklenir; istemci siniflarina dogrudan referans veremez.
 */
public final class BlitzLock {
    public static volatile boolean clientLocked;
    /** Yerel oyuncu ultimate sinematiginde / stasis'te (fare kilidi). */
    public static volatile boolean ultimateLocked;

    private BlitzLock() {}
}
