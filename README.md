# Bandi Rush

Game platformer 3D-semu ala **Crash Bandicoot** untuk Android, ditulis dengan **Java native murni**
(tanpa engine, tanpa library tambahan, tanpa file gambar atau suara). Semua grafis digambar
dengan `Canvas` lewat proyeksi perspektif buatan sendiri, dan semua efek suara disintesis saat runtime.

Bandi berlari masuk ke dalam layar di koridor hutan, melompati jurang, menghancurkan peti,
mengumpulkan buah wumpa, dan menghindari musuh sampai ke kristal di garis finish.

## Fitur

- Kamera di belakang tokoh yang mengikuti Bandi, dengan kabut jarak jauh, dinding hutan, pohon, dan bukit.
- Gerak bebas (maju/mundur/kiri/kanan), **lompat** (tinggi lompatan tergantung lama tombol ditekan), dan **serangan putar (spin)**.
- Jenis peti:
  - kayu (1 wumpa), **?** (5 wumpa), **pantul** (lompatan super), **1UP** (nyawa), **A** (topeng pelindung)
  - **TNT** meledak 3 detik setelah diinjak, atau langsung kalau kena spin
  - **Nitro** (hijau) meledak begitu disentuh
  - **C** checkpoint dan peti **besi** yang tidak bisa dihancurkan
  - Ledakan bisa berantai ke TNT/Nitro di dekatnya.
- Musuh: kepiting (patroli kiri-kanan) dan babi hutan (patroli maju-mundur). Kalahkan dengan spin atau dengan menginjaknya.
- Pijakan bergerak di atas jurang, peti besi sebagai batu loncatan, dan wumpa melayang.
- Topeng pelindung menahan satu serangan (bisa ditumpuk sampai 2).
- 100 wumpa = 1 nyawa. Hancurkan **semua** peti di satu level untuk mendapat **kristal**.
- 3 level dengan tema berbeda: Hutan Wumpa, Jembatan Senja, dan Kuil TNT (malam hari).
- Mendukung layar sentuh, keyboard, dan gamepad.

## Kontrol

| Aksi | Layar sentuh | Keyboard | Gamepad |
|---|---|---|---|
| Gerak | Joystick di separuh kiri layar | Panah / WASD | Stik kiri / D-pad |
| Lompat | Tombol **X** biru (tahan = lebih tinggi) | Spasi / K | A |
| Spin | Tombol **O** merah | J | X / Y |
| Jeda | Tombol ⏸ kanan atas / Back | P / Enter | Start |

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
├── GameView.java      Loop game, fisika, tabrakan, kamera, renderer 3D-semu, HUD, input
├── Level.java         Parser peta berbasis teks + legenda karakter
├── Levels.java        Data 3 level
├── Entity.java        Peti, wumpa, musuh, pijakan, finish
├── Player.java        Status Bandi
├── Particle.java      Serpihan, ledakan, kilau
├── Theme.java         Palet warna tiap dunia
└── Sfx.java           Efek suara sintetis (AudioTrack)
```

### Cara kerja renderer

Setiap titik dunia `(x, y, z)` diproyeksikan ke layar dengan kamera yang sedikit menunduk
(`project()` di `GameView`). Dunia digambar dari baris terjauh ke terdekat (*painter's algorithm*):
untuk setiap baris, dasar jurang, dinding, tebing, lalu permukaan tanah digambar dulu, kemudian semua
objek di baris itu diurutkan menurut kedalaman. Dengan cara ini Bandi bisa tertutup tebing saat
jatuh ke jurang, dan peti di depan menutupi objek di belakangnya.

### Membuat level sendiri

Tambahkan `Level.Def` baru di `Levels.java`. Peta ditulis dari garis finish (baris atas) ke titik awal
(baris bawah), dan semua baris harus sama panjang. Legenda:

```
.  tanah            (spasi) jurang       S  posisi awal        G  finish
C  peti kayu        ?  peti 5 wumpa      B  peti pantul        L  peti nyawa
A  peti topeng      T  TNT               N  Nitro              K  checkpoint
I  peti besi        W  wumpa             E  kepiting           H  babi hutan
w  wumpa melayang di atas jurang         m  pijakan bergerak di atas jurang
i  peti besi di atas jurang              b  peti pantul di atas jurang
```

Tema yang tersedia: `Theme.JUNGLE`, `Theme.SUNSET`, `Theme.TEMPLE`.
