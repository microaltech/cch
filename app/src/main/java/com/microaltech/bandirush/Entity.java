package com.microaltech.bandirush;

/** Semua objek di dunia game: peti, buah wumpa, musuh, pijakan bergerak, dan kristal finish. */
final class Entity {
    // Peti (urutan penting: semua tipe <= IRON adalah peti padat)
    static final int CRATE = 0;     // peti kayu biasa: 1 wumpa
    static final int QCRATE = 1;    // peti "?": 5 wumpa
    static final int BOUNCE = 2;    // peti pantul
    static final int LIFE = 3;      // peti nyawa
    static final int AKU = 4;       // peti topeng pelindung
    static final int TNT = 5;       // meledak 3 detik setelah diinjak
    static final int NITRO = 6;     // meledak kalau disentuh
    static final int CHECK = 7;     // checkpoint
    static final int IRON = 8;      // peti besi, tidak bisa hancur

    static final int WUMPA = 20;
    static final int CRAB = 30;     // kepiting, patroli ke kiri-kanan
    static final int HOG = 31;      // babi hutan, patroli maju-mundur
    static final int PLATFORM = 40; // pijakan bergerak di atas jurang
    static final int GOAL = 50;

    static final int DIE_NONE = 0, DIE_KNOCK = 1, DIE_SQUASH = 2;

    final int type;
    float x, y, z;
    float baseX, baseZ, prevX;
    float vx, vy, vz;
    float t, phase;
    float fuse = -1f;
    float squash;
    int hits;
    boolean alive = true;
    int dieMode = DIE_NONE;
    float dieT;
    float key; // kedalaman untuk urutan gambar

    Entity(int type, float x, float y, float z) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.baseX = x;
        this.baseZ = z;
        this.prevX = x;
    }

    boolean isCrate() {
        return type <= IRON;
    }

    boolean isEnemy() {
        return type == CRAB || type == HOG;
    }

    boolean isBreakable() {
        return isCrate() && type != IRON;
    }

    boolean isExplosive() {
        return type == TNT || type == NITRO;
    }
}
