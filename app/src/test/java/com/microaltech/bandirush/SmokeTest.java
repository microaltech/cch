package com.microaltech.bandirush;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.FileOutputStream;
import java.lang.reflect.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/**
 * Menjalankan seluruh game tanpa perangkat (Robolectric + grafik native): semua level dimainkan oleh bot sederhana,
 * semua layar menu dibuka, dan screenshot disimpan ke folder "shots" untuk diperiksa.
 */
public class SmokeTest {
    static final String OUT = System.getProperty("shots", "/tmp");
    static final int W = 1600, H = 800;
    GameView g;

    Object get(String f) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); return x.get(g); }
    void set(String f, Object v) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); x.set(g, v); }
    Object call(String m, Object... args) throws Exception {
        for (Method mm : GameView.class.getDeclaredMethods()) {
            if (mm.getName().equals(m) && mm.getParameterCount() == args.length) { mm.setAccessible(true); return mm.invoke(g, args); }
        }
        throw new NoSuchMethodException(m);
    }
    void frames(int n) throws Exception { for (int i = 0; i < n; i++) call("update", 1f / 60f); }
    void shot(String name) throws Exception {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        call("render", new Canvas(b));
        try (FileOutputStream o = new FileOutputStream(OUT + "/" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 90, o); }
    }
    void tap(float x, float y) throws Exception { set("tapX", x); set("tapY", y); set("tapQueued", true); frames(2); }
    Player p() throws Exception { return (Player) get("p"); }
    int state() throws Exception { return (Integer) get("state"); }

    @Test public void smoke() throws Exception {
        new java.io.File(OUT).mkdirs();
        g = new GameView(RuntimeEnvironment.getApplication());
        g.surfaceChanged(null, 0, W, H);
        frames(30); shot("00_title");
        tap(W / 2f, H / 2f); frames(10); shot("01_map");

        for (int i = 0; i < Levels.ALL.length; i++) {
            call("startLevel", i, false);
            frames(20);
            shot(String.format("1%d_level_start", i));
            // bot: lari maju, lompat berkala, spin; tak bisa mati supaya menjelajah seluruh level
            set("joyZ", 1f);
            Level lv = (Level) get("level");
            int deaths = 0;
            for (int f = 0; f < 60 * 40 && state() != 3; f++) {
                Player pl = p();
                pl.invuln = 5f;
                if (f % 45 == 0) set("jumpQueued", true);
                if (f % 70 == 10) set("spinQueued", true);
                call("update", 1f / 60f);
                if (state() == 2) { deaths++; frames(120); set("joyZ", 1f); }
                if (f == 60 * 6) shot(String.format("1%d_level_mid", i));
            }
            set("joyZ", 0f);
            System.out.println("level " + i + " (" + Levels.ALL[i].name + ") state=" + state() + " z=" + p().z + " deaths=" + deaths + " broken=" + get("broken"));
            shot(String.format("1%d_level_end", i));
        }
        // tantangan harian & tanpa akhir
        for (int mode = 1; mode <= 2; mode++) {
            call("startRun", mode); frames(20);
            shot("3" + mode + "_run_start");
            set("joyZ", 1f);
            for (int f = 0; f < 60 * 25 && state() != 3; f++) {
                p().invuln = 5f;
                if (f % 45 == 0) set("jumpQueued", true);
                call("update", 1f / 60f);
                if (state() == 2) { frames(120); set("joyZ", 1f); }
            }
            set("joyZ", 0f);
            frames(5);
            System.out.println("run " + mode + " state=" + state() + " score=" + get("runScore") + " z=" + p().z);
            shot("3" + mode + "_run_end");
        }
        call("enterMap"); frames(5); shot("02_map_after");
        set("state", 12); frames(5); shot("26_scores");
        call("openSettings", 7); frames(5);
        set("state", 13); frames(2);
        tap(1480f, 680f); // pilih tombol lompat
        set("layoutDragX", 1200f); set("layoutDragY", 500f); set("layoutDragQueued", true); frames(2);
        shot("27_layout");
        call("closeLayout"); frames(2);
        // bahasa Inggris
        Save sv = (Save) get("save"); sv.english = true; Lang.en = true;
        shot("28_settings_en");
        call("enterMap"); frames(5); shot("29_map_en");
        call("startLevel", 0, false); frames(200); shot("30_tutorial_en");
        sv.english = false; Lang.en = false; sv.resetLayout(); call("layoutButtons");
        // menu lain
        call("enterMap"); frames(5);
        set("state", 9); frames(5); shot("20_shop");
        set("state", 10); frames(5); shot("21_ach");
        call("openSettings", 7); frames(5); shot("22_settings");
        call("enterEditor"); frames(5); shot("23_editor");
        call("enterMap");
        // bonus
        call("startLevel", 1, false); frames(5);
        Level lv = (Level) get("level");
        for (Entity e : lv.entities) if (e.type == Entity.PAD) { p().x = e.x; p().z = e.z; }
        frames(10); shot("24_bonus");
        System.out.println("inBonus=" + get("inBonus"));
        // pause
        set("pauseQueued", true); frames(3); shot("25_pause");
        System.out.println("DONE");
    }
}
