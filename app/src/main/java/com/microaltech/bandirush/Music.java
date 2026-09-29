package com.microaltech.bandirush;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Musik latar yang disintesis saat runtime (tanpa file audio) dan diputar berulang
 * lewat AudioTrack mode streaming di thread terpisah.
 *
 * Lagu ditulis sebagai pola 16-an: tiap karakter = satu ketukan 1/16.
 * Melodi/bass: '0'-'9' lalu 'a'-'e' = derajat tangga nada, '.' = diam/lanjut.
 * Drum: k kick, s snare, h hi-hat, o hi-hat terbuka, b/B bongo tinggi/rendah, x kick+hat.
 */
final class Music implements Runnable {
    static final int JUNGLE = 0, SUNSET = 1, TEMPLE = 2, SNOW = 3, CHASE = 4, BOSS = 5;

    private static final int RATE = 22050;
    private static final int MARIMBA = 0, FLUTE = 1, SQUARE = 2, BELL = 3;

    private static final int[] MAJ_PENTA = {0, 2, 4, 7, 9};
    private static final int[] MIN_PENTA = {0, 3, 5, 7, 10};
    private static final int[] HARM_MIN = {0, 2, 3, 5, 7, 8, 11};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    private static final class Song {
        final int bpm, root, lead;
        final int[] scale, barShift;
        final String[] melody;
        final String bass, drums;

        Song(int bpm, int root, int[] scale, int lead, String[] melody, String bass, int[] barShift, String drums) {
            this.bpm = bpm;
            this.root = root;
            this.scale = scale;
            this.lead = lead;
            this.melody = melody;
            this.bass = bass;
            this.barShift = barShift;
            this.drums = drums;
        }
    }

    private static final Song[] SONGS = {
            // JUNGLE: marimba ceria + bongo
            new Song(120, 60, MAJ_PENTA, MARIMBA, new String[]{
                    "5.7.8.7.5.4.3..." + "4.5.7.5.4.3.2..." + "5.7.8.9.8.7.5.4." + "3.4.5.3.2.1.0...",
                    "8...7.8.9...8.7." + "5...4.5.7...5.4." + "3.4.5.7.8.7.5.3." + "4.3.2.1.0......."},
                    "0...0.3.0...3.4.", new int[]{0, 5, 7, 0}, "k.b.bBh.k.bBs.bh"),
            // SUNSET: seruling santai
            new Song(96, 57, MIN_PENTA, FLUTE, new String[]{
                    "5...7.8.7...5.4." + "3...4.5.4...3.2." + "5...7.8.9...8.7." + "8...7.5.4.......",
                    "a...9.8.7...8.9." + "a...c.a.9...7.8." + "7...5.4.5...7.8." + "5..............."},
                    "0.......3...2...", new int[]{0, 8, 10, 0}, "k...s..hk.k.s..h"),
            // TEMPLE: misterius
            new Song(92, 50, HARM_MIN, MARIMBA, new String[]{
                    "7.8.9.8.7.6.7..." + "4.5.6.5.4.3.4..." + "7.8.9.a.b.a.9.8." + "7.6.5.4.6...7...",
                    "b...a.9.a...9.8." + "7...6.5.6...5.4." + "4.5.6.7.8.9.a.b." + "7..............."},
                    "0...0...4...0...", new int[]{0, 8, 7, 0}, "B..b..B.b.B.s..."),
            // SNOW: lonceng
            new Song(108, 64, MAJOR, BELL, new String[]{
                    "7...9.a.b...a.9." + "7...5.4.2...4.5." + "7...9.a.b.c.b.a." + "9...7.5.7.......",
                    "4...5.7.9...7.5." + "4...2.0.2...4..." + "5...7.9.a...9.7." + "7..............."},
                    "0.......4.......", new int[]{0, 5, 9, 7}, "k...h...s...h..."),
            // CHASE: cepat dan tegang
            new Song(150, 52, MIN_PENTA, SQUARE, new String[]{
                    "5.5.7.5.8.7.5.4." + "5.5.7.5.3.4.5..." + "5.5.7.5.8.9.a.9." + "8.7.5.4.3.2.0...",
                    "a.a.9.8.7...8.9." + "a.a.9.8.7...5..." + "7.7.8.9.a...9.8." + "7.5.4.3.4.5.7..."},
                    "0.0.0.0.0.0.3.2.", new int[]{0, 0, 8, 10}, "x.h.s.h.x.x.s.hh"),
            // BOSS
            new Song(140, 57, HARM_MIN, SQUARE, new String[]{
                    "0.0.1.2.4...2.1." + "0.0.1.2.6...5.4." + "7.7.6.5.4...2.4." + "5.4.2.1.0...6...",
                    "7.7.8.9.b...9.8." + "7.7.8.9.d...c.b." + "e.e.d.c.b...9.b." + "c.b.9.8.7...6..."},
                    "0.0.0.0.0.0.0.0.", new int[]{0, 0, 5, 7}, "x.h.s.hkx.hks.hs"),
    };

    private final short[][] cache = new short[SONGS.length][];
    private final Thread thread;
    private volatile boolean running = true;
    private volatile boolean paused;
    private volatile int want = -1;
    private volatile float volume = 0.6f;

    Music() {
        thread = new Thread(this, "BandiRushMusic");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    void play(int song) {
        want = song;
    }

    void setVolume(float v) {
        volume = v;
    }

    void pause() {
        paused = true;
    }

    void resume() {
        paused = false;
    }

    void release() {
        running = false;
        thread.interrupt();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        AudioTrack track;
        int chunk = 1024;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, chunk * 4) * 2, AudioTrack.MODE_STREAM);
        } catch (Throwable t) {
            return; // tanpa audio
        }
        int playing = -1;
        int pos = 0;
        float appliedVol = -1f;
        short[] silence = new short[chunk];
        try {
            while (running) {
                if (paused || volume <= 0.001f || want < 0) {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause();
                        track.flush();
                    }
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                int song = want;
                if (song != playing) {
                    if (cache[song] == null) cache[song] = render(SONGS[song]);
                    playing = song;
                    pos = 0;
                    track.pause();
                    track.flush();
                }
                if (appliedVol != volume) {
                    appliedVol = volume;
                    track.setVolume(appliedVol);
                }
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(silence, 0, silence.length);
                    track.play();
                }
                short[] buf = cache[playing];
                int n = Math.min(chunk, buf.length - pos);
                track.write(buf, pos, n);
                pos += n;
                if (pos >= buf.length) pos = 0;
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                track.stop();
            } catch (Throwable ignored) {
            }
            track.release();
        }
    }

    // ------------------------------------------------------------------
    // Sintesis
    // ------------------------------------------------------------------

    private static float midiToFreq(int m) {
        return (float) (440.0 * Math.pow(2.0, (m - 69) / 12.0));
    }

    private static int degree(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'z') return 10 + (c - 'a');
        return -1;
    }

    private static int noteOf(int[] scale, int root, int deg) {
        return root + scale[deg % scale.length] + 12 * (deg / scale.length);
    }

    private static short[] render(Song s) {
        int step = Math.round(RATE * 60f / s.bpm / 4f);
        int steps = s.melody.length * 64;
        int len = steps * step;
        float[] mix = new float[len];
        Random rnd = new Random(42);

        for (int part = 0; part < s.melody.length; part++) {
            String mel = s.melody[part];
            for (int i = 0; i < 64; i++) {
                int d = degree(mel.charAt(i));
                if (d < 0) continue;
                int start = (part * 64 + i) * step;
                instrument(mix, start, midiToFreq(noteOf(s.scale, s.root, d)), s.lead, 0.32f);
            }
        }
        int bars = steps / 16;
        for (int bar = 0; bar < bars; bar++) {
            int shift = s.barShift[bar % s.barShift.length];
            for (int i = 0; i < 16; i++) {
                int start = (bar * 16 + i) * step;
                int d = degree(s.bass.charAt(i));
                if (d >= 0) bass(mix, start, midiToFreq(noteOf(s.scale, s.root - 24, d) + shift), 0.42f);
                char dr = s.drums.charAt(i);
                if (dr != '.') drum(mix, start, dr, rnd);
            }
        }

        float peak = 0.001f;
        for (float v : mix) peak = Math.max(peak, Math.abs(v));
        float g = 0.85f / peak;
        short[] out = new short[len];
        for (int i = 0; i < len; i++) out[i] = (short) (mix[i] * g * 32767f);
        return out;
    }

    private static void add(float[] mix, int start, int i, float v) {
        int idx = start + i;
        if (idx >= mix.length) idx -= mix.length; // ekor nada menyambung ke awal loop
        mix[idx] += v;
    }

    private static void instrument(float[] mix, int start, float f, int inst, float vol) {
        float dur;
        switch (inst) {
            case FLUTE: dur = 0.7f; break;
            case SQUARE: dur = 0.35f; break;
            case BELL: dur = 1.0f; break;
            default: dur = 0.55f; break;
        }
        int n = (int) (dur * RATE);
        double w = 2.0 * Math.PI * f / RATE;
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float v;
            switch (inst) {
                case FLUTE: {
                    float e = Math.min(1f, t / 0.03f) * (float) Math.exp(-t / 0.35f);
                    double ph = w * i + 0.25 * Math.sin(2.0 * Math.PI * 5.0 * t);
                    v = (float) (Math.sin(ph) + 0.2 * Math.sin(2 * ph)) * e;
                    break;
                }
                case SQUARE: {
                    float e = Math.min(1f, t / 0.005f) * (float) Math.exp(-t / 0.12f);
                    double frac = (f * t) % 1.0;
                    float sq = frac < 0.3 ? 0.5f : -0.5f;
                    lp += (sq - lp) * 0.35f;
                    v = lp * e;
                    break;
                }
                case BELL: {
                    float e = (float) Math.exp(-t / 0.3f);
                    v = (float) (Math.sin(w * i) + 0.5 * Math.sin(w * 2.76 * i) * e + 0.25 * Math.sin(w * 5.4 * i) * e * e) * e * 0.8f;
                    break;
                }
                default: {
                    float e = Math.min(1f, t / 0.003f) * (float) Math.exp(-t / 0.13f);
                    v = (float) (Math.sin(w * i) + 0.3 * Math.sin(w * 4 * i) * e * e) * e;
                    break;
                }
            }
            add(mix, start, i, v * vol);
        }
    }

    private static void bass(float[] mix, int start, float f, float vol) {
        int n = (int) (0.3f * RATE);
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float e = Math.min(1f, t / 0.004f) * (float) Math.exp(-t / 0.15f);
            double frac = (f * t) % 1.0;
            float tri = (float) (4.0 * Math.abs(frac - 0.5) - 1.0);
            add(mix, start, i, tri * e * vol);
        }
    }

    private static void drum(float[] mix, int start, char c, Random rnd) {
        if (c == 'x') {
            drum(mix, start, 'k', rnd);
            drum(mix, start, 'h', rnd);
            return;
        }
        int n = (int) (0.2f * RATE);
        double ph = 0;
        float prevNoise = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float nz = rnd.nextFloat() * 2f - 1f;
            float v;
            switch (c) {
                case 'k': {
                    float f = 45f + 90f * (float) Math.exp(-t / 0.03f);
                    ph += 2.0 * Math.PI * f / RATE;
                    v = (float) Math.sin(ph) * (float) Math.exp(-t / 0.09f) * 0.9f;
                    break;
                }
                case 's':
                    v = nz * (float) Math.exp(-t / 0.06f) * 0.45f
                            + (float) Math.sin(2.0 * Math.PI * 190.0 * t) * (float) Math.exp(-t / 0.05f) * 0.3f;
                    break;
                case 'h':
                case 'o': {
                    float hp = nz - prevNoise;
                    v = hp * (float) Math.exp(-t / (c == 'h' ? 0.015f : 0.08f)) * 0.14f;
                    break;
                }
                case 'b':
                case 'B': {
                    float f0 = c == 'b' ? 400f : 270f;
                    float f = f0 * (1f + 0.3f * (float) Math.exp(-t / 0.01f));
                    ph += 2.0 * Math.PI * f / RATE;
                    v = (float) Math.sin(ph) * (float) Math.exp(-t / 0.07f) * 0.45f;
                    break;
                }
                default:
                    v = 0f;
                    break;
            }
            prevNoise = nz;
            add(mix, start, i, v);
        }
    }
}
