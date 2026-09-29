package com.microaltech.bandirush;

/** Status bos Raja Kepiting. Logikanya ada di {@link GameView#updateBoss(float)}. */
final class Boss {
    static final int INTRO = 0, STRAFE = 1, WINDUP = 2, CHARGE = 3, STUNNED = 4, RETREAT = 5, DEFEATED = 6;
    static final int MAX_HP = 3;

    float x, z, homeZ;
    int state = INTRO;
    float t;
    float anim;
    int hp = MAX_HP;
    float flash;
    float throwT;
    int throwsLeft;
    final Entity proxy = new Entity(Entity.BOSS, 0f, 0f, 0f);

    void reset(float x, float z) {
        this.x = x;
        this.z = z;
        homeZ = z;
        state = INTRO;
        t = 0f;
        anim = 0f;
        hp = MAX_HP;
        flash = 0f;
        throwT = 1.2f;
        throwsLeft = 3;
    }
}
