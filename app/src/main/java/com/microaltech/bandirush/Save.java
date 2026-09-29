package com.microaltech.bandirush;

import android.content.Context;
import android.content.SharedPreferences;

/** Progres pemain dan pengaturan, disimpan di SharedPreferences. */
final class Save {
    static final int RELIC_NONE = 0, RELIC_BRONZE = 1, RELIC_SILVER = 2, RELIC_GOLD = 3;

    private final SharedPreferences sp;

    int unlocked;
    int musicVol;
    int sfxVol;
    boolean vibrate;
    int btnSize;

    Save(Context context) {
        sp = context.getSharedPreferences("bandirush", Context.MODE_PRIVATE);
        unlocked = Math.max(1, sp.getInt("unlocked", 1));
        musicVol = sp.getInt("musicVol", 6);
        sfxVol = sp.getInt("sfxVol", 8);
        vibrate = sp.getBoolean("vibrate", true);
        btnSize = sp.getInt("btnSize", 1);
    }

    boolean isUnlocked(int level) {
        return level < unlocked;
    }

    void unlock(int count) {
        if (count > unlocked) {
            unlocked = count;
            sp.edit().putInt("unlocked", count).apply();
        }
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

    int totalGems(int levels) {
        int n = 0;
        for (int i = 0; i < levels; i++) if (gem(i)) n++;
        return n;
    }

    int totalRelics(int levels) {
        int n = 0;
        for (int i = 0; i < levels; i++) if (relic(i) > RELIC_NONE) n++;
        return n;
    }

    void saveSettings() {
        sp.edit()
                .putInt("musicVol", musicVol)
                .putInt("sfxVol", sfxVol)
                .putBoolean("vibrate", vibrate)
                .putInt("btnSize", btnSize)
                .apply();
    }

    /** Menghapus progres (level, kristal, rekor) tapi mempertahankan pengaturan. */
    void resetProgress() {
        sp.edit().clear().apply();
        unlocked = 1;
        saveSettings();
    }
}
