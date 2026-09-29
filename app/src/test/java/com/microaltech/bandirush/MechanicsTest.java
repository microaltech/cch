package com.microaltech.bandirush;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.*;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/** Uji mekanik permainan yang dijalankan di JVM lewat Robolectric. */
public class MechanicsTest {
    GameView g;

    Object get(String f) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); return x.get(g); }
    void set(String f, Object v) throws Exception { Field x = GameView.class.getDeclaredField(f); x.setAccessible(true); x.set(g, v); }
    Object call(String m, Object... args) throws Exception {
        for (Method mm : GameView.class.getDeclaredMethods())
            if (mm.getName().equals(m) && mm.getParameterCount() == args.length) { mm.setAccessible(true); return mm.invoke(g, args); }
        throw new NoSuchMethodException(m);
    }
    void frames(int n) throws Exception { for (int i = 0; i < n; i++) call("update", 1f / 60f); }
    Player p() throws Exception { return (Player) get("p"); }
    Level level() throws Exception { return (Level) get("level"); }
    int state() throws Exception { return (Integer) get("state"); }
    Entity find(int type) throws Exception { for (Entity e : level().entities) if (e.type == type) return e; return null; }
    int idx(String name) { for (int i = 0; i < Levels.ALL.length; i++) if (Levels.ALL[i].name.contains(name)) return i; return -1; }

    @Before public void setUp() throws Exception {
        g = new GameView(RuntimeEnvironment.getApplication());
        g.surfaceChanged(null, 0, 1600, 800);
    }

    @Test public void slideUnderBarrier() throws Exception {
        call("startLevel", idx("Puncak Salju"), false);
        Entity bar = find(Entity.BARRIER);
        assertNotNull(bar);
        // tanpa meluncur: tertahan
        p().x = bar.x; p().z = bar.z - 1.2f; frames(2);
        set("joyZ", 1f); frames(60);
        assertTrue("berjalan harus tertahan palang, z=" + p().z, p().z < bar.z);
        // meluncur: lewat
        p().z = bar.z - 1.4f; p().vz = 0f; frames(3);
        set("slideQueued", true); frames(60);
        set("joyZ", 0f);
        assertTrue("meluncur harus melewati palang, z=" + p().z, p().z > bar.z + 0.5f);
        assertEquals(1, state());
    }

    @Test public void sinkingLogDrowns() throws Exception {
        call("startLevel", idx("Sungai Deras"), false);
        Entity log = find(Entity.SINK);
        assertNotNull(log);
        p().x = log.x; p().z = log.z; p().y = 0.5f; p().vy = 0f; p().invuln = 99f;
        frames(30);
        assertSame("harus berdiri di kayu", log, p().standingOn);
        boolean died = false;
        for (int i = 0; i < 60 * 5 && !died; i++) { frames(1); died = state() == 2; }
        assertTrue("berdiri lama di kayu harus tenggelam", died);
    }

    @Test public void bonusDeathRestoresProgress() throws Exception {
        call("startLevel", idx("Hutan Wumpa"), false);
        int lives = (Integer) get("lives");
        Entity pad = find(Entity.PAD);
        p().x = pad.x; p().z = pad.z; frames(5);
        assertTrue((Boolean) get("inBonus"));
        int brokenBefore = (Integer) get("brokenBeforeBonus");
        // hancurkan satu peti di bonus lalu jatuh
        for (Entity e : level().entities) if (e.type == Entity.CRATE) { call("breakCrate", e); break; }
        call("killPlayer", true);
        frames(200);
        assertFalse((Boolean) get("inBonus"));
        assertEquals("jatuh di bonus tidak mengurangi nyawa", lives, (int) (Integer) get("lives"));
        assertEquals("peti bonus direset", brokenBefore, (int) (Integer) get("broken"));
        assertTrue("pad masih bisa dipakai", pad.alive);
    }

    @Test public void rideCrashIntoIron() throws Exception {
        call("startLevel", idx("Tunggangan"), false);
        Entity iron = null;
        for (Entity e : level().entities) if (e.type == Entity.IRON && Math.abs(e.x) < 0.1f) { iron = e; break; }
        assertNotNull(iron);
        p().x = iron.x; p().z = iron.z - 3f; frames(90);
        assertEquals("menabrak peti besi saat menunggang = mati", 2, state());
    }

    @Test public void rideSmashesCrates() throws Exception {
        call("startLevel", idx("Tunggangan"), false);
        Entity crate = null;
        for (Entity e : level().entities) if (e.type == Entity.CRATE) { crate = e; break; }
        p().x = crate.x; p().z = crate.z - 3f; frames(60);
        assertFalse("babi harus menghancurkan peti kayu", crate.alive);
        assertEquals(1, state());
    }

    @Test public void bossCanBeDefeated() throws Exception {
        call("startLevel", idx("Raja Kepiting"), false);
        Boss b = (Boss) get("boss");
        for (int hit = 0; hit < Boss.MAX_HP; hit++) {
            // tunggu bos menerjang & pusing
            int guard = 0;
            while (b.state != Boss.STUNNED && guard++ < 60 * 30) {
                p().invuln = 99f;
                p().x = 3f; p().z = 1f; // menjauh dari jalur terjangan
                frames(1);
            }
            assertEquals("bos harus pusing setelah menerjang (hit " + hit + ")", Boss.STUNNED, b.state);
            p().x = b.x; p().z = b.z - 1.2f; p().invuln = 99f;
            set("spinQueued", true);
            frames(3);
        }
        assertEquals(Boss.DEFEATED, b.state);
        frames(60 * 4);
        assertEquals("level selesai setelah bos kalah", 3, state());
    }

    @Test public void endlessEndsOnDeath() throws Exception {
        call("startRun", 2);
        call("killPlayer", true);
        frames(150);
        assertEquals(3, state());
        assertTrue((Integer) get("runScore") >= 0);
    }

    @Test public void dailyIsDeterministic() {
        Level.Def a = Gen.daily(20260929), b = Gen.daily(20260929), c = Gen.daily(20260930);
        assertArrayEquals(a.map, b.map);
        assertFalse(java.util.Arrays.equals(a.map, c.map));
        for (String r : a.map) assertEquals(5, r.length());
        Level l = new Level(a);
        assertTrue(l.goalRow > 10);
    }

    @Test public void translationsCoverUi() {
        Lang.en = true;
        try {
            assertEquals("SELECT LEVEL", Lang.t("PILIH LEVEL"));
            assertEquals("Crates: 3 / 10     Wumpa: 5", Lang.t("Peti: 3 / 10     Wumpa: 5"));
            for (Level.Def d : Levels.ALL) {
                assertNotEquals("hint " + d.name, d.hint, Lang.t(d.hint));
                for (String tip : d.tipTexts) assertNotEquals("tip", tip, Lang.t(tip));
            }
            for (String n : Achievements.NAMES) if (!n.equals("Fashionista")) assertNotEquals(n, Lang.t(n));
            for (String n : Achievements.DESCS) assertNotEquals(n, Lang.t(n));
            for (Shop.Item it : Shop.ITEMS) assertNotEquals(it.desc, Lang.t(it.desc));
            for (String n : Editor.TILE_NAMES) {
                if (n.equals("Finish") || n.equals("Checkpoint") || n.equals("Nitro") || n.equals("TNT") || n.equals("Wumpa") || n.equals("Piranha")) continue;
                assertNotEquals(n, Lang.t(n));
            }
        } finally {
            Lang.en = false;
        }
    }
}
