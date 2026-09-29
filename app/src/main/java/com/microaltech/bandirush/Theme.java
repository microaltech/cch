package com.microaltech.bandirush;

/** Palet warna tiap dunia. */
final class Theme {
    static final int DECOR_PALM = 0, DECOR_PINE = 1, DECOR_TORCH = 2;

    final int sky1, sky2, fog, groundA, groundB, groundEdge, dirt, wallA, wallB, wallTop, leaf, trunk, hill, abyss;
    final int decor;
    final boolean night;

    Theme(int sky1, int sky2, int fog, int groundA, int groundB, int groundEdge, int dirt,
          int wallA, int wallB, int wallTop, int leaf, int trunk, int hill, int abyss,
          int decor, boolean night) {
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
        this.decor = decor;
        this.night = night;
    }

    boolean hasTrees() {
        return decor != DECOR_TORCH;
    }

    static final Theme JUNGLE = new Theme(
            0xFF2F8FD8, 0xFFBFE6FF, 0xFFA9D9EC,
            0xFFCFA86E, 0xFFBE965C, 0xFF6DB33F, 0xFF6B4423,
            0xFF2E7D32, 0xFF388E3C, 0xFF4CAF50, 0xFF2E8B3A, 0xFF7B5230,
            0xFF6FA88A, 0xFF14231A, DECOR_PALM, false);

    static final Theme SUNSET = new Theme(
            0xFFFF7A45, 0xFFFFD68A, 0xFFF4C18E,
            0xFFA7713F, 0xFF936234, 0xFF7CA83A, 0xFF4E342E,
            0xFF7D5A44, 0xFF6E4F3B, 0xFF8D6E63, 0xFF5B8C2A, 0xFF5D4037,
            0xFFB5654A, 0xFF2B1A12, DECOR_PALM, false);

    static final Theme TEMPLE = new Theme(
            0xFF140F3A, 0xFF4B3B8F, 0xFF3A3169,
            0xFFA3A3AD, 0xFF8E8E99, 0xFF6F7D6A, 0xFF4D4B5C,
            0xFF6D6A7A, 0xFF625F70, 0xFF7E7B8C, 0xFF2F6B4F, 0xFF4E4A5E,
            0xFF2A2360, 0xFF08070F, DECOR_TORCH, true);

    static final Theme SNOW = new Theme(
            0xFF7FA9D6, 0xFFE3F2FD, 0xFFDCE7F0,
            0xFFF5F9FC, 0xFFE2EBF3, 0xFFD4E3EE, 0xFF7A8A99,
            0xFFB0C4D6, 0xFFA3B8CB, 0xFFF0F6FA, 0xFF2E5E4E, 0xFF5D4037,
            0xFFB8CCE0, 0xFF1A2530, DECOR_PINE, false);

    static final Theme BEACH = new Theme(
            0xFF1E9FE8, 0xFFB3E5FC, 0xFFB7E3F7,
            0xFFF2D8A0, 0xFFE8CB8C, 0xFFE0BE78, 0xFF9C7A4A,
            0xFF8D8074, 0xFF7E7266, 0xFFA1968A, 0xFF43A047, 0xFF8D6E63,
            0xFF4FC3F7, 0xFF01579B, DECOR_PALM, false);
}
