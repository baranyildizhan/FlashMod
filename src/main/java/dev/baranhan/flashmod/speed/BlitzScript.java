package dev.baranhan.flashmod.speed;

/**
 * "Blitz" sinematiginin tam koreografisi (3 grup / 60 referans kare). Sunucu ve istemci ayni tabloyu okur;
 * her sey deterministik oldugu icin herkes kare kare ayni sahneyi gorur.
 *
 * Sahne koordinatlari (blok): orijin = baslangicta hedefin ayaklari.
 *   x = ana kameraya gore SAG(+)/SOL(-),  y = yukari,  z = ileri (kameradan uzaga, +).
 * Hedef de hizci da bu uzayda senaryolanir; hedefin sunucudaki varligi yerinde donar, istemciler onu
 * senaryodaki yerinde cizer -> darbe ve tepki ayni karede olur (ag gecikmesi yok).
 *
 * Zaman: tick (20 = 1 sn), kesirli. Toplam 158 tick (~7.9 sn; sonda rakip yerde bekler).
 */
public final class BlitzScript {
    // ---------------------------------------------------------------- hizci rotasi
    /** Konum modu: ABS sahneye gore, REL hedefin O ANKI konumuna gore, FIN hedefin son darbedeki konumuna gore,
     *  PLR hizcinin baslangic konumuna gore. (REL/FIN yatayda hedefi izler, y her zaman zemine gore.) */
    public static final int ABS = 0, REL = 1, FIN = 2, PLR = 3;
    /** Anahtar tipi: STOP = orada durur/yavaslar (teget 0), FLOW = icinden akarak gecer, IMP = darbe (hizli cikis). */
    public static final int STOP = 0, FLOW = 1, IMP = 2;

    private static final float O = 18F;   // ekran disi
    private static final float OW = 24F;  // genis cekimde ekran disi

    /**
     * {t, x, y, z, mod, tip}
     * Giris/cikislar tek bir dogrultuda degil: ekran disi noktalar on/arka derinliklerde degisir, temas oncesi ve
     * sonrasi FLOW ara noktalar yolu kavisler. Bazi cikis/girisler capraz: kameranin yanindan ekranin sol-alt /
     * sag-alt kosesinden (kameranin arkasindaki z ~ -10.5 noktalari). Kameradan UZAKLASARAK cikis yok
     * (ekrandan cikamaz), o yon sadece giris icin kullanilir. Ustune her blitz'de ekran disi
     * noktalara tohumlu rastgele derinlik eklenir (BlitzPath) -> her seferinde biraz farkli rota.
     */
    public static final float[][] RUN = {
            // ===== 1. grup: yaklasma, yanindan gecis, fren (1-1 .. 1-8) =====
            {0.0F, 0F, 0F, 0F, PLR, STOP},
            {1.5F, 0F, 0F, 0F, PLR, STOP},              // cikis coklugu
            {4.2F, -2.2F, 0F, -3.0F, REL, FLOW},        // kavis
            {6.5F, -1.15F, 0F, -0.2F, REL, FLOW},       // rakibin yanindan cizgi duvari birakarak gec
            {9.0F, -1.4F, 0F, 7.6F, ABS, FLOW},         // ileride frene gir
            {12.5F, -1.4F, 0F, 8.4F, ABS, STOP},        // kayarak dur (eli yerde)
            {15.0F, -1.4F, 0F, 8.4F, ABS, STOP},
            // ===== 1-9 .. 1-20 =====
            {16.6F, -1.6F, 0F, -0.2F, REL, FLOW},       // sol yanindan dolas...
            {17.5F, 0.25F, 0F, -0.85F, REL, FLOW},      // ...onune gecerken carp
            {18.6F, -4.0F, 0F, -3.2F, REL, FLOW},       // kameraya dogru kavis
            {19.8F, -15F, 0F, -6F, ABS, STOP},          // sol onden cik
            {21.0F, -16F, 0F, 2.5F, ABS, STOP},         // (ekran disinda arkaya gec)
            {22.0F, -3.0F, 0F, 1.4F, REL, FLOW},        // sol arkadan kavisle gel
            {22.5F, -0.85F, 0F, 0F, REL, STOP},         // yumruk
            {23.6F, -0.85F, 0F, 0F, REL, STOP},
            {24.4F, -1.8F, 0F, -2.2F, REL, FLOW},       // capraz: ekranin sol altindan (kameranin yanindan) cik
            {25.6F, -5.5F, 0F, -10.5F, ABS, STOP},
            {26.6F, -O, 0F, -1.0F, ABS, STOP},
            {27.4F, -2.8F, 0F, -1.2F, REL, FLOW},
            {28.0F, -0.85F, 0F, 0.2F, REL, STOP},       // seri yumruklar (ardil goruntulu)
            {29.0F, -1.35F, 0F, -0.1F, REL, STOP},
            {30.0F, -0.85F, 0F, 0.05F, REL, STOP},
            {31.0F, -1.3F, 0F, 0.3F, REL, STOP},
            {32.0F, -0.85F, 0F, 0.1F, REL, STOP},
            {33.6F, 0.0F, 0F, 1.5F, REL, FLOW},         // arkasindan dolas
            {35.2F, O, 0F, 2.5F, ABS, STOP},            // sag arkadan cik
            {36.2F, O, 0F, -2.0F, ABS, STOP},
            {37.4F, 2.8F, 0F, -1.3F, REL, FLOW},        // sag onden kavisle gel
            {38.0F, 0.85F, 0F, 0F, REL, STOP},          // yumruk
            // ===== 2. grup =====
            {40.5F, 0.85F, 0F, 0F, REL, STOP},          // 2-1/2-2 sag kol
            {41.4F, 0.1F, 0F, -0.95F, REL, FLOW},       // onunden gecip
            {42.6F, -3.5F, 0F, -2.8F, REL, FLOW},       // kameraya dogru
            {44.0F, -O, 0F, -4.5F, ABS, STOP},          // sola kosup cik
            {46.0F, -5.5F, 0F, -10.5F, ABS, STOP},
            {47.3F, -1.8F, 0F, -2.4F, REL, FLOW},       // capraz: sol alttan (kameranin yanindan) dal
            {48.0F, -0.85F, 0F, 0F, REL, STOP},         // yumruk -> rakip saga
            {49.0F, -0.85F, 0F, 0F, REL, STOP},
            {50.0F, 1.2F, 0F, -2.4F, REL, FLOW},        // onunden capraz: ekranin sag altindan cik
            {51.5F, 6.5F, 0F, -10.5F, ABS, STOP},
            {53.0F, -15F, 0F, -5.0F, ABS, STOP},
            {54.3F, -3.0F, 0F, -1.8F, REL, FLOW},       // sol onden
            {55.0F, -0.8F, 0F, -0.15F, REL, STOP},      // yumruk
            {56.0F, -0.8F, 0F, -0.15F, REL, STOP},
            {57.2F, -1.6F, 0F, -2.4F, REL, FLOW},
            {59.0F, -6.0F, 0F, -10.5F, ABS, STOP},      // sol on capraz: sol alttan cik, rakip coker
            {61.0F, -O, 0F, 1.5F, ABS, STOP},
            {62.7F, -2.8F, 0F, 0.9F, REL, FLOW},
            {63.5F, -0.75F, 0F, 0F, REL, STOP},         // asagidan yukari vur, kaldir
            {64.6F, -0.75F, 0F, 0F, REL, STOP},
            // ===== 3. grup =====
            {65.8F, -3.6F, 0F, -1.5F, REL, FLOW},
            {67.0F, -O, 0F, -2.5F, ABS, STOP},          // 3-1
            {68.0F, -O, 0F, 1.0F, ABS, STOP},
            {70.0F, 0.0F, 0F, -0.75F, REL, FLOW},       // 3-2 soldan saga gecerken carp
            {72.0F, O, 0F, -0.5F, ABS, FLOW},           // 3-3 sagdan cik (iz havada kalir)
            {72.7F, 12F, 0F, -14F, ABS, FLOW},          //     kameranin arkasindan dolas
            {73.4F, -12F, 0F, -14F, ABS, FLOW},
            {74.2F, -O, 1.2F, 1.4F, ABS, FLOW},         //     soldan, havadan gir
            {75.3F, -0.35F, 1.15F, 0.8F, REL, STOP},    //     rakibin arka ustunde
            {76.4F, -0.15F, 0.85F, 0.55F, REL, STOP},   // 3-4/3-5 kafaya tekme
            {77.4F, 1.0F, 0.4F, 0.7F, REL, FLOW},       //     omzunun ustunden asip
            {78.6F, 2.2F, 0F, -2.6F, REL, FLOW},
            {80.0F, 6.5F, 0F, -10.5F, ABS, STOP},       // 3-6/3-7 sag on capraz: sag alttan cik
            {82.0F, O, 0F, 2.5F, ABS, STOP},
            {83.8F, 3.0F, 0F, 1.2F, REL, FLOW},         // sag arkadan
            {84.5F, 0.85F, 0F, 0F, REL, STOP},          // 3-8 yumruk
            {85.5F, 0.85F, 0F, 0F, REL, STOP},
            {86.7F, 0.0F, 0F, 1.5F, REL, FLOW},
            {88.6F, -O, 0F, 2.5F, ABS, STOP},           // 3-9 sola cik
            {90.0F, -15F, 0F, -4.5F, ABS, STOP},
            {91.8F, -2.9F, 0F, -1.4F, REL, FLOW},
            {92.5F, -0.85F, 0F, 0F, REL, STOP},         // 3-10 yumruk
            {93.5F, -0.85F, 0F, 0F, REL, STOP},
            {94.6F, -3.6F, 0F, 1.2F, REL, FLOW},
            {96.0F, -O, 0F, 2.0F, ABS, STOP},           //      geri cik (kamera degisir)
            {97.0F, -OW, 0F, -3.0F, ABS, STOP},
            {99.0F, -OW, 0F, -3.0F, ABS, STOP},
            {100.6F, -3.8F, 0F, -1.8F, REL, FLOW},      // FINAL: soldan, kameraya yakin derinlikten tam hizla
            {101.5F, -0.95F, 0F, -0.45F, REL, STOP},      //        rakibin sol-onunde (kameraya yakin, sirti bize) carp
            {104.0F, -0.9F, 0F, -0.42F, FIN, STOP},     //        darbe ani: yumruk uzanmis, zaman donar
            {105.5F, -1.15F, 0F, -0.7F, FIN, FLOW},     //        geri tepmeyle kisa kayma (orta noktanin solunda kalir)
            {108.5F, -1.3F, 0F, -0.85F, FIN, STOP},
            {158.0F, -1.3F, 0F, -0.85F, FIN, STOP},
    };

    // ---------------------------------------------------------------- hedefin tepkileri
    /** {t, x, y, z, yaw(0 = kameraya doner, + saga), egilme(+ one), yatma(+ saga), tip} */
    public static final float[][] TARGET = {
            {0F, 0F, 0F, 0F, 20F, 0F, 0F, STOP},
            {6.5F, 0F, 0F, 0F, 20F, 0F, 0F, IMP},              // yanindan gecis ruzgari
            {9.5F, 0.05F, 0F, 0.4F, 38F, 12F, -7F, FLOW},
            {14F, 0.1F, 0F, 0.55F, 42F, 5F, -2F, STOP},
            {17.5F, 0.1F, 0F, 0.55F, 42F, 4F, 0F, IMP},        // carpma -> saga
            {21.2F, 0.55F, 0F, 0.5F, 56F, 11F, 14F, STOP},
            {22.5F, 0.55F, 0F, 0.5F, 54F, 8F, 10F, IMP},
            {26.5F, 1.0F, 0F, 0.45F, 64F, 13F, 16F, STOP},
            {28F, 1.0F, 0F, 0.45F, 62F, 9F, 11F, IMP},
            {30F, 1.2F, 0F, 0.45F, 68F, 12F, 15F, IMP},
            {32F, 1.4F, 0F, 0.45F, 74F, 14F, 18F, IMP},
            {36.5F, 1.6F, 0F, 0.45F, 74F, 9F, 9F, STOP},
            {38F, 1.6F, 0F, 0.45F, 72F, 7F, 5F, IMP},          // sagdan -> sola
            {40.5F, 1.35F, 0F, 0.45F, 50F, 9F, -12F, IMP},     // 2-1
            {44.5F, 0.95F, 0F, 0.45F, 32F, 14F, -16F, STOP},
            {48F, 0.95F, 0F, 0.45F, 30F, 9F, -10F, IMP},       // soldan -> saga
            {52F, 1.4F, 0F, 0.45F, 54F, 14F, 16F, STOP},
            {55F, 1.45F, 0F, 0.45F, 56F, 10F, 11F, IMP},
            {59.5F, 1.85F, -0.42F, 0.35F, 70F, 30F, 16F, FLOW}, // dizlerinin ustune coker
            {62F, 1.95F, -0.55F, 0.3F, 72F, 38F, 8F, STOP},
            {63.5F, 1.95F, -0.55F, 0.3F, 72F, 36F, 6F, IMP},   // asagidan vurus
            {65.8F, 2.25F, 0.3F, 0.3F, 80F, -16F, 12F, FLOW},  // havalanir
            {68.6F, 2.4F, 0F, 0.3F, 85F, 12F, 8F, STOP},       // kambur iner
            {70F, 2.4F, 0F, 0.3F, 85F, 9F, 5F, IMP},           // 3-2 carpma
            {73F, 2.65F, 0F, 0.25F, 95F, 13F, 10F, STOP},
            {75.6F, 2.7F, 0F, 0.2F, 95F, 10F, 6F, IMP},        // 3-4 kafaya tekme -> one savrulur
            {79.5F, 2.65F, -0.12F, -0.45F, 90F, 38F, 4F, FLOW},
            {83F, 2.6F, 0F, -0.55F, 86F, 20F, 0F, STOP},
            {84.5F, 2.6F, 0F, -0.55F, 86F, 13F, 0F, IMP},      // sagdan -> sola
            {89F, 2.2F, 0F, -0.55F, 62F, 16F, -15F, STOP},
            {92.5F, 2.15F, 0F, -0.55F, 60F, 10F, -8F, IMP},    // soldan -> saga
            {97F, 2.55F, 0F, -0.55F, 5F, 12F, 6F, STOP},       // final kamerasina (bize) doner
            {101.5F, 2.6F, 0F, -0.55F, -5F, 8F, 2F, IMP},      // FINAL temas
            {104.0F, 2.62F, 0F, -0.35F, -3F, 24F, 4F, IMP},    // darbe ani: buklulur, zaman donar... sonra geriye firlar
            {107.0F, 4.4F, 1.9F, 3.6F, 10F, -40F, 10F, FLOW},  //   kameradan uzaga, hafif saga
            {110.0F, 6.6F, 0.7F, 7.4F, 25F, -70F, 8F, FLOW},
            {112.0F, 7.4F, 0F, 9.0F, 30F, -84F, 6F, FLOW},     // yere carpar
            {116.0F, 7.8F, 0F, 9.8F, 32F, -87F, 4F, STOP},     // kayip sirt ustu yatar
            {150.0F, 7.8F, 0F, 9.8F, 32F, -88F, 4F, STOP},     // yerde kalir
            {154.5F, 7.82F, 0F, 9.85F, 30F, -25F, 2F, FLOW},   // sendeleyerek kalkar
            {158.0F, 7.82F, 0F, 9.85F, 28F, 12F, 0F, STOP},    // kambur ayakta (biterken kopma olmasin)
    };

    // ---------------------------------------------------------------- hizcinin animasyonlari
    public static final int A_LAUNCH = 0, A_RUN = 1, A_PASS = 2, A_BRAKE = 3, A_READY = 4, A_PUNCH_R = 5,
            A_PUNCH_L = 6, A_UPPER = 7, A_KICK = 8, A_STAND = 9, A_FINAL = 10;
    /** {baslangic, bitis, tur, vurus ani} */
    public static final float[][] ACTS = {
            {0F, 1.5F, A_LAUNCH, 0F},
            {1.5F, 5.6F, A_RUN, 0F},
            {5.6F, 7.4F, A_PASS, 6.5F},
            {7.4F, 8.6F, A_RUN, 0F},
            {8.6F, 13.0F, A_BRAKE, 8.6F},
            {13.0F, 15.0F, A_READY, 0F},
            {15.0F, 16.8F, A_RUN, 0F},
            {16.8F, 18.3F, A_PASS, 17.5F},
            {18.3F, 21.5F, A_RUN, 0F},
            {21.5F, 23.7F, A_PUNCH_R, 22.5F},
            {23.7F, 27.0F, A_RUN, 0F},
            {27.0F, 28.9F, A_PUNCH_R, 28F},
            {28.9F, 30.9F, A_PUNCH_L, 30F},
            {30.9F, 33.0F, A_PUNCH_R, 32F},
            {33.0F, 37.0F, A_RUN, 0F},
            {37.0F, 39.4F, A_PUNCH_R, 38F},
            {39.4F, 41.2F, A_PUNCH_R, 40.5F},
            {41.2F, 47.0F, A_RUN, 0F},
            {47.0F, 49.1F, A_PUNCH_R, 48F},
            {49.1F, 54.0F, A_RUN, 0F},
            {54.0F, 56.2F, A_PUNCH_R, 55F},
            {56.2F, 62.5F, A_RUN, 0F},
            {62.5F, 65.0F, A_UPPER, 63.5F},
            {65.0F, 69.3F, A_RUN, 0F},
            {69.3F, 70.8F, A_PASS, 70F},
            {70.8F, 74.4F, A_RUN, 0F},
            {74.4F, 77.3F, A_KICK, 75.6F},
            {77.3F, 83.6F, A_RUN, 0F},
            {83.6F, 85.7F, A_PUNCH_R, 84.5F},
            {85.7F, 91.6F, A_RUN, 0F},
            {91.6F, 93.7F, A_PUNCH_R, 92.5F},
            {93.7F, 100.6F, A_RUN, 0F},
            {100.6F, 104.4F, A_FINAL, 101.5F},
            {104.4F, 108.8F, A_BRAKE, 104.4F},
            {108.8F, 158F, A_STAND, 0F},
    };

    // ---------------------------------------------------------------- darbeler
    public static final int PUNCH = 0, KICK = 1, PASS = 2, LIFT = 3, FINISH = 4;
    /** {t, tur, hasar} */
    public static final float[][] HITS = {
            {6.5F, PASS, 2F}, {17.5F, PASS, 3F}, {22.5F, PUNCH, 3F}, {28F, PUNCH, 2.5F},
            {30F, PUNCH, 2F}, {32F, PUNCH, 2F}, {38F, PUNCH, 3F}, {40.5F, PUNCH, 3F},
            {48F, PUNCH, 3F}, {55F, PUNCH, 3F}, {63.5F, LIFT, 3.5F}, {70F, PASS, 2F},
            {75.6F, KICK, 4F}, {84.5F, PUNCH, 3F}, {92.5F, PUNCH, 3F}, {101.5F, FINISH, 10F},
    };

    // ---------------------------------------------------------------- kamera (fare etkilemez, hedefi takip etmez)
    public static final int CHASE = 0, FIXED = 1, MID = 2, DOLLY = 3;
    /**
     * {t, tip, kx, ky, kz, bx, by, bz [, k2x, k2y, k2z]}
     * FIXED: k = kamera, b = bakilan nokta (sahne, sabit).  CHASE: k = kuklaya gore ofset.
     * MID: hizci ile hedefin ortasi + k = kamera, ortasi + (0, by, 0) = bakilan.
     * DOLLY: kamera cekim boyunca k'den k2'ye yumusakca kayar, bakilan nokta b sabit (yavas geri cekilme).
     */
    public static final float[][] SHOTS = {
            {0F, CHASE, -0.9F, 1.7F, -3.2F, 0F, 0F, 0F},                // 1-1..1-3 arkasindan takip
            {5.5F, FIXED, 2.4F, 2.1F, -1.8F, -1.0F, 0.9F, 5.0F},        // 1-4..1-8 rakip onde, hizci arkada frende
            {15F, DOLLY, 0.8F, 2.6F, -7.6F, 0.6F, 1.0F, 2.0F,           // ana cekim (1-9 .. 3-10): finale kadar
                    0.84F, 2.93F, -9.57F},                              //   ~2 blok yavasca geri cekilir
            {96F, FIXED, 2.05F, 1.7F, -6.55F, 2.05F, 1.7F, 20F},          // FINAL: 1.7 blok yukseklikte dumduz ileri,
            //   TAMAMEN sabit (ucus ve yatis dahil sonuna kadar).
            //   Hizci yakinda, ortanin solunda, sirti bize;
            //   rakip ortanin saginda, yuzu bize donuk.
    };

    /** Fren kivilcimi araliklari. */
    public static final float[][] BRAKES = {{8.6F, 12.6F}, {104.4F, 108.2F}};

    public static final float FINAL_T = 101.5F;
    public static final float DURATION = 158F;
    /** Darbe ani donmasinin bittigi an (hedef firlar). */
    public static final float LAUNCH_T = 104.0F;

    private BlitzScript() {}
}