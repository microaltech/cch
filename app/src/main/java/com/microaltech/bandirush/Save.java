package com.microaltech.bandirush;

import android.content.Context;
import android.content.SharedPreferences;

/** Progres pemain, toko, statistik, pencapaian, level buatan, dan pengaturan (SharedPreferences). */
final class Save {
    static final int RELIC_NONE = 0, RELIC_BRONZE = 1, RELIC_SILVER = 2, RELIC_GOLD = 3;

    static final int ST_CRATES = 0, ST_WUMPA = 1, ST_DEATHS = 2, ST_ENEMIES = 3, ST_JUMPS = 4,
            ST_FLAWLESS = 5, ST_BONUS = 6, ST_CUSTOM = 7, ST_PLAYTIME = 8, ST_COSTUMES = 9;
    static final int STAT_COUNT = 10;
    static final int CUSTOM_SLOTS = 3;

    private final SharedPreferences sp;

    // pengaturan
    int musicVol;
    int sfxVol;
    boolean vibrate;
    int btnSize;

    // progres
    int unlocked;
    int bank;
    int owned;
    int selChar;
    int selCostume;
    int startMasks;
    int achMask;
    final int[] stats = new int[STAT_COUNT];

    Save(Context context) {
        sp = context.getSharedPreferences("bandirush2", Context.MODE_PRIVATE);
        musicVol = sp.getInt("musicVol", 6);
        sfxVol = sp.getInt("sfxVol", 8);
        vibrate = sp.getBoolean("vibrate", true);
        btnSize = sp.getInt("btnSize", 1);
        loadProgress();
    }

    private void loadProgress() {
        unlocked = Math.max(2, sp.getInt("unlocked", 2)); // tutorial + level 1 terbuka dari awal
        bank = sp.getInt("bank", 0);
        owned = sp.getInt("owned", Shop.DEFAULT_OWNED) | Shop.DEFAULT_OWNED;
        selChar = sp.getInt("selChar", 0);
        selCostume = sp.getInt("selCostume", 0);
        startMasks = sp.getInt("startMasks", 0);
        achMask = sp.getInt("ach", 0);
        for (int i = 0; i < STAT_COUNT; i++) stats[i] = sp.getInt("st" + i, 0);
    }

    boolean isUnlocked(int level) {
        return level < unlocked;
    }

    void unlock(int count) {
        if (count > unlocked) unlocked = count;
    }

    boolean completed(int level) {
        return sp.getBoolean("done" + level, false);
    }

    void setCompleted(int level) {
        sp.edit().putBoolean("done" + level, true).apply();
    }

    boolean gem(int level) {
        return sp.getBoolean("gem" + level, false);
    }

    void setGem(int level) {
        sp.edit().putBoolean("gem" + level, true).apply();
    }

    /** Waktu terbaik Time Trial dalam detik, 0 kalau belum ada. */
    float best(int level) {
        return sp.getFloat("best" + level, 0f);
    }

    void setBest(int level, float t) {
        sp.edit().putFloat("best" + level, t).apply();
    }

    int relic(int level) {
        return sp.getInt("relic" + level, RELIC_NONE);
    }

    void setRelic(int level, int relic) {
        if (relic > relic(level)) sp.edit().putInt("relic" + level, relic).apply();
    }

    int totalGems(Level.Def[] defs) {
        int n = 0;
        for (int i = 0; i < defs.length; i++) if (!defs[i].tutorial && gem(i)) n++;
        return n;
    }

    int totalRelics(Level.Def[] defs) {
        int n = 0;
        for (int i = 0; i < defs.length; i++) if (!defs[i].tutorial && relic(i) > RELIC_NONE) n++;
        return n;
    }

    boolean owns(int item) {
        return (owned & (1 << item)) != 0;
    }

    void addStat(int stat, int n) {
        stats[stat] += n;
    }

    boolean hasAch(int i) {
        return (achMask & (1 << i)) != 0;
    }

    void setAch(int i) {
        achMask |= 1 << i;
    }

    String custom(int slot) {
        return sp.getString("custom" + slot, null);
    }

    void setCustom(int slot, String data) {
        sp.edit().putString("custom" + slot, data).apply();
    }

    /** Menyimpan semua nilai yang ditahan di memori (bank, toko, statistik, pencapaian). */
    void flush() {
        SharedPreferences.Editor e = sp.edit()
                .putInt("unlocked", unlocked)
                .putInt("bank", bank)
                .putInt("owned", owned)
                .putInt("selChar", selChar)
                .putInt("selCostume", selCostume)
                .putInt("startMasks", startMasks)
                .putInt("ach", achMask);
        for (int i = 0; i < STAT_COUNT; i++) e.putInt("st" + i, stats[i]);
        e.apply();
    }

    void saveSettings() {
        sp.edit()
                .putInt("musicVol", musicVol)
                .putInt("sfxVol", sfxVol)
                .putBoolean("vibrate", vibrate)
                .putInt("btnSize", btnSize)
                .apply();
    }

    /** Menghapus progres, toko, statistik, dan pencapaian. Pengaturan dan level buatan tetap ada. */
    void resetProgress() {
        String[] customs = new String[CUSTOM_SLOTS];
        for (int i = 0; i < CUSTOM_SLOTS; i++) customs[i] = custom(i);
        sp.edit().clear().apply();
        saveSettings();
        for (int i = 0; i < CUSTOM_SLOTS; i++) if (customs[i] != null) setCustom(i, customs[i]);
        loadProgress();
    }
}
