package com.microaltech.bandirush;

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
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

/**
 * Mesin game: loop, fisika, dan renderer 3D-semu (proyeksi perspektif + painter's algorithm)
 * di atas Canvas. Kamera berada di belakang Bandi dan melihat ke depan sepanjang koridor,
 * seperti level "lari ke dalam layar" di Crash Bandicoot.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int ST_TITLE = 0, ST_PLAY = 1, ST_DYING = 2, ST_DONE = 3,
            ST_OVER = 4, ST_WIN = 5, ST_PAUSE = 6;

    // Fisika (satuan: 1 unit = 1 petak)
    private static final float GRAVITY = 26f;
    private static final float RUN_SPEED = 5.2f;
    private static final float JUMP_V = 8.8f;
    private static final float BOUNCE_V = 7.5f;
    private static final float SUPER_BOUNCE_V = 12.5f;
    private static final float PR = 0.28f;  // radius Bandi
    private static final float CH = 0.45f;  // setengah ukuran peti
    private static final float CS = 0.9f;   // ukuran peti
    private static final float PLAT_HX = 0.6f, PLAT_HZ = 0.5f;
    private static final float SPIN_TIME = 0.45f, SPIN_COOLDOWN = 0.7f;
    private static final float EXPLODE_R = 1.7f;
    private static final int START_LIVES = 4;

    // Kamera & render
    private static final float PITCH = 0.22f;
    private static final float NEAR = 0.05f;
    private static final int VIEW_ROWS = 34;
    private static final float FOG_START = 10f, FOG_END = 33f;
    private static final float WALL_H = 2.0f, PIT_Y = -3.2f;
    private static final float CAM_BACK = 4.6f, CAM_UP = 2.7f;

    private static final int C_ORANGE = 0xFFF57C00, C_TAN = 0xFFFFE0B2, C_JEANS = 0xFF1E5AA8,
            C_SHOE = 0xFFD32F2F, C_GLOVE = 0xFF5D4037, C_HAIR = 0xFF3E2723;

    private final SurfaceHolder holder;
    private final Sfx sfx;
    private final Random rng = new Random();
    private Thread thread;
    private volatile boolean running;
    private volatile int w = 1, h = 1;

    // ---------- input (ditulis thread UI, dibaca thread game) ----------
    private volatile float joyX, joyZ, padX, padY;
    private volatile boolean kLeft, kRight, kUp, kDown, kJump;
    private volatile boolean jumpQueued, spinQueued, tapQueued, backQueued, pauseQueued;
    private volatile boolean touchJumpHeld;
    private volatile boolean joyActive;
    private volatile float joyBaseX, joyBaseY, joyKnobX, joyKnobY;
    private int joyId = -1, jumpId = -1;
    private volatile float btnR = 60, jumpBx, jumpBy, spinBx, spinBy, pauseBx, pauseBy;

    // ---------- status game ----------
    private int state = ST_TITLE;
    private float stateT, time;
    private Level level;
    private int levelIndex;
    private int lives = START_LIVES, wumpa, gems, broken;
    private final Player p = new Player();
    private float checkX, checkZ;
    private boolean dieFall;
    private float bannerT;
    private String msg = "";
    private float msgT;
    private boolean gemThisLevel;
    private final ArrayList<Particle> particles = new ArrayList<>();

    // ---------- render ----------
    private Canvas cv;
    private final Paint solid = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] qx = new float[4], qy = new float[4];
    private float qz;
    private float pX, pY, pZc, pS;
    private float camX, camY = CAM_UP, camZ;
    private float focal = 1f, cx, cy;
    private final float cosP = (float) Math.cos(PITCH), sinP = (float) Math.sin(PITCH);
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

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        sfx = new Sfx();

        solid.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);

        loadTitle();
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
        float r = h * 0.1f;
        btnR = r;
        jumpBx = w - r * 1.55f;
        jumpBy = h - r * 1.6f;
        spinBx = w - r * 3.9f;
        spinBy = h - r * 1.15f;
        pauseBx = w - r * 0.75f;
        pauseBy = r * 0.75f;
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
    }

    void release() {
        sfx.release();
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

    private void loadTitle() {
        level = new Level(Levels.ALL[0]);
        p.reset(level.startX, level.startZ);
        p.facing = (float) Math.PI;
        particles.clear();
        snapCamera();
        state = ST_TITLE;
        stateT = 0f;
    }

    private void startGame() {
        lives = START_LIVES;
        wumpa = 0;
        gems = 0;
        loadLevel(0);
    }

    private void loadLevel(int index) {
        levelIndex = index;
        level = new Level(Levels.ALL[index]);
        p.reset(level.startX, level.startZ);
        checkX = level.startX;
        checkZ = level.startZ;
        broken = 0;
        gemThisLevel = false;
        particles.clear();
        bannerT = 2.8f;
        msgT = 0f;
        snapCamera();
        state = ST_PLAY;
        stateT = 0f;
        jumpQueued = false;
        spinQueued = false;
    }

    private void snapCamera() {
        camX = p.x * 0.55f;
        camZ = p.z - CAM_BACK;
        camY = CAM_UP;
    }

    private void update(float dt) {
        time += dt;
        stateT += dt;
        if (bannerT > 0) bannerT -= dt;
        if (msgT > 0) msgT -= dt;

        boolean tap = tapQueued;
        tapQueued = false;
        boolean back = backQueued;
        backQueued = false;

        switch (state) {
            case ST_TITLE:
                pauseQueued = false;
                updateWorld(dt);
                updateParticles(dt);
                camX = (float) Math.sin(time * 0.4f) * 0.6f;
                if (tap || jumpQueued) startGame();
                break;
            case ST_PLAY:
                if (back || pauseQueued) {
                    pauseQueued = false;
                    state = ST_PAUSE;
                    stateT = 0f;
                    break;
                }
                updatePlaying(dt);
                break;
            case ST_PAUSE:
                pauseQueued = false;
                if (back) {
                    loadTitle();
                } else if (tap) {
                    state = ST_PLAY;
                    stateT = 0f;
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
                if (tap && stateT > 1f) {
                    if (levelIndex + 1 < Levels.ALL.length) {
                        loadLevel(levelIndex + 1);
                    } else {
                        state = ST_WIN;
                        stateT = 0f;
                    }
                }
                break;
            case ST_OVER:
            case ST_WIN:
                updateWorld(dt);
                updateParticles(dt);
                if ((tap || back) && stateT > 1f) loadTitle();
                break;
            default:
                break;
        }
        if (state != ST_PLAY) {
            jumpQueued = false;
            spinQueued = false;
        }
    }

    private void updatePlaying(float dt) {
        boolean jumpNow = jumpQueued;
        jumpQueued = false;
        boolean spinNow = spinQueued;
        spinQueued = false;

        int steps = Math.max(1, (int) Math.ceil(dt / (1f / 90f)));
        float sdt = dt / steps;
        for (int i = 0; i < steps && state == ST_PLAY; i++) {
            updateWorld(sdt);
            updatePlayer(sdt, i == 0 && jumpNow, i == 0 && spinNow);
            if (state != ST_PLAY) break;
            interactions();
        }
        updateParticles(dt);
        if (state != ST_PLAY) return;

        if (p.y < -4f) {
            killPlayer(true);
            return;
        }
        if (p.z >= level.goalRow + 0.3f) {
            state = ST_DONE;
            stateT = 0f;
            gemThisLevel = broken >= level.totalCrates;
            if (gemThisLevel) gems++;
            sfx.play(Sfx.WIN);
            burst(p.x, 1.2f, p.z + 0.5f, 0xFFFF80D0, 30, 5f);
        }
        updateCamera(dt);
    }

    private void updateCamera(float dt) {
        float ty = CAM_UP + Math.max(0f, Math.min(1.8f, p.grounded ? p.y : p.shadowY)) * 0.85f;
        camX += (p.x * 0.55f - camX) * Math.min(1f, dt * 5f);
        camZ += (p.z - CAM_BACK - camZ) * Math.min(1f, dt * 8f);
        camY += (ty - camY) * Math.min(1f, dt * 3f);
    }

    private void respawnOrGameOver() {
        lives--;
        if (lives <= 0) {
            lives = 0;
            state = ST_OVER;
            stateT = 0f;
            return;
        }
        p.reset(checkX, checkZ);
        p.invuln = 1.2f;
        for (Entity e : level.entities) {
            if (e.type == Entity.TNT && e.alive) e.fuse = -1f;
        }
        snapCamera();
        state = ST_PLAY;
        stateT = 0f;
    }

    // =====================================================================
    // Dunia: musuh, pijakan, sumbu TNT
    // =====================================================================

    private void updateWorld(float dt) {
        float hw = level.halfWidth();
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
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
        }
    }

    // =====================================================================
    // Bandi: gerak, lompat, spin, tabrakan
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

    private void updatePlayer(float dt, boolean jumpNow, boolean spinNow) {
        float ix = inputX(), iz = inputZ();
        float mag = (float) Math.sqrt(ix * ix + iz * iz);
        if (mag > 1f) {
            ix /= mag;
            iz /= mag;
        }
        float acc = p.grounded ? 14f : 7f;
        float k = Math.min(1f, acc * dt);
        p.vx += (ix * RUN_SPEED - p.vx) * k;
        p.vz += (iz * RUN_SPEED - p.vz) * k;
        float sp = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        p.moving = sp > 0.6f;
        if (p.moving) p.facing = (float) Math.atan2(p.vx, p.vz);
        p.runPhase += sp * dt * 3.4f;

        boolean held = touchJumpHeld || kJump;
        if (jumpNow && !p.jumping && (p.grounded || p.airT < 0.12f)) {
            p.vy = JUMP_V;
            p.grounded = false;
            p.jumping = true;
            p.standingOn = null;
            p.airT = 1f;
            sfx.play(Sfx.JUMP);
        }
        if (p.jumping && !held && p.vy > 3f) p.vy = 3f; // lompatan pendek kalau tombol cepat dilepas

        if (spinNow && p.spinCd <= 0f) {
            p.spinT = SPIN_TIME;
            p.spinCd = SPIN_COOLDOWN;
            sfx.play(Sfx.SPIN);
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

        if (p.grounded) {
            p.airT = 0f;
            p.jumping = false;
        } else {
            p.airT += dt;
        }
        computeShadow();
    }

    private boolean blocksSide(Entity e) {
        if (!e.alive || !e.isCrate()) return false;
        float top = e.y + CS;
        return p.y < top - 0.05f && p.y + 0.95f > e.y;
    }

    private void moveX(float dx) {
        float hw = level.halfWidth() - PR;
        float nx = Math.max(-hw, Math.min(hw, p.x + dx));
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!blocksSide(e)) continue;
            if (Math.abs(p.z - e.z) >= CH + PR) continue;
            if (Math.abs(nx - e.x) < CH + PR) {
                if (e.type == Entity.NITRO) {
                    explode(e);
                    continue;
                }
                nx = p.x < e.x ? e.x - CH - PR : e.x + CH + PR;
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
            if (Math.abs(p.x - e.x) >= CH + PR) continue;
            if (Math.abs(nz - e.z) < CH + PR) {
                if (e.type == Entity.NITRO) {
                    explode(e);
                    continue;
                }
                nz = p.z < e.z ? e.z - CH - PR : e.z + CH + PR;
                p.vz = 0f;
            }
        }
        p.z = nz;
    }

    private void moveY(float dt) {
        float ny = p.y + p.vy * dt;
        boolean found = false;
        float bestTop = -1e9f;
        Entity best = null;
        if (p.vy <= 0f) {
            if (level.groundUnder(p.x, p.z) && 0f <= p.y + 0.001f && 0f >= ny) {
                found = true;
                bestTop = 0f;
            }
            for (int i = 0, n = level.entities.size(); i < n; i++) {
                Entity e = level.entities.get(i);
                if (!e.alive) continue;
                float top;
                if (e.isCrate()) {
                    if (Math.abs(p.x - e.x) >= CH + 0.12f || Math.abs(p.z - e.z) >= CH + 0.12f) continue;
                    top = e.y + CS;
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
            if (e.isCrate()) {
                if (Math.abs(p.x - e.x) >= CH || Math.abs(p.z - e.z) >= CH) continue;
                top = e.y + CS;
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
        p.y += 0.01f;
    }

    private void landOnCrate(Entity e) {
        switch (e.type) {
            case Entity.IRON:
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

    // =====================================================================
    // Interaksi: spin, musuh, wumpa
    // =====================================================================

    private void interactions() {
        boolean spinning = p.spinT > 0f;
        for (int i = 0, n = level.entities.size(); i < n && state == ST_PLAY; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive) continue;
            float dx = e.x - p.x, dz = e.z - p.z;
            float d2 = dx * dx + dz * dz;

            if (e.type == Entity.WUMPA) {
                float wy = e.y + 0.45f;
                if (Math.abs(dx) < 0.55f && Math.abs(dz) < 0.55f && wy > p.y - 0.1f && wy < p.y + 1.25f) {
                    e.alive = false;
                    addWumpa(1);
                    sfx.play(Sfx.WUMPA);
                    burst(e.x, wy, e.z, 0xFFFFB300, 6, 2.5f);
                }
            } else if (e.isEnemy()) {
                if (e.dieMode != Entity.DIE_NONE) continue;
                if (spinning && d2 < 1.1f * 1.1f && p.y < 1.1f) {
                    knockEnemy(e, dx, dz);
                } else if (d2 < 0.6f * 0.6f && p.y < 0.8f) {
                    if (p.vy < -1f && p.y > 0.15f) {
                        e.dieMode = Entity.DIE_SQUASH;
                        e.dieT = 0f;
                        bounce(BOUNCE_V + 1f);
                        sfx.play(Sfx.STOMP);
                    } else {
                        hurtPlayer();
                    }
                }
            } else if (spinning && e.isCrate() && d2 < 1.0f && Math.abs(e.y - p.y) < 0.85f) {
                if (e.isExplosive()) {
                    explode(e);
                } else if (e.type != Entity.IRON) {
                    breakCrate(e);
                }
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
        e.vx = dx / d * 7f;
        e.vz = dz / d * 7f + 3f;
        e.vy = 7f;
        sfx.play(Sfx.STOMP);
    }

    private void addWumpa(int n) {
        wumpa += n;
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
                checkX = e.x;
                checkZ = e.z;
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
        if (state == ST_PLAY && pdx * pdx + pdz * pdz < EXPLODE_R * EXPLODE_R && Math.abs(p.y - e.y) < 1.8f) {
            hurtPlayer();
        }
    }

    private void hurtPlayer() {
        if (state != ST_PLAY || p.invuln > 0f) return;
        if (p.mask > 0) {
            p.mask--;
            p.invuln = 1.6f;
            sfx.play(Sfx.HURT);
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
        sfx.play(Sfx.DIE);
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
        float dx = x - camX, dy = y - camY, dz = z - camZ;
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

    private int fog(int color, float zc) {
        float f = (zc - FOG_START) / (FOG_END - FOG_START);
        if (f <= 0f) return color;
        if (f > 1f) f = 1f;
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
        focal = h * 0.95f;
        cx = w * 0.5f;
        cy = h * 0.40f;

        drawSky();
        drawWorld();
        drawParticles();
        drawHud();
        cv = null;
    }

    private void drawSky() {
        Theme th = level.theme;
        float horizon = cy - focal * (sinP / cosP);
        if (skyShader == null || skyTheme != th || skyH != h) {
            skyShader = new LinearGradient(0, 0, 0, Math.max(1f, horizon), th.sky1, th.sky2, Shader.TileMode.CLAMP);
            skyTheme = th;
            skyH = h;
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

        float off = camX * h * 0.08f + camZ * h * 0.01f;
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
        int rStart = (int) Math.floor(camZ) + 1;
        int rEnd = Math.min((int) camZ + VIEW_ROWS, level.rows + 6);

        drawList.clear();
        for (int i = 0, n = level.entities.size(); i < n; i++) {
            Entity e = level.entities.get(i);
            if (!e.alive || e.z < rStart || e.z > rEnd + 1) continue;
            e.key = e.z;
            drawList.add(e);
        }
        Collections.sort(drawList, byKeyDesc);

        boolean showPlayer = true;
        float playerKey = p.z;
        if (p.standingOn != null) playerKey = Math.min(playerKey, p.standingOn.z - 0.01f);
        if (p.y > 0.2f) playerKey = Math.min(playerKey, p.z - 0.55f);
        if (state == ST_DYING && dieFall && p.y < -3.5f) showPlayer = false;
        boolean playerDrawn = !showPlayer;

        int idx = 0, n = drawList.size();
        for (int r = rEnd; r >= rStart; r--) {
            drawRow(r);
            while (true) {
                float ek = idx < n ? drawList.get(idx).key : -1e9f;
                float pk = playerDrawn ? -1e9f : playerKey;
                if (ek < r && pk < r) break;
                if (pk > ek) {
                    drawPlayer();
                    playerDrawn = true;
                } else {
                    drawEntity(drawList.get(idx++));
                }
            }
        }
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
        quad(-hw, PIT_Y, z0, -hw, WALL_H, z0, -hw, WALL_H, z1, -hw, PIT_Y, z1, shade(wc, 0.9f));
        quad(-hw - 6f, WALL_H, z0, -hw, WALL_H, z0, -hw, WALL_H, z1, -hw - 6f, WALL_H, z1, th.wallTop);
        quad(hw, PIT_Y, z0, hw, WALL_H, z0, hw, WALL_H, z1, hw, PIT_Y, z1, shade(wc, 0.8f));
        quad(hw, WALL_H, z0, hw + 6f, WALL_H, z0, hw + 6f, WALL_H, z1, hw, WALL_H, z1, th.wallTop);

        // tanah: sisi tebing, muka depan tebing, lalu permukaan
        for (int c = 0; c < wd; c++) {
            if (!level.groundAt(r, c)) continue;
            float x0 = level.colX(c) - 0.5f, x1 = x0 + 1f;
            if (c + 1 < wd && !level.groundAt(r, c + 1) && camX > x1) {
                quad(x1, 0, z0, x1, 0, z1, x1, PIT_Y, z1, x1, PIT_Y, z0, shade(th.dirt, 0.8f));
            }
            if (c > 0 && !level.groundAt(r, c - 1) && camX < x0) {
                quad(x0, 0, z1, x0, 0, z0, x0, PIT_Y, z0, x0, PIT_Y, z1, shade(th.dirt, 0.8f));
            }
            if (!level.groundAt(r - 1, c)) {
                quad(x0, 0, z0, x1, 0, z0, x1, PIT_Y, z0, x0, PIT_Y, z0, th.dirt);
                // bibir rumput di tepi tebing
                quad(x0, 0.001f, z0, x1, 0.001f, z0, x1, -0.18f, z0, x0, -0.18f, z0, shade(th.groundEdge, 0.85f));
            }
            int col;
            if (th.trees && (c == 0 || c == wd - 1)) {
                col = ((r + c) & 1) == 0 ? th.groundEdge : shade(th.groundEdge, 0.92f);
            } else {
                col = ((r + c) & 1) == 0 ? th.groundA : th.groundB;
            }
            quad(x0, 0, z0, x1, 0, z0, x1, 0, z1, x0, 0, z1, col);
        }

        // dekorasi di atas dinding
        int hsh = (r * 7919) & 0xFF;
        if (th.trees) {
            drawBush(-hw - 0.1f, r + 0.5f, 0.55f + (hsh % 5) * 0.05f, hsh);
            drawBush(hw + 0.1f, r + 0.5f, 0.55f + ((hsh >> 3) % 5) * 0.05f, hsh + 3);
            if (hsh % 3 == 0) drawTree(-hw - 1.3f - (hsh % 2), r + 0.5f, 3.2f + (hsh % 4) * 0.4f, hsh);
            if ((hsh >> 2) % 3 == 0) drawTree(hw + 1.3f + ((hsh >> 4) % 2), r + 0.5f, 3.0f + (hsh % 3) * 0.5f, hsh + 7);
        } else if (r % 4 == 0) {
            drawTorch(-hw - 0.4f, r + 0.5f);
            drawTorch(hw + 0.4f, r + 0.5f);
        }
    }

    private void drawBush(float x, float z, float size, int seed) {
        if (!project(x, WALL_H, z)) return;
        float s = pS;
        fill.setColor(fog(shade(level.theme.leaf, 0.9f + (seed % 3) * 0.08f), pZc));
        cv.drawCircle(pX, pY, size * s, fill);
        cv.drawCircle(pX + size * 0.6f * s, pY + size * 0.15f * s, size * 0.7f * s, fill);
        cv.drawCircle(pX - size * 0.6f * s, pY + size * 0.2f * s, size * 0.65f * s, fill);
    }

    private void drawTree(float x, float z, float hgt, int seed) {
        if (!project(x, WALL_H, z)) return;
        float bx = pX, by = pY, s = pS, zc = pZc;
        if (!project(x, WALL_H + hgt, z)) return;
        float tx = pX, ty = pY;
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
            case Entity.CRAB: drawCrab(e); break;
            case Entity.HOG: drawHog(e); break;
            case Entity.PLATFORM: drawPlatform(e); break;
            case Entity.GOAL: drawGoal(e); break;
            default:
                if (e.isCrate()) drawCrate(e);
                break;
        }
    }

    private void drawShadow(float x, float y, float z, float radius) {
        if (!project(x, y + 0.01f, z)) return;
        float r = radius * pS;
        rect.set(pX - r, pY - r * 0.4f, pX + r, pY + r * 0.4f);
        fill.setColor(0x55000000);
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
        if (camY > y1 && quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, cTop)) {
            topOk = true;
            System.arraycopy(qx, 0, topX, 0, 4);
            System.arraycopy(qy, 0, topY, 0, 4);
        }
        if (quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, cFront)) {
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
            case Entity.CHECK:
                base = 0xFFD6A15E;
                break;
            default:
                base = 0xFFC07A3A;
                break;
        }
        if (e.y >= -0.01f && level.groundUnder(e.x, e.z)) drawShadow(e.x, 0f, e.z, 0.55f);
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

    private void drawCrab(Entity e) {
        drawShadow(e.x, 0f, e.z, 0.45f);
        if (!project(e.x, e.y, e.z)) return;
        cv.save();
        cv.translate(pX, pY);
        cv.scale(pS, pS);
        if (e.dieMode == Entity.DIE_KNOCK) cv.rotate(e.dieT * 720f, 0, -0.3f);
        if (e.dieMode == Entity.DIE_SQUASH) cv.scale(1.3f, 0.3f);
        float walk = (float) Math.sin(e.t * 14f);
        stroke.setColor(fog(0xFFB71C1C, pZc));
        stroke.setStrokeWidth(0.05f);
        for (int k = 0; k < 3; k++) {
            float lx = 0.18f + k * 0.07f;
            float ly = walk * 0.03f * (k % 2 == 0 ? 1 : -1);
            cv.drawLine(-lx, -0.22f, -lx - 0.12f, ly, stroke);
            cv.drawLine(lx, -0.22f, lx + 0.12f, -ly, stroke);
        }
        fill.setColor(fog(0xFFE53935, pZc));
        rect.set(-0.36f, -0.44f, 0.36f, -0.12f);
        cv.drawOval(rect, fill);
        float claw = (float) Math.sin(e.t * 6f) * 0.06f;
        cv.drawCircle(-0.44f, -0.42f + claw, 0.13f, fill);
        cv.drawCircle(0.44f, -0.42f - claw, 0.13f, fill);
        fill.setColor(fog(0xFFFF8A80, pZc));
        rect.set(-0.2f, -0.4f, 0.2f, -0.28f);
        cv.drawOval(rect, fill);
        stroke.setColor(fog(0xFFE53935, pZc));
        cv.drawLine(-0.1f, -0.42f, -0.13f, -0.6f, stroke);
        cv.drawLine(0.1f, -0.42f, 0.13f, -0.6f, stroke);
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(-0.13f, -0.62f, 0.07f, fill);
        cv.drawCircle(0.13f, -0.62f, 0.07f, fill);
        fill.setColor(0xFF000000);
        cv.drawCircle(-0.13f, -0.61f, 0.035f, fill);
        cv.drawCircle(0.13f, -0.61f, 0.035f, fill);
        cv.restore();
    }

    private void drawHog(Entity e) {
        drawShadow(e.x, 0f, e.z, 0.45f);
        if (!project(e.x, e.y, e.z)) return;
        cv.save();
        cv.translate(pX, pY);
        cv.scale(pS, pS);
        if (e.dieMode == Entity.DIE_KNOCK) cv.rotate(e.dieT * 720f, 0, -0.35f);
        if (e.dieMode == Entity.DIE_SQUASH) cv.scale(1.3f, 0.3f);
        float step = (float) Math.sin(e.t * 12f) * 0.04f;
        fill.setColor(fog(0xFF3E2723, pZc));
        rect.set(-0.3f, -0.2f + step, -0.16f, 0f);
        cv.drawRect(rect, fill);
        rect.set(0.16f, -0.2f - step, 0.3f, 0f);
        cv.drawRect(rect, fill);
        fill.setColor(fog(0xFF795548, pZc));
        rect.set(-0.4f, -0.62f, 0.4f, -0.14f);
        cv.drawOval(rect, fill);
        fill.setColor(fog(0xFF4E342E, pZc));
        path.reset();
        path.moveTo(-0.28f, -0.56f);
        path.lineTo(-0.36f, -0.78f);
        path.lineTo(-0.14f, -0.6f);
        path.close();
        path.moveTo(0.28f, -0.56f);
        path.lineTo(0.36f, -0.78f);
        path.lineTo(0.14f, -0.6f);
        path.close();
        cv.drawPath(path, fill);
        fill.setColor(fog(0xFFF48FB1, pZc));
        rect.set(-0.14f, -0.38f, 0.14f, -0.2f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF3E2723);
        cv.drawCircle(-0.05f, -0.29f, 0.025f, fill);
        cv.drawCircle(0.05f, -0.29f, 0.025f, fill);
        fill.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(-0.14f, -0.26f);
        path.lineTo(-0.22f, -0.42f);
        path.lineTo(-0.1f, -0.3f);
        path.close();
        path.moveTo(0.14f, -0.26f);
        path.lineTo(0.22f, -0.42f);
        path.lineTo(0.1f, -0.3f);
        path.close();
        cv.drawPath(path, fill);
        fill.setColor(0xFFFFEB3B);
        cv.drawCircle(-0.12f, -0.47f, 0.045f, fill);
        cv.drawCircle(0.12f, -0.47f, 0.045f, fill);
        fill.setColor(0xFFD50000);
        cv.drawCircle(-0.12f, -0.47f, 0.022f, fill);
        cv.drawCircle(0.12f, -0.47f, 0.022f, fill);
        cv.restore();
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
            if (p.spinT > 0f) {
                drawTornado();
            } else {
                drawBandiBody();
            }
            cv.restore();
        }
        if (p.mask > 0 && state != ST_DYING) drawMask();
    }

    private void drawBandiBody() {
        float fc = (float) Math.cos(p.facing), fs = (float) Math.sin(p.facing);
        int view; // 0 belakang, 1 depan, 2 samping
        if (fc > 0.5f) view = 0;
        else if (fc < -0.5f) view = 1;
        else view = 2;
        if (view == 2 && fs < 0) cv.scale(-1f, 1f);

        boolean air = !p.grounded && state == ST_PLAY;
        float sw = p.moving && !air ? (float) Math.sin(p.runPhase) : 0f;
        float idle = (float) Math.sin(time * 3f) * 0.01f;

        // kaki
        stroke.setStrokeWidth(0.13f);
        stroke.setColor(C_JEANS);
        fill.setColor(C_SHOE);
        float lfx, lfy, rfx, rfy;
        if (view == 2) {
            lfx = sw * 0.16f;
            rfx = -sw * 0.16f;
            lfy = -0.05f - Math.max(0f, sw) * 0.1f;
            rfy = -0.05f - Math.max(0f, -sw) * 0.1f;
        } else {
            lfx = -0.1f;
            rfx = 0.1f;
            lfy = -0.05f - Math.max(0f, sw) * 0.12f;
            rfy = -0.05f - Math.max(0f, -sw) * 0.12f;
        }
        if (air) {
            lfx = -0.12f;
            rfx = 0.14f;
            lfy = -0.18f;
            rfy = -0.1f;
        }
        cv.drawLine(-0.07f, -0.42f, lfx, lfy, stroke);
        cv.drawLine(0.07f, -0.42f, rfx, rfy, stroke);
        float shoeF = view == 2 ? 0.07f : 0f;
        rect.set(lfx - 0.08f, lfy - 0.04f, lfx + 0.08f + shoeF, lfy + 0.05f);
        cv.drawOval(rect, fill);
        rect.set(rfx - 0.08f, rfy - 0.04f, rfx + 0.08f + shoeF, rfy + 0.05f);
        cv.drawOval(rect, fill);

        // badan
        fill.setColor(C_JEANS);
        rect.set(-0.17f, -0.52f + idle, 0.17f, -0.34f);
        cv.drawOval(rect, fill);
        fill.setColor(C_ORANGE);
        rect.set(-0.17f, -0.76f + idle, 0.17f, -0.42f + idle);
        cv.drawOval(rect, fill);
        if (view == 1) {
            fill.setColor(C_TAN);
            rect.set(-0.09f, -0.66f + idle, 0.09f, -0.45f + idle);
            cv.drawOval(rect, fill);
        }

        // tangan
        stroke.setColor(C_ORANGE);
        stroke.setStrokeWidth(0.08f);
        fill.setColor(C_GLOVE);
        float lhx, lhy, rhx, rhy;
        if (air) {
            lhx = -0.3f;
            lhy = -0.85f;
            rhx = 0.3f;
            rhy = -0.85f;
        } else if (view == 2) {
            lhx = -sw * 0.2f;
            lhy = -0.47f;
            rhx = sw * 0.2f;
            rhy = -0.47f;
        } else {
            lhx = -0.27f;
            lhy = -0.5f - sw * 0.07f;
            rhx = 0.27f;
            rhy = -0.5f + sw * 0.07f;
        }
        cv.drawLine(-0.13f, -0.68f + idle, lhx, lhy, stroke);
        cv.drawLine(0.13f, -0.68f + idle, rhx, rhy, stroke);
        cv.drawCircle(lhx, lhy, 0.06f, fill);
        cv.drawCircle(rhx, rhy, 0.06f, fill);

        // kepala
        float hy = -0.9f + idle;
        fill.setColor(C_ORANGE);
        cv.drawCircle(-0.15f, hy - 0.12f, 0.06f, fill);
        cv.drawCircle(0.15f, hy - 0.12f, 0.06f, fill);
        cv.drawCircle(0, hy, 0.18f, fill);
        fill.setColor(C_HAIR);
        path.reset();
        path.moveTo(-0.06f, hy - 0.15f);
        path.lineTo(-0.01f, hy - 0.29f);
        path.lineTo(0.03f, hy - 0.16f);
        path.close();
        path.moveTo(0.01f, hy - 0.16f);
        path.lineTo(0.1f, hy - 0.26f);
        path.lineTo(0.08f, hy - 0.13f);
        path.close();
        cv.drawPath(path, fill);

        if (view == 1) {
            fill.setColor(C_TAN);
            rect.set(-0.14f, hy + 0.02f, 0.14f, hy + 0.17f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFFFFFFFF);
            rect.set(-0.12f, hy - 0.1f, -0.02f, hy + 0.03f);
            cv.drawOval(rect, fill);
            rect.set(0.02f, hy - 0.1f, 0.12f, hy + 0.03f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF388E3C);
            cv.drawCircle(-0.06f, hy - 0.035f, 0.032f, fill);
            cv.drawCircle(0.06f, hy - 0.035f, 0.032f, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(-0.06f, hy - 0.035f, 0.016f, fill);
            cv.drawCircle(0.06f, hy - 0.035f, 0.016f, fill);
            rect.set(-0.035f, hy + 0.03f, 0.035f, hy + 0.07f);
            cv.drawOval(rect, fill);
            stroke.setColor(0xFF8D1B1B);
            stroke.setStrokeWidth(0.022f);
            rect.set(-0.08f, hy + 0.04f, 0.08f, hy + 0.14f);
            cv.drawArc(rect, 20, 140, false, stroke);
        } else if (view == 2) {
            fill.setColor(C_TAN);
            rect.set(0.02f, hy - 0.03f, 0.31f, hy + 0.14f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(0.3f, hy + 0.03f, 0.037f, fill);
            fill.setColor(0xFFFFFFFF);
            rect.set(0.04f, hy - 0.12f, 0.14f, hy + 0.01f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF388E3C);
            cv.drawCircle(0.11f, hy - 0.05f, 0.03f, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(0.12f, hy - 0.05f, 0.015f, fill);
            stroke.setColor(0xFF8D1B1B);
            stroke.setStrokeWidth(0.02f);
            cv.drawLine(0.12f, hy + 0.1f, 0.24f, hy + 0.09f, stroke);
        }
    }

    private void drawTornado() {
        float a = time * 30f;
        for (int i = 0; i < 6; i++) {
            float y = -0.08f - i * 0.15f;
            float hw = 0.26f + 0.08f * (float) Math.sin(a * 0.7f + i * 1.3f) + (i == 2 || i == 3 ? 0.06f : 0f);
            boolean orange = (((int) (a / Math.PI) + i) & 1) == 0;
            fill.setColor(i < 2 ? (orange ? C_JEANS : 0xFF3F7BD1) : (orange ? C_ORANGE : 0xFFFFA040));
            rect.set(-hw, y - 0.1f, hw, y + 0.1f);
            cv.drawOval(rect, fill);
        }
        fill.setColor(C_ORANGE);
        cv.drawCircle(0, -0.95f, 0.15f, fill);
        fill.setColor(C_HAIR);
        path.reset();
        path.moveTo(-0.05f, -1.08f);
        path.lineTo(0f, -1.2f);
        path.lineTo(0.04f, -1.08f);
        path.close();
        cv.drawPath(path, fill);
        stroke.setColor(0xCCFFFFFF);
        stroke.setStrokeWidth(0.03f);
        for (int k = 0; k < 3; k++) {
            float yy = -0.25f - k * 0.25f;
            float st = (a * 40f + k * 120f) % 360f;
            rect.set(-0.5f, yy - 0.12f, 0.5f, yy + 0.12f);
            cv.drawArc(rect, st, 110f, false, stroke);
        }
    }

    private void drawMask() {
        float bob = (float) Math.sin(time * 3f) * 0.07f;
        if (!project(p.x - 0.5f, p.y + 0.95f + bob, p.z - 0.15f)) return;
        cv.save();
        cv.translate(pX, pY);
        cv.scale(pS, pS);
        if (p.mask > 1) {
            fill.setColor(0x55FFEB3B);
            cv.drawCircle(0, 0, 0.3f, fill);
        }
        fill.setColor(0xFFE53935);
        rect.set(-0.14f, -0.36f, -0.06f, -0.12f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFFFFEB3B);
        rect.set(-0.04f, -0.4f, 0.04f, -0.14f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF43A047);
        rect.set(0.06f, -0.36f, 0.14f, -0.12f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF8D5524);
        rect.set(-0.13f, -0.2f, 0.13f, 0.2f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(-0.05f, -0.06f, 0.035f, fill);
        cv.drawCircle(0.05f, -0.06f, 0.035f, fill);
        fill.setColor(0xFF000000);
        cv.drawCircle(-0.05f, -0.06f, 0.017f, fill);
        cv.drawCircle(0.05f, -0.06f, 0.017f, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(0.025f);
        cv.drawLine(-0.06f, 0.09f, 0.06f, 0.09f, stroke);
        cv.restore();
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
                    fill.setColor((q.color & 0x00FFFFFF) | ((int) (Math.min(1f, a * 1.5f) * 255) << 24));
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
        text.setColor(0xFF1A1A1A);
        cv.drawText(s, x, y, text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(color);
        cv.drawText(s, x, y, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawHud() {
        float u = h * 0.01f;
        if (state == ST_TITLE) {
            drawTitle();
            return;
        }

        // wumpa
        fill.setColor(0xFFFF6F00);
        cv.drawCircle(u * 6, u * 7, u * 3.4f, fill);
        fill.setColor(0xFFFFA726);
        cv.drawCircle(u * 5.2f, u * 6.2f, u * 1.8f, fill);
        outlined(String.valueOf(wumpa), u * 11, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);

        // peti
        float bx = u * 30;
        fill.setColor(0xFFC07A3A);
        cv.drawRect(bx - u * 3, u * 4, bx + u * 3, u * 10, fill);
        stroke.setColor(0xFF6B3E1A);
        stroke.setStrokeWidth(u * 0.7f);
        cv.drawRect(bx - u * 2.3f, u * 4.7f, bx + u * 2.3f, u * 9.3f, stroke);
        outlined(broken + "/" + level.totalCrates, bx + u * 5, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);

        // kristal
        if (gems > 0) {
            float gx = u * 58;
            fill.setColor(0xFFFF80D0);
            path.reset();
            path.moveTo(gx, u * 3);
            path.lineTo(gx + u * 2.4f, u * 6.5f);
            path.lineTo(gx, u * 10.5f);
            path.lineTo(gx - u * 2.4f, u * 6.5f);
            path.close();
            cv.drawPath(path, fill);
            outlined("x" + gems, gx + u * 4, u * 9.6f, u * 6, 0xFFFFFFFF, Paint.Align.LEFT);
        }

        // nyawa
        float lx = w - u * 30;
        fill.setColor(C_ORANGE);
        cv.drawCircle(lx, u * 7, u * 3.2f, fill);
        fill.setColor(C_TAN);
        rect.set(lx - u * 2, u * 7.3f, lx + u * 2, u * 9.8f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF000000);
        cv.drawCircle(lx - u, u * 6.2f, u * 0.6f, fill);
        cv.drawCircle(lx + u, u * 6.2f, u * 0.6f, fill);
        outlined("x" + lives, lx + u * 4.5f, u * 9.6f, u * 7, 0xFFFFFFFF, Paint.Align.LEFT);

        // tombol pause
        if (state == ST_PLAY) {
            fill.setColor(0x66000000);
            cv.drawCircle(pauseBx, pauseBy, btnR * 0.45f, fill);
            fill.setColor(0xFFFFFFFF);
            float pw = btnR * 0.09f, ph = btnR * 0.2f;
            cv.drawRect(pauseBx - pw * 2.2f, pauseBy - ph, pauseBx - pw * 0.6f, pauseBy + ph, fill);
            cv.drawRect(pauseBx + pw * 0.6f, pauseBy - ph, pauseBx + pw * 2.2f, pauseBy + ph, fill);
        }

        if (bannerT > 0f && state == ST_PLAY) {
            float a = Math.min(1f, bannerT);
            int col = ((int) (a * 255) << 24) | 0xFFD54F;
            outlined(level.name, w * 0.5f, h * 0.24f, u * 10, col, Paint.Align.CENTER);
        }
        if (msgT > 0f && state == ST_PLAY) {
            float rise = (1.6f - msgT) * u * 4;
            outlined(msg, w * 0.5f, h * 0.36f - rise, u * 7, 0xFFFFF176, Paint.Align.CENTER);
        }

        if (state == ST_PLAY) drawControls();

        switch (state) {
            case ST_PAUSE:
                dim();
                outlined("JEDA", w * 0.5f, h * 0.42f, u * 14, 0xFFFFD54F, Paint.Align.CENTER);
                outlined("Ketuk untuk lanjut  -  Back untuk ke menu", w * 0.5f, h * 0.58f, u * 5, 0xFFFFFFFF, Paint.Align.CENTER);
                break;
            case ST_DONE: {
                dim();
                outlined("LEVEL SELESAI!", w * 0.5f, h * 0.3f, u * 12, 0xFFFFD54F, Paint.Align.CENTER);
                outlined("Peti: " + broken + " / " + level.totalCrates + "     Wumpa: " + wumpa,
                        w * 0.5f, h * 0.45f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER);
                if (gemThisLevel) {
                    outlined("Semua peti hancur!  +1 KRISTAL", w * 0.5f, h * 0.56f, u * 6, 0xFFFF80D0, Paint.Align.CENTER);
                }
                if (stateT > 1f && ((int) (time * 2) & 1) == 0) {
                    outlined("Ketuk untuk lanjut", w * 0.5f, h * 0.72f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                }
                break;
            }
            case ST_OVER:
                dim();
                outlined("GAME OVER", w * 0.5f, h * 0.42f, u * 16, 0xFFFF5252, Paint.Align.CENTER);
                if (stateT > 1f) outlined("Ketuk untuk kembali ke menu", w * 0.5f, h * 0.6f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                break;
            case ST_WIN:
                dim();
                outlined("TAMAT! KAMU MENANG!", w * 0.5f, h * 0.36f, u * 12, 0xFFFFD54F, Paint.Align.CENTER);
                outlined("Kristal: " + gems + " / " + Levels.ALL.length + "     Wumpa: " + wumpa,
                        w * 0.5f, h * 0.52f, u * 6, 0xFFFFFFFF, Paint.Align.CENTER);
                if (stateT > 1f) outlined("Ketuk untuk kembali ke menu", w * 0.5f, h * 0.68f, u * 5.5f, 0xFFFFFFFF, Paint.Align.CENTER);
                break;
            default:
                break;
        }
    }

    private void dim() {
        fill.setColor(0x99000000);
        cv.drawRect(0, 0, w, h, fill);
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
        outlined("Kiri: joystick gerak   |   X: lompat   |   O: putar (spin)", w * 0.5f, h * 0.84f, u * 4.2f, 0xFFE0E0E0, Paint.Align.CENTER);
        outlined("Hancurkan semua peti untuk dapat kristal!  Awas TNT & Nitro.", w * 0.5f, h * 0.91f, u * 4.2f, 0xFFE0E0E0, Paint.Align.CENTER);
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

        // tombol lompat (X) & spin (O)
        boolean jh = touchJumpHeld;
        fill.setColor(jh ? 0xCC42A5F5 : 0x882196F3);
        cv.drawCircle(jumpBx, jumpBy, r, fill);
        stroke.setColor(0xDDFFFFFF);
        stroke.setStrokeWidth(r * 0.12f);
        float d = r * 0.33f;
        cv.drawLine(jumpBx - d, jumpBy - d, jumpBx + d, jumpBy + d, stroke);
        cv.drawLine(jumpBx - d, jumpBy + d, jumpBx + d, jumpBy - d, stroke);

        boolean spinReady = p.spinCd <= 0f;
        fill.setColor(spinReady ? 0x88E53935 : 0x55E53935);
        cv.drawCircle(spinBx, spinBy, r * 0.85f, fill);
        cv.drawCircle(spinBx, spinBy, r * 0.36f, stroke);
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
            tapQueued = true;
            return;
        }
        float r = btnR;
        if (dist(x, y, pauseBx, pauseBy) < r * 0.8f) {
            pauseQueued = true;
        } else if (dist(x, y, jumpBx, jumpBy) < r * 1.35f) {
            jumpId = id;
            touchJumpHeld = true;
            jumpQueued = true;
        } else if (dist(x, y, spinBx, spinBy) < r * 1.2f) {
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
            // ketukan lain di sisi kanan: di atas tombol = lompat, di kiri tombol = spin
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
                if (event.getRepeatCount() == 0) {
                    if (state == ST_PLAY) jumpQueued = true;
                    else tapQueued = true;
                }
                kJump = true;
                return true;
            case KeyEvent.KEYCODE_J:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (event.getRepeatCount() == 0) spinQueued = true;
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_P:
                if (event.getRepeatCount() == 0) {
                    if (state == ST_PLAY) pauseQueued = true;
                    else tapQueued = true;
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
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
