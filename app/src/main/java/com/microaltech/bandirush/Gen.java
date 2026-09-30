package com.microaltech.bandirush;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

/**
 * Pembuat level otomatis dari potongan-potongan (chunk) kecil. Dipakai oleh Tantangan Harian
 * (seed = tanggal, jadi semua pemain mendapat level yang sama hari itu) dan mode Tanpa Akhir.
 * Setiap chunk ditulis dari baris terdekat ke terjauh, lebar 5.
 */
final class Gen {
    private Gen() {
    }

    private static final String[][] EASY = {
            {".....", ".W.W.", "....."},
            {".....", "..C..", ".....", "C...C", "....."},
            {".....", "     ", "....."},
            {".....", "E....", "....."},
            {".....", ".?.C.", "....."},
            {".....", "..W..", "..W..", "....."},
            {".....", ".C.C.", "....."},
    };

    private static final String[][] MEDIUM = {
            {".....", "  .  ", "  w  ", "  .  ", "....."},
            {".....", "     ", "     ", "....."},
            {".....", "..T..", ".....", "N...N", "....."},
            {".....", "  .  ", "  m  ", "     ", "  m  ", "  .  ", "....."},
            {".....", "..B..", ".....", "W...W", "....."},
            {".....", "UUUUU", "....."},
            {".....", "I.W.I", ".....", ".3.3.", "....."},
            {".....", "  .  ", "  i  ", "     ", "  i  ", "  .  ", "....."},
            {".....", "H...H", ".....", "....."},
            {".....", ".2.2.", ".....", "..?..", "....."},
    };

    private static final String[][] HARD = {
            // jurang lebar: wumpa melayang menunjukkan lintasan lompat jauh
            {".....", ".....", "  .  ", "     ", "  w  ", "  w  ", "     ", "  .  ", "....."},
            {".....", "NN.NN", ".....", "..N..", "....."},
            {".....", "E.T.E", ".....", "....."},
            {".....", "  .  ", "  m  ", "     ", "  m  ", "     ", "  m  ", "  .  ", "....."},
            {".....", ".....", "UUUUU", ".....", "  .  ", "     ", "     ", "  .  ", "....."},
            {".....", "I...I", ".N.N.", ".....", "..W..", "....."},
    };

    private static final Theme[] THEMES = {Theme.JUNGLE, Theme.SUNSET, Theme.TEMPLE, Theme.SNOW, Theme.BEACH};
    private static final int[] MUSIC = {Music.JUNGLE, Music.SUNSET, Music.TEMPLE, Music.SNOW, Music.BOSS};

    /** Tanggal hari ini dalam format yyyymmdd. */
    static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    static String formatDate(int yyyymmdd) {
        int d = yyyymmdd % 100, m = (yyyymmdd / 100) % 100, y = yyyymmdd / 10000;
        return (d < 10 ? "0" : "") + d + "/" + (m < 10 ? "0" : "") + m + "/" + y;
    }

    static Level.Def daily(int date) {
        Random rnd = new Random(date * 7919L + 17);
        int t = rnd.nextInt(THEMES.length);
        ArrayList<String> near = new ArrayList<>();
        Collections.addAll(near, ".....", "..S..", ".....");
        int chunks = 18;
        for (int i = 0; i < chunks; i++) {
            if (i > 0 && i % 7 == 0) Collections.addAll(near, ".....", "..K..", ".....");
            String[][] pool = i < 5 ? (rnd.nextInt(3) == 0 ? MEDIUM : EASY)
                    : i < 12 ? (rnd.nextInt(3) == 0 ? HARD : MEDIUM)
                    : (rnd.nextBoolean() ? HARD : MEDIUM);
            Collections.addAll(near, pool[rnd.nextInt(pool.length)]);
        }
        Collections.addAll(near, ".....", ".W?W.", " ... ", "  G  ");
        return new Level.Def("Tantangan " + formatDate(date), THEMES[t], Level.MODE_NORMAL, MUSIC[t], 0f,
                "Tantangan hari ini! Hancurkan peti & selesaikan secepat mungkin.", toMap(near));
    }

    /** Level sangat panjang dengan kesulitan yang terus naik. */
    static Level.Def endless(long seed) {
        Random rnd = new Random(seed);
        int t = rnd.nextInt(THEMES.length);
        ArrayList<String> near = new ArrayList<>();
        Collections.addAll(near, ".....", "..S..", ".....", ".W.W.", ".....");
        for (int i = 0; i < 180; i++) {
            int roll = rnd.nextInt(100);
            int hardChance = Math.min(60, i / 2);
            int mediumChance = Math.min(80, 25 + i);
            String[][] pool = roll < hardChance ? HARD : roll < mediumChance ? MEDIUM : EASY;
            Collections.addAll(near, pool[rnd.nextInt(pool.length)]);
        }
        Collections.addAll(near, ".....", ".....", "  G  ");
        return new Level.Def("Tanpa Akhir", THEMES[t], Level.MODE_NORMAL, MUSIC[t], 0f,
                "Lari sejauh mungkin! Hanya satu nyawa.", toMap(near));
    }

    private static String[] toMap(ArrayList<String> near) {
        String[] map = new String[near.size()];
        for (int i = 0; i < near.size(); i++) map[near.size() - 1 - i] = near.get(i);
        return map;
    }
}
