package com.microaltech.bandirush;

/** Daftar pencapaian. Syarat tiap pencapaian diperiksa di {@link GameView#achievementDone(int)}. */
final class Achievements {
    static final String[] NAMES = {
            "Murid Teladan",
            "Langkah Pertama",
            "Tukang Kayu",
            "Penghancur",
            "Pecinta Wumpa",
            "Tak Tersentuh",
            "Pemburu Kristal",
            "Sang Juara",
            "Pelari Kilat",
            "Kolektor Relik",
            "Jagoan Putar",
            "Penjelajah Rahasia",
            "Joki Babi",
            "Arsitek",
            "Fashionista",
    };

    static final String[] DESCS = {
            "Selesaikan tutorial",
            "Selesaikan level 1",
            "Hancurkan 100 peti",
            "Hancurkan 500 peti",
            "Kumpulkan 500 wumpa",
            "Selesaikan level tanpa mati",
            "Kumpulkan semua kristal",
            "Kalahkan Raja Kepiting",
            "Dapatkan relik emas",
            "Dapatkan relik di semua level",
            "Kalahkan 50 musuh",
            "Selesaikan area bonus",
            "Selesaikan level tunggangan",
            "Selesaikan level buatanmu",
            "Beli kostum pertama",
    };

    private Achievements() {
    }
}
