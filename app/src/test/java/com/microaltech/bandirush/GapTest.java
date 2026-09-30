package com.microaltech.bandirush;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/** Memastikan jurang selebar 4 petak tidak bisa dilompati biasa, tapi bisa dengan teknik luncur+lompat atau putar di udara. */
public class GapTest {
  GameView g;
  Object get(String f) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); return x.get(g); }
  void set(String f, Object v) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); x.set(g, v); }
  Object call(String m, Object... a) throws Exception { for (Method mm : GameView.class.getDeclaredMethods()) if (mm.getName().equals(m) && mm.getParameterCount()==a.length) { mm.setAccessible(true); return mm.invoke(g, a);} throw new NoSuchMethodException(m); }
  Player p() throws Exception { return (Player) get("p"); }
  // gap: baris 6..9 jurang, tepi dekat di z=6, tepi jauh di z=10
  static final String[] MAP = {"..G..", ".....", ".....", "  .  ", "     ", "     ", "     ", "     ", "  .  ", ".....", ".....", ".....", "..S..", "....."};
  /** mode 0: lompat biasa di tepi, 1: luncur lalu lompat, 2: lompat lalu putar di udara. @return z akhir (>10 = berhasil) */
  float attempt(int mode, float triggerZ) throws Exception {
    g = new GameView(RuntimeEnvironment.getApplication());
    g.surfaceChanged(null, 0, 1600, 800);
    call("startDef", new Level.Def("gap", Theme.TEMPLE, Level.MODE_NORMAL, Music.TEMPLE, 0f, "", MAP), -1, false, true);
    for (int i = 0; i < 90; i++) call("update", 1f/60f); // lewati banner
    set("joyZ", 1f);
    boolean done = false, spun = false;
    for (int f = 0; f < 60 * 6; f++) {
      Player pl = p();
      if (!done && pl.z >= triggerZ) {
        done = true;
        if (mode == 1) set("slideQueued", true); else set("jumpQueued", true);
        set("touchJumpHeld", true);
      }
      // pemain menekan lompat ketika sampai di bibir jurang (z >= 5.75)
      if (mode == 1 && done && !pl.jumping && pl.z >= 5.75f && pl.z < 6.3f) set("jumpQueued", true);
      if (mode == 2 && done && !spun && !pl.grounded && pl.vy < 1f) { set("spinQueued", true); spun = true; }
      call("update", 1f/60f);
      if ((Integer) get("state") != 1) break;
      if (pl.grounded && pl.z > 10.2f) return pl.z;
    }
    return (Integer) get("state") == 1 ? p().z : -1;
  }
  int successes(int mode) throws Exception {
    int ok = 0;
    for (float tz = 3.0f; tz <= 6.0f; tz += 0.25f) if (attempt(mode, tz) > 10.2f) ok++;
    return ok;
  }

  @Test public void wideGapNeedsTechnique() throws Exception {
    org.junit.Assert.assertEquals("lompat biasa tidak boleh cukup", 0, successes(0));
    org.junit.Assert.assertTrue("meluncur lalu lompat di tepi harus mudah", successes(1) >= 10);
    org.junit.Assert.assertTrue("lompat lalu putar di udara harus bisa", successes(2) >= 5);
  }
}
