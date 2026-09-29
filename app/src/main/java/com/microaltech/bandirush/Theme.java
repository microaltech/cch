package com.microaltech.bandirush;

/** Palet warna tiap dunia. */
final class Theme {
    final int sky1, sky2, fog, groundA, groundB, groundEdge, dirt, wallA, wallB, wallTop, leaf, trunk, hill, abyss;
    final boolean trees;
    final boolean night;

    Theme(int sky1, int sky2, int fog, int groundA, int groundB, int groundEdge, int dirt,
          int wallA, int wallB, int wallTop, int leaf, int trunk, int hill, int abyss,
          boolean trees, boolean night) {
        this.sky1 = sky1;
        this.sky2 = sky2;
        this.fog = fog;
        this.groundA = groundA;
        this.groundB = groundB;
        this.groundEdge = groundEdge;
        this.dirt = dirt;
        this.wallA = wallA;
        this.wallB = wallB;
        this.wallTop = wallTop;
        this.leaf = leaf;
        this.trunk = trunk;
        this.hill = hill;
        this.abyss = abyss;
        this.trees = trees;
        this.night = night;
    }

    static final Theme JUNGLE = new Theme(
            0xFF2F8FD8, 0xFFBFE6FF, 0xFFA9D9EC,
            0xFFCFA86E, 0xFFBE965C, 0xFF6DB33F, 0xFF6B4423,
            0xFF2E7D32, 0xFF388E3C, 0xFF4CAF50, 0xFF2E8B3A, 0xFF7B5230,
            0xFF6FA88A, 0xFF14231A, true, false);

    static final Theme SUNSET = new Theme(
            0xFFFF7A45, 0xFFFFD68A, 0xFFF4C18E,
            0xFFA7713F, 0xFF936234, 0xFF7CA83A, 0xFF4E342E,
            0xFF7D5A44, 0xFF6E4F3B, 0xFF8D6E63, 0xFF5B8C2A, 0xFF5D4037,
            0xFFB5654A, 0xFF2B1A12, true, false);

    static final Theme TEMPLE = new Theme(
            0xFF140F3A, 0xFF4B3B8F, 0xFF3A3169,
            0xFFA3A3AD, 0xFF8E8E99, 0xFF6F7D6A, 0xFF4D4B5C,
            0xFF6D6A7A, 0xFF625F70, 0xFF7E7B8C, 0xFF2F6B4F, 0xFF4E4A5E,
            0xFF2A2360, 0xFF08070F, false, true);
}
