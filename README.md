# Bandi Rush

Game platformer 3D-semu ala **Crash Bandicoot** untuk Android, ditulis dengan **Java native murni**
(tanpa engine, tanpa library tambahan, tanpa file gambar atau suara). Semua grafis digambar
dengan `Canvas` lewat proyeksi perspektif buatan sendiri, dan semua efek suara **serta musik latar**
disintesis saat runtime.

Bandi berlari masuk ke dalam layar di koridor hutan, melompati jurang, menghancurkan peti,
mengumpulkan buah wumpa, kabur dari batu raksasa, dan melawan Raja Kepiting.

## Fitur

### Gerakan
- Gerak bebas (maju/mundur/kiri/kanan) dengan joystick.
- **Lompat**: tinggi lompatan tergantung lama tombol ditahan.
- **Spin (putar)**: menghancurkan peti dan mengalahkan musuh. Spin di udara memberi dorongan kecil ke atas (**lompat ganda**), sekali per lompatan.
- **Meluncur (slide)**: melesat rendah, menghancurkan peti dan musuh di depan, dan bisa lewat di bawah **palang rendah**.
- **Lompat jauh**: tekan lompat saat meluncur untuk melompati jurang lebar.

### Peti & rintangan
- Kayu (1 wumpa), **?** (5 wumpa), **pantul** (tahan lompat untuk pantulan lebih tinggi), **1UP**, **A** (topeng pelindung).
- **TNT** meledak 3 detik setelah diinjak; **Nitro** meledak kalau disentuh. Ledakan bisa berantai.
- **C** checkpoint, peti **besi** yang tidak bisa hancur.
- **Peti bertumpuk** (2–3 tingkat) yang jatuh saat peti di bawahnya hancur.
- **Peti "!"**: memunculkan peti bergaris, misalnya menjadi batu loncatan di atas jurang.
- Kepala Bandi yang membentur peti dari bawah juga menghancurkannya.
- Musuh kepiting & babi hutan, pijakan bergerak, wumpa melayang.

### Level (tutorial + 7)
- **Tutorial**: mengajarkan semua gerakan satu per satu lewat petunjuk yang muncul di layar. Tidak mengurangi nyawa.
1. **Hutan Wumpa**: pengenalan, dengan **area bonus** tersembunyi.
2. **Jembatan Senja**: jurang, pijakan bergerak, peti besi, peti pantul.
3. **Kuil TNT**: malam hari, penuh TNT & Nitro.
4. **Puncak Salju**: meluncur, lompat jauh, peti bertumpuk, peti "!", dengan area bonus.
5. **Tunggangan Babi**: menunggangi babi hutan yang berlari sendiri. Geser kiri-kanan dan lompat. Babi menerjang peti kayu dan musuh, tapi menabrak peti besi berarti celaka.
6. **Kejaran Batu**: kamera berbalik, Bandi berlari *ke arah layar* dikejar batu raksasa.
7. **Raja Kepiting** (bos): hindari bom yang dilempar dan terjangannya. Saat bos menabrak dinding dan pusing, putar, injak, atau luncur ke arahnya. Butuh 3 pukulan.

### Area bonus
Injak petak **BONUS** yang bercahaya untuk masuk ke area rahasia penuh peti. Jatuh di area bonus tidak mengurangi nyawa, kamu hanya kembali ke level utama dan bisa mencoba lagi. Peti di area bonus ikut dihitung untuk kristal.

### Toko & kostum
Semua wumpa yang dikumpulkan masuk ke tabungan dan bisa dibelanjakan di **Toko**:
- Karakter kedua, **Nia**: lompat lebih tinggi, lari sedikit lebih pelan.
- 5 kostum: Pantai (kacamata hitam), Ninja (ikat kepala), Musim Dingin (syal), Raja (mahkota), dan Emas.
- **Topeng Awal**: mulai level berikutnya dengan topeng pelindung (bisa disimpan sampai 3).
- **Nyawa +1**.

### Pencapaian & statistik
15 pencapaian (mis. *Tukang Kayu*: hancurkan 100 peti, *Tak Tersentuh*: selesaikan level tanpa mati, *Arsitek*: tamatkan level buatanmu). Notifikasi muncul saat pencapaian terbuka. Layar pencapaian juga menampilkan statistik: peti, wumpa, musuh, lompatan, kematian, dan waktu main.

### Editor level
Buat level sendiri langsung di HP:
- Grid dilihat dari atas. Pilih petak dari palet, lalu ketuk atau seret di grid untuk menggambar.
- 3 slot simpanan, lebar 5 atau 7, tambah/kurangi baris, dan pilih tema (Hutan, Senja, Kuil, Salju, Pantai).
- **Uji Main** untuk langsung memainkan levelnya (tanpa kehilangan nyawa).
- **Salin / Tempel**: level disalin sebagai teks ke clipboard, jadi bisa dibagikan lewat chat dan ditempel di HP lain.

### Progres & mode
- **Peta level**: level berikutnya terbuka setelah level sebelumnya selesai. Progres tersimpan otomatis.
- **Kristal**: hancurkan semua peti di level itu, atau kalahkan bos.
- **Time Trial**: terbuka setelah level selesai sekali (tombol jam di kartu level). Tidak ada nyawa yang hilang, tapi checkpoint juga tidak berlaku. Kalahkan target waktu untuk **relik emas**, perak, atau perunggu. Waktu terbaik tersimpan.
- 100 wumpa = 1 nyawa.

### Suara & pengaturan
- Musik latar sintetis yang berbeda di tiap dunia (marimba + bongo di hutan, seruling saat senja, lonceng di salju, lagu cepat saat dikejar, lagu bos).
- Menu **Pengaturan**: volume musik, volume efek suara, getar (nyala/mati), ukuran tombol sentuh (kecil/sedang/besar), dan hapus progres.
- Menu **Jeda**: lanjutkan, pengaturan, atau kembali ke peta.
- Getar saat terkena serangan, ledakan, dan pukulan ke bos.

## Kontrol

| Aksi | Layar sentuh | Keyboard | Gamepad |
|---|---|---|---|
| Gerak | Joystick di separuh kiri layar | Panah / WASD | Stik kiri / D-pad |
| Lompat | Tombol **X** biru | Spasi / K | A |
| Spin | Tombol **O** merah | J | X / Y |
| Meluncur | Tombol **▼** hijau | L / Shift | R1 / R2 |
| Jeda | Tombol ⏸ kanan atas / Back | P / Enter | Start |

Di menu, ketuk layar untuk memilih. Enter/Start/A memilih pilihan utama.

## Cara build & menjalankan

**Android Studio (paling mudah)**

1. Buka folder ini lewat *File > Open*.
2. Tunggu sinkronisasi Gradle selesai (Android Studio akan mengunduh SDK yang diperlukan).
3. Tekan **Run** ke HP (mode developer + USB debugging aktif) atau ke emulator.

**Command line** (butuh Android SDK; set `ANDROID_HOME` atau buat `local.properties` berisi `sdk.dir=...`):

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Kebutuhan: JDK 17+, Android SDK Platform 34. Aplikasi berjalan di Android 5.0 (API 21) ke atas.

## Struktur kode

```
app/src/main/java/com/microaltech/bandirush/
├── MainActivity.java  Activity layar penuh (landscape, imersif)
├── GameView.java      Loop game, fisika, kamera, renderer 3D-semu, bos, batu, bonus, HUD, menu, input
├── Editor.java        Editor level di dalam game
├── Sprites.java       Gambar karakter: Bandi/Nia + kostum, kepiting/bos, babi hutan, topeng
├── Shop.java          Barang toko, karakter & kostum
├── Skin.java          Warna & aksesori karakter
├── Achievements.java  Daftar pencapaian
├── Ui.java            Alat gambar menu (teks, tombol, ikon)
├── Level.java         Parser peta berbasis teks + legenda karakter
├── Levels.java        Data 6 level
├── Entity.java        Peti, wumpa, musuh, pijakan, palang, bom, finish
├── Player.java        Status Bandi
├── Boss.java          Status bos Raja Kepiting
├── Particle.java      Serpihan, ledakan, kilau
├── Theme.java         Palet warna tiap dunia
├── Save.java          Progres, tabungan wumpa, statistik, level buatan & pengaturan (SharedPreferences)
├── Music.java         Musik latar sintetis (AudioTrack streaming)
└── Sfx.java           Efek suara sintetis (AudioTrack)
```

### Cara kerja renderer

Setiap titik dunia `(x, y, z)` diproyeksikan ke layar dengan kamera yang sedikit menunduk
(`project()` di `GameView`). Dunia digambar dari baris terjauh ke terdekat (*painter's algorithm*):
untuk setiap baris, dasar jurang, dinding, tebing, lalu permukaan tanah digambar dulu, kemudian semua
objek di baris itu diurutkan menurut kedalaman. Di level kejar-kejaran, arah kamera (`camDir`) dibalik
sehingga seluruh urutan gambar, sisi balok yang terlihat, dan arah joystick ikut terbalik.

### Membuat level sendiri

Tambahkan `Level.Def` baru di `Levels.java`:

```java
new Level.Def("7. Nama Level", Theme.JUNGLE, Level.MODE_NORMAL, Music.JUNGLE,
        25f,                      // target Time Trial (detik) untuk relik emas
        "Petunjuk singkat di awal level",
        "  G  ",                  // peta: baris atas = finish ...
        ".....",
        "..S..")                  // ... baris bawah = awal
```

Semua baris harus sama panjang. Mode: `MODE_NORMAL`, `MODE_RIDE` (tunggangan babi), `MODE_CHASE`
(dikejar batu, butuh `G`), `MODE_BOSS` (butuh `X` untuk posisi bos, tanpa `G`).
Tambahan opsional: `.bonus(new Level.Def(...))` untuk area bonus (masuk lewat petak `P`), dan
`.tips("12:Teks petunjuk", ...)` untuk petunjuk yang muncul saat pemain melewati baris tertentu. Legenda:

```
.  tanah            (spasi) jurang       S  posisi awal        G  finish
C  peti kayu        ?  peti 5 wumpa      B  peti pantul        L  peti nyawa
A  peti topeng      T  TNT               N  Nitro              K  checkpoint
I  peti besi        W  wumpa             E  kepiting           H  babi hutan
2  tumpukan 2 peti  3  tumpukan 3 peti   !  peti "!"           U  palang rendah (harus meluncur)
o  peti bergaris    O  peti bergaris di atas jurang (jadi peti besi)
X  posisi bos        P  pintu area bonus
w  wumpa melayang di atas jurang         m  pijakan bergerak di atas jurang
i  peti besi di atas jurang              b  peti pantul di atas jurang
```

Tema: `Theme.JUNGLE`, `SUNSET`, `TEMPLE`, `SNOW`, `BEACH`.
Musik: `Music.JUNGLE`, `SUNSET`, `TEMPLE`, `SNOW`, `CHASE`, `BOSS`.
