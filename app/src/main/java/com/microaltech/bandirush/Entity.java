package com.microaltech.bandirush;

/** Semua objek di dunia game: peti, buah wumpa, musuh, pijakan bergerak, rintangan, dan kristal finish. */
final class Entity {
    // Peti (urutan penting: semua tipe <= EXCL adalah peti padat, tipe <= CHECK bisa dihancurkan)
    static final int CRATE = 0;     // peti kayu biasa: 1 wumpa
    static final int QCRATE = 1;    // peti "?": 5 wumpa
    static final int BOUNCE = 2;    // peti pantul
    static final int LIFE = 3;      // peti nyawa
    static final int AKU = 4;       // peti topeng pelindung
    static final int TNT = 5;       // meledak 3 detik setelah diinjak
    static final int NITRO = 6;     // meledak kalau disentuh
    static final int CHECK = 7;     // checkpoint
    static final int IRON = 8;      // peti besi, tidak bisa hancur
    static final int EXCL = 9;      // peti "!": memunculkan peti bergaris

    static final int OUTLINE = 10;  // peti bergaris (hantu), jadi nyata setelah peti "!" diaktifkan
    static final int WUMPA = 20;
    static final int CRAB = 30;     // kepiting, patroli ke kiri-kanan
    static final int HOG = 31;      // babi hutan, patroli maju-mundur
    static final int PLATFORM = 40; // pijakan bergerak di atas jurang
    static final int GOAL = 50;
    static final int BARRIER = 60;  // palang rendah, hanya bisa dilewati sambil meluncur
    static final int BOULDER = 70;  // batu raksasa (hanya untuk gambar)
    static final int BOSS = 80;     // proxy gambar bos
    static final int BOMB = 90;     // bom lemparan bos
    static final int PAD = 100;     // pintu masuk area bonus

    static final int DIE_NONE = 0, DIE_KNOCK = 1, DIE_SQUASH = 2;

    int type;
    int outlineTo = CRATE;
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
        return type <= EXCL;
    }

    boolean isEnemy() {
        return type == CRAB || type == HOG;
    }

    boolean isBreakable() {
        return type <= CHECK;
    }

    boolean isExplosive() {
        return type == TNT || type == NITRO;
    }
}
