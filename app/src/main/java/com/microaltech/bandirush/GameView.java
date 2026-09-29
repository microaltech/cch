package com.microaltech.bandirush;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Random;

/**
 * Mesin game: loop, fisika, dan renderer 3D-semu (proyeksi perspektif + painter's algorithm)
 * di atas Canvas, plus semua layar menu.
 *
 * Kamera biasanya berada di belakang Bandi dan melihat ke depan sepanjang koridor (+Z).
 * Di level kejar-kejaran kamera dibalik: berada di depan Bandi dan melihat ke belakang (-Z)
 * sehingga batu raksasa yang mengejar terlihat, persis seperti Crash Bandicoot.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int ST_TITLE = 0, ST_PLAY = 1, ST_DYING = 2, ST_DONE = 3, ST_OVER = 4,
            ST_WIN = 5, ST_PAUSE = 6, ST_MAP = 7, ST_SETTINGS = 8, ST_SHOP = 9, ST_ACH = 10, ST_EDITOR = 11;

    // Fisika (satuan: 1 unit = 1 petak)
    private static final float GRAVITY = 26f;
    private static final float RUN_SPEED = 5.2f;
    private static final float JUMP_V = 8.8f;
    private static final float BOUNCE_V = 7.5f;
    private static final float SUPER_BOUNCE_V = 12.5f;
    private static final float AIR_SPIN_V = 6.5f;
    private static final float LONG_JUMP_FACTOR = 1.55f;
    private static final float PR = 0.28f;           // radius Bandi
    private static final float PLAYER_H = 0.95f, SLIDE_H = 0.45f;
    private static final float CH = 0.45f;           // setengah ukuran peti
    private static final float CS = 0.9f;            // ukuran peti
    private static final float PLAT_HX = 0.6f, PLAT_HZ = 0.5f;
    private static final float BAR_Y0 = 0.55f, BAR_Y1 = 2.2f, BAR_HZ = 0.3f;
    private static final float SPIN_TIME = 0.45f, SPIN_COOLDOWN = 0.7f;
    private static final float SLIDE_TIME = 0.55f, SLIDE_SPEED = 8.2f, SLIDE_CD = 0.8f;
    private static final float EXPLODE_R = 1.7f;
    private static final float BOULDER_R = 2.4f, BOULDER_SPEED = 4.35f, BOULDER_DELAY = 1.6f, BOULDER_GAP = 5.5f;
    private static final float BOMB_R = 1.3f;
    private static final float RIDE_SPEED = 7.2f, RIDE_SIDE_SPEED = 4.8f;
    private static final int START_LIVES = 4;

    // Kamera & render
    private static final float NEAR = 0.05f;
    private static final int VIEW_ROWS = 34;
    private static final float FOG_START = 10f, FOG_END = 33f;
    private static final float WALL_H = 2.0f, PIT_Y = -3.2f;

    private final SurfaceHolder holder;
    private final Sfx sfx;
    private final Music music;
    private final Save save;
    private final Sprites sprites = new Sprites();
    private final Vibrator vibrator;
    private final Random rng = new Random();
    private Thread thread;
    private volatile boolean running;
    private volatile int w = 1, h = 1;

    // ---------- input (ditulis thread UI, dibaca thread game) ----------
    private volatile float joyX, joyZ, padX, padY;
    private volatile boolean kLeft, kRight, kUp, kDown, kJump;
    private volatile boolean jumpQueued, spinQueued, slideQueued, tapQueued, backQueued, pauseQueued;
    private volatile float tapX, tapY;
    private volatile boolean touchJumpHeld;
    private volatile boolean joyActive;
    private volatile float joyBaseX, joyBaseY, joyKnobX, joyKnobY;
    private int joyId = -1, jumpId = -1;
    private volatile float btnR = 60, jumpBx, jumpBy, spinBx, spinBy, slideBx, slideBy, pauseBx, pauseBy;

    // ---------- status game ----------
    private int state = ST_TITLE;
    private int settingsReturn = ST_MAP;
    private float stateT, time;
    private Level level;
    private int levelIndex;
    private int lives = START_LIVES, wumpa, broken;
    private final Player p = new Player();
    private float checkX, checkZ;
    private boolean dieFall;
    private float bannerT, hintT;
    private String msg = "";
    private float msgT;
    private boolean gemThisLevel, gemIsNew;
    private boolean ttMode, ttNewRecord;
    private float ttTime;
    private int ttRelic;
    private boolean confirmReset;
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<Entity> bombs = new ArrayList<>();
    private final Entity boulder = new Entity(Entity.BOULDER, 0f, 0f, 0f);
    private float boulderT;
    private final Boss boss = new Boss();

    // ---------- level aktif, bonus, toko, pencapaian, editor ----------
    private final Ui ui = new Ui();
    private final Editor editor;
    private Level.Def curDef;
    private boolean custom, practice;
    private Level mainLevel;
    private boolean inBonus;
    private Entity bonusPad;
    private int brokenBeforeBonus, maskBeforeBonus;
    private int levelTotal;
    private int bankGain;
    private float playAccum, achTimer;
    private final ArrayList<String> toasts = new ArrayList<>();
    private float toastT;
    private int tipIdx = -1;
    private float tipT;
    private float runSpeed = RUN_SPEED, jumpV = JUMP_V;
    private Skin skin = new Skin();
    private boolean diedThisLevel;
    private int shopSel = -1;
    private volatile String pendingPaste;
    private volatile float dragX, dragY;
    private volatile boolean dragQueued;

    // ---------- kamera ----------
    private float camX, camY = 2.7f, camZ;
    private int camDir = 1;
    private float camBack = 4.6f, camUp = 2.7f;
    private float cosP = 1f, sinP = 0f;

    // ---------- render ----------
    private Canvas cv;
    private final Paint solid = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final RectF uiRect = new RectF();
    private final float[] qx = new float[4], qy = new float[4];
    private final float[] wx = new float[8], wy = new float[8];
    private float qz;
    private float pX, pY, pZc, pS;
    private float focal = 1f, cx, cy;
    private final ArrayList<Entity> drawList = new ArrayList<>();
    private final Comparator<Entity> byKeyDesc = new Comparator<Entity>() {
        @Override
        public int compare(Entity a, Entity b) {
            return Float.compare(b.key, a.key);
        }
    };
    private LinearGradient skyShader;
    private Theme skyTheme;
    private int skyH;
    private float sHX, sHZ, sY0, sY1; // hasil solidBounds()

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        save = new Save(context);
        editor = new Editor(save, ui);
        sfx = new Sfx();
        music = new Music();
        Vibrator v = null;
        try {
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && !v.hasVibrator()) v = null;
        } catch (Exception ignored) {
        }
        vibrator = v;
        applyVolumes();

        solid.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);

        loadTitleWorld();
        state = ST_TITLE;
    }

    // =====================================================================
    // Siklus hidup surface & thread
    // =====================================================================

    @Override
    public void surfaceCreated(SurfaceHolder sh) {
        running = true;
        thread = new Thread(this, "BandiRushLoop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder sh, int format, int width, int height) {
        w = Math.max(1, width);
        h = Math.max(1, height);
        layoutButtons();
    }

    private void layoutButtons() {
        float k = save.btnSize == 0 ? 0.085f : save.btnSize == 2 ? 0.12f : 0.1f;
        float r = h * k;
        btnR = r;
        jumpBx = w - r * 1.55f;
        jumpBy = h - r * 1.6f;
        spinBx = w - r * 3.9f;
        spinBy = h - r * 1.15f;
        slideBx = w - r * 1.35f;
        slideBy = h - r * 3.55f;
        pauseBx = w - h * 0.075f;
        pauseBy = h * 0.075f;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder sh) {
        running = false;
        boolean retry = true;
        while (retry) {
            try {
                if (thread != null) thread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
        thread = null;
    }

    void pauseGame() {
        pauseQueued = true;
        joyActive = false;
        joyId = -1;
        jumpId = -1;
        joyX = joyZ = 0f;
        touchJumpHeld = false;
        music.pause();
    }

    void resumeGame() {
        music.resume();
    }

    void release() {
        sfx.release();
        music.release();
    }

    /** @return true kalau tombol back sudah ditangani game. */
    boolean onBack() {
        if (state == ST_TITLE) return false;
        backQueued = true;
        return true;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;
            update(dt);

            Canvas c = null;
            try {
                c = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
                if (c != null) render(c);
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
            long spent = (System.nanoTime() - now) / 1_000_000L;
            if (spent < 8) {
                try {
                    Thread.sleep(8 - spent);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    // =====================================================================
    // Alur game
    // =====================================================================

    private void applyVolumes() {
        music.setVolume(save.musicVol / 10f * 0.7f);
        sfx.setVolume(save.sfxVol / 10f);
    }

    private void vibe(int ms) {
        if (!save.vibrate || vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrateLegacy(ms);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrateLegacy(int ms) {
        vibrator.vibrate(ms);
    }

    private void setCamera(int dir, float back, float up, float pitch) {
        camDir = dir;
        camBack = back;
        camUp = up;
        cosP = (float) Math.cos(pitch);
        sinP = (float) Math.sin(pitch);
    }

    private void loadTitleWorld() {
        level = new Level(Levels.ALL[0]);
        setCamera(1, 4.6f, 2.7f, 0.22f);
        p.reset(level.startX, level.startZ);
        p.facing = (float) Math.PI;
        particles.clear();
        bombs.clear();
        ttMode = false;
        custom = false;
        inBonus = false;
        mainLevel = null;
        skin = Shop.skin(save.selChar, save.selCostume);
        snapCamera();
        music.play(Music.JUNGLE);
    }

    private void enterMap() {
        flushBank();
        loadTitleWorld();
        state = ST_MAP;
        stateT = 0f;
        checkAchievements();
    }

    private void enterEditor() {
        flushBank();
        loadTitleWorld();
        state = ST_EDITOR;
        stateT = 0f;
    }

    private void flushBank() {
        save.bank += bankGain;
        bankGain = 0;
        save.flush();
    }

    private void startLevel(int index, boolean timeTrial) {
        startDef(Levels.ALL[index], index, timeTrial, false);
    }

    private void startDef(Level.Def def, int index, boolean timeTrial, boolean isCustom) {
        curDef = def;
        custom = isCustom;
        ttMode = timeTrial;
        practice = timeTrial || isCustom || def.tutorial;
        levelIndex = index;
        level = new Level(def);
        inBonus = false;
        mainLevel = null;
        bonusPad = null;
        levelTotal = level.totalCrates + (def.bonus != null ? new Level(def.bonus).totalCrates : 0);
        switch (level.mode) {
            case Level.MODE_CHASE: setCamera(-1, 7f, 3.6f, 0.33f); break;
            case Level.MODE_BOSS: setCamera(1, 5.6f, 3.5f, 0.3f); break;
            case Level.MODE_RIDE: setCamera(1, 5.2f, 3.1f, 0.25f); break;
            default: setCamera(1, 4.6f, 2.7f, 0.22f); break;
        }
        skin = Shop.skin(save.selChar, save.selCostume);
        runSpeed = Shop.runSpeed(save.selChar);
        jumpV = Shop.jumpV(save.selChar);
        diedThisLevel = false;
        tipIdx = -1;
        p.reset(level.startX, level.startZ);
        checkX = level.startX;
        checkZ = level.startZ;
        broken = 0;
        gemThisLevel = false;
        gemIsNew = false;
        ttTime = 0f;
        ttNewRecord = false;
        ttRelic = Save.RELIC_NONE;
        particles.clear();
        bombs.clear();
        boulder.z = level.startZ - BOULDER_GAP;
        boulderT = 0f;
        boss.reset(level.bossX, level.bossZ);
        bannerT = 2.8f;
        hintT = 5f;
        msgT = 0f;
        snapCamera();
        state = ST_PLAY;
        stateT = 0f;
        jumpQueued = spinQueued = slideQueued = false;
        music.play(level.def.music);
        if (level.mode == Level.MODE_BOSS) sfx.play(Sfx.ROAR);
        if (!practice && save.startMasks > 0) {
            save.startMasks--;
            p.mask = 1;
            showMsg("Topeng dari toko dipakai!");
        }
    }

    // ---------------- area bonus ----------------

    private void enterBonus(Entity pad) {
        bonusPad = pad;
        mainLevel = level;
        brokenBeforeBonus = broken;
        maskBeforeBonus = p.mask;
        level = new Level(curDef.bonus);
        inBonus = true;
        p.reset(level.startX, level.startZ);
        p.mask = maskBeforeBonus;
        particles.clear();
        snapCamera();
        sfx.play(Sfx.POP);
        showMsg("AREA BONUS!");
        hintT = 4f;
    }

    private void exitBonus(boolean success) {
        level = mainLevel;
        mainLevel = null;
        inBonus = false;
        if (success) {
            bonusPad.alive = false;
            save.addStat(Save.ST_BONUS, 1);
            showMsg("BONUS SELESAI!");
            sfx.play(Sfx.CHECK);
        } else {
            broken = brokenBeforeBonus;
            showMsg("Bonus gagal - coba lagi!");
        }
        p.reset(bonusPad.x, bonusPad.z + 1.2f);
        p.mask = maskBeforeBonus;
        p.invuln = 1f;
        particles.clear();
        snapCamera();
        state = ST_PLAY;
        stateT = 0f;
    }

    private void snapCamera() {
        camX = p.x * 0.55f;
        camZ = p.z - camBack * camDir;
        camY = camUp;
    }

    private void completeLevel() {
        state = ST_DONE;
        stateT = 0f;
        sfx.play(Sfx.WIN);
        burst(p.x, 1.2f, p.z + 0.5f * camDir, 0xFFFF80D0, 30, 5f);
        gemThisLevel = false;
        if (custom) {
            save.addStat(Save.ST_CUSTOM, 1);
        } else if (curDef.tutorial) {
            save.setCompleted(levelIndex);
        } else if (ttMode) {
            float best = save.best(levelIndex);
            ttNewRecord = best <= 0f || ttTime < best;
            if (ttNewRecord) save.setBest(levelIndex, ttTime);
            float target = level.def.targetTime;
            ttRelic = ttTime <= target ? Save.RELIC_GOLD
                    : ttTime <= target * 1.25f ? Save.RELIC_SILVER : Save.RELIC_BRONZE;
            save.setRelic(levelIndex, ttRelic);
        } else {
            gemThisLevel = level.mode == Level.MODE_BOSS
                    || (levelTotal > 0 && broken >= levelTotal);
            if (gemThisLevel && !save.gem(levelIndex)) {
                gemIsNew = true;
                save.setGem(levelIndex);
            }
            save.setCompleted(levelIndex);
            save.unlock(levelIndex + 2);
            if (!diedThisLevel) save.addStat(Save.ST_FLAWLESS, 1);
        }
        flushBank();
        checkAchievements();
    }

    // ---------------- pencapaian ----------------

    boolean achievementDone(int i) {
        Level.Def[] defs = Levels.ALL;
        int n = Levels.progressCount();
        int[] st = save.stats;
        switch (i) {
            case 0: return save.completed(0);
            case 1: return save.completed(1);
            case 2: return st[Save.ST_CRATES] >= 100;
            case 3: return st[Save.ST_CRATES] >= 500;
            case 4: return st[Save.ST_WUMPA] >= 500;
            case 5: return st[Save.ST_FLAWLESS] >= 1;
            case 6: return save.totalGems(defs) >= n;
            case 7: return save.completed(Levels.indexOfMode(Level.MODE_BOSS));
            case 8:
                for (int k = 0; k < defs.length; k++) if (save.relic(k) == Save.RELIC_GOLD) return true;
                return false;
            case 9: return save.totalRelics(defs) >= n;
            case 10: return st[Save.ST_ENEMIES] >= 50;
            case 11: return st[Save.ST_BONUS] >= 1;
            case 12: return save.completed(Levels.indexOfMode(Level.MODE_RIDE));
            case 13: return st[Save.ST_CUSTOM] >= 1;
            case 14: return st[Save.ST_COSTUMES] >= 1;
            default: return false;
        }
    }

    private void checkAchievements() {
        boolean changed = false;
        for (int i = 0; i < Achievements.NAMES.length; i++) {
            if (!save.hasAch(i) && achievementDone(i)) {
                save.setAch(i);
                toasts.add(Achievements.NAMES[i]);
                changed = true;
            }
        }
        if (changed) save.flush();
    }

    private void update(float dt) {
        time += dt;
        stateT += dt;
        if (bannerT > 0) bannerT -= dt;
        if (hintT > 0) hintT -= dt;
        if (msgT > 0) msgT -= dt;
        if (!toasts.isEmpty()) {
            if (toastT <= 0f) {
                toastT = 3f;
                sfx.play(Sfx.CHECK);
            }
            toastT -= dt;
            if (toastT <= 0f) toasts.remove(0);
        }

        boolean tap = tapQueued;
        tapQueued = false;
        float tx = tapX, ty = tapY;
        boolean back = backQueued;
        backQueued = false;

        switch (state) {
            case ST_TITLE:
                pauseQueued = false;
                updateWorld(dt);
                updateParticles(dt);
                camX = (float) Math.sin(time * 0.4f) * 0.6f;
                if (tap) {
                    sfx.play(Sfx.SELECT);
                    state = ST_MAP;
                    stateT = 0f;
                }
                break;
            case ST_MAP:
                pauseQueued = false;
                updateWorld(dt);
                updateParticles(dt);
                camX = (float) Math.sin(time * 0.4f) * 0.6f;
                if (back) {
                    state = ST_TITLE;
                    stateT = 0f;
                } else if (tap) {
                    mapTap(tx, ty);
                }
                break;
            case ST_SETTINGS:
                pauseQueued = false;
                if (settingsReturn != ST_PAUSE) {
                    updateWorld(dt);
                    camX = (float) Math.sin(time * 0.4f) * 0.6f;
                }
                if (back) closeSettings();
                else if (tap) settingsTap(tx, ty);
                break;
            case ST_SHOP:
            case ST_ACH:
                pauseQueued = false;
                updateWorld(dt);
                camX = (float) Math.sin(time * 0.4f) * 0.6f;
                if (back) {
                    sfx.play(Sfx.SELECT);
                    enterMap();
                } else if (tap) {
                    if (dist(tx, ty, h * 0.08f, h * 0.08f) < h * 0.08f) {
                        sfx.play(Sfx.SELECT);
                        enterMap();
                    } else if (state == ST_SHOP) {
                        shopTap(tx, ty);
                    }
                }
                break;
            case ST_EDITOR: {
                pauseQueued = false;
                editor.update(dt);
                String paste = pendingPaste;
                if (paste != null) {
                    pendingPaste = null;
                    editor.showMsg(editor.load(paste) ? "Level ditempel dari clipboard!" : "Teks di clipboard bukan level Bandi Rush");
                }
                if (dragQueued) {
                    dragQueued = false;
                    editor.drag(dragX, dragY, w, h);
                }
                if (back) {
                    editor.saveSlot();
                    enterMap();
                } else if (tap) {
                    editorAction(editor.tap(tx, ty, w, h));
                }
                break;
            }
            case ST_PLAY:
                if (back || pauseQueued) {
                    pauseQueued = false;
                    state = ST_PAUSE;
                    stateT = 0f;
                    flushBank();
                    break;
                }
                updatePlaying(dt);
                break;
            case ST_PAUSE:
                pauseQueued = false;
                if (back) {
                    state = ST_PLAY;
                } else if (tap) {
                    pauseTap(tx, ty);
                }
                break;
            case ST_DYING:
                updateWorld(dt);
                updateParticles(dt);
                if (dieFall) {
                    p.vy -= GRAVITY * dt;
                    p.y += p.vy * dt;
                }
                if (stateT > 1.8f) respawnOrGameOver();
                break;
            case ST_DONE:
                updateWorld(dt);
                updateParticles(dt);
                if ((tap || back) && stateT > 1f) {
                    if (custom) {
                        enterEditor();
                    } else if (!practice && levelIndex == Levels.ALL.length - 1) {
                        state = ST_WIN;
                        stateT = 0f;
                    } else {
                        enterMap();
                    }
                }
                break;
            case ST_OVER:
            case ST_WIN:
                updateWorld(dt);
                updateParticles(dt);
                if ((tap || back) && stateT > 1f) {
                    if (state == ST_OVER) lives = START_LIVES;
                    enterMap();
                }
                break;
            default:
                break;
        }
        if (state != ST_PLAY) {
            jumpQueued = false;
            spinQueued = false;
            slideQueued = false;
        }
    }

    private void updatePlaying(float dt) {
        boolean jumpNow = jumpQueued;
        jumpQueued = false;
        boolean spinNow = spinQueued;
        spinQueued = false;
        boolean slideNow = slideQueued;
        slideQueued = false;
        if (ttMode) ttTime += dt;

        int steps = Math.max(1, (int) Math.ceil(dt / (1f / 90f)));
        float sdt = dt / steps;
        for (int i = 0; i < steps && state == ST_PLAY; i++) {
            updateWorld(sdt);
            updateBoulder(sdt);
            updateBoss(sdt);
            updateBombs(sdt);
            if (state != ST_PLAY) break;
            updatePlayer(sdt, i == 0 && jumpNow, i == 0 && spinNow, i == 0 && slideNow);
            if (state != ST_PLAY) break;
            interactions();
        }
        updateParticles(dt);
        if (state != ST_PLAY) return;

        playAccum += dt;
        if (playAccum >= 1f) {
            playAccum -= 1f;
            save.addStat(Save.ST_PLAYTIME, 1);
        }
        achTimer += dt;
        if (achTimer > 2f) {
            achTimer = 0f;
            checkAchievements();
        }

        // petunjuk tutorial
        if (!inBonus) {
            int[] rows = level.def.tipRows;
            int idx = -1;
            for (int i = 0; i < rows.length; i++) if (p.z >= rows[i]) idx = i;
            if (idx != tipIdx) {
                tipIdx = idx;
                tipT = 0f;
            }
            tipT += dt;
        }

        if (p.y < -4f) {
            killPlayer(true);
            return;
        }
        if (level.mode != Level.MODE_BOSS && p.z >= level.goalRow + 0.3f) {
            if (inBonus) exitBonus(true);
            else completeLevel();
            return;
        }
        updateCamera(dt);
    }

    private void updateCamera(float dt) {
        float ty = camUp + Math.max(0f, Math.min(1.8f, p.grounded ? p.y : p.shadowY)) * 0.85f;
        camX += (p.x * 0.55f - camX) * Math.min(1f, dt * 5f);
        camZ += (p.z - camBack * camDir - camZ) * Math.min(1f, dt * 8f);
        camY += (ty - camY) * Math.min(1f, dt * 3f);
    }

    private void respawnOrGameOver() {
        if (inBonus) {
            exitBonus(false); // jatuh di area bonus tidak mengurangi nyawa
            return;
        }
        if (!practice) {
            lives--;
            if (lives <= 0) {
                lives = 0;
                state = ST_OVER;
                stateT = 0f;
                flushBank();
                music.play(-1);
                return;
            }
        }
        p.reset(checkX, checkZ);
        p.invuln = 1.2f;
        for (Entity e : level.entities) {
            if (e.type == Entity.TNT && e.alive) e.fuse = -1f;
        }
        bombs.clear();
        boulder.z = checkZ - BOULDER_GAP;
        boulderT = 0f;
        if (level.mode == Level.MODE_BOSS && boss.state != Boss.DEFEATED) {
            boss.x = level.bossX;
            boss.z = boss.homeZ;
            boss.state = Boss.INTRO;
            boss.t = 1f;
            boss.throwsLeft = 3;
        }
        snapCamera();
        state = ST_PLAY;
        stateT = 0f;
    }

    // =====================================================================
    // Menu
    // =====================================================================

    private void cardRect(int i, RectF out) {
        int cols = 4;
        float m = w * 0.025f;
        float top = h * 0.17f;
        float cw = (w - m * (cols + 1)) / cols;
        float chh = h * 0.3f;
        int col = i % cols, row = i / cols;
        float x = m + col * (cw + m), y = top + row * (chh + h * 0.03f);
        out.set(x, y, x + cw, y + chh);
    }

    private static final String[] MAP_BUTTONS = {"TOKO", "PENCAPAIAN", "EDITOR"};

    private void mapButtonRect(int i, RectF out) {
        float bw = w * 0.26f, bh = h * 0.1f;
        float cxb = w * (0.2f + i * 0.3f);
        out.set(cxb - bw / 2, h * 0.84f, cxb + bw / 2, h * 0.84f + bh);
    }

    private float ttBtnR(RectF card) {
        return card.height() * 0.16f;
    }

    private void mapTap(float x, float y) {
        float corner = h * 0.08f;
        if (dist(x, y, corner, corner) < corner) {
            sfx.play(Sfx.SELECT);
            state = ST_TITLE;
            stateT = 0f;
            return;
        }
        if (dist(x, y, w - corner, corner) < corner) {
            openSettings(ST_MAP);
            return;
        }
        for (int i = 0; i < MAP_BUTTONS.length; i++) {
            mapButtonRect(i, uiRect);
            if (!uiRect.contains(x, y)) continue;
            sfx.play(Sfx.SELECT);
            if (i == 0) {
                state = ST_SHOP;
                shopSel = -1;
            } else if (i == 1) {
                state = ST_ACH;
            } else {
                editor.loadSlot(editor.slot);
                state = ST_EDITOR;
            }
            stateT = 0f;
            return;
        }
        for (int i = 0; i < Levels.ALL.length; i++) {
            cardRect(i, uiRect);
            if (!uiRect.contains(x, y)) continue;
            if (!save.isUnlocked(i)) {
                sfx.play(Sfx.HURT);
                return;
            }
            sfx.play(Sfx.SELECT);
            float r = ttBtnR(uiRect);
            boolean tt = !Levels.ALL[i].tutorial && save.completed(i)
                    && dist(x, y, uiRect.right - r * 1.25f, uiRect.top + r * 1.25f) < r * 1.4f;
            startLevel(i, tt);
            return;
        }
    }

    private void openSettings(int returnTo) {
        sfx.play(Sfx.SELECT);
        settingsReturn = returnTo;
        confirmReset = false;
        state = ST_SETTINGS;
        stateT = 0f;
    }

    private void closeSettings() {
        save.saveSettings();
        sfx.play(Sfx.SELECT);
        state = settingsReturn;
        stateT = 0f;
    }

    private float settingsRowY(int i) {
        return h * (0.27f + i * 0.12f);
    }

    private void settingsTap(float x, float y) {
        for (int i = 0; i < 5; i++) {
            float ry = settingsRowY(i);
            if (Math.abs(y - ry) > h * 0.055f) continue;
            if (i <= 1) {
                int delta = 0;
                if (dist(x, y, w * 0.52f, ry) < h * 0.07f) delta = -1;
                else if (dist(x, y, w * 0.86f, ry) < h * 0.07f) delta = 1;
                if (delta == 0) return;
                if (i == 0) save.musicVol = Math.max(0, Math.min(10, save.musicVol + delta));
                else save.sfxVol = Math.max(0, Math.min(10, save.sfxVol + delta));
                applyVolumes();
                sfx.play(Sfx.SELECT);
                return;
            }
            if (x < w * 0.48f || x > w * 0.9f) return;
            sfx.play(Sfx.SELECT);
            if (i == 2) {
                save.vibrate = !save.vibrate;
                vibe(60);
            } else if (i == 3) {
                save.btnSize = (save.btnSize + 1) % 3;
                layoutButtons();
            } else {
                if (confirmReset) {
                    save.resetProgress();
                    confirmReset = false;
                    showMsg("Progres dihapus");
                } else {
                    confirmReset = true;
                }
            }
            return;
        }
        if (Math.abs(y - h * 0.9f) < h * 0.06f && Math.abs(x - w * 0.5f) < w * 0.15f) closeSettings();
    }

    private void pauseBtnRect(int i, RectF out) {
        float bw = w * 0.36f, bh = h * 0.11f;
        float y = h * (0.42f + i * 0.14f);
        out.set(w * 0.5f - bw / 2, y - bh / 2, w * 0.5f + bw / 2, y + bh / 2);
    }

    private void pauseTap(float x, float y) {
        for (int i = 0; i < 3; i++) {
            pauseBtnRect(i, uiRect);
            if (!uiRect.contains(x, y)) continue;
            sfx.play(Sfx.SELECT);
            if (i == 0) state = ST_PLAY;
            else if (i == 1) openSettings(ST_PAUSE);
            else if (custom) enterEditor();
            else enterMap();
            return;
        }
    }

    private void editorAction(int act) {
        switch (act) {
            case Editor.ACT_BACK:
                sfx.play(Sfx.SELECT);
                editor.saveSlot();
                enterMap();
                break;
            case Editor.ACT_PLAY: {
                Level.Def def = editor.toDef();
                if (def != null) {
                    editor.saveSlot();
                    sfx.play(Sfx.SELECT);
                    startDef(def, -1, false, true);
                } else {
                    sfx.play(Sfx.HURT);
                }
                break;
            }
            case Editor.ACT_COPY: {
                final String text = editor.serialize();
                post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("Bandi Rush", text));
                        } catch (Exception ignored) {
                        }
                    }
                });
                editor.showMsg("Level disalin ke clipboard - bagikan ke temanmu!");
                sfx.play(Sfx.SELECT);
                break;
            }
            case Editor.ACT_PASTE:
                post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = cm.getPrimaryClip();
                            if (clip != null && clip.getItemCount() > 0) {
                                CharSequence cs = clip.getItemAt(0).coerceToText(getContext());
                                pendingPaste = cs == null ? "" : cs.toString();
                            } else {
                                pendingPaste = "";
                            }
                        } catch (Exception e) {
                            pendingPaste = "";
                        }
                    }
                });
                sfx.play(Sfx.SELECT);
                break;
            default:
                break;
        }
    }

    // ---------------- toko ----------------

    private void shopCardRect(int i, RectF out) {
        int cols = 5;
        float m = w * 0.02f;
        float top = h * 0.18f;
        float cw = (w - m * (cols + 1)) / cols;
        float chh = h * 0.34f;
        int col = i % cols, row = i / cols;
        float x = m + col * (cw + m), y = top + row * (chh + h * 0.03f);
        out.set(x, y, x + cw, y + chh);
    }

    private boolean isEquipped(Shop.Item it) {
        return (it.kind == Shop.KIND_CHAR && save.selChar == it.id)
                || (it.kind == Shop.KIND_COSTUME && save.selCostume == it.id);
    }

    private void shopTap(float x, float y) {
        for (int i = 0; i < Shop.ITEMS.length; i++) {
            shopCardRect(i, uiRect);
            if (!uiRect.contains(x, y)) continue;
            Shop.Item it = Shop.ITEMS[i];
            shopSel = i;
            boolean wearable = it.kind == Shop.KIND_CHAR || it.kind == Shop.KIND_COSTUME;
            if (wearable && save.owns(i)) {
                if (it.kind == Shop.KIND_CHAR) save.selChar = it.id;
                else save.selCostume = it.id;
                sfx.play(Sfx.SELECT);
                showMsg("Dipakai: " + it.name);
            } else if (it.kind == Shop.KIND_MASK && save.startMasks >= Shop.MAX_START_MASKS) {
                sfx.play(Sfx.HURT);
                showMsg("Topeng awal sudah penuh (" + Shop.MAX_START_MASKS + ")");
            } else if (save.bank < it.price) {
                sfx.play(Sfx.HURT);
                showMsg("Wumpa kurang! Butuh " + it.price);
            } else {
                save.bank -= it.price;
                sfx.play(Sfx.LIFE);
                showMsg("Dibeli: " + it.name);
                switch (it.kind) {
                    case Shop.KIND_CHAR:
                        save.owned |= 1 << i;
                        save.selChar = it.id;
                        break;
                    case Shop.KIND_COSTUME:
                        save.owned |= 1 << i;
                        save.selCostume = it.id;
                        save.addStat(Save.ST_COSTUMES, 1);
                        break;
                    case Shop.KIND_MASK:
                        save.startMasks++;
                        break;
                    default:
                        lives++;
                        break;
                }
            }
            skin = Shop.skin(save.selChar, save.selCostume);
            save.flush();
            checkAchievements();
            return;
        }
    }

    // =====================================================================
    // Dunia: musuh, pijakan, sumbu TNT, peti bertumpuk
    // =====================================================================

    private void updateWorld(float dt) {
        float hw = level.halfWidth();
        ArrayList<Entity> list = level.entities;
        for (int i = 0, n = list.size(); i < n; i++) {
            Entity e = list.get(i);
            if (!e.alive) continue;
            e.t += dt;
            if (e.squash > 0) e.squash -= dt;
            switch (e.type) {
                case Entity.PLATFORM: {
                    float amp = hw - PLAT_HX - 0.05f;
                    e.prevX = e.x;
                    e.x = (float) Math.sin(e.t * 1.25f + e.phase) * amp;
                    break;
                }
                case Entity.CRAB:
                case Entity.HOG:
                    if (e.dieMode == Entity.DIE_KNOCK) {
                        e.x += e.vx * dt;
                        e.z += e.vz * dt;
                        e.vy -= GRAVITY * dt;
                        e.y += e.vy * dt;
                        e.dieT += dt;
                        if (e.dieT > 1.3f) e.alive = false;
                    } else if (e.dieMode == Entity.DIE_SQUASH) {
                        e.dieT += dt;
                        if (e.dieT > 0.6f) e.alive = false;
                    } else if (e.type == Entity.CRAB) {
                        e.prevX = e.x;
                        e.x = (float) Math.sin(e.t * 1.5f + e.phase) * level.patrolAmp(0.45f);
                    } else {
                        e.z = e.baseZ + (float) Math.sin(e.t * 1.3f + e.phase) * 1.4f;
                    }
                    break;
                case Entity.TNT:
                case Entity.NITRO:
                    if (e.fuse >= 0f) {
                        int before = (int) Math.ceil(e.fuse);
                        e.fuse -= dt;
                        if (e.type == Entity.TNT && (int) Math.ceil(e.fuse) != before && e.fuse > 0f) {
                            sfx.play(Sfx.TICK);
                        }
                        if (e.fuse <= 0f) explode(e);
                    }
                    break;
                default:
                    break;
            }
            // peti bertumpuk jatuh kalau peti di bawahnya hancur
            if (e.alive && e.isCrate() && e.y > 0.001f) {
                float support = 0f;
                for (int j = 0; j < n; j++) {
                    Entity o = list.get(j);
                    if (o == e || !o.alive || !o.isCrate() || o.y >= e.y - 0.1f) continue;
                    if (Math.abs(o.x - e.x) < 0.1f && Math.abs(o.z - e.z) < 0.1f) {
                        support = Math.max(support, o.y + CS);
                    }
                }
                if (e.y > support + 0.001f) {
                    e.vy -= GRAVITY * dt;
                    e.y += e.vy * dt;
                    if (e.y <= support) {
                        e.y = support;
                        e.vy = 0f;
                    }
                }
            }
        }
    }

    // ---------------- batu raksasa ----------------

    private void updateBoulder(float dt) {
        if (level.mode != Level.MODE_CHASE) return;
        boulderT += dt;
        if (boulderT < BOULDER_DELAY) return;
        if (boulderT - dt < BOULDER_DELAY) {
            sfx.play(Sfx.RUMBLE);
            vibe(150);
        }
        boulder.z += BOULDER_SPEED * dt;
        float front = boulder.z + BOULDER_R * 0.6f;
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive || e.z > front) continue;
            if (e.type == Entity.PLATFORM || e.type == Entity.GOAL) continue;
            e.alive = false;
            if (e.isCrate()) {
                for (int k = 0; k < 5; k++) {
                    Particle q = spawn(Particle.CHUNK, e.x, e.y + 0.4f, e.z);
                    q.vx = rnd(3f);
                    q.vy = 3f + rng.nextFloat() * 3f;
                    q.vz = 2f + rng.nextFloat() * 3f;
                    q.size = 0.12f;
                    q.color = 0xFFC07A3A;
                    q.life = q.maxLife = 0.7f;
                }
            }
        }
        if (p.z - boulder.z < 1.5f + PR) {
            vibe(250);
            killPlayer(false);
        }
    }

    // ---------------- bos ----------------

    void updateBoss(float dt) {
        if (level.mode != Level.MODE_BOSS) return;
        Boss b = boss;
        b.t += dt;
        b.anim += dt;
        if (b.flash > 0f) b.flash -= dt;
        float hw = level.halfWidth() - 1.1f;
        float rage = Boss.MAX_HP - b.hp;
        switch (b.state) {
            case Boss.INTRO:
                if (b.t > 2f) setBossState(Boss.STRAFE);
                break;
            case Boss.STRAFE: {
                float tx = (float) Math.sin(b.t * (0.8f + 0.25f * rage)) * hw;
                b.x += Math.max(-3f * dt, Math.min(3f * dt, tx - b.x));
                b.throwT -= dt;
                if (b.throwT <= 0f) {
                    throwBomb();
                    b.throwsLeft--;
                    b.throwT = 1.5f - 0.3f * rage;
                    if (b.throwsLeft <= 0) setBossState(Boss.WINDUP);
                }
                break;
            }
            case Boss.WINDUP:
                b.x += Math.max(-2f * dt, Math.min(2f * dt, p.x - b.x));
                if (b.t > 0.9f) {
                    setBossState(Boss.CHARGE);
                    sfx.play(Sfx.ROAR);
                }
                break;
            case Boss.CHARGE:
                b.z -= (7f + rage) * dt;
                b.x += Math.max(-1.2f * dt, Math.min(1.2f * dt, p.x - b.x));
                if (b.z <= 1.6f) {
                    b.z = 1.6f;
                    setBossState(Boss.STUNNED);
                    sfx.play(Sfx.BOOM);
                    vibe(120);
                    burst(b.x, 0.5f, b.z - 1f, 0xFFFFFFFF, 16, 4f);
                }
                break;
            case Boss.STUNNED:
                if (b.t > 2.6f - 0.3f * rage) setBossState(Boss.RETREAT);
                break;
            case Boss.RETREAT:
                b.z += 4.5f * dt;
                if (b.z >= b.homeZ) {
                    b.z = b.homeZ;
                    setBossState(Boss.STRAFE);
                }
                break;
            case Boss.DEFEATED:
                if (rng.nextFloat() < dt * 8f) {
                    burst(b.x + rnd(1f), 0.5f + rng.nextFloat() * 1.5f, b.z + rnd(0.8f), 0xFFFF6D00, 10, 4f);
                    sfx.play(Sfx.BOOM);
                }
                if (b.t > 2.6f && state == ST_PLAY) completeLevel();
                return;
            default:
                break;
        }
        if (b.state == Boss.INTRO || state != ST_PLAY) return;

        // kontak dengan Bandi
        float dx = p.x - b.x, dz = p.z - b.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (b.state == Boss.STUNNED) {
            if (d < 1.4f && p.vy < 0f && p.y > 0.6f) {
                hitBoss();
                bounce(BOUNCE_V + 1f);
            } else if ((p.spinT > 0f && d < 1.9f && p.y < 1.5f) || (p.slideT > 0f && d < 1.6f)) {
                hitBoss();
            }
        } else if (d < 1.15f && p.y < 1.3f) {
            hurtPlayer();
        }
    }

    private void setBossState(int s) {
        boss.state = s;
        boss.t = 0f;
        if (s == Boss.STRAFE) {
            boss.throwT = 1.0f;
            boss.throwsLeft = 3;
        }
    }

    private void hitBoss() {
        boss.hp--;
        boss.flash = 0.6f;
        sfx.play(Sfx.STOMP);
        sfx.play(Sfx.ROAR);
        vibe(100);
        burst(boss.x, 1.2f, boss.z, 0xFFFFEB3B, 14, 4f);
        if (boss.hp <= 0) {
            setBossState(Boss.DEFEATED);
            bombs.clear();
            showMsg("RAJA KEPITING KALAH!");
        } else {
            setBossState(Boss.RETREAT);
        }
    }

    private void throwBomb() {
        Entity bm = new Entity(Entity.BOMB, boss.x, 2f, boss.z);
        float hw = level.halfWidth() - 0.5f;
        bm.baseX = boss.x;
        bm.baseZ = boss.z;
        bm.vx = Math.max(-hw, Math.min(hw, p.x + p.vx * 0.4f + rnd(0.3f)));
        bm.vz = Math.max(1f, Math.min(boss.z - 1.5f, p.z + p.vz * 0.4f));
        bm.fuse = 1.15f;
        bm.t = 0f;
        bombs.add(bm);
        sfx.play(Sfx.THROW);
    }

    private void updateBombs(float dt) {
        for (int i = bombs.size() - 1; i >= 0; i--) {
            Entity bm = bombs.get(i);
            bm.t += dt;
            float k = Math.min(1f, bm.t / bm.fuse);
            bm.x = bm.baseX + (bm.vx - bm.baseX) * k;
            bm.z = bm.baseZ + (bm.vz - bm.baseZ) * k;
            bm.y = 2f * (1f - k) + 10f * k * (1f - k);
            if (k >= 1f) {
                bombs.remove(i);
                sfx.play(Sfx.BOOM);
                Particle ring = spawn(Particle.RING, bm.x, 0.4f, bm.z);
                ring.size = BOMB_R + 0.3f;
                ring.color = 0xFFFF7043;
                ring.life = ring.maxLife = 0.4f;
                burst(bm.x, 0.4f, bm.z, 0xFFFF6D00, 14, 5f);
                float dx = p.x - bm.x, dz = p.z - bm.z;
                if (dx * dx + dz * dz < BOMB_R * BOMB_R && p.y < 1.5f) {
                    vibe(120);
                    hurtPlayer();
                    if (state != ST_PLAY) return;
                }
            }
        }
    }

    // =====================================================================
    // Bandi: gerak, lompat, spin, luncur, tabrakan
    // =====================================================================

    private float inputX() {
        float x = joyX + padX;
        if (kLeft) x -= 1f;
        if (kRight) x += 1f;
        return x;
    }

    private float inputZ() {
        float z = joyZ - padY;
        if (kUp) z += 1f;
        if (kDown) z -= 1f;
        return z;
    }

    private float playerH() {
        return p.slideT > 0f ? SLIDE_H : PLAYER_H;
    }

    private boolean underBarrier() {
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive || e.type != Entity.BARRIER) continue;
            if (Math.abs(p.x - e.x) < 0.5f + PR && Math.abs(p.z - e.z) < BAR_HZ + PR && p.y < BAR_Y0) return true;
        }
        return false;
    }

    private void updatePlayer(float dt, boolean jumpNow, boolean spinNow, boolean slideNow) {
        // kamera terbalik (level kejar-kejaran) juga membalik arah joystick
        float ix = inputX() * camDir, iz = inputZ() * camDir;
        float mag = (float) Math.sqrt(ix * ix + iz * iz);
        if (mag > 1f) {
            ix /= mag;
            iz /= mag;
            mag = 1f;
        }
        boolean held = touchJumpHeld || kJump;
        boolean riding = level.mode == Level.MODE_RIDE;
        if (riding) {
            // babi berlari sendiri: hanya bisa geser kiri-kanan dan lompat
            slideNow = false;
            spinNow = false;
        }

        // mulai meluncur
        if (slideNow && p.grounded && p.slideT <= 0f && p.slideCd <= 0f && p.spinT <= 0f) {
            float fx = (float) Math.sin(p.facing), fz = (float) Math.cos(p.facing);
            if (mag > 0.2f) {
                fx = ix / mag;
                fz = iz / mag;
                p.facing = (float) Math.atan2(fx, fz);
            }
            p.slideDirX = fx;
            p.slideDirZ = fz;
            p.slideT = SLIDE_TIME;
            p.slideCd = SLIDE_CD;
            sfx.play(Sfx.SLIDE);
        }
        p.slideCd -= dt;

        if (p.slideT > 0f) {
            p.slideT -= dt;
            if (p.slideT <= 0f && underBarrier()) p.slideT = 0.02f; // jangan berdiri di bawah palang
            float k = Math.max(0f, p.slideT) / SLIDE_TIME;
            float spd = SLIDE_SPEED * (0.55f + 0.45f * k);
            p.vx = p.slideDirX * spd;
            p.vz = p.slideDirZ * spd;
            if (rng.nextFloat() < 0.4f) {
                Particle q = spawn(Particle.SPARK, p.x + rnd(0.2f), 0.05f, p.z + rnd(0.2f));
                q.vx = -p.vx * 0.1f + rnd(0.5f);
                q.vy = 1f + rng.nextFloat();
                q.vz = -p.vz * 0.1f + rnd(0.5f);
                q.size = 0.08f;
                q.color = 0xCCE0D0B0;
                q.life = q.maxLife = 0.35f;
            }
        } else if (riding) {
            p.vx += (ix * RIDE_SIDE_SPEED - p.vx) * Math.min(1f, 10f * dt);
            p.vz += (RIDE_SPEED - p.vz) * Math.min(1f, 4f * dt);
        } else {
            float target = runSpeed * (p.longJump ? LONG_JUMP_FACTOR : 1f);
            float acc = p.grounded ? 14f : (p.longJump ? 2.5f : 7f);
            float k = Math.min(1f, acc * dt);
            p.vx += (ix * target - p.vx) * k;
            p.vz += (iz * target - p.vz) * k;
        }
        float sp = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        p.moving = sp > 0.6f;
        if (riding) p.facing = 0f;
        else if (p.moving && p.slideT <= 0f) p.facing = (float) Math.atan2(p.vx, p.vz);
        p.runPhase += sp * dt * 3.4f;

        if (jumpNow && !p.jumping && (p.grounded || p.airT < 0.12f) && !underBarrier()) {
            if (p.slideT > 0f) {
                p.longJump = true; // luncur + lompat = lompat jauh
                p.slideT = 0f;
            }
            p.vy = jumpV;
            p.grounded = false;
            p.jumping = true;
            p.standingOn = null;
            p.airT = 1f;
            save.addStat(Save.ST_JUMPS, 1);
            sfx.play(Sfx.JUMP);
        }
        if (p.jumping && !held && p.vy > 3f) p.vy = 3f; // lompatan pendek kalau tombol cepat dilepas

        if (spinNow && p.spinCd <= 0f && p.slideT <= 0f) {
            p.spinT = SPIN_TIME;
            p.spinCd = SPIN_COOLDOWN;
            sfx.play(Sfx.SPIN);
            if (!p.grounded && !p.usedAirSpin) {
                // spin di udara memberi sedikit dorongan ke atas (lompat ganda), sekali per lompatan
                p.usedAirSpin = true;
                if (p.vy < AIR_SPIN_V) p.vy = AIR_SPIN_V;
                p.jumping = false;
            }
        }
        p.spinT -= dt;
        p.spinCd -= dt;
        p.invuln -= dt;

        p.vy -= GRAVITY * dt;
        if (p.vy < -22f) p.vy = -22f;

        float carry = 0f;
        if (p.standingOn != null && p.standingOn.type == Entity.PLATFORM) {
            carry = p.standingOn.x - p.standingOn.prevX;
        }
        moveX(p.vx * dt + carry);
        if (state != ST_PLAY) return;
        moveZ(p.vz * dt);
        if (state != ST_PLAY) return;
        moveY(dt);
        if (state != ST_PLAY) return;

        if (p.grounded) {
            p.airT = 0f;
            p.jumping = false;
            p.longJump = false;
            p.usedAirSpin = false;
        } else {
            p.airT += dt;
            if (p.slideT > 0f) {
                // meluncur keluar dari tepi: momentum tetap terbawa
                p.slideT = 0f;
                p.longJump = true;
            }
        }
        computeShadow();
    }

    /** Mengisi sHX/sHZ/sY0/sY1 untuk objek padat. */
    private boolean solidBounds(Entity e) {
        if (!e.alive) return false;
        if (e.isCrate()) {
            sHX = CH;
            sHZ = CH;
            sY0 = e.y;
            sY1 = e.y + CS;
            return true;
        }
        if (e.type == Entity.BARRIER) {
            sHX = 0.5f;
            sHZ = BAR_HZ;
            sY0 = BAR_Y0;
            sY1 = BAR_Y1;
            return true;
        }
        return false;
    }

    private boolean blocksSide(Entity e) {
        return solidBounds(e) && p.y < sY1 - 0.05f && p.y + playerH() > sY0;
    }

    private void moveX(float dx) {
        float hw = level.halfWidth() - PR;
        float nx = Math.max(-hw, Math.min(hw, p.x + dx));
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!blocksSide(e)) continue;
            if (Math.abs(p.z - e.z) >= sHZ + PR) continue;
            if (Math.abs(nx - e.x) < sHX + PR) {
                if (e.type == Entity.NITRO) {
                    explode(e);
                    continue;
                }
                nx = p.x < e.x ? e.x - sHX - PR : e.x + sHX + PR;
                p.vx = 0f;
            }
        }
        p.x = nx;
    }

    private void moveZ(float dz) {
        float nz = Math.max(0.3f, p.z + dz);
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!blocksSide(e)) continue;
            if (Math.abs(p.x - e.x) >= sHX + PR) continue;
            if (Math.abs(nz - e.z) < sHZ + PR) {
                if (e.type == Entity.NITRO || (e.type == Entity.TNT && level.mode == Level.MODE_RIDE)) {
                    explode(e);
                    continue;
                }
                if (level.mode == Level.MODE_RIDE && p.z < e.z) {
                    // babi menerjang peti kayu, tapi menabrak peti besi / palang = celaka
                    if (e.isBreakable()) {
                        breakCrate(e);
                        continue;
                    }
                    killPlayer(false);
                    return;
                }
                nz = p.z < e.z ? e.z - sHZ - PR : e.z + sHZ + PR;
                p.vz = 0f;
            }
        }
        p.z = nz;
    }

    private void moveY(float dt) {
        float ny = p.y + p.vy * dt;
        float ph = playerH();
        int n = level.entities.size();

        if (p.vy > 0f) {
            // kepala terbentur bagian bawah peti / palang
            for (int i = 0; i < n; i++) {
                Entity e = level.entities.get(i);
                if (!solidBounds(e)) continue;
                if (Math.abs(p.x - e.x) >= sHX + PR - 0.08f || Math.abs(p.z - e.z) >= sHZ + PR - 0.08f) continue;
                if (p.y + ph <= sY0 + 0.001f && ny + ph > sY0) {
                    ny = sY0 - ph;
                    p.vy = 0f;
                    if (e.isBreakable() && !e.isExplosive()) breakCrate(e);
                    else if (e.type == Entity.EXCL) activateExcl(e);
                    else if (e.type == Entity.NITRO) explode(e);
                    break;
                }
            }
        }

        boolean found = false;
        float bestTop = -1e9f;
        Entity best = null;
        if (p.vy <= 0f) {
            if (level.groundUnder(p.x, p.z) && 0f <= p.y + 0.001f && 0f >= ny) {
                found = true;
                bestTop = 0f;
            }
            for (int i = 0; i < n; i++) {
                Entity e = level.entities.get(i);
                if (!e.alive) continue;
                float top;
                if (solidBounds(e)) {
                    if (Math.abs(p.x - e.x) >= sHX + 0.12f || Math.abs(p.z - e.z) >= sHZ + 0.12f) continue;
                    top = sY1;
                } else if (e.type == Entity.PLATFORM) {
                    if (Math.abs(p.x - e.x) >= PLAT_HX + 0.12f || Math.abs(p.z - e.z) >= PLAT_HZ + 0.1f) continue;
                    top = 0f;
                } else {
                    continue;
                }
                if (top <= p.y + 0.001f && top >= ny && top > bestTop) {
                    found = true;
                    bestTop = top;
                    best = e;
                }
            }
        }
        if (found) {
            p.y = bestTop;
            p.vy = 0f;
            p.grounded = true;
            p.standingOn = best;
            if (best != null && best.isCrate()) landOnCrate(best);
        } else {
            p.y = ny;
            p.grounded = false;
            p.standingOn = null;
        }
    }

    private void computeShadow() {
        float best = -1e9f;
        if (level.groundUnder(p.x, p.z) && p.y >= -0.01f) best = 0f;
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive) continue;
            float top;
            if (solidBounds(e)) {
                if (Math.abs(p.x - e.x) >= sHX || Math.abs(p.z - e.z) >= sHZ) continue;
                top = sY1;
            } else if (e.type == Entity.PLATFORM) {
                if (Math.abs(p.x - e.x) >= PLAT_HX || Math.abs(p.z - e.z) >= PLAT_HZ) continue;
                top = 0f;
            } else {
                continue;
            }
            if (top <= p.y + 0.01f && top > best) best = top;
        }
        p.hasShadow = best > -1e8f;
        p.shadowY = p.hasShadow ? best : 0f;
    }

    private void bounce(float v) {
        boolean held = touchJumpHeld || kJump;
        p.vy = held ? v * 1.3f : v;
        p.grounded = false;
        p.standingOn = null;
        p.jumping = false;
        p.usedAirSpin = false;
        p.y += 0.01f;
    }

    private void landOnCrate(Entity e) {
        switch (e.type) {
            case Entity.IRON:
                break;
            case Entity.EXCL:
                if (e.hits == 0) {
                    activateExcl(e);
                    bounce(BOUNCE_V);
                }
                break;
            case Entity.BOUNCE:
                bounce(SUPER_BOUNCE_V);
                e.squash = 0.2f;
                if (e.hits < 5) {
                    e.hits++;
                    addWumpa(1);
                }
                sfx.play(Sfx.BOUNCE);
                break;
            case Entity.TNT:
                if (e.fuse < 0f) {
                    e.fuse = 3f;
                    sfx.play(Sfx.TICK);
                }
                bounce(BOUNCE_V + 1.5f);
                break;
            case Entity.NITRO:
                explode(e);
                break;
            default:
                breakCrate(e);
                bounce(BOUNCE_V);
                break;
        }
    }

    private void activateExcl(Entity e) {
        if (e.hits > 0) return;
        e.hits = 1;
        e.squash = 0.2f;
        sfx.play(Sfx.POP);
        showMsg("PETI MUNCUL!");
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity o = level.entities.get(i);
            if (!o.alive || o.type != Entity.OUTLINE) continue;
            o.type = o.outlineTo;
            burst(o.x, 0.45f, o.z, 0xFFFFFFFF, 6, 2f);
        }
    }

    // =====================================================================
    // Interaksi: spin, luncur, musuh, wumpa
    // =====================================================================

    private void interactions() {
        boolean spinning = p.spinT > 0f;
        boolean sliding = p.slideT > 0f || level.mode == Level.MODE_RIDE; // babi tunggangan menerjang musuh
        for (int i = 0, n = level.entities.size(); i < n && state == ST_PLAY; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive) continue;
            float dx = e.x - p.x, dz = e.z - p.z;
            float d2 = dx * dx + dz * dz;

            if (e.type == Entity.PAD) {
                if (!inBonus && curDef.bonus != null && p.grounded && Math.abs(dx) < 0.45f && Math.abs(dz) < 0.45f) {
                    enterBonus(e);
                    return;
                }
            } else if (e.type == Entity.WUMPA) {
                float wy = e.y + 0.45f;
                if (Math.abs(dx) < 0.55f && Math.abs(dz) < 0.55f && wy > p.y - 0.1f && wy < p.y + 1.25f) {
                    e.alive = false;
                    addWumpa(1);
                    sfx.play(Sfx.WUMPA);
                    burst(e.x, wy, e.z, 0xFFFFB300, 6, 2.5f);
                }
            } else if (e.isEnemy()) {
                if (e.dieMode != Entity.DIE_NONE) continue;
                if (((spinning && d2 < 1.1f * 1.1f) || (sliding && d2 < 0.9f * 0.9f)) && p.y < 1.1f) {
                    knockEnemy(e, dx, dz);
                } else if (d2 < 0.6f * 0.6f && p.y < 0.8f) {
                    if (p.vy < -1f && p.y > 0.15f) {
                        e.dieMode = Entity.DIE_SQUASH;
                        e.dieT = 0f;
                        save.addStat(Save.ST_ENEMIES, 1);
                        bounce(BOUNCE_V + 1f);
                        sfx.play(Sfx.STOMP);
                    } else {
                        hurtPlayer();
                    }
                }
            } else if (e.isCrate()) {
                boolean hit = (spinning && d2 < 1.0f && Math.abs(e.y - p.y) < 0.85f)
                        || (sliding && d2 < 0.85f * 0.85f && Math.abs(e.y - p.y) < 0.5f);
                if (!hit) continue;
                if (e.isExplosive()) explode(e);
                else if (e.type == Entity.EXCL) activateExcl(e);
                else if (e.type != Entity.IRON) breakCrate(e);
            }
        }
    }

    private void knockEnemy(Entity e, float dx, float dz) {
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01f) {
            dx = 0f;
            dz = 1f;
            d = 1f;
        }
        e.dieMode = Entity.DIE_KNOCK;
        e.dieT = 0f;
        save.addStat(Save.ST_ENEMIES, 1);
        e.vx = dx / d * 7f;
        e.vz = dz / d * 7f + 3f * camDir;
        e.vy = 7f;
        sfx.play(Sfx.STOMP);
    }

    private void addWumpa(int n) {
        wumpa += n;
        bankGain += n;
        save.addStat(Save.ST_WUMPA, n);
        while (wumpa >= 100) {
            wumpa -= 100;
            lives++;
            showMsg("NYAWA +1");
            sfx.play(Sfx.LIFE);
        }
    }

    private void showMsg(String s) {
        msg = s;
        msgT = 1.6f;
    }

    private void breakCrate(Entity e) {
        if (!e.alive) return;
        e.alive = false;
        broken++;
        save.addStat(Save.ST_CRATES, 1);
        switch (e.type) {
            case Entity.QCRATE:
                addWumpa(5);
                sfx.play(Sfx.WUMPA);
                break;
            case Entity.LIFE:
                lives++;
                showMsg("NYAWA +1");
                sfx.play(Sfx.LIFE);
                break;
            case Entity.AKU:
                if (p.mask < 2) p.mask++;
                showMsg("TOPENG PELINDUNG!");
                sfx.play(Sfx.LIFE);
                break;
            case Entity.CHECK:
                if (!ttMode && !inBonus) {
                    checkX = e.x;
                    checkZ = e.z;
                }
                showMsg("CHECKPOINT!");
                sfx.play(Sfx.CHECK);
                break;
            default:
                addWumpa(1);
                break;
        }
        sfx.play(Sfx.BREAK);
        for (int i = 0; i < 12; i++) {
            Particle q = spawn(Particle.CHUNK, e.x + rnd(0.4f), e.y + 0.45f + rnd(0.4f), e.z + rnd(0.4f));
            q.vx = rnd(4f);
            q.vy = 3f + rng.nextFloat() * 5f;
            q.vz = rnd(4f);
            q.size = 0.1f + rng.nextFloat() * 0.12f;
            q.color = rng.nextBoolean() ? 0xFFC07A3A : 0xFF8A5324;
            q.life = q.maxLife = 0.9f;
        }
    }

    private void explode(Entity e) {
        if (!e.alive) return;
        e.alive = false;
        broken++;
        save.addStat(Save.ST_CRATES, 1);
        sfx.play(Sfx.BOOM);
        Particle ring = spawn(Particle.RING, e.x, e.y + 0.45f, e.z);
        ring.size = EXPLODE_R + 0.4f;
        ring.color = e.type == Entity.NITRO ? 0xFF76FF03 : 0xFFFFA000;
        ring.life = ring.maxLife = 0.45f;
        burst(e.x, e.y + 0.45f, e.z, e.type == Entity.NITRO ? 0xFF64DD17 : 0xFFFF6D00, 22, 7f);

        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity o = level.entities.get(i);
            if (!o.alive || o == e) continue;
            float dx = o.x - e.x, dz = o.z - e.z;
            if (dx * dx + dz * dz > EXPLODE_R * EXPLODE_R) continue;
            if (o.isExplosive()) {
                if (o.fuse < 0f || o.fuse > 0.2f) o.fuse = 0.2f;
            } else if (o.isBreakable()) {
                breakCrate(o);
            } else if (o.isEnemy() && o.dieMode == Entity.DIE_NONE) {
                knockEnemy(o, dx, dz);
            }
        }
        float pdx = p.x - e.x, pdz = p.z - e.z;
        float pd2 = pdx * pdx + pdz * pdz;
        if (pd2 < 16f) vibe(80);
        if (state == ST_PLAY && pd2 < EXPLODE_R * EXPLODE_R && Math.abs(p.y - e.y) < 1.8f) {
            hurtPlayer();
        }
    }

    private void hurtPlayer() {
        if (state != ST_PLAY || p.invuln > 0f) return;
        if (p.mask > 0) {
            p.mask--;
            p.invuln = 1.6f;
            sfx.play(Sfx.HURT);
            vibe(80);
            burst(p.x, p.y + 0.9f, p.z, 0xFFFFD54F, 10, 3f);
        } else {
            killPlayer(false);
        }
    }

    private void killPlayer(boolean fall) {
        state = ST_DYING;
        stateT = 0f;
        dieFall = fall;
        p.spinT = 0f;
        p.slideT = 0f;
        diedThisLevel = true;
        save.addStat(Save.ST_DEATHS, 1);
        sfx.play(Sfx.DIE);
        vibe(200);
        if (!fall) burst(p.x, p.y + 0.5f, p.z, 0xFFFFFFFF, 14, 3f);
    }

    // =====================================================================
    // Partikel
    // =====================================================================

    private float rnd(float a) {
        return (rng.nextFloat() * 2f - 1f) * a;
    }

    private Particle spawn(int kind, float x, float y, float z) {
        Particle q = new Particle();
        q.kind = kind;
        q.x = x;
        q.y = y;
        q.z = z;
        particles.add(q);
        return q;
    }

    private void burst(float x, float y, float z, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Particle q = spawn(Particle.SPARK, x, y, z);
            q.vx = rnd(speed);
            q.vy = rnd(speed) + speed * 0.4f;
            q.vz = rnd(speed);
            q.size = 0.06f + rng.nextFloat() * 0.08f;
            q.color = color;
            q.life = q.maxLife = 0.5f + rng.nextFloat() * 0.4f;
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            q.life -= dt;
            if (q.life <= 0f) {
                particles.remove(i);
                continue;
            }
            if (q.kind != Particle.RING) {
                q.vy -= GRAVITY * 0.6f * dt;
                q.x += q.vx * dt;
                q.y += q.vy * dt;
                q.z += q.vz * dt;
            }
        }
    }

    // =====================================================================
    // Proyeksi 3D
    // =====================================================================

    private boolean project(float x, float y, float z) {
        float dx = (x - camX) * camDir, dy = y - camY, dz = (z - camZ) * camDir;
        float zc = dz * cosP - dy * sinP;
        if (zc < NEAR) return false;
        float yc = dy * cosP + dz * sinP;
        float inv = focal / zc;
        pX = cx + dx * inv;
        pY = cy - yc * inv;
        pZc = zc;
        pS = inv;
        return true;
    }

    private float fogAmt(float zc) {
        float f = (zc - FOG_START) / (FOG_END - FOG_START);
        return f <= 0f ? 0f : Math.min(1f, f);
    }

    private int fog(int color, float zc) {
        float f = fogAmt(zc);
        if (f <= 0f) return color;
        int fc = level.theme.fog;
        int r = (int) (Color.red(color) + (Color.red(fc) - Color.red(color)) * f);
        int g = (int) (Color.green(color) + (Color.green(fc) - Color.green(color)) * f);
        int b = (int) (Color.blue(color) + (Color.blue(fc) - Color.blue(color)) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int shade(int color, float k) {
        int r = Math.min(255, (int) (Color.red(color) * k));
        int g = Math.min(255, (int) (Color.green(color) * k));
        int b = Math.min(255, (int) (Color.blue(color) * k));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Menggambar segi empat 3D. Titik layar disimpan di qx/qy untuk dekorasi. */
    private boolean quad(float x0, float y0, float z0, float x1, float y1, float z1,
                         float x2, float y2, float z2, float x3, float y3, float z3, int color) {
        if (!project(x0, y0, z0)) return false;
        qx[0] = pX;
        qy[0] = pY;
        float zs = pZc;
        if (!project(x1, y1, z1)) return false;
        qx[1] = pX;
        qy[1] = pY;
        zs += pZc;
        if (!project(x2, y2, z2)) return false;
        qx[2] = pX;
        qy[2] = pY;
        zs += pZc;
        if (!project(x3, y3, z3)) return false;
        qx[3] = pX;
        qy[3] = pY;
        zs += pZc;
        qz = zs * 0.25f;
        path.reset();
        path.moveTo(qx[0], qy[0]);
        path.lineTo(qx[1], qy[1]);
        path.lineTo(qx[2], qy[2]);
        path.lineTo(qx[3], qy[3]);
        path.close();
        solid.setColor(fog(color, qz));
        cv.drawPath(path, solid);
        return true;
    }

    // =====================================================================
    // Render
    // =====================================================================

    private void render(Canvas c) {
        cv = c;
        ui.cv = c;
        focal = h * 0.95f;
        cx = w * 0.5f;
        cy = h * 0.40f;
        sprites.fogColor = level.theme.fog;

        if (state == ST_EDITOR) {
            editor.draw(c, w, h, time);
        } else {
            drawSky();
            drawWorld();
            drawParticles();
            drawHud();
        }
        drawToast();
        cv = null;
    }

    private void drawToast() {
        if (toasts.isEmpty() || toastT <= 0f) return;
        float u = h * 0.01f;
        float slide = Math.min(1f, Math.min(3f - toastT, toastT) * 4f);
        float top = -u * 14 + slide * u * 16;
        rect.set(w * 0.3f, top, w * 0.7f, top + u * 12);
        fill.setColor(0xE6202020);
        cv.drawRoundRect(rect, u * 3, u * 3, fill);
        stroke.setColor(0xFFFFC107);
        stroke.setStrokeWidth(u * 0.5f);
        cv.drawRoundRect(rect, u * 3, u * 3, stroke);
        ui.trophy(rect.left + u * 7, rect.centerY(), u * 3.5f, true);
        outlined("PENCAPAIAN TERBUKA!", rect.left + u * 13, top + u * 5, u * 3.6f, 0xFFFFC107, Paint.Align.LEFT);
        outlined(toasts.get(0), rect.left + u * 13, top + u * 9.8f, u * 4.6f, 0xFFFFFFFF, Paint.Align.LEFT);
    }

    private void drawSky() {
        Theme th = level.theme;
        float horizon = cy - focal * (sinP / cosP);
        if (skyShader == null || skyTheme != th || skyH != (int) horizon) {
            skyShader = new LinearGradient(0, 0, 0, Math.max(1f, horizon), th.sky1, th.sky2, Shader.TileMode.CLAMP);
            skyTheme = th;
            skyH = (int) horizon;
        }
        fill.setShader(skyShader);
        cv.drawRect(0, 0, w, horizon + 2, fill);
        fill.setShader(null);
        fill.setColor(th.fog);
        cv.drawRect(0, horizon, w, h, fill);

        if (th.night) {
            fill.setColor(0xCCFFFFFF);
            for (int i = 0; i < 40; i++) {
                float sx = ((i * 97 + 13) % 101) / 101f * w;
                float sy = ((i * 53 + 7) % 89) / 89f * horizon * 0.85f;
                float tw = 0.6f + 0.4f * (float) Math.sin(time * 2f + i);
                cv.drawCircle(sx, sy, h * 0.003f * (1 + (i % 3)) * tw, fill);
            }
            fill.setColor(0xFFFFF8E1);
            cv.drawCircle(w * 0.8f, horizon * 0.3f, h * 0.06f, fill);
            fill.setColor(th.sky1);
            cv.drawCircle(w * 0.8f + h * 0.025f, horizon * 0.3f - h * 0.01f, h * 0.052f, fill);
        } else {
            fill.setColor(0xFFFFF59D);
            cv.drawCircle(w * 0.18f, horizon * 0.28f, h * 0.06f, fill);
            fill.setColor(0xB0FFFFFF);
            for (int i = 0; i < 4; i++) {
                float sx = ((i * 0.31f + time * 0.008f + camX * 0.01f) % 1.2f) * w * 1.1f - w * 0.1f;
                float sy = horizon * (0.18f + 0.13f * i);
                float r = h * (0.035f + 0.01f * i);
                cv.drawCircle(sx, sy, r, fill);
                cv.drawCircle(sx + r * 1.1f, sy + r * 0.2f, r * 0.8f, fill);
                cv.drawCircle(sx - r * 1.1f, sy + r * 0.25f, r * 0.7f, fill);
            }
        }

        float off = (camX * camDir * h * 0.08f) + camZ * h * 0.01f;
        drawHills(horizon, off * 0.5f, 0.16f, shade(th.hill, 1.12f), 0.004f);
        drawHills(horizon, off, 0.1f, th.hill, 0.0075f);
    }

    private void drawHills(float horizon, float off, float amp, int color, float freq) {
        path.reset();
        path.moveTo(0, horizon + 2);
        for (int i = 0; i <= 48; i++) {
            float sx = i * w / 48f;
            float u = (sx + off) / (h * 0.01f);
            float v = (float) (Math.sin(u * freq * 3.1) * 0.45 + Math.sin(u * freq * 7.3 + 1.3) * 0.25 + 0.75);
            path.lineTo(sx, horizon - v * h * amp);
        }
        path.lineTo(w, horizon + 2);
        path.close();
        fill.setColor(color);
        cv.drawPath(path, fill);
    }

    private void drawWorld() {
        int rFirst, rLast;
        if (camDir > 0) {
            rFirst = Math.min((int) camZ + VIEW_ROWS, level.rows + 6);
            rLast = (int) Math.floor(camZ) + 1;
        } else {
            rFirst = Math.max((int) Math.floor(camZ) - VIEW_ROWS, -8);
            rLast = (int) Math.ceil(camZ) - 2;
        }

        drawList.clear();
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive) continue;
            float v = (e.z - camZ) * camDir;
            if (v < 0.8f || v > VIEW_ROWS + 1) continue;
            e.key = e.z * camDir;
            drawList.add(e);
        }
        for (int i = 0, n = bombs.size(); i < n; i++) {
            Entity bm = bombs.get(i);
            bm.key = bm.z * camDir;
            drawList.add(bm);
        }
        if (level.mode == Level.MODE_CHASE && state != ST_TITLE) {
            boulder.key = (boulder.z - 1.5f * camDir) * camDir;
            drawList.add(boulder);
        }
        if (level.mode == Level.MODE_BOSS && !(boss.state == Boss.DEFEATED && boss.t > 2.4f)) {
            boss.proxy.key = (boss.z - 0.9f * camDir) * camDir;
            drawList.add(boss.proxy);
        }
        Collections.sort(drawList, byKeyDesc);

        float playerKey = p.z * camDir;
        if (p.standingOn != null) playerKey = Math.min(playerKey, p.standingOn.z * camDir - 0.01f);
        if (p.y > 0.2f) playerKey = Math.min(playerKey, p.z * camDir - 0.55f);
        boolean playerDrawn = state == ST_DYING && dieFall && p.y < -3.5f;

        int idx = 0, n = drawList.size();
        int step = camDir > 0 ? -1 : 1;
        for (int r = rFirst; camDir > 0 ? r >= rLast : r <= rLast; r += step) {
            drawRow(r);
            float thr = camDir > 0 ? r : -(r + 1);
            while (true) {
                float ek = idx < n ? drawList.get(idx).key : -1e9f;
                float pk = playerDrawn ? -1e9f : playerKey;
                if (ek < thr && pk < thr) break;
                if (pk > ek) {
                    drawPlayer();
                    playerDrawn = true;
                } else {
                    drawEntity(drawList.get(idx++));
                }
            }
        }
        while (idx < n) drawEntity(drawList.get(idx++));
        if (!playerDrawn) drawPlayer();
    }

    private void drawRow(int r) {
        Theme th = level.theme;
        float z0 = r, z1 = r + 1;
        float hw = level.halfWidth();
        int wd = level.width;

        // dasar jurang
        for (int c = 0; c < wd; c++) {
            if (level.groundAt(r, c)) continue;
            float x0 = level.colX(c) - 0.5f, x1 = x0 + 1f;
            quad(x0, PIT_Y, z0, x1, PIT_Y, z0, x1, PIT_Y, z1, x0, PIT_Y, z1, th.abyss);
        }

        // dinding kiri & kanan
        int wc = (r & 1) == 0 ? th.wallA : th.wallB;
        quad(-hw, PIT_Y, z0, -hw, WALL_H, z0, -hw, WALL_H, z1, -hw, PIT_Y, z1, shade(wc, camDir > 0 ? 0.9f : 0.8f));
        quad(-hw - 6f, WALL_H, z0, -hw, WALL_H, z0, -hw, WALL_H, z1, -hw - 6f, WALL_H, z1, th.wallTop);
        quad(hw, PIT_Y, z0, hw, WALL_H, z0, hw, WALL_H, z1, hw, PIT_Y, z1, shade(wc, camDir > 0 ? 0.8f : 0.9f));
        quad(hw, WALL_H, z0, hw + 6f, WALL_H, z0, hw + 6f, WALL_H, z1, hw, WALL_H, z1, th.wallTop);

        // tanah: sisi tebing, muka tebing yang menghadap kamera, lalu permukaan
        int nearRow = camDir > 0 ? r - 1 : r + 1;
        float zf = camDir > 0 ? z0 : z1;
        for (int c = 0; c < wd; c++) {
            if (!level.groundAt(r, c)) continue;
            float x0 = level.colX(c) - 0.5f, x1 = x0 + 1f;
            if (c + 1 < wd && !level.groundAt(r, c + 1) && camX > x1) {
                quad(x1, 0, z0, x1, 0, z1, x1, PIT_Y, z1, x1, PIT_Y, z0, shade(th.dirt, 0.8f));
            }
            if (c > 0 && !level.groundAt(r, c - 1) && camX < x0) {
                quad(x0, 0, z1, x0, 0, z0, x0, PIT_Y, z0, x0, PIT_Y, z1, shade(th.dirt, 0.8f));
            }
            if (!level.groundAt(nearRow, c)) {
                quad(x0, 0, zf, x1, 0, zf, x1, PIT_Y, zf, x0, PIT_Y, zf, th.dirt);
                quad(x0, 0.001f, zf, x1, 0.001f, zf, x1, -0.18f, zf, x0, -0.18f, zf, shade(th.groundEdge, 0.85f));
            }
            int col;
            if (th.hasTrees() && (c == 0 || c == wd - 1)) {
                col = ((r + c) & 1) == 0 ? th.groundEdge : shade(th.groundEdge, 0.92f);
            } else {
                col = ((r + c) & 1) == 0 ? th.groundA : th.groundB;
            }
            quad(x0, 0, z0, x1, 0, z0, x1, 0, z1, x0, 0, z1, col);
        }

        // dekorasi di atas dinding
        int hsh = (r * 7919) & 0xFF;
        if (th.decor == Theme.DECOR_TORCH) {
            if ((r & 3) == 0) {
                drawTorch(-hw - 0.4f, r + 0.5f);
                drawTorch(hw + 0.4f, r + 0.5f);
            }
        } else {
            drawBush(-hw - 0.1f, r + 0.5f, 0.55f + (hsh % 5) * 0.05f, hsh);
            drawBush(hw + 0.1f, r + 0.5f, 0.55f + ((hsh >> 3) % 5) * 0.05f, hsh + 3);
            if (hsh % 3 == 0) drawTree(-hw - 1.3f - (hsh % 2), r + 0.5f, 3.2f + (hsh % 4) * 0.4f, hsh);
            if ((hsh >> 2) % 3 == 0) drawTree(hw + 1.3f + ((hsh >> 4) % 2), r + 0.5f, 3.0f + (hsh % 3) * 0.5f, hsh + 7);
        }
    }

    private void drawBush(float x, float z, float size, int seed) {
        if (!project(x, WALL_H, z)) return;
        float s = pS;
        int base = level.theme.decor == Theme.DECOR_PINE ? 0xFFF4F8FB : level.theme.leaf;
        fill.setColor(fog(shade(base, 0.9f + (seed % 3) * 0.05f), pZc));
        cv.drawCircle(pX, pY, size * s, fill);
        cv.drawCircle(pX + size * 0.6f * s, pY + size * 0.15f * s, size * 0.7f * s, fill);
        cv.drawCircle(pX - size * 0.6f * s, pY + size * 0.2f * s, size * 0.65f * s, fill);
    }

    private void drawTree(float x, float z, float hgt, int seed) {
        if (!project(x, WALL_H, z)) return;
        float bx = pX, by = pY, s = pS, zc = pZc;
        if (!project(x, WALL_H + hgt, z)) return;
        float tx = pX, ty = pY;
        if (level.theme.decor == Theme.DECOR_PINE) {
            stroke.setColor(fog(level.theme.trunk, zc));
            stroke.setStrokeWidth(0.2f * s);
            cv.drawLine(bx, by, bx, by - 0.6f * s, stroke);
            for (int k = 0; k < 3; k++) {
                float ly = by - (0.4f + k * 0.8f) * s;
                float lw = (1.0f - k * 0.25f) * s;
                path.reset();
                path.moveTo(bx - lw, ly);
                path.lineTo(bx + lw, ly);
                path.lineTo(bx, ly - 1.3f * s);
                path.close();
                fill.setColor(fog(level.theme.leaf, zc));
                cv.drawPath(path, fill);
                path.reset();
                path.moveTo(bx - lw * 0.45f, ly - 0.75f * s);
                path.lineTo(bx + lw * 0.45f, ly - 0.75f * s);
                path.lineTo(bx, ly - 1.3f * s);
                path.close();
                fill.setColor(fog(0xFFFFFFFF, zc));
                cv.drawPath(path, fill);
            }
            return;
        }
        stroke.setColor(fog(level.theme.trunk, zc));
        stroke.setStrokeWidth(0.25f * s);
        cv.drawLine(bx, by, tx, ty, stroke);
        fill.setColor(fog(level.theme.leaf, zc));
        for (int k = 0; k < 6; k++) {
            float ang = k * 60f + (seed % 30) + (float) Math.sin(time * 1.2f + seed) * 4f;
            cv.save();
            cv.rotate(ang, tx, ty);
            rect.set(tx, ty - 0.2f * s, tx + 1.4f * s, ty + 0.2f * s);
            cv.drawOval(rect, fill);
            cv.restore();
        }
        fill.setColor(fog(0xFF6D4C1D, zc));
        cv.drawCircle(tx, ty + 0.12f * s, 0.14f * s, fill);
    }

    private void drawTorch(float x, float z) {
        if (!project(x, WALL_H, z)) return;
        float bx = pX, by = pY, s = pS, zc = pZc;
        stroke.setColor(fog(0xFF5D5470, zc));
        stroke.setStrokeWidth(0.35f * s);
        cv.drawLine(bx, by, bx, by - 1.0f * s, stroke);
        float fl = 1f + 0.15f * (float) Math.sin(time * 18f + z);
        fill.setColor(0x55FF9800);
        cv.drawCircle(bx, by - 1.25f * s, 0.55f * s * fl, fill);
        fill.setColor(0xFFFF9800);
        cv.drawCircle(bx, by - 1.2f * s, 0.22f * s * fl, fill);
        fill.setColor(0xFFFFEB3B);
        cv.drawCircle(bx, by - 1.17f * s, 0.11f * s * fl, fill);
    }

    // ---------------- entitas ----------------

    private void drawEntity(Entity e) {
        switch (e.type) {
            case Entity.WUMPA: drawWumpa(e); break;
            case Entity.CRAB:
            case Entity.HOG: drawEnemy(e); break;
            case Entity.PLATFORM: drawPlatform(e); break;
            case Entity.GOAL: drawGoal(e); break;
            case Entity.OUTLINE: drawOutline(e); break;
            case Entity.BARRIER: drawBarrier(e); break;
            case Entity.BOULDER: drawBoulder(); break;
            case Entity.BOSS: drawBoss(); break;
            case Entity.BOMB: drawBomb(e); break;
            case Entity.PAD: drawPad(e); break;
            default:
                if (e.isCrate()) drawCrate(e);
                break;
        }
    }

    private void drawShadow(float x, float y, float z, float radius) {
        drawShadowColor(x, y, z, radius, 0x55000000);
    }

    private void drawShadowColor(float x, float y, float z, float radius, int color) {
        if (!project(x, y + 0.01f, z)) return;
        float r = radius * pS;
        rect.set(pX - r, pY - r * 0.4f, pX + r, pY + r * 0.4f);
        fill.setColor(color);
        cv.drawOval(rect, fill);
    }

    /** Menggambar balok; menyimpan titik muka depan & atas untuk dekorasi. */
    private final float[] frontX = new float[4], frontY = new float[4], topX = new float[4], topY = new float[4];
    private boolean frontOk, topOk;
    private float boxZc;

    private void drawBox(float x0, float x1, float y0, float y1, float z0, float z1,
                         int cFront, int cTop, int cSide) {
        frontOk = topOk = false;
        if (camX < x0) quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, cSide);
        if (camX > x1) quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, cSide);
        boolean top = camY > y1 && (camDir > 0
                ? quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, cTop)
                : quad(x1, y1, z1, x0, y1, z1, x0, y1, z0, x1, y1, z0, cTop));
        if (top) {
            topOk = true;
            System.arraycopy(qx, 0, topX, 0, 4);
            System.arraycopy(qy, 0, topY, 0, 4);
        }
        boolean front = camDir > 0
                ? quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, cFront)
                : quad(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, cFront);
        if (front) {
            frontOk = true;
            boxZc = qz;
            System.arraycopy(qx, 0, frontX, 0, 4);
            System.arraycopy(qy, 0, frontY, 0, 4);
        }
    }

    private void insetPath(float[] xs, float[] ys, float k) {
        float mx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25f;
        float my = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25f;
        path.reset();
        for (int i = 0; i < 4; i++) {
            float x = xs[i] + (mx - xs[i]) * k, y = ys[i] + (my - ys[i]) * k;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    private void drawCrate(Entity e) {
        float hop = 0f;
        if (e.type == Entity.NITRO) hop = Math.max(0f, (float) Math.sin(e.t * 5f + e.phase)) * 0.12f;
        float sq = e.squash > 0 ? e.squash * 0.8f : 0f;
        float x0 = e.x - CH, x1 = e.x + CH, y0 = e.y + hop, y1 = y0 + CS * (1f - sq), z0 = e.z - CH, z1 = e.z + CH;

        int base;
        int border = 0xFF6B3E1A;
        switch (e.type) {
            case Entity.TNT:
                base = 0xFFF4511E;
                if (e.fuse >= 0f && Math.sin(time * 25f) > 0) base = 0xFFFFF3E0;
                border = 0xFF5D1A00;
                break;
            case Entity.NITRO:
                base = 0xFF43A047;
                border = 0xFF1B5E20;
                break;
            case Entity.IRON:
                base = 0xFF90A4AE;
                border = 0xFF546E7A;
                break;
            case Entity.EXCL:
                base = e.hits > 0 ? 0xFF78909C : 0xFFCFD8DC;
                border = 0xFF455A64;
                break;
            case Entity.CHECK:
                base = 0xFFD6A15E;
                break;
            default:
                base = 0xFFC07A3A;
                break;
        }
        if (e.y <= 0.01f && level.groundUnder(e.x, e.z)) drawShadow(e.x, 0f, e.z, 0.55f);
        drawBox(x0, x1, y0, y1, z0, z1, base, shade(base, 1.18f), shade(base, 0.72f));

        stroke.setColor(fog(border, boxZc));
        if (topOk) {
            float th = Math.abs(topY[0] - topY[3]) + Math.abs(topX[1] - topX[0]) * 0.1f;
            stroke.setStrokeWidth(Math.max(1f, th * 0.1f));
            insetPath(topX, topY, 0.14f);
            cv.drawPath(path, stroke);
        }
        if (!frontOk) return;
        float fw = Math.abs(frontX[1] - frontX[0]);
        float fh = Math.abs(frontY[0] - frontY[3]);
        float mx = (frontX[0] + frontX[1] + frontX[2] + frontX[3]) * 0.25f;
        float my = (frontY[0] + frontY[1] + frontY[2] + frontY[3]) * 0.25f;
        stroke.setStrokeWidth(Math.max(1f, fw * 0.07f));
        insetPath(frontX, frontY, 0.14f);
        cv.drawPath(path, stroke);

        String label = null;
        int labelColor = 0xFF3E2210;
        switch (e.type) {
            case Entity.CRATE:
                cv.drawLine(frontX[0] + fw * 0.1f, frontY[0] - fh * 0.1f, frontX[2] - fw * 0.1f, frontY[2] + fh * 0.1f, stroke);
                cv.drawLine(frontX[3] + fw * 0.1f, frontY[3] + fh * 0.1f, frontX[1] - fw * 0.1f, frontY[1] - fh * 0.1f, stroke);
                break;
            case Entity.QCRATE: label = "?"; break;
            case Entity.LIFE: label = "1UP"; labelColor = 0xFFB71C1C; break;
            case Entity.AKU: label = "A"; labelColor = 0xFF6A1B9A; break;
            case Entity.CHECK: label = "C"; labelColor = 0xFF0D47A1; break;
            case Entity.EXCL: label = "!"; labelColor = e.hits > 0 ? 0xFF455A64 : 0xFFD32F2F; break;
            case Entity.TNT:
                label = e.fuse >= 0f ? String.valueOf(Math.max(1, (int) Math.ceil(e.fuse))) : "TNT";
                labelColor = 0xFF1A0A00;
                break;
            case Entity.NITRO: label = "!"; labelColor = 0xFFCCFF90; break;
            case Entity.BOUNCE: {
                fill.setColor(fog(0xFF3E2210, boxZc));
                for (int k = 0; k < 2; k++) {
                    float oy = my - fh * 0.05f + k * fh * 0.22f;
                    path.reset();
                    path.moveTo(mx, oy - fh * 0.2f);
                    path.lineTo(mx + fw * 0.22f, oy + fh * 0.05f);
                    path.lineTo(mx - fw * 0.22f, oy + fh * 0.05f);
                    path.close();
                    cv.drawPath(path, fill);
                }
                break;
            }
            case Entity.IRON: {
                fill.setColor(fog(0xFFCFD8DC, boxZc));
                float r = fw * 0.06f;
                cv.drawCircle(mx - fw * 0.3f, my - fh * 0.3f, r, fill);
                cv.drawCircle(mx + fw * 0.3f, my - fh * 0.3f, r, fill);
                cv.drawCircle(mx - fw * 0.3f, my + fh * 0.3f, r, fill);
                cv.drawCircle(mx + fw * 0.3f, my + fh * 0.3f, r, fill);
                break;
            }
            default:
                break;
        }
        if (label != null) {
            float ts = fh * (label.length() > 1 ? 0.36f : 0.6f);
            text.setTextSize(ts);
            text.setStyle(Paint.Style.FILL);
            text.setColor(fog(labelColor, boxZc));
            cv.drawText(label, mx, my + ts * 0.36f, text);
        }
    }

    /** Peti bergaris: hanya rangka. */
    private void drawOutline(Entity e) {
        float x0 = e.x - CH, x1 = e.x + CH, y0 = e.y, y1 = e.y + CS, z0 = e.z - CH, z1 = e.z + CH;
        float s = 0f;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float z = (i & 4) == 0 ? z0 : z1;
            if (!project(x, y, z)) return;
            wx[i] = pX;
            wy[i] = pY;
            s = pS;
        }
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 4f + e.phase);
        int a = (int) (pulse * 200);
        stroke.setColor((a << 24) | (e.outlineTo == Entity.IRON ? 0xB3E5FC : 0xFFFFFF));
        stroke.setStrokeWidth(Math.max(1.5f, s * 0.04f));
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                int j = i | bit;
                if (j != i) cv.drawLine(wx[i], wy[i], wx[j], wy[j], stroke);
            }
        }
    }

    private void drawBarrier(Entity e) {
        int base = shade(level.theme.wallA, 1.05f);
        drawBox(e.x - 0.5f, e.x + 0.5f, BAR_Y0, BAR_Y1, e.z - BAR_HZ, e.z + BAR_HZ,
                base, level.theme.wallTop, shade(base, 0.75f));
        if (!frontOk) return;
        // balok kayu di bagian bawah + panah "meluncur"
        float fh = Math.abs(frontY[0] - frontY[3]);
        fill.setColor(fog(0xFF6D4C41, boxZc));
        path.reset();
        path.moveTo(frontX[0], frontY[0]);
        path.lineTo(frontX[1], frontY[1]);
        path.lineTo(frontX[1], frontY[1] - fh * 0.22f);
        path.lineTo(frontX[0], frontY[0] - fh * 0.22f);
        path.close();
        cv.drawPath(path, fill);
        float mx = (frontX[0] + frontX[1]) * 0.5f;
        float ay = frontY[0] - fh * 0.5f;
        float aw = Math.abs(frontX[1] - frontX[0]) * 0.18f;
        fill.setColor(fog(0xFFFFD600, boxZc));
        path.reset();
        path.moveTo(mx - aw, ay - aw * 0.6f);
        path.lineTo(mx + aw, ay - aw * 0.6f);
        path.lineTo(mx, ay + aw * 0.6f);
        path.close();
        cv.drawPath(path, fill);
    }

    /** Pintu area bonus: petak bercahaya dengan tanda tanya melayang. */
    private void drawPad(Entity e) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 5f);
        int glow = Color.argb(255, 64 + (int) (pulse * 100), 200 + (int) (pulse * 55), 255);
        quad(e.x - 0.45f, 0.02f, e.z - 0.45f, e.x + 0.45f, 0.02f, e.z - 0.45f,
                e.x + 0.45f, 0.02f, e.z + 0.45f, e.x - 0.45f, 0.02f, e.z + 0.45f, glow);
        quad(e.x - 0.28f, 0.03f, e.z - 0.28f, e.x + 0.28f, 0.03f, e.z - 0.28f,
                e.x + 0.28f, 0.03f, e.z + 0.28f, e.x - 0.28f, 0.03f, e.z + 0.28f, 0xFFFFFFFF);
        float bob = (float) Math.sin(time * 3f) * 0.1f;
        if (!project(e.x, 1.1f + bob, e.z)) return;
        outlined("?", pX, pY + pS * 0.2f, pS * 0.6f, 0xFF80DEEA, Paint.Align.CENTER);
        outlined("BONUS", pX, pY + pS * 0.45f, pS * 0.2f, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    private void drawWumpa(Entity e) {
        float bob = (float) Math.sin(time * 3f + e.phase) * 0.08f;
        float wy = e.y + 0.45f + bob;
        if (e.y < 0.1f) drawShadow(e.x, 0f, e.z, 0.2f);
        if (!project(e.x, wy, e.z)) return;
        float s = pS, r = 0.2f * s;
        float squeeze = 0.55f + 0.45f * Math.abs((float) Math.cos(time * 2.5f + e.phase));
        cv.save();
        cv.translate(pX, pY);
        cv.scale(squeeze, 1f);
        fill.setColor(fog(0xFFFF6F00, pZc));
        cv.drawCircle(0, 0, r, fill);
        fill.setColor(fog(0xFFFFA726, pZc));
        cv.drawCircle(-r * 0.25f, -r * 0.25f, r * 0.55f, fill);
        fill.setColor(0xAAFFFFFF);
        cv.drawCircle(-r * 0.35f, -r * 0.4f, r * 0.2f, fill);
        cv.restore();
        stroke.setColor(fog(0xFF33691E, pZc));
        stroke.setStrokeWidth(Math.max(1f, r * 0.22f));
        cv.drawLine(pX, pY - r * 0.9f, pX + r * 0.35f, pY - r * 1.4f, stroke);
    }

    private void drawPlatform(Entity e) {
        drawBox(e.x - PLAT_HX, e.x + PLAT_HX, -0.3f, 0f, e.z - PLAT_HZ, e.z + PLAT_HZ,
                0xFF6D4C41, 0xFFA1887F, 0xFF5D4037);
        if (topOk) {
            stroke.setColor(fog(0xFF5D4037, boxZc));
            stroke.setStrokeWidth(Math.max(1f, Math.abs(topX[1] - topX[0]) * 0.02f));
            for (int k = 1; k < 4; k++) {
                float t = k / 4f;
                cv.drawLine(topX[0] + (topX[1] - topX[0]) * t, topY[0] + (topY[1] - topY[0]) * t,
                        topX[3] + (topX[2] - topX[3]) * t, topY[3] + (topY[2] - topY[3]) * t, stroke);
            }
        }
    }

    private void drawGoal(Entity e) {
        drawBox(e.x - 0.5f, e.x + 0.5f, 0f, 0.3f, e.z - 0.5f, e.z + 0.5f, 0xFF9575CD, 0xFFB39DDB, 0xFF7E57C2);
        float bob = (float) Math.sin(time * 2f) * 0.12f;
        if (!project(e.x, 1.3f + bob, e.z)) return;
        float s = pS;
        fill.setColor(0x44FF80AB);
        cv.drawCircle(pX, pY, 0.75f * s * (1f + 0.1f * (float) Math.sin(time * 4f)), fill);
        float sq = 0.3f + 0.7f * Math.abs((float) Math.cos(time * 1.8f));
        int col = Color.HSVToColor(new float[]{(time * 60f) % 360f, 0.55f, 1f});
        cv.save();
        cv.translate(pX, pY);
        cv.scale(sq, 1f);
        path.reset();
        path.moveTo(0, -0.45f * s);
        path.lineTo(0.3f * s, -0.1f * s);
        path.lineTo(0, 0.45f * s);
        path.lineTo(-0.3f * s, -0.1f * s);
        path.close();
        fill.setColor(col);
        cv.drawPath(path, fill);
        fill.setColor(0x88FFFFFF);
        path.reset();
        path.moveTo(0, -0.45f * s);
        path.lineTo(0.3f * s, -0.1f * s);
        path.lineTo(0, -0.02f * s);
        path.close();
        cv.drawPath(path, fill);
        cv.restore();
    }

    private void drawEnemy(Entity e) {
        drawShadow(e.x, 0f, e.z, 0.45f);
        if (!project(e.x, e.y, e.z)) return;
        sprites.fogAmt = fogAmt(pZc);
        cv.save();
        cv.translate(pX, pY);
        cv.scale(pS, pS);
        if (e.dieMode == Entity.DIE_KNOCK) cv.rotate(e.dieT * 720f, 0, -0.3f);
        if (e.dieMode == Entity.DIE_SQUASH) cv.scale(1.3f, 0.3f);
        if (e.type == Entity.CRAB) sprites.crab(cv, e.t, false, false);
        else sprites.hog(cv, e.t);
        cv.restore();
    }

    private void drawBoss() {
        Boss b = boss;
        drawShadow(b.x, 0f, b.z, 1.2f);
        if (!project(b.x, 0f, b.z)) return;
        sprites.fogAmt = fogAmt(pZc);
        cv.save();
        cv.translate(pX, pY);
        float s = pS * 2.4f;
        cv.scale(s, s);
        if (b.state == Boss.WINDUP) cv.translate((float) Math.sin(time * 70f) * 0.03f, 0f);
        if (b.state == Boss.DEFEATED) cv.scale(1f + b.t * 0.1f, Math.max(0.1f, 1f - b.t / 2.6f));
        boolean flash = b.flash > 0f && ((int) (time * 20f) & 1) == 0;
        sprites.crab(cv, b.state == Boss.CHARGE ? b.anim * 2.5f : b.anim, true, flash);
        if (b.state == Boss.STUNNED) sprites.stars(cv, time, -1.05f);
        cv.restore();
    }

    private void drawBomb(Entity bm) {
        float k = Math.min(1f, bm.t / bm.fuse);
        int a = (int) (60 + 120 * k);
        drawShadowColor(bm.vx, 0f, bm.vz, BOMB_R * (0.4f + 0.6f * k), (a << 24) | 0xFF1744);
        if (!project(bm.x, bm.y, bm.z)) return;
        float r = 0.28f * pS;
        fill.setColor(0xFF212121);
        cv.drawCircle(pX, pY, r, fill);
        fill.setColor(0x88FFFFFF);
        cv.drawCircle(pX - r * 0.35f, pY - r * 0.35f, r * 0.25f, fill);
        fill.setColor(((int) (time * 30f) & 1) == 0 ? 0xFFFFEB3B : 0xFFFF6D00);
        cv.drawCircle(pX + r * 0.5f, pY - r * 1.1f, r * 0.3f, fill);
    }

    private void drawBoulder() {
        float z = boulder.z, R = BOULDER_R;
        drawShadow(0f, 0f, z, R * 0.9f);
        if (!project(0f, R, z)) return;
        float r = R * pS, x = pX, y = pY;
        fill.setColor(fog(0xFF5D4E40, pZc));
        cv.drawCircle(x, y, r, fill);
        fill.setColor(fog(0xFF8D7B68, pZc));
        cv.drawCircle(x - r * 0.08f, y - r * 0.08f, r * 0.9f, fill);
        fill.setColor(fog(0xFFA89580, pZc));
        cv.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.3f, fill);
        // retakan yang bergulir ke arah kamera
        stroke.setColor(fog(0xFF4E4034, pZc));
        stroke.setStrokeWidth(r * 0.05f);
        float roll = z / R;
        for (int k = 0; k < 5; k++) {
            double ph = roll + k * 1.2566;
            float sn = (float) Math.sin(ph);
            if (sn <= 0.1f) continue;
            float yy = (float) Math.cos(ph) * r * 0.85f;
            float half = (float) Math.sqrt(Math.max(0f, r * r * 0.72f - yy * yy)) * (0.3f + 0.25f * (k % 3));
            float ox = (k - 2) * r * 0.12f;
            cv.drawLine(x + ox - half, y - yy, x + ox + half * 0.6f, y - yy + r * 0.05f, stroke);
        }
    }

    // ---------------- Bandi ----------------

    private void drawPlayer() {
        if (p.hasShadow && state != ST_DYING) drawShadow(p.x, p.shadowY, p.z, 0.32f);
        boolean blink = p.invuln > 0f && ((int) (time * 16f) & 1) == 0 && state == ST_PLAY;
        if (!blink && project(p.x, p.y, p.z)) {
            float s = pS;
            cv.save();
            cv.translate(pX, pY);
            cv.scale(s, s);
            if (state == ST_DYING && !dieFall) {
                cv.translate(0, -stateT * 0.8f);
                cv.rotate(stateT * 420f, 0, -0.5f);
            }
            boolean riding = level.mode == Level.MODE_RIDE && state != ST_TITLE;
            if (riding) {
                cv.save();
                cv.scale(1.4f, 1.4f);
                sprites.hogBack(cv, time);
                cv.restore();
                cv.translate(0f, -0.62f);
            }
            if (p.spinT > 0f) {
                sprites.tornado(cv, skin, time);
            } else {
                // arah hadap relatif terhadap kamera
                float rel = p.facing + (camDir < 0 ? (float) Math.PI : 0f);
                float fc = (float) Math.cos(rel), fs = (float) Math.sin(rel);
                int view = fc > 0.5f ? Sprites.VIEW_BACK : fc < -0.5f ? Sprites.VIEW_FRONT : Sprites.VIEW_SIDE;
                boolean air = riding || (!p.grounded && state == ST_PLAY);
                sprites.bandi(cv, skin, view, fs < 0f, air, p.moving, p.runPhase, time, p.slideT > 0f);
            }
            cv.restore();
        }
        if (p.mask > 0 && state != ST_DYING) {
            float bob = (float) Math.sin(time * 3f) * 0.07f;
            if (project(p.x - 0.5f * camDir, p.y + 0.95f + bob, p.z - 0.15f * camDir)) {
                cv.save();
                cv.translate(pX, pY);
                cv.scale(pS, pS);
                sprites.mask(cv, p.mask > 1);
                cv.restore();
            }
        }
    }

    private void drawParticles() {
        for (int i = 0, n = particles.size(); i < n; i++) {
            Particle q = particles.get(i);
            if (!project(q.x, q.y, q.z)) continue;
            float a = q.life / q.maxLife;
            switch (q.kind) {
                case Particle.RING: {
                    float r = q.size * (1f - a * 0.7f) * pS;
                    fill.setColor((q.color & 0x00FFFFFF) | ((int) (a * 180) << 24));
                    cv.drawCircle(pX, pY, r, fill);
                    fill.setColor(((int) (a * 220) << 24) | 0xFFFFFF);
                    cv.drawCircle(pX, pY, r * 0.5f, fill);
                    break;
                }
                case Particle.CHUNK: {
                    float r = q.size * pS;
                    fill.setColor(q.color);
                    cv.drawRect(pX - r, pY - r, pX + r, pY + r, fill);
                    break;
                }
                default: {
                    int alpha = (int) (Math.min(1f, a * 1.5f) * ((q.color >>> 24) & 0xFF));
                    fill.setColor((q.color & 0x00FFFFFF) | (alpha << 24));
                    cv.drawCircle(pX, pY, q.size * pS, fill);
                    break;
                }
            }
        }
    }

    // =====================================================================
    // HUD & layar menu
    // =====================================================================

    private void outlined(String s, float x, float y, float size, int color, Paint.Align align) {
        text.setTextAlign(align);
        text.setTextSize(size);
        text.setStyle(Paint.Style.STROKE);
        text.setStrokeWidth(size * 0.14f);
        text.setColor((color & 0xFF000000) | 0x1A1A1A);
        cv.drawText(s, x, y, text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(color);
        cv.drawText(s, x, y, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private static String formatTime(float t) {
        int m = (int) (t / 60f);
        float s = t - m * 60;
        return String.format(Locale.US, "%d:%05.2f", m, s);
    }

    private static int relicColor(int relic) {
        switch (relic) {
            case Save.RELIC_GOLD: return 0xFFFFD700;
            case Save.RELIC_SILVER: return 0xFFE0E0E0;
            case Save.RELIC_BRONZE: return 0xFFCD7F32;
            default: return 0x55FFFFFF;
        }
    }

    private static String relicName(int relic) {
        switch (relic) {
            case Save.RELIC_GOLD: return "RELIK EMAS";
            case Save.RELIC_SILVER: return "RELIK PERAK";
            default: return "RELIK PERUNGGU";
        }
    }

    private void drawRelic(float x, float y, float r, int relic) {
        fill.setColor(relicColor(relic));
        cv.drawCircle(x, y, r, fill);
        stroke.setColor(relic > 0 ? 0x88000000 : 0x55FFFFFF);
        stroke.setStrokeWidth(r * 0.15f);
        cv.drawCircle(x, y, r * 0.62f, stroke);
    }

    private void drawStopwatch(float x, float y, float r, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(r * 0.18f);
        cv.drawCircle(x, y + r * 0.1f, r * 0.75f, stroke);
        cv.drawLine(x, y + r * 0.1f, x, y - r * 0.35f, stroke);
        cv.drawLine(x, y + r * 0.1f, x + r * 0.3f, y + r * 0.2f, stroke);
        cv.drawLine(x, y - r * 0.65f, x, y - r * 0.95f, stroke);
    }

    private void drawButton(RectF r, String label, int color) {
        fill.setColor(color);
        float rr = r.height() * 0.3f;
        cv.drawRoundRect(r, rr, rr, fill);
        stroke.setColor(0xCCFFFFFF);
        stroke.setStrokeWidth(r.height() * 0.05f);
        cv.drawRoundRect(r, rr, rr, stroke);
        outlined(label, r.centerX(), r.centerY() + r.height() * 0.17f, r.height() * 0.45f, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    private void dim() {
        fill.setColor(0x99000000);
        cv.drawRect(0, 0, w, h, fill);
    }

    private void drawHud() {
        switch (state) {
            case ST_TITLE: drawTitle(); return;
            case ST_MAP: drawMap(); return;
            case ST_SETTINGS: drawSettings(); return;
            case ST_SHOP: drawShop(); return;
            case ST_ACH: drawAchievements(); return;
            default: break;
        }
        float u = h * 0.01f;

        // wumpa
        fill.setColor(0xFFFF6F00);
        cv.drawCircle(u * 6, u * 7, u * 3.4f, fill);
        fill.setColor(0xFFFFA726);
        cv.drawCircle(u * 5.2f, u * 6.2f, u * 1.8f, fill);
        outlined(String.valueOf(wumpa), u * 11, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);

        // peti
        if (levelTotal > 0 && level.mode != Level.MODE_BOSS) {
            float bx = u * 30;
            fill.setColor(0xFFC07A3A);
            cv.drawRect(bx - u * 3, u * 4, bx + u * 3, u * 10, fill);
            stroke.setColor(0xFF6B3E1A);
            stroke.setStrokeWidth(u * 0.7f);
            cv.drawRect(bx - u * 2.3f, u * 4.7f, bx + u * 2.3f, u * 9.3f, stroke);
            outlined(broken + "/" + levelTotal, bx + u * 5, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);
            if (inBonus) outlined("AREA BONUS", bx - u * 3, u * 15, u * 4, 0xFF80DEEA, Paint.Align.LEFT);
        }

        // petunjuk tutorial
        if (state == ST_PLAY && !inBonus && tipIdx >= 0) {
            String tip = level.def.tipTexts[tipIdx];
            float a = Math.min(1f, tipT * 3f);
            text.setTextSize(u * 4.4f);
            float tw = text.measureText(tip) + u * 8;
            rect.set(w * 0.5f - tw / 2, h * 0.14f, w * 0.5f + tw / 2, h * 0.14f + u * 8);
            fill.setColor(((int) (a * 200) << 24) | 0x0D2B45);
            cv.drawRoundRect(rect, u * 2, u * 2, fill);
            outlined(tip, w * 0.5f, h * 0.14f + u * 5.5f, u * 4.4f, ((int) (a * 255) << 24) | 0xFFFFFF, Paint.Align.CENTER);
        }

        // nyawa atau stopwatch
        float lx = w - u * 34;
        if (ttMode) {
            drawStopwatch(lx, u * 7, u * 3.5f, 0xFFFFFFFF);
            outlined(formatTime(ttTime), lx + u * 4.5f, u * 9.6f, u * 7, 0xFFFFEB3B, Paint.Align.LEFT);
        } else {
            fill.setColor(0xFFF57C00);
            cv.drawCircle(lx, u * 7, u * 3.2f, fill);
            fill.setColor(0xFFFFE0B2);
            rect.set(lx - u * 2, u * 7.3f, lx + u * 2, u * 9.8f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(lx - u, u * 6.2f, u * 0.6f, fill);
            cv.drawCircle(lx + u, u * 6.2f, u * 0.6f, fill);
            outlined("x" + lives, lx + u * 4.5f, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);
        }

        // bar nyawa bos
        if (level.mode == Level.MODE_BOSS && boss.state != Boss.DEFEATED) {
            outlined("RAJA KEPITING", w * 0.5f, u * 17, u * 5, 0xFFFFCDD2, Paint.Align.CENTER);
            float bw = w * 0.3f, bx0 = w * 0.5f - bw / 2;
            fill.setColor(0x88000000);
            cv.drawRect(bx0 - u * 0.6f, u * 19, bx0 + bw + u * 0.6f, u * 23, fill);
            fill.setColor(0xFFE53935);
            cv.drawRect(bx0, u * 19.6f, bx0 + bw * boss.hp / (float) Boss.MAX_HP, u * 22.4f, fill);
        }

        if (state == ST_PLAY) {
            // tombol pause
            fill.setColor(0x66000000);
            cv.drawCircle(pauseBx, pauseBy, h * 0.05f, fill);
            fill.setColor(0xFFFFFFFF);
            float pw = h * 0.009f, phh = h * 0.02f;
            cv.drawRect(pauseBx - pw * 2.2f, pauseBy - phh, pauseBx - pw * 0.6f, pauseBy + phh, fill);
            cv.drawRect(pauseBx + pw * 0.6f, pauseBy - phh, pauseBx + pw * 2.2f, pauseBy + phh, fill);

            if (bannerT > 0f) {
                float a = Math.min(1f, bannerT);
                int col = ((int) (a * 255) << 24) | 0xFFD54F;
                outlined(level.name + (ttMode ? "  -  TIME TRIAL" : ""), w * 0.5f, h * 0.27f, u * 9, col, Paint.Align.CENTER);
            }
            if (hintT > 0f && level.def.hint != null && tipIdx < 0) {
                float a = Math.min(1f, hintT);
                int col = ((int) (a * 255) << 24) | 0xFFFFFF;
                outlined(level.def.hint, w * 0.5f, h * 0.34f, u * 4.5f, col, Paint.Align.CENTER);
            }
            if (msgT > 0f) {
                float rise = (1.6f - msgT) * u * 4;
                outlined(msg, w * 0.5f, h * 0.42f - rise, u * 7, 0xFFFFF176, Paint.Align.CENTER);
            }
            drawControls();
        }

        switch (state) {
            case ST_PAUSE:
                dim();
                outlined("JEDA", w * 0.5f, h * 0.27f, u * 14, 0xFFFFD54F, Paint.Align.CENTER);
                pauseBtnRect(0, uiRect);
                drawButton(uiRect, "LANJUTKAN", 0xCC2E7D32);
                pauseBtnRect(1, uiRect);
                drawButton(uiRect, "PENGATURAN", 0xCC1565C0);
                pauseBtnRect(2, uiRect);
                drawButton(uiRect, custom ? "KE EDITOR" : "KE PETA", 0xCCC62828);
                break;
            case ST_DONE: {
                dim();
                outlined(curDef.tutorial ? "TUTORIAL SELESAI!" : "LEVEL SELESAI!", w * 0.5f, h * 0.26f, u * 12, 0xFFFFD54F, Paint.Align.CENTER);
                if (custom || curDef.tutorial) {
                    outlined(custom ? "Level buatanmu berhasil ditamatkan!" : "Sekarang kamu siap menjelajahi Pulau Wumpa!",
                            w * 0.5f, h * 0.42f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER);
                    outlined("Peti: " + broken + " / " + levelTotal + "     Wumpa: " + wumpa,
                            w * 0.5f, h * 0.52f, u * 5, 0xFFE0E0E0, Paint.Align.CENTER);
                } else if (ttMode) {
                    outlined("Waktu: " + formatTime(ttTime), w * 0.5f, h * 0.4f, u * 8, 0xFFFFFFFF, Paint.Align.CENTER);
                    float best = save.best(levelIndex);
                    outlined(ttNewRecord ? "REKOR BARU!" : "Rekor: " + formatTime(best),
                            w * 0.5f, h * 0.5f, u * 6, ttNewRecord ? 0xFF76FF03 : 0xFFE0E0E0, Paint.Align.CENTER);
                    drawRelic(w * 0.5f - u * 22, h * 0.6f - u * 2, u * 3.5f, ttRelic);
                    outlined(relicName(ttRelic) + "  (target " + formatTime(level.def.targetTime) + ")",
                            w * 0.5f - u * 16, h * 0.6f, u * 5, relicColor(ttRelic), Paint.Align.LEFT);
                } else {
                    String info = levelTotal > 0 && level.mode != Level.MODE_BOSS
                            ? "Peti: " + broken + " / " + levelTotal + "     Wumpa: " + wumpa
                            : "Wumpa: " + wumpa;
                    outlined(info, w * 0.5f, h * 0.42f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER);
                    if (gemThisLevel) {
                        String g = level.mode == Level.MODE_BOSS ? "Bos dikalahkan!  +1 KRISTAL" : "Semua peti hancur!  +1 KRISTAL";
                        outlined(gemIsNew ? g : "Kristal sudah dimiliki", w * 0.5f, h * 0.53f, u * 6, 0xFFFF80D0, Paint.Align.CENTER);
                    }
                    if (levelIndex + 1 < Levels.ALL.length) {
                        outlined("Level berikutnya terbuka!  Time Trial juga terbuka.", w * 0.5f, h * 0.62f, u * 4.5f, 0xFFB2FF59, Paint.Align.CENTER);
                    }
                }
                if (stateT > 1f && ((int) (time * 2) & 1) == 0) {
                    outlined(custom ? "Ketuk untuk kembali ke editor" : "Ketuk untuk lanjut",
                            w * 0.5f, h * 0.76f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                }
                break;
            }
            case ST_OVER:
                dim();
                outlined("GAME OVER", w * 0.5f, h * 0.42f, u * 16, 0xFFFF5252, Paint.Align.CENTER);
                if (stateT > 1f) outlined("Ketuk untuk kembali ke peta", w * 0.5f, h * 0.6f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                break;
            case ST_WIN: {
                dim();
                int n = Levels.progressCount();
                outlined("TAMAT! KAMU MENANG!", w * 0.5f, h * 0.34f, u * 12, 0xFFFFD54F, Paint.Align.CENTER);
                outlined("Kristal: " + save.totalGems(Levels.ALL) + " / " + n + "     Relik: " + save.totalRelics(Levels.ALL) + " / " + n,
                        w * 0.5f, h * 0.5f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER);
                outlined("Coba Time Trial untuk mengumpulkan relik emas!", w * 0.5f, h * 0.6f, u * 4.5f, 0xFFE0E0E0, Paint.Align.CENTER);
                if (stateT > 1f) outlined("Ketuk untuk kembali ke peta", w * 0.5f, h * 0.72f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                break;
            }
            default:
                break;
        }
    }

    private void drawTitle() {
        float u = h * 0.01f;
        fill.setColor(0x55000000);
        cv.drawRect(0, 0, w, h, fill);
        float wob = (float) Math.sin(time * 2f) * u;
        outlined("BANDI RUSH", w * 0.5f + u * 0.8f, h * 0.3f + wob + u * 0.8f, u * 20, 0xFF7F2A00, Paint.Align.CENTER);
        outlined("BANDI RUSH", w * 0.5f, h * 0.3f + wob, u * 20, 0xFFFF9800, Paint.Align.CENTER);
        outlined("Petualangan di Pulau Wumpa", w * 0.5f, h * 0.42f, u * 6, 0xFFFFF59D, Paint.Align.CENTER);
        if (((int) (time * 2) & 1) == 0) {
            outlined("Ketuk layar untuk mulai", w * 0.5f, h * 0.62f, u * 7, 0xFFFFFFFF, Paint.Align.CENTER);
        }
        outlined("Joystick kiri: gerak  |  X: lompat  |  O: putar  |  ▼: meluncur", w * 0.5f, h * 0.84f, u * 4.2f, 0xFFE0E0E0, Paint.Align.CENTER);
        outlined("Hancurkan semua peti untuk kristal. Kalahkan waktu untuk relik!", w * 0.5f, h * 0.91f, u * 4.2f, 0xFFE0E0E0, Paint.Align.CENTER);
    }

    private void drawCornerButtons() {
        float c = h * 0.08f, r = h * 0.055f;
        fill.setColor(0x88000000);
        cv.drawCircle(c, c, r, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(r * 0.14f);
        cv.drawLine(c + r * 0.35f, c, c - r * 0.35f, c, stroke);
        cv.drawLine(c - r * 0.35f, c, c - r * 0.05f, c - r * 0.3f, stroke);
        cv.drawLine(c - r * 0.35f, c, c - r * 0.05f, c + r * 0.3f, stroke);
        float gx = w - c;
        cv.drawCircle(gx, c, r, fill);
        fill.setColor(0xFFFFFFFF);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            cv.drawCircle(gx + (float) Math.cos(a) * r * 0.45f, c + (float) Math.sin(a) * r * 0.45f, r * 0.13f, fill);
        }
        cv.drawCircle(gx, c, r * 0.4f, fill);
        fill.setColor(0xFF424242);
        cv.drawCircle(gx, c, r * 0.18f, fill);
    }

    private void drawMap() {
        float u = h * 0.01f;
        fill.setColor(0x88000000);
        cv.drawRect(0, 0, w, h, fill);
        outlined("PILIH LEVEL", w * 0.5f, h * 0.115f, u * 8, 0xFFFFD54F, Paint.Align.CENTER);
        drawCornerButtons();
        int pc = Levels.progressCount();
        ui.wumpa(w * 0.66f, h * 0.085f, u * 2.2f);
        outlined(String.valueOf(save.bank), w * 0.68f, h * 0.1f, u * 4.5f, 0xFFFFFFFF, Paint.Align.LEFT);
        outlined("Kristal " + save.totalGems(Levels.ALL) + "/" + pc + "   Relik " + save.totalRelics(Levels.ALL) + "/" + pc,
                h * 0.16f, h * 0.1f, u * 3.8f, 0xFFE0E0E0, Paint.Align.LEFT);
        for (int i = 0; i < MAP_BUTTONS.length; i++) {
            mapButtonRect(i, uiRect);
            drawButton(uiRect, MAP_BUTTONS[i], i == 0 ? 0xCCEF6C00 : i == 1 ? 0xCCF9A825 : 0xCC00838F);
        }

        int n = Levels.ALL.length;
        for (int i = 0; i < n; i++) {
            Level.Def d = Levels.ALL[i];
            cardRect(i, uiRect);
            boolean open = save.isUnlocked(i);
            float rr = uiRect.height() * 0.1f;
            fill.setColor(open ? (d.theme.wallA & 0x00FFFFFF) | 0xE6000000 : 0xE6303030);
            cv.drawRoundRect(uiRect, rr, rr, fill);
            fill.setColor(open ? (d.theme.sky1 & 0x00FFFFFF) | 0x99000000 : 0x55202020);
            rect.set(uiRect.left, uiRect.top, uiRect.right, uiRect.top + uiRect.height() * 0.45f);
            cv.drawRoundRect(rect, rr, rr, fill);
            stroke.setColor(open ? 0xCCFFFFFF : 0x55FFFFFF);
            stroke.setStrokeWidth(u * 0.5f);
            cv.drawRoundRect(uiRect, rr, rr, stroke);

            int dot = d.name.indexOf(". ");
            String num = dot > 0 ? d.name.substring(0, dot) : d.tutorial ? "T" : String.valueOf(i);
            String nm = dot > 0 ? d.name.substring(dot + 2) : d.name;
            float lx = uiRect.left + u * 3;
            outlined(num, lx, uiRect.top + uiRect.height() * 0.36f, uiRect.height() * 0.28f,
                    open ? 0xFFFFFFFF : 0xFF9E9E9E, Paint.Align.LEFT);
            outlined(nm, lx, uiRect.top + uiRect.height() * 0.6f, uiRect.height() * 0.13f,
                    open ? 0xFFFFF59D : 0xFF9E9E9E, Paint.Align.LEFT);
            String tag = d.mode == Level.MODE_BOSS ? "BOS" : d.mode == Level.MODE_CHASE ? "KEJAR"
                    : d.mode == Level.MODE_RIDE ? "TUNGGANG" : d.bonus != null ? "+BONUS" : "";
            if (!tag.isEmpty()) {
                outlined(tag, uiRect.left + uiRect.width() * 0.3f, uiRect.top + uiRect.height() * 0.3f,
                        uiRect.height() * 0.11f, 0xFFFF8A80, Paint.Align.LEFT);
            }

            if (!open) {
                // gembok
                float gx = uiRect.centerX() + uiRect.width() * 0.25f, gy = uiRect.top + uiRect.height() * 0.3f;
                float gs = uiRect.height() * 0.1f;
                stroke.setColor(0xFFBDBDBD);
                stroke.setStrokeWidth(gs * 0.3f);
                rect.set(gx - gs * 0.6f, gy - gs * 1.3f, gx + gs * 0.6f, gy);
                cv.drawArc(rect, 180, 180, false, stroke);
                fill.setColor(0xFFBDBDBD);
                cv.drawRect(gx - gs, gy - gs * 0.5f, gx + gs, gy + gs * 0.9f, fill);
                continue;
            }
            if (d.tutorial) {
                outlined(save.completed(i) ? "SELESAI" : "Mulai di sini!", lx, uiRect.top + uiRect.height() * 0.86f,
                        uiRect.height() * 0.11f, save.completed(i) ? 0xFF76FF03 : 0xFFFFFFFF, Paint.Align.LEFT);
                continue;
            }

            float iy = uiRect.top + uiRect.height() * 0.82f;
            float ir = uiRect.height() * 0.07f;
            // kristal
            float gx = lx + ir;
            fill.setColor(save.gem(i) ? 0xFFFF80D0 : 0x44FFFFFF);
            path.reset();
            path.moveTo(gx, iy - ir * 1.3f);
            path.lineTo(gx + ir * 0.9f, iy);
            path.lineTo(gx, iy + ir * 1.3f);
            path.lineTo(gx - ir * 0.9f, iy);
            path.close();
            cv.drawPath(path, fill);
            // relik & rekor waktu
            drawRelic(gx + ir * 3.2f, iy, ir, save.relic(i));
            float best = save.best(i);
            if (best > 0f) {
                outlined(formatTime(best), gx + ir * 5f, iy + ir * 0.5f, uiRect.height() * 0.1f, 0xFFFFFFFF, Paint.Align.LEFT);
            }
            // tombol Time Trial
            if (save.completed(i)) {
                float r = ttBtnR(uiRect);
                float bx = uiRect.right - r * 1.25f, by = uiRect.top + r * 1.25f;
                fill.setColor(0xCC1565C0);
                cv.drawCircle(bx, by, r, fill);
                drawStopwatch(bx, by, r * 0.6f, 0xFFFFFFFF);
            }
        }
    }

    private void drawShop() {
        float u = h * 0.01f;
        fill.setColor(0xAA000000);
        cv.drawRect(0, 0, w, h, fill);
        ui.backButton(h * 0.08f, h * 0.08f, h * 0.055f);
        outlined("TOKO", w * 0.5f, h * 0.115f, u * 8, 0xFFFFD54F, Paint.Align.CENTER);
        ui.wumpa(w * 0.8f, h * 0.085f, u * 2.5f);
        outlined(String.valueOf(save.bank), w * 0.82f, h * 0.105f, u * 5.5f, 0xFFFFFFFF, Paint.Align.LEFT);

        for (int i = 0; i < Shop.ITEMS.length; i++) {
            Shop.Item it = Shop.ITEMS[i];
            shopCardRect(i, uiRect);
            boolean wearable = it.kind == Shop.KIND_CHAR || it.kind == Shop.KIND_COSTUME;
            boolean owned = wearable && save.owns(i);
            boolean equipped = wearable && isEquipped(it);
            float rr = uiRect.height() * 0.08f;
            fill.setColor(i == shopSel ? 0xE6263238 : 0xCC1C2A33);
            cv.drawRoundRect(uiRect, rr, rr, fill);
            stroke.setColor(equipped ? 0xFFFFC107 : 0x88FFFFFF);
            stroke.setStrokeWidth(equipped ? u * 0.8f : u * 0.4f);
            cv.drawRoundRect(uiRect, rr, rr, stroke);

            // pratinjau
            float pcx = uiRect.centerX(), pby = uiRect.top + uiRect.height() * 0.6f;
            float ps = uiRect.height() * 0.44f;
            cv.save();
            cv.translate(pcx, pby);
            cv.scale(ps, ps);
            if (it.kind == Shop.KIND_CHAR) {
                sprites.bandi(cv, Shop.skin(it.id, save.selCostume), Sprites.VIEW_FRONT, false, false, false, 0f, time, false);
            } else if (it.kind == Shop.KIND_COSTUME) {
                sprites.bandi(cv, Shop.skin(save.selChar, it.id), Sprites.VIEW_FRONT, false, false, false, 0f, time, false);
            } else if (it.kind == Shop.KIND_MASK) {
                cv.translate(0f, -0.5f);
                cv.scale(1.6f, 1.6f);
                sprites.mask(cv, false);
            }
            cv.restore();
            if (it.kind == Shop.KIND_LIFE) {
                fill.setColor(0xFFF57C00);
                cv.drawCircle(pcx, pby - ps * 0.45f, ps * 0.3f, fill);
                fill.setColor(0xFFFFE0B2);
                rect.set(pcx - ps * 0.2f, pby - ps * 0.42f, pcx + ps * 0.2f, pby - ps * 0.22f);
                cv.drawOval(rect, fill);
                outlined("+1", pcx + ps * 0.35f, pby - ps * 0.1f, ps * 0.35f, 0xFF76FF03, Paint.Align.CENTER);
            }

            outlined(it.name, pcx, uiRect.top + uiRect.height() * 0.76f, uiRect.height() * 0.1f, 0xFFFFFFFF, Paint.Align.CENTER);
            float sy = uiRect.top + uiRect.height() * 0.92f;
            float ts = uiRect.height() * 0.09f;
            if (equipped) {
                outlined("DIPAKAI", pcx, sy, ts, 0xFF76FF03, Paint.Align.CENTER);
            } else if (owned) {
                outlined("PAKAI", pcx, sy, ts, 0xFF80DEEA, Paint.Align.CENTER);
            } else {
                String price = String.valueOf(it.price);
                if (it.kind == Shop.KIND_MASK) price += "  (" + save.startMasks + "/" + Shop.MAX_START_MASKS + ")";
                if (it.kind == Shop.KIND_LIFE) price += "  (x" + lives + ")";
                ui.wumpa(pcx - ts * (price.length() * 0.3f + 0.6f), sy - ts * 0.35f, ts * 0.4f);
                outlined(price, pcx + ts * 0.3f, sy, ts, save.bank >= it.price ? 0xFFFFFFFF : 0xFFFF8A80, Paint.Align.CENTER);
            }
        }
        String info = shopSel >= 0 ? Shop.ITEMS[shopSel].desc : "Wumpa yang kamu kumpulkan di level tersimpan di sini. Ketuk barang untuk membeli / memakai.";
        outlined(info, w * 0.5f, h * 0.965f, u * 4, 0xFFE0E0E0, Paint.Align.CENTER);
        if (msgT > 0f) {
            fill.setColor(0xCC000000);
            cv.drawRect(0, h * 0.45f, w, h * 0.55f, fill);
            outlined(msg, w * 0.5f, h * 0.52f, u * 5, 0xFFFFF176, Paint.Align.CENTER);
        }
    }

    private void drawAchievements() {
        float u = h * 0.01f;
        fill.setColor(0xCC000000);
        cv.drawRect(0, 0, w, h, fill);
        ui.backButton(h * 0.08f, h * 0.08f, h * 0.055f);
        int got = 0;
        for (int i = 0; i < Achievements.NAMES.length; i++) if (save.hasAch(i)) got++;
        outlined("PENCAPAIAN  " + got + "/" + Achievements.NAMES.length, w * 0.5f, h * 0.115f, u * 7, 0xFFFFD54F, Paint.Align.CENTER);

        int cols = 3;
        float m = w * 0.02f, top = h * 0.17f;
        float cw = (w - m * (cols + 1)) / cols, chh = h * 0.13f;
        for (int i = 0; i < Achievements.NAMES.length; i++) {
            int col = i % cols, row = i / cols;
            float x = m + col * (cw + m), y = top + row * (chh + h * 0.012f);
            boolean done = save.hasAch(i);
            rect.set(x, y, x + cw, y + chh);
            fill.setColor(done ? 0xCC33691E : 0xAA263238);
            cv.drawRoundRect(rect, chh * 0.15f, chh * 0.15f, fill);
            ui.trophy(x + chh * 0.5f, y + chh * 0.5f, chh * 0.3f, done);
            outlined(Achievements.NAMES[i], x + chh, y + chh * 0.45f, chh * 0.27f,
                    done ? 0xFFFFFFFF : 0xFFB0BEC5, Paint.Align.LEFT);
            outlined(Achievements.DESCS[i], x + chh, y + chh * 0.8f, chh * 0.2f,
                    done ? 0xFFDCEDC8 : 0xFF90A4AE, Paint.Align.LEFT);
        }
        int[] st = save.stats;
        int mins = st[Save.ST_PLAYTIME] / 60;
        outlined("Peti: " + st[Save.ST_CRATES] + "   Wumpa: " + st[Save.ST_WUMPA] + "   Musuh: " + st[Save.ST_ENEMIES]
                        + "   Lompatan: " + st[Save.ST_JUMPS] + "   Mati: " + st[Save.ST_DEATHS]
                        + "   Waktu main: " + (mins / 60) + "j " + (mins % 60) + "m",
                w * 0.5f, h * 0.975f, u * 3.6f, 0xFFE0E0E0, Paint.Align.CENTER);
    }

    private void drawSettings() {
        float u = h * 0.01f;
        fill.setColor(0xCC000000);
        cv.drawRect(0, 0, w, h, fill);
        outlined("PENGATURAN", w * 0.5f, h * 0.14f, u * 9, 0xFFFFD54F, Paint.Align.CENTER);
        String[] labels = {"Musik", "Efek suara", "Getar", "Ukuran tombol", "Hapus progres"};
        for (int i = 0; i < 5; i++) {
            float y = settingsRowY(i);
            outlined(labels[i], w * 0.12f, y + u * 2, u * 6, 0xFFFFFFFF, Paint.Align.LEFT);
            if (i <= 1) {
                int v = i == 0 ? save.musicVol : save.sfxVol;
                float r = h * 0.045f;
                fill.setColor(0xCC455A64);
                cv.drawCircle(w * 0.52f, y, r, fill);
                cv.drawCircle(w * 0.86f, y, r, fill);
                outlined("-", w * 0.52f, y + r * 0.45f, r * 1.4f, 0xFFFFFFFF, Paint.Align.CENTER);
                outlined("+", w * 0.86f, y + r * 0.45f, r * 1.4f, 0xFFFFFFFF, Paint.Align.CENTER);
                float x0 = w * 0.57f, x1 = w * 0.81f, seg = (x1 - x0) / 10f;
                for (int k = 0; k < 10; k++) {
                    fill.setColor(k < v ? 0xFF66BB6A : 0x55FFFFFF);
                    cv.drawRect(x0 + k * seg + seg * 0.12f, y - h * 0.025f, x0 + (k + 1) * seg - seg * 0.12f, y + h * 0.025f, fill);
                }
            } else {
                String val;
                int col;
                if (i == 2) {
                    val = save.vibrate ? "NYALA" : "MATI";
                    col = save.vibrate ? 0xCC2E7D32 : 0xCC616161;
                } else if (i == 3) {
                    val = save.btnSize == 0 ? "KECIL" : save.btnSize == 2 ? "BESAR" : "SEDANG";
                    col = 0xCC1565C0;
                } else {
                    val = confirmReset ? "YAKIN? KETUK LAGI" : "HAPUS";
                    col = 0xCCC62828;
                }
                uiRect.set(w * 0.5f, y - h * 0.045f, w * 0.88f, y + h * 0.045f);
                drawButton(uiRect, val, col);
            }
        }
        uiRect.set(w * 0.35f, h * 0.85f, w * 0.65f, h * 0.95f);
        drawButton(uiRect, "KEMBALI", 0xCC2E7D32);
        if (msgT > 0f) outlined(msg, w * 0.5f, h * 0.8f, u * 5, 0xFFFFF176, Paint.Align.CENTER);
    }

    private void drawControls() {
        float r = btnR;
        // joystick
        float bx = joyActive ? joyBaseX : r * 2.2f;
        float by = joyActive ? joyBaseY : h - r * 2.2f;
        float kx = joyActive ? joyKnobX : bx;
        float ky = joyActive ? joyKnobY : by;
        fill.setColor(joyActive ? 0x55FFFFFF : 0x33FFFFFF);
        cv.drawCircle(bx, by, r * 1.3f, fill);
        stroke.setColor(0x88FFFFFF);
        stroke.setStrokeWidth(r * 0.05f);
        cv.drawCircle(bx, by, r * 1.3f, stroke);
        fill.setColor(joyActive ? 0xCCFFFFFF : 0x77FFFFFF);
        cv.drawCircle(kx, ky, r * 0.55f, fill);

        // lompat (X)
        fill.setColor(touchJumpHeld ? 0xCC42A5F5 : 0x882196F3);
        cv.drawCircle(jumpBx, jumpBy, r, fill);
        stroke.setColor(0xDDFFFFFF);
        stroke.setStrokeWidth(r * 0.12f);
        float d = r * 0.33f;
        cv.drawLine(jumpBx - d, jumpBy - d, jumpBx + d, jumpBy + d, stroke);
        cv.drawLine(jumpBx - d, jumpBy + d, jumpBx + d, jumpBy - d, stroke);

        // spin (O)
        fill.setColor(p.spinCd <= 0f ? 0x88E53935 : 0x55E53935);
        cv.drawCircle(spinBx, spinBy, r * 0.85f, fill);
        cv.drawCircle(spinBx, spinBy, r * 0.36f, stroke);

        // luncur (segitiga ke bawah)
        fill.setColor(p.slideCd <= 0f ? 0x8843A047 : 0x5543A047);
        cv.drawCircle(slideBx, slideBy, r * 0.7f, fill);
        fill.setColor(0xDDFFFFFF);
        float t = r * 0.3f;
        path.reset();
        path.moveTo(slideBx - t, slideBy - t * 0.6f);
        path.lineTo(slideBx + t, slideBy - t * 0.6f);
        path.lineTo(slideBx, slideBy + t * 0.8f);
        path.close();
        cv.drawPath(path, fill);
    }

    // =====================================================================
    // Input
    // =====================================================================

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = ev.getActionIndex();
                touchDown(ev.getPointerId(i), ev.getX(i), ev.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (state == ST_EDITOR) {
                    dragX = ev.getX(0);
                    dragY = ev.getY(0);
                    dragQueued = true;
                    break;
                }
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    if (ev.getPointerId(i) == joyId) moveJoystick(ev.getX(i), ev.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = ev.getActionIndex();
                touchUp(ev.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                joyId = -1;
                jumpId = -1;
                joyActive = false;
                joyX = joyZ = 0f;
                touchJumpHeld = false;
                break;
            default:
                break;
        }
        return true;
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void touchDown(int id, float x, float y) {
        if (state != ST_PLAY) {
            tapX = x;
            tapY = y;
            tapQueued = true;
            return;
        }
        float r = btnR;
        if (dist(x, y, pauseBx, pauseBy) < h * 0.07f) {
            pauseQueued = true;
        } else if (dist(x, y, jumpBx, jumpBy) < r * 1.3f) {
            jumpId = id;
            touchJumpHeld = true;
            jumpQueued = true;
        } else if (dist(x, y, slideBx, slideBy) < r * 0.95f) {
            slideQueued = true;
        } else if (dist(x, y, spinBx, spinBy) < r * 1.15f) {
            spinQueued = true;
        } else if (x < w * 0.5f && joyId == -1) {
            joyId = id;
            joyBaseX = x;
            joyBaseY = y;
            joyKnobX = x;
            joyKnobY = y;
            joyActive = true;
            joyX = joyZ = 0f;
        } else if (x >= w * 0.5f) {
            // ketukan lain di sisi kanan: dekat tombol lompat = lompat, selain itu = spin
            if (x > spinBx + r) {
                jumpId = id;
                touchJumpHeld = true;
                jumpQueued = true;
            } else {
                spinQueued = true;
            }
        }
    }

    private void moveJoystick(float x, float y) {
        float max = btnR * 1.2f;
        float dx = x - joyBaseX, dy = y - joyBaseY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > max) {
            dx = dx / d * max;
            dy = dy / d * max;
        }
        joyKnobX = joyBaseX + dx;
        joyKnobY = joyBaseY + dy;
        float nx = dx / max, nz = -dy / max;
        float m = (float) Math.sqrt(nx * nx + nz * nz);
        if (m < 0.15f) {
            nx = nz = 0f;
        }
        joyX = nx;
        joyZ = nz;
    }

    private void touchUp(int id) {
        if (id == joyId) {
            joyId = -1;
            joyActive = false;
            joyX = joyZ = 0f;
        }
        if (id == jumpId) {
            jumpId = -1;
            touchJumpHeld = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean first = event.getRepeatCount() == 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                kLeft = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                kRight = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                kUp = true;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                kDown = true;
                return true;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_K:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (first) {
                    if (state == ST_PLAY) jumpQueued = true;
                    else keyConfirm();
                }
                kJump = true;
                return true;
            case KeyEvent.KEYCODE_J:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (first) spinQueued = true;
                return true;
            case KeyEvent.KEYCODE_L:
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (first) slideQueued = true;
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_P:
                if (first) {
                    if (state == ST_PLAY) pauseQueued = true;
                    else keyConfirm();
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /** Enter/Start/A di menu: memilih pilihan utama tanpa layar sentuh. */
    private void keyConfirm() {
        switch (state) {
            case ST_MAP: {
                int i = Math.max(0, Math.min(save.unlocked, Levels.ALL.length) - 1);
                cardRect(i, uiRect);
                tapX = uiRect.left + uiRect.width() * 0.3f;
                tapY = uiRect.bottom - uiRect.height() * 0.1f;
                break;
            }
            case ST_PAUSE:
                pauseBtnRect(0, uiRect);
                tapX = uiRect.centerX();
                tapY = uiRect.centerY();
                break;
            case ST_SETTINGS:
                tapX = w * 0.5f;
                tapY = h * 0.9f;
                break;
            case ST_SHOP:
            case ST_ACH:
            case ST_EDITOR:
                backQueued = true;
                return;
            default:
                tapX = w * 0.5f;
                tapY = h * 0.5f;
                break;
        }
        tapQueued = true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                kLeft = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                kRight = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                kUp = false;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                kDown = false;
                return true;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_K:
            case KeyEvent.KEYCODE_BUTTON_A:
                kJump = false;
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            float x = ev.getAxisValue(MotionEvent.AXIS_X);
            float y = ev.getAxisValue(MotionEvent.AXIS_Y);
            float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(x) < 0.2f) x = 0f;
            if (Math.abs(y) < 0.2f) y = 0f;
            padX = Math.abs(hx) > 0.5f ? hx : x;
            padY = Math.abs(hy) > 0.5f ? hy : y;
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }
}
