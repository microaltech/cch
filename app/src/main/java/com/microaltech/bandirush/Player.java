package com.microaltech.bandirush;

/** Status tokoh utama, Bandi. */
final class Player {
    float x, y, z;
    float vx, vy, vz;
    boolean grounded;
    boolean jumping;
    boolean longJump;
    boolean usedAirSpin;
    float airT;
    float spinT, spinCd;
    float slideT, slideCd;
    float slideDirX, slideDirZ;
    float slideGrace;
    float jumpBuf;
    float facing;
    float runPhase;
    boolean moving;
    int mask;
    float invuln;
    Entity standingOn;
    float shadowY;
    boolean hasShadow;

    void reset(float x, float z) {
        this.x = x;
        this.y = 0f;
        this.z = z;
        vx = vy = vz = 0f;
        grounded = true;
        jumping = false;
        longJump = false;
        usedAirSpin = false;
        airT = 0f;
        spinT = 0f;
        spinCd = 0f;
        slideT = 0f;
        slideCd = 0f;
        slideGrace = 0f;
        jumpBuf = 0f;
        facing = 0f;
        runPhase = 0f;
        moving = false;
        mask = 0;
        invuln = 0f;
        standingOn = null;
        hasShadow = true;
        shadowY = 0f;
    }
}
