package com.microaltech.bandirush;

import java.util.ArrayList;

/**
 * Peta level berbentuk grid. Setiap baris = 1 unit ke depan (sumbu Z), setiap kolom = 1 unit ke samping (X).
 *
 * Legenda karakter:
 *   '.' tanah         ' ' jurang          'S' posisi awal     'G' finish (kristal)
 *   'C' peti kayu     '?' peti 5 wumpa    'B' peti pantul     'L' peti nyawa
 *   'A' peti topeng   'T' TNT             'N' Nitro           'K' checkpoint
 *   'I' peti besi     'W' wumpa           'E' kepiting        'H' babi hutan
 *   '2' / '3' tumpukan 2 / 3 peti kayu    '!' peti "!"        'U' palang rendah (harus meluncur)
 *   'o' peti bergaris (jadi peti kayu setelah "!" diaktifkan)
 *   'O' peti bergaris di atas jurang (jadi peti besi / batu loncatan)
 *   'X' posisi bos                    'P' pintu area bonus (butuh Def.bonus)
 *   'w' wumpa melayang di atas jurang     'm' pijakan bergerak di atas jurang
 *   'i' peti besi di atas jurang          'b' peti pantul di atas jurang
 */
final class Level {

    static final int MODE_NORMAL = 0, MODE_CHASE = 1, MODE_BOSS = 2, MODE_RIDE = 3;

    static final class Def {
        final String name;
        final Theme theme;
        final int mode;
        final int music;
        final float targetTime; // target Time Trial untuk relik emas (detik)
        final String hint;
        final String[] map; // ditulis dari ujung (atas) ke awal (bawah)
        int[] tipRows = new int[0];
        String[] tipTexts = new String[0];
        Def bonus;
        boolean tutorial;

        Def(String name, Theme theme, int mode, int music, float targetTime, String hint, String... map) {
            this.name = name;
            this.theme = theme;
            this.mode = mode;
            this.music = music;
            this.targetTime = targetTime;
            this.hint = hint;
            this.map = map;
        }

        /** Petunjuk yang muncul saat pemain melewati baris tertentu, format "baris:teks". */
        Def tips(String... tips) {
            tipRows = new int[tips.length];
            tipTexts = new String[tips.length];
            for (int i = 0; i < tips.length; i++) {
                int colon = tips[i].indexOf(':');
                tipRows[i] = Integer.parseInt(tips[i].substring(0, colon).trim());
                tipTexts[i] = tips[i].substring(colon + 1).trim();
            }
            return this;
        }

        /** Area bonus yang dimasuki lewat petak 'P'. */
        Def bonus(Def b) {
            bonus = b;
            return this;
        }

        Def tutorial() {
            tutorial = true;
            return this;
        }
    }

    final Def def;
    final String name;
    final Theme theme;
    final int mode;
    final int width;
    final int rows;
    final boolean[][] ground;
    final ArrayList<Entity> entities = new ArrayList<>();
    float startX, startZ;
    float bossX, bossZ;
    int goalRow;
    int totalCrates;

    Level(Def def) {
        this.def = def;
        name = def.name;
        theme = def.theme;
        mode = def.mode;
        rows = def.map.length;
        int wMax = 0;
        for (String s : def.map) wMax = Math.max(wMax, s.length());
        width = wMax;
        ground = new boolean[rows][width];
        goalRow = mode == MODE_BOSS ? rows + 100 : rows - 1;
        startX = 0f;
        startZ = 0.5f;
        bossZ = rows - 1.5f;

        for (int r = 0; r < rows; r++) {
            String line = def.map[rows - 1 - r];
            for (int c = 0; c < width; c++) {
                char ch = c < line.length() ? line.charAt(c) : ' ';
                float x = colX(c);
                float z = r + 0.5f;
                ground[r][c] = " wmibO".indexOf(ch) < 0;
                switch (ch) {
                    case 'S': startX = x; startZ = z; break;
                    case 'X': bossX = x; bossZ = z; break;
                    case 'G': goalRow = r; add(Entity.GOAL, x, 0f, z, r, c); break;
                    case 'C': add(Entity.CRATE, x, 0f, z, r, c); break;
                    case '2':
                    case '3': {
                        int n = ch - '0';
                        for (int k = 0; k < n; k++) add(Entity.CRATE, x, k * 0.9f, z, r, c);
                        break;
                    }
                    case '?': add(Entity.QCRATE, x, 0f, z, r, c); break;
                    case 'B': case 'b': add(Entity.BOUNCE, x, 0f, z, r, c); break;
                    case 'L': add(Entity.LIFE, x, 0f, z, r, c); break;
                    case 'A': add(Entity.AKU, x, 0f, z, r, c); break;
                    case 'T': add(Entity.TNT, x, 0f, z, r, c); break;
                    case 'N': add(Entity.NITRO, x, 0f, z, r, c); break;
                    case 'K': add(Entity.CHECK, x, 0f, z, r, c); break;
                    case 'I': case 'i': add(Entity.IRON, x, 0f, z, r, c); break;
                    case '!': add(Entity.EXCL, x, 0f, z, r, c); break;
                    case 'o': add(Entity.OUTLINE, x, 0f, z, r, c).outlineTo = Entity.CRATE; break;
                    case 'O': add(Entity.OUTLINE, x, 0f, z, r, c).outlineTo = Entity.IRON; break;
                    case 'U': add(Entity.BARRIER, x, 0f, z, r, c); break;
                    case 'W': add(Entity.WUMPA, x, 0f, z, r, c); break;
                    case 'w': add(Entity.WUMPA, x, 0.5f, z, r, c); break;
                    case 'E': {
                        Entity e = add(Entity.CRAB, x, 0f, z, r, c);
                        float amp = patrolAmp(0.45f);
                        float s = amp > 0f ? Math.max(-1f, Math.min(1f, x / amp)) : 0f;
                        e.phase = (float) Math.asin(s);
                        break;
                    }
                    case 'H': add(Entity.HOG, x, 0f, z, r, c); break;
                    case 'm': add(Entity.PLATFORM, x, 0f, z, r, c); break;
                    case 'P': add(Entity.PAD, x, 0f, z, r, c); break;
                    default: break;
                }
            }
        }
        for (Entity e : entities) {
            if (e.isBreakable() || (e.type == Entity.OUTLINE && e.outlineTo == Entity.CRATE)) totalCrates++;
        }
    }

    private Entity add(int type, float x, float y, float z, int r, int c) {
        Entity e = new Entity(type, x, y, z);
        e.phase = r * 1.37f + c * 0.71f;
        entities.add(e);
        return e;
    }

    float colX(int c) {
        return c - (width - 1) * 0.5f;
    }

    float halfWidth() {
        return width * 0.5f;
    }

    /** Amplitudo gerakan kiri-kanan yang muat di antara dinding. */
    float patrolAmp(float halfSize) {
        return Math.max(0f, halfWidth() - halfSize - 0.05f);
    }

    boolean groundAt(int r, int c) {
        if (c < 0 || c >= width) return false;
        if (r < 0) return true;
        if (r >= rows) return false;
        return ground[r][c];
    }

    boolean groundUnder(float x, float z) {
        int r = (int) Math.floor(z);
        int c = Math.round(x + (width - 1) * 0.5f);
        return groundAt(r, c);
    }
}
