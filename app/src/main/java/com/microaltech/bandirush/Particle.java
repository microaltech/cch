package com.microaltech.bandirush;

/** Partikel sederhana: serpihan peti, cincin ledakan, dan kilau wumpa. */
final class Particle {
    static final int CHUNK = 0, RING = 1, SPARK = 2;

    int kind;
    int color;
    float x, y, z;
    float vx, vy, vz;
    float life, maxLife;
    float size;
}
