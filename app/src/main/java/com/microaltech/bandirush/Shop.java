package com.microaltech.bandirush;

/** Daftar barang di toko, dibayar dengan wumpa yang terkumpul. */
final class Shop {
    static final int KIND_CHAR = 0, KIND_COSTUME = 1, KIND_MASK = 2, KIND_LIFE = 3;
    static final int CHAR_BANDI = 0, CHAR_NIA = 1;
    static final int MAX_START_MASKS = 3;

    static final class Item {
        final int kind, id, price;
        final String name, desc;

        Item(int kind, int id, int price, String name, String desc) {
            this.kind = kind;
            this.id = id;
            this.price = price;
            this.name = name;
            this.desc = desc;
        }
    }

    /** Urutan penting: indeks barang dipakai sebagai bit di Save.owned. */
    static final Item[] ITEMS = {
            new Item(KIND_CHAR, CHAR_BANDI, 0, "Bandi", "Lari cepat, lompatan normal."),
            new Item(KIND_CHAR, CHAR_NIA, 300, "Nia", "Lompat lebih tinggi, tapi lari sedikit lebih pelan."),
            new Item(KIND_COSTUME, 0, 0, "Klasik", "Pakaian standar."),
            new Item(KIND_COSTUME, 1, 150, "Pantai", "Celana renang oranye dan kacamata hitam."),
            new Item(KIND_COSTUME, 2, 200, "Ninja", "Serba hitam dengan ikat kepala merah."),
            new Item(KIND_COSTUME, 3, 250, "Musim Dingin", "Syal merah dan sarung tangan hangat."),
            new Item(KIND_COSTUME, 4, 500, "Raja", "Mahkota emas dan celana ungu kerajaan."),
            new Item(KIND_COSTUME, 5, 800, "Emas", "Berkilau dari ujung kepala sampai kaki."),
            new Item(KIND_MASK, 0, 60, "Topeng Awal", "Mulai level berikutnya dengan topeng pelindung (maks 3)."),
            new Item(KIND_LIFE, 0, 100, "Nyawa +1", "Langsung tambah 1 nyawa."),
    };

    static final int DEFAULT_OWNED = (1 << 0) | (1 << 2);

    private Shop() {
    }

    static int itemIndex(int kind, int id) {
        for (int i = 0; i < ITEMS.length; i++) {
            if (ITEMS[i].kind == kind && ITEMS[i].id == id) return i;
        }
        return -1;
    }

    static float runSpeed(int character) {
        return character == CHAR_NIA ? 4.9f : 5.2f;
    }

    static float jumpV(int character) {
        return character == CHAR_NIA ? 9.6f : 8.8f;
    }

    static Skin skin(int character, int costume) {
        Skin s = new Skin();
        if (character == CHAR_NIA) {
            s.girl = true;
            s.fur = 0xFFFFA940;
            s.hair = 0xFFFFE082;
            s.pants = 0xFFEC407A;
            s.shoe = 0xFFFFFFFF;
            s.glove = 0xFFFFA940;
        }
        switch (costume) {
            case 1:
                s.pants = 0xFFFF7043;
                s.shoe = 0xFF29B6F6;
                s.acc = Skin.ACC_SHADES;
                break;
            case 2:
                s.pants = 0xFF212121;
                s.shoe = 0xFF212121;
                s.glove = 0xFF212121;
                s.acc = Skin.ACC_HEADBAND;
                break;
            case 3:
                s.pants = 0xFF3949AB;
                s.shoe = 0xFFFFFFFF;
                s.glove = 0xFFE53935;
                s.acc = Skin.ACC_SCARF;
                break;
            case 4:
                s.pants = 0xFF6A1B9A;
                s.shoe = 0xFFFFC107;
                s.glove = 0xFFFFC107;
                s.acc = Skin.ACC_CROWN;
                break;
            case 5:
                s.fur = 0xFFFFD54F;
                s.tan = 0xFFFFF8E1;
                s.pants = 0xFFFFB300;
                s.shoe = 0xFFFFA000;
                s.glove = 0xFFFFA000;
                break;
            default:
                break;
        }
        return s;
    }
}
