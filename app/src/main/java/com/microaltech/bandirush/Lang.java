package com.microaltech.bandirush;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Terjemahan Indonesia -> Inggris. Teks di kode ditulis dalam bahasa Indonesia; saat mode Inggris aktif,
 * setiap teks yang digambar lewat {@link #t(String)} diterjemahkan: dulu dicari utuh, lalu potongan
 * (untuk teks dinamis seperti "Peti: 3 / 10").
 */
final class Lang {
    static volatile boolean en;

    private static final HashMap<String, String> EXACT = new HashMap<>();
    private static final String[][] FRAGMENTS;
    private static final HashMap<String, String> CACHE = new HashMap<>();

    private Lang() {
    }

    static String t(String s) {
        if (!en || s == null || s.isEmpty()) return s;
        String r = EXACT.get(s);
        if (r != null) return r;
        synchronized (CACHE) {
            r = CACHE.get(s);
            if (r != null) return r;
            r = s;
            for (String[] f : FRAGMENTS) {
                if (r.contains(f[0])) r = r.replace(f[0], f[1]);
            }
            if (CACHE.size() > 400) CACHE.clear();
            CACHE.put(s, r);
            return r;
        }
    }

    private static void x(String id, String en) {
        EXACT.put(id, en);
    }

    static {
        // ---- umum & menu ----
        x("BANDI RUSH", "BANDI RUSH");
        x("Petualangan di Pulau Wumpa", "Adventures on Wumpa Island");
        x("Ketuk layar untuk mulai", "Tap the screen to start");
        x("Joystick kiri: gerak  |  X: lompat  |  O: putar  |  ▼: meluncur", "Left joystick: move  |  X: jump  |  O: spin  |  ▼: slide");
        x("Hancurkan semua peti untuk kristal. Kalahkan waktu untuk relik!", "Smash every crate for a gem. Beat the clock for relics!");
        x("PILIH LEVEL", "SELECT LEVEL");
        x("TOKO", "SHOP");
        x("PENCAPAIAN", "ACHIEVEMENTS");
        x("EDITOR", "EDITOR");
        x("HARIAN", "DAILY");
        x("TANPA AKHIR", "ENDLESS");
        x("SKOR", "SCORES");
        x("Mulai di sini!", "Start here!");
        x("SELESAI", "DONE");
        x("BOS", "BOSS");
        x("KEJAR", "CHASE");
        x("TUNGGANG", "RIDE");
        x("+BONUS", "+BONUS");
        x("AIR", "WATER");
        x("GELAP", "DARK");
        x("ES", "ICE");
        x("JEDA", "PAUSED");
        x("LANJUTKAN", "RESUME");
        x("PENGATURAN", "SETTINGS");
        x("KE PETA", "TO MAP");
        x("KE EDITOR", "TO EDITOR");
        x("KEMBALI", "BACK");
        x("LEVEL SELESAI!", "LEVEL COMPLETE!");
        x("TUTORIAL SELESAI!", "TUTORIAL COMPLETE!");
        x("TANTANGAN SELESAI!", "CHALLENGE COMPLETE!");
        x("LARI SELESAI!", "RUN OVER!");
        x("REKOR BARU!", "NEW RECORD!");
        x("Ketuk untuk lanjut", "Tap to continue");
        x("Ketuk untuk kembali ke editor", "Tap to return to the editor");
        x("Ketuk untuk kembali ke peta", "Tap to return to the map");
        x("GAME OVER", "GAME OVER");
        x("TAMAT! KAMU MENANG!", "THE END! YOU WIN!");
        x("Coba Time Trial untuk mengumpulkan relik emas!", "Try Time Trials to collect gold relics!");
        x("Level buatanmu berhasil ditamatkan!", "You beat your own level!");
        x("Sekarang kamu siap menjelajahi Pulau Wumpa!", "You are ready to explore Wumpa Island!");
        x("Bos dikalahkan!  +1 KRISTAL", "Boss defeated!  +1 GEM");
        x("Semua peti hancur!  +1 KRISTAL", "All crates smashed!  +1 GEM");
        x("Kristal sudah dimiliki", "Gem already collected");
        x("Level berikutnya terbuka!  Time Trial juga terbuka.", "Next level unlocked!  Time Trial unlocked too.");
        x("Bonus harian pertama: +50 wumpa!", "First daily clear: +50 wumpa!");
        x("RELIK EMAS", "GOLD RELIC");
        x("RELIK PERAK", "SILVER RELIC");
        x("RELIK PERUNGGU", "BRONZE RELIC");
        x("AREA BONUS", "BONUS AREA");
        x("RAJA KEPITING", "KING CRAB");
        x("PENCAPAIAN TERBUKA!", "ACHIEVEMENT UNLOCKED!");
        x("DIPAKAI", "EQUIPPED");
        x("PAKAI", "EQUIP");
        x("Wumpa yang kamu kumpulkan di level tersimpan di sini. Ketuk barang untuk membeli / memakai.",
                "Wumpa you collect is saved here. Tap an item to buy / equip it.");
        x("SKOR TERTINGGI", "HIGH SCORES");
        x("TANTANGAN HARIAN", "DAILY CHALLENGE");
        x("Belum ada skor", "No scores yet");
        x("ATUR TOMBOL", "ARRANGE BUTTONS");
        x("Ketuk tombol lalu seret untuk memindahkan. Pakai - / + untuk ukuran.", "Tap a button, then drag to move it. Use - / + to resize.");
        x("- KECIL", "- SMALLER");
        x("+ BESAR", "+ BIGGER");
        x("RESET", "RESET");

        // ---- pesan dalam game ----
        x("Topeng dari toko dipakai!", "Shop mask equipped!");
        x("Bonus gagal - coba lagi!", "Bonus failed - try again!");
        x("BONUS SELESAI!", "BONUS COMPLETE!");
        x("CHECKPOINT!", "CHECKPOINT!");
        x("NYAWA +1", "EXTRA LIFE!");
        x("TOPENG PELINDUNG!", "PROTECTIVE MASK!");
        x("PETI MUNCUL!", "CRATES APPEARED!");
        x("RAJA KEPITING KALAH!", "KING CRAB DEFEATED!");
        x("Progres dihapus", "Progress erased");
        x("Level ditempel dari clipboard!", "Level pasted from clipboard!");
        x("Teks di clipboard bukan level Bandi Rush", "Clipboard text is not a Bandi Rush level");
        x("Level disalin ke clipboard - bagikan ke temanmu!", "Level copied to clipboard - share it with friends!");

        // ---- pengaturan ----
        x("Musik", "Music");
        x("Efek suara", "Sound effects");
        x("Getar", "Vibration");
        x("Ukuran tombol", "Button size");
        x("Posisi tombol", "Button layout");
        x("Mode kidal", "Left-handed mode");
        x("Bahasa", "Language");
        x("Jarak kamera", "Camera distance");
        x("Sudut kamera", "Camera angle");
        x("Hapus progres", "Erase progress");
        x("NYALA", "ON");
        x("MATI", "OFF");
        x("KECIL", "SMALL");
        x("SEDANG", "MEDIUM");
        x("BESAR", "LARGE");
        x("ATUR", "EDIT");
        x("DEKAT", "NEAR");
        x("NORMAL", "NORMAL");
        x("JAUH", "FAR");
        x("RENDAH", "LOW");
        x("TINGGI", "HIGH");
        x("HAPUS", "ERASE");
        x("YAKIN? KETUK LAGI", "SURE? TAP AGAIN");

        // ---- editor ----
        x("UJI MAIN", "TEST PLAY");
        x("SIMPAN", "SAVE");
        x("TEMA", "THEME");
        x("+ BARIS", "+ ROW");
        x("- BARIS", "- ROW");
        x("SALIN", "COPY");
        x("TEMPEL", "PASTE");
        x("KOSONGKAN", "CLEAR");
        x("YAKIN?", "SURE?");
        x("jauh", "far");
        x("awal", "start");
        x("Level butuh tepat 1 'S' (awal) dan minimal 1 'G' (finish)", "A level needs exactly 1 'S' (start) and at least 1 'G' (finish)");
        x("Level buatanmu! Tekan jeda untuk kembali ke editor.", "Your own level! Press pause to return to the editor.");
        x("Grid dikosongkan", "Grid cleared");
        x("Ketuk KOSONGKAN sekali lagi untuk menghapus semua", "Tap CLEAR once more to erase everything");

        // ---- toko ----
        x("Lari cepat, lompatan normal.", "Runs fast, normal jump.");
        x("Lompat lebih tinggi, tapi lari sedikit lebih pelan.", "Jumps higher, but runs a bit slower.");
        x("Klasik", "Classic");
        x("Pakaian standar.", "Standard outfit.");
        x("Pantai", "Beach");
        x("Celana renang oranye dan kacamata hitam.", "Orange swim shorts and sunglasses.");
        x("Serba hitam dengan ikat kepala merah.", "All black with a red headband.");
        x("Musim Dingin", "Winter");
        x("Syal merah dan sarung tangan hangat.", "Red scarf and warm gloves.");
        x("Raja", "King");
        x("Mahkota emas dan celana ungu kerajaan.", "Golden crown and royal purple pants.");
        x("Emas", "Gold");
        x("Berkilau dari ujung kepala sampai kaki.", "Shiny from head to toe.");
        x("Topeng Awal", "Starter Mask");
        x("Mulai level berikutnya dengan topeng pelindung (maks 3).", "Start your next level with a protective mask (max 3).");
        x("Nyawa +1", "Extra Life");
        x("Langsung tambah 1 nyawa.", "Get 1 extra life right away.");

        // ---- pencapaian ----
        String[][] ach = {
                {"Murid Teladan", "Model Student"}, {"Langkah Pertama", "First Steps"}, {"Tukang Kayu", "Carpenter"},
                {"Penghancur", "Wrecker"}, {"Pecinta Wumpa", "Wumpa Lover"}, {"Tak Tersentuh", "Untouchable"},
                {"Pemburu Kristal", "Gem Hunter"}, {"Sang Juara", "Champion"}, {"Pelari Kilat", "Speedrunner"},
                {"Kolektor Relik", "Relic Collector"}, {"Jagoan Putar", "Spin Master"}, {"Penjelajah Rahasia", "Secret Explorer"},
                {"Joki Babi", "Hog Jockey"}, {"Arsitek", "Architect"}, {"Fashionista", "Fashionista"},
                {"Pemanasan Harian", "Daily Warm-up"}, {"Pelari Jarak Jauh", "Long-distance Runner"}, {"Penjelajah Dunia", "World Explorer"},
                {"Selesaikan tutorial", "Finish the tutorial"}, {"Selesaikan level 1", "Finish level 1"},
                {"Hancurkan 100 peti", "Smash 100 crates"}, {"Hancurkan 500 peti", "Smash 500 crates"},
                {"Kumpulkan 500 wumpa", "Collect 500 wumpa"}, {"Selesaikan level tanpa mati", "Finish a level without dying"},
                {"Kumpulkan semua kristal", "Collect every gem"}, {"Kalahkan Raja Kepiting", "Defeat the King Crab"},
                {"Dapatkan relik emas", "Earn a gold relic"}, {"Dapatkan relik di semua level", "Earn a relic in every level"},
                {"Kalahkan 50 musuh", "Defeat 50 enemies"}, {"Selesaikan area bonus", "Clear a bonus area"},
                {"Selesaikan level tunggangan", "Finish the ride level"}, {"Selesaikan level buatanmu", "Beat a level you made"},
                {"Beli kostum pertama", "Buy your first costume"}, {"Selesaikan Tantangan Harian", "Finish a Daily Challenge"},
                {"Tempuh 300 m di mode Tanpa Akhir", "Run 300 m in Endless mode"},
                {"Selesaikan Sungai, Gua & Gletser", "Finish the River, Cave & Glacier"},
        };
        for (String[] a : ach) x(a[0], a[1]);

        // ---- level ----
        String[][] lv = {
                {"Tutorial", "Tutorial"}, {"Ikuti petunjuk di atas layar.", "Follow the hints at the top of the screen."},
                {"Hutan Wumpa", "Wumpa Jungle"}, {"Jembatan Senja", "Sunset Bridge"}, {"Kuil TNT", "TNT Temple"},
                {"Puncak Salju", "Snowy Peak"}, {"Tunggangan Babi", "Hog Ride"}, {"Kejaran Batu", "Boulder Chase"},
                {"Raja Kepiting", "King Crab"}, {"Sungai Deras", "Rushing River"}, {"Gua Gelap", "Dark Cave"},
                {"Gletser Licin", "Slippery Glacier"}, {"Bonus Hutan", "Jungle Bonus"}, {"Bonus Salju", "Snow Bonus"},
                {"Tanpa Akhir", "Endless"},
                {"Tombol X lompat, O putar. Injak atau putar peti!", "X jumps, O spins. Stomp or spin the crates!"},
                {"Hancurkan semua peti! Jatuh di sini tidak mengurangi nyawa.", "Smash every crate! Falling here costs no lives."},
                {"Tahan X saat mendarat di peti pantul untuk lompat lebih tinggi.", "Hold X when landing on a bounce crate to jump higher."},
                {"Jangan sentuh Nitro hijau! TNT meledak 3 detik setelah diinjak.", "Don't touch green Nitro! TNT blows up 3 seconds after you step on it."},
                {"Tombol ▼ untuk meluncur. Meluncur + lompat = lompat jauh!", "Press ▼ to slide. Slide + jump = long jump!"},
                {"Babi berlari sendiri! Geser kiri-kanan, lompati jurang, hindari peti besi & Nitro.", "The hog runs by itself! Steer, jump the gaps, avoid iron crates & Nitro."},
                {"LARI! Batu raksasa mengejar dari belakang!", "RUN! A giant boulder is right behind you!"},
                {"Hindari bom! Saat bos pusing, putar atau injak dia.", "Dodge the bombs! When the boss is dizzy, spin or stomp it."},
                {"Kayu apung tenggelam kalau diinjak terlalu lama. Awas piranha!", "Floating logs sink if you stand too long. Watch out for piranhas!"},
                {"Gelap gulita! Kelelawar terbang rendah: meluncur di bawahnya atau putar.", "Pitch dark! Bats fly low: slide under them or spin."},
                {"Lantai es licin! Bandi susah berhenti. Awas pinguin yang meluncur.", "Slippery ice! Hard to stop. Beware of sliding penguins."},
                {"Tantangan hari ini! Hancurkan peti & selesaikan secepat mungkin.", "Today's challenge! Smash crates and finish as fast as you can."},
                {"Lari sejauh mungkin! Hanya satu nyawa.", "Run as far as you can! Only one life."},
                {"Selamat datang! Dorong joystick kiri ke atas untuk berlari.", "Welcome! Push the left joystick up to run."},
                {"Ambil buah wumpa. 100 wumpa = 1 nyawa, dan wumpa bisa dibelanjakan di Toko!", "Grab wumpa fruit. 100 wumpa = 1 life, and you can spend wumpa in the Shop!"},
                {"Tekan X untuk melompati jurang. Tahan X untuk lompat lebih tinggi.", "Press X to jump over the gap. Hold X to jump higher."},
                {"Lompat ke atas peti untuk menghancurkannya.", "Jump on a crate to break it."},
                {"Tekan O untuk berputar dan menghancurkan peti di sekitarmu.", "Press O to spin and smash crates around you."},
                {"Putar atau injak musuh untuk mengalahkannya!", "Spin into or stomp enemies to defeat them!"},
                {"Peti pantul melempar tinggi. Tahan X saat mendarat untuk lebih tinggi.", "Bounce crates launch you high. Hold X when landing to go higher."},
                {"Tekan tombol ▼ untuk meluncur di bawah palang.", "Press ▼ to slide under the barrier."},
                {"Jurang lebar: meluncur lalu lompat = lompat jauh! Atau tekan O di udara.", "Wide gap: slide then jump = long jump! Or press O in mid-air."},
                {"TNT meledak 3 detik setelah diinjak. Segera menjauh!", "TNT explodes 3 seconds after you step on it. Get away!"},
                {"Nitro hijau meledak kalau disentuh. Jangan sampai kena!", "Green Nitro explodes on touch. Don't touch it!"},
                {"Peti C = checkpoint. Peti A = topeng yang menahan 1 serangan.", "C crate = checkpoint. A crate = a mask that blocks 1 hit."},
                {"Aktifkan peti ! untuk memunculkan pijakan di atas jurang.", "Hit the ! crate to make stepping stones appear over the gap."},
                {"Kristal di depan adalah garis finish. Kamu siap bertualang!", "The gem ahead is the finish line. You're ready for adventure!"},
        };
        for (String[] l : lv) x(l[0], l[1]);

        // ---- nama petak editor & tema ----
        String[][] tiles = {
                {"Tanah", "Ground"}, {"Jurang", "Pit"}, {"Posisi awal", "Start"}, {"Finish", "Finish"},
                {"Peti kayu", "Wooden crate"}, {"Peti ?", "? crate"}, {"Peti pantul", "Bounce crate"},
                {"Peti nyawa", "Life crate"}, {"Peti topeng", "Mask crate"}, {"Checkpoint", "Checkpoint"},
                {"Peti besi", "Iron crate"}, {"Tumpukan 2 peti", "Stack of 2 crates"}, {"Tumpukan 3 peti", "Stack of 3 crates"},
                {"Wumpa melayang", "Floating wumpa"}, {"Kepiting", "Crab"}, {"Babi hutan", "Wild hog"},
                {"Pijakan bergerak", "Moving platform"}, {"Palang rendah", "Low barrier"}, {"Besi di atas jurang", "Iron over pit"},
                {"Pantul di atas jurang", "Bounce over pit"}, {"Peti !", "! crate"}, {"Peti bergaris", "Outline crate"},
                {"Bergaris di atas jurang", "Outline over pit"}, {"Pinguin", "Penguin"}, {"Kelelawar", "Bat"},
                {"Piranha", "Piranha"}, {"Kayu apung", "Floating log"},
                {"Hutan", "Jungle"}, {"Senja", "Sunset"}, {"Kuil", "Temple"}, {"Salju", "Snow"},
                {"Sungai", "River"}, {"Gua", "Cave"}, {"Gletser", "Glacier"},
        };
        for (String[] tt : tiles) x(tt[0], tt[1]);

        // ---- potongan untuk teks dinamis ----
        String[][] frags = {
                {"Peti: ", "Crates: "}, {"Wumpa: ", "Wumpa: "}, {"Waktu: ", "Time: "}, {"Waktu ", "Time "},
                {"   Peti ", "   Crates "}, {"   Mati ", "   Deaths "}, {"Jarak ", "Distance "}, {" m   Peti ", " m   Crates "},
                {"Skor: ", "Score: "}, {"Terbaik hari ini: ", "Today's best: "}, {"Rekor: ", "Record: "},
                {"Kristal: ", "Gems: "}, {"Relik: ", "Relics: "}, {"Kristal ", "Gems "}, {"   Relik ", "   Relics "},
                {"Musuh: ", "Enemies: "}, {"Lompatan: ", "Jumps: "}, {"Mati: ", "Deaths: "}, {"Waktu main: ", "Play time: "},
                {"Peti: ", "Crates: "}, {"Hari ini (", "Today ("}, {"belum dimainkan", "not played yet"},
                {"Jarak terjauh: ", "Longest run: "}, {"Dipakai: ", "Equipped: "}, {"Dibeli: ", "Bought: "},
                {"Topeng awal sudah penuh (", "Starter masks are full ("}, {"Wumpa kurang! Butuh ", "Not enough wumpa! Need "},
                {"  (target ", "  (target "}, {"Level Kustom ", "Custom Level "}, {"Tersimpan di slot ", "Saved to slot "},
                {"Slot ", "Slot "}, {"Tema: ", "Theme: "}, {"Dipilih: ", "Selected: "}, {"LEBAR ", "WIDTH "},
                {"Tantangan ", "Challenge "}, {"PENCAPAIAN  ", "ACHIEVEMENTS  "}, {"Tanpa Akhir", "Endless"},
                {"1. Hutan Wumpa", "1. Wumpa Jungle"}, {"2. Jembatan Senja", "2. Sunset Bridge"}, {"3. Kuil TNT", "3. TNT Temple"},
                {"4. Puncak Salju", "4. Snowy Peak"}, {"5. Tunggangan Babi", "5. Hog Ride"}, {"6. Kejaran Batu", "6. Boulder Chase"},
                {"7. Raja Kepiting", "7. King Crab"}, {"8. Sungai Deras", "8. Rushing River"}, {"9. Gua Gelap", "9. Dark Cave"},
                {"10. Gletser Licin", "10. Slippery Glacier"},
        };
        FRAGMENTS = frags.clone();
        Arrays.sort(FRAGMENTS, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Integer.compare(b[0].length(), a[0].length());
            }
        });
    }
}
