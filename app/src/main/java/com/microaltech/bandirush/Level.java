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
 *   'w' wumpa melayang di atas jurang     'm' pijakan bergerak di atas jurang
 *   'i' peti besi di atas jurang          'b' peti pantul di atas jurang
 */
final class Level {

    static final class Def {
        final String name;
        final Theme theme;
        final String[] map; // ditulis dari ujung (atas) ke awal (bawah)

        Def(String name, Theme theme, String... map) {
            this.name = name;
            this.theme = theme;
            this.map = map;
        }
    }

    final String name;
    final Theme theme;
    final int width;
    final int rows;
    final boolean[][] ground;
    final ArrayList<Entity> entities = new ArrayList<>();
    float startX, startZ;
    int goalRow;
    int totalCrates;

    Level(Def def) {
        name = def.name;
        theme = def.theme;
        rows = def.map.length;
        int wMax = 0;
        for (String s : def.map) wMax = Math.max(wMax, s.length());
        width = wMax;
        ground = new boolean[rows][width];
        goalRow = rows - 1;
        startX = 0f;
        startZ = 0.5f;

        for (int r = 0; r < rows; r++) {
            String line = def.map[rows - 1 - r];
            for (int c = 0; c < width; c++) {
                char ch = c < line.length() ? line.charAt(c) : ' ';
                float x = colX(c);
                float z = r + 0.5f;
                ground[r][c] = ch != ' ' && ch != 'w' && ch != 'm' && ch != 'i' && ch != 'b';
                Entity e = null;
                switch (ch) {
                    case 'S': startX = x; startZ = z; break;
                    case 'G': goalRow = r; e = new Entity(Entity.GOAL, x, 0f, z); break;
                    case 'C': e = new Entity(Entity.CRATE, x, 0f, z); break;
                    case '?': e = new Entity(Entity.QCRATE, x, 0f, z); break;
                    case 'B': case 'b': e = new Entity(Entity.BOUNCE, x, 0f, z); break;
                    case 'L': e = new Entity(Entity.LIFE, x, 0f, z); break;
                    case 'A': e = new Entity(Entity.AKU, x, 0f, z); break;
                    case 'T': e = new Entity(Entity.TNT, x, 0f, z); break;
                    case 'N': e = new Entity(Entity.NITRO, x, 0f, z); break;
                    case 'K': e = new Entity(Entity.CHECK, x, 0f, z); break;
                    case 'I': case 'i': e = new Entity(Entity.IRON, x, 0f, z); break;
                    case 'W': e = new Entity(Entity.WUMPA, x, 0f, z); break;
                    case 'w': e = new Entity(Entity.WUMPA, x, 0.5f, z); break;
                    case 'E': e = new Entity(Entity.CRAB, x, 0f, z); break;
                    case 'H': e = new Entity(Entity.HOG, x, 0f, z); break;
                    case 'm': e = new Entity(Entity.PLATFORM, x, 0f, z); break;
                    default: break;
                }
                if (e != null) {
                    e.phase = r * 1.37f + c * 0.71f;
                    if (e.type == Entity.CRAB) {
                        float amp = patrolAmp(0.6f);
                        float s = amp > 0f ? Math.max(-1f, Math.min(1f, x / amp)) : 0f;
                        e.phase = (float) Math.asin(s);
                    }
                    if (e.isBreakable()) totalCrates++;
                    entities.add(e);
                }
            }
        }
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
