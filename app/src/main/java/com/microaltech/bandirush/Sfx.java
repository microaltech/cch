package com.microaltech.bandirush;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** Efek suara yang disintesis saat runtime, jadi tidak perlu file audio. */
final class Sfx {
    static final int JUMP = 0, SPIN = 1, BREAK = 2, WUMPA = 3, BOUNCE = 4, BOOM = 5, HURT = 6,
            LIFE = 7, STOMP = 8, CHECK = 9, WIN = 10, DIE = 11, TICK = 12, SLIDE = 13, ROAR = 14,
            THROW = 15, POP = 16, SELECT = 17, RUMBLE = 18;
    private static final int COUNT = 19;
    private static final int RATE = 22050;
    private static final int SINE = 0, SQUARE = 1, TRI = 2;

    private final AudioTrack[] tracks = new AudioTrack[COUNT];
    private volatile float volume = 0.8f;

    Sfx() {
        try {
            put(JUMP, tone(0.14f, 320, 720, SQUARE, 0f, 0.22f, 0f, false));
            put(SPIN, tone(0.38f, 180, 520, TRI, 0.55f, 0.35f, 0.6f, false));
            put(BREAK, cat(tone(0.05f, 220, 160, SQUARE, 0.5f, 0.3f, 0.3f, false),
                    tone(0.16f, 140, 60, SQUARE, 0.85f, 0.35f, 0.5f, true)));
            put(WUMPA, cat(tone(0.05f, 988, 988, SQUARE, 0f, 0.16f, 0f, false),
                    tone(0.09f, 1318, 1318, SQUARE, 0f, 0.16f, 0f, false)));
            put(BOUNCE, tone(0.26f, 180, 900, SINE, 0f, 0.5f, 0f, false));
            put(BOOM, tone(0.8f, 90, 30, SINE, 0.9f, 0.9f, 0.85f, true));
            put(HURT, tone(0.35f, 520, 140, SQUARE, 0.1f, 0.25f, 0f, false));
            put(LIFE, cat(note(523, 0.08f), note(659, 0.08f), note(784, 0.08f), note(1046, 0.16f)));
            put(STOMP, tone(0.12f, 240, 70, SQUARE, 0.2f, 0.3f, 0.2f, false));
            put(CHECK, cat(note(659, 0.09f), note(880, 0.09f), note(1318, 0.18f)));
            put(WIN, cat(note(523, 0.12f), note(659, 0.12f), note(784, 0.12f),
                    note(1046, 0.18f), note(784, 0.1f), note(1046, 0.35f)));
            put(DIE, tone(0.7f, 620, 90, TRI, 0.05f, 0.45f, 0f, false));
            put(TICK, tone(0.05f, 1400, 1400, SQUARE, 0f, 0.15f, 0f, false));
            put(SLIDE, tone(0.4f, 400, 120, TRI, 0.8f, 0.3f, 0.75f, false));
            put(ROAR, tone(0.9f, 110, 60, SQUARE, 0.45f, 0.45f, 0.7f, false));
            put(THROW, tone(0.25f, 700, 250, SINE, 0.3f, 0.3f, 0.3f, false));
            put(POP, cat(note(784, 0.06f), note(1175, 0.06f), note(1568, 0.12f)));
            put(SELECT, tone(0.06f, 880, 1200, SQUARE, 0f, 0.14f, 0f, false));
            put(RUMBLE, tone(1.2f, 60, 40, SINE, 0.85f, 0.8f, 0.92f, false));
        } catch (Throwable ignored) {
            // Audio tidak tersedia (misalnya emulator tanpa audio) - game tetap jalan tanpa suara.
        }
    }

    void setVolume(float v) {
        volume = v;
    }

    void play(int id) {
        AudioTrack t = tracks[id];
        if (t == null || volume <= 0.001f) return;
        try {
            if (t.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) t.stop();
            t.reloadStaticData();
            t.setVolume(volume);
            t.play();
        } catch (Exception ignored) {
        }
    }

    void release() {
        for (int i = 0; i < COUNT; i++) {
            if (tracks[i] != null) {
                try {
                    tracks[i].release();
                } catch (Exception ignored) {
                }
                tracks[i] = null;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void put(int id, short[] data) {
        AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, data.length * 2, AudioTrack.MODE_STATIC);
        t.write(data, 0, data.length);
        tracks[id] = t;
    }

    private static short[] note(float freq, float dur) {
        return tone(dur, freq, freq, TRI, 0f, 0.3f, 0f, false);
    }

    private static short[] tone(float dur, float f0, float f1, int wave, float noise, float vol,
                                float lowpass, boolean quadDecay) {
        int n = Math.max(1, (int) (dur * RATE));
        short[] out = new short[n];
        Random rnd = new Random(1234);
        double ph = 0;
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float f = f0 + (f1 - f0) * t;
            ph += f / RATE;
            float frac = (float) (ph - Math.floor(ph));
            float s;
            switch (wave) {
                case SQUARE: s = frac < 0.5f ? 0.7f : -0.7f; break;
                case TRI: s = 4f * Math.abs(frac - 0.5f) - 1f; break;
                default: s = (float) Math.sin(ph * 2.0 * Math.PI); break;
            }
            s = s * (1f - noise) + (rnd.nextFloat() * 2f - 1f) * noise;
            prev += (s - prev) * (1f - lowpass);
            float env = Math.min(1f, i / (RATE * 0.004f)) * (1f - t);
            if (quadDecay) env *= (1f - t);
            float v = prev * env * vol;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            out[i] = (short) (v * 32767);
        }
        return out;
    }

    private static short[] cat(short[]... parts) {
        int len = 0;
        for (short[] p : parts) len += p.length;
        short[] out = new short[len];
        int o = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
