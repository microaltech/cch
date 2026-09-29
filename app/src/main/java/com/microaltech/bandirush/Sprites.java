package com.microaltech.bandirush;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Menggambar karakter dalam "satuan dunia": pemanggil sudah men-translate kanvas ke kaki karakter
 * dan men-scale 1 unit = ukuran piksel pada kedalaman itu. Sumbu Y negatif = ke atas.
 */
final class Sprites {
    static final int VIEW_BACK = 0, VIEW_FRONT = 1, VIEW_SIDE = 2;

    private static final int C_ORANGE = 0xFFF57C00, C_TAN = 0xFFFFE0B2, C_JEANS = 0xFF1E5AA8,
            C_SHOE = 0xFFD32F2F, C_GLOVE = 0xFF5D4037, C_HAIR = 0xFF3E2723;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    /** Warna kabut dan kekuatannya (0..1) untuk karakter berikutnya. */
    int fogColor;
    float fogAmt;

    Sprites() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    private int f(int color) {
        if (fogAmt <= 0f) return color;
        float k = Math.min(1f, fogAmt);
        int r = (int) (Color.red(color) + (Color.red(fogColor) - Color.red(color)) * k);
        int g = (int) (Color.green(color) + (Color.green(fogColor) - Color.green(color)) * k);
        int b = (int) (Color.blue(color) + (Color.blue(fogColor) - Color.blue(color)) * k);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ Bandi

    void bandi(Canvas cv, int view, boolean mirror, boolean air, boolean moving, float runPhase,
               float time, boolean sliding) {
        if (sliding) cv.scale(1.15f, 0.55f);
        if (view == VIEW_SIDE && mirror) cv.scale(-1f, 1f);
        float sw = moving && !air && !sliding ? (float) Math.sin(runPhase) : 0f;
        float idle = (float) Math.sin(time * 3f) * 0.01f;

        // kaki
        stroke.setStrokeWidth(0.13f);
        stroke.setColor(C_JEANS);
        fill.setColor(C_SHOE);
        float lfx, lfy, rfx, rfy;
        if (view == VIEW_SIDE) {
            lfx = sw * 0.16f;
            rfx = -sw * 0.16f;
            lfy = -0.05f - Math.max(0f, sw) * 0.1f;
            rfy = -0.05f - Math.max(0f, -sw) * 0.1f;
        } else {
            lfx = -0.1f;
            rfx = 0.1f;
            lfy = -0.05f - Math.max(0f, sw) * 0.12f;
            rfy = -0.05f - Math.max(0f, -sw) * 0.12f;
        }
        if (air) {
            lfx = -0.12f;
            rfx = 0.14f;
            lfy = -0.18f;
            rfy = -0.1f;
        }
        cv.drawLine(-0.07f, -0.42f, lfx, lfy, stroke);
        cv.drawLine(0.07f, -0.42f, rfx, rfy, stroke);
        float shoeF = view == VIEW_SIDE ? 0.07f : 0f;
        rect.set(lfx - 0.08f, lfy - 0.04f, lfx + 0.08f + shoeF, lfy + 0.05f);
        cv.drawOval(rect, fill);
        rect.set(rfx - 0.08f, rfy - 0.04f, rfx + 0.08f + shoeF, rfy + 0.05f);
        cv.drawOval(rect, fill);

        // badan
        fill.setColor(C_JEANS);
        rect.set(-0.17f, -0.52f + idle, 0.17f, -0.34f);
        cv.drawOval(rect, fill);
        fill.setColor(C_ORANGE);
        rect.set(-0.17f, -0.76f + idle, 0.17f, -0.42f + idle);
        cv.drawOval(rect, fill);
        if (view == VIEW_FRONT) {
            fill.setColor(C_TAN);
            rect.set(-0.09f, -0.66f + idle, 0.09f, -0.45f + idle);
            cv.drawOval(rect, fill);
        }

        // tangan
        stroke.setColor(C_ORANGE);
        stroke.setStrokeWidth(0.08f);
        fill.setColor(C_GLOVE);
        float lhx, lhy, rhx, rhy;
        if (air) {
            lhx = -0.3f;
            lhy = -0.85f;
            rhx = 0.3f;
            rhy = -0.85f;
        } else if (sliding) {
            lhx = -0.32f;
            lhy = -0.7f;
            rhx = 0.32f;
            rhy = -0.7f;
        } else if (view == VIEW_SIDE) {
            lhx = -sw * 0.2f;
            lhy = -0.47f;
            rhx = sw * 0.2f;
            rhy = -0.47f;
        } else {
            lhx = -0.27f;
            lhy = -0.5f - sw * 0.07f;
            rhx = 0.27f;
            rhy = -0.5f + sw * 0.07f;
        }
        cv.drawLine(-0.13f, -0.68f + idle, lhx, lhy, stroke);
        cv.drawLine(0.13f, -0.68f + idle, rhx, rhy, stroke);
        cv.drawCircle(lhx, lhy, 0.06f, fill);
        cv.drawCircle(rhx, rhy, 0.06f, fill);

        // kepala
        float hy = -0.9f + idle;
        fill.setColor(C_ORANGE);
        cv.drawCircle(-0.15f, hy - 0.12f, 0.06f, fill);
        cv.drawCircle(0.15f, hy - 0.12f, 0.06f, fill);
        cv.drawCircle(0, hy, 0.18f, fill);
        hairTuft(cv, hy);

        if (view == VIEW_FRONT) {
            fill.setColor(C_TAN);
            rect.set(-0.14f, hy + 0.02f, 0.14f, hy + 0.17f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFFFFFFFF);
            rect.set(-0.12f, hy - 0.1f, -0.02f, hy + 0.03f);
            cv.drawOval(rect, fill);
            rect.set(0.02f, hy - 0.1f, 0.12f, hy + 0.03f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF388E3C);
            cv.drawCircle(-0.06f, hy - 0.035f, 0.032f, fill);
            cv.drawCircle(0.06f, hy - 0.035f, 0.032f, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(-0.06f, hy - 0.035f, 0.016f, fill);
            cv.drawCircle(0.06f, hy - 0.035f, 0.016f, fill);
            rect.set(-0.035f, hy + 0.03f, 0.035f, hy + 0.07f);
            cv.drawOval(rect, fill);
            stroke.setColor(0xFF8D1B1B);
            stroke.setStrokeWidth(0.022f);
            rect.set(-0.08f, hy + 0.04f, 0.08f, hy + 0.14f);
            cv.drawArc(rect, 20, 140, false, stroke);
        } else if (view == VIEW_SIDE) {
            fill.setColor(C_TAN);
            rect.set(0.02f, hy - 0.03f, 0.31f, hy + 0.14f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(0.3f, hy + 0.03f, 0.037f, fill);
            fill.setColor(0xFFFFFFFF);
            rect.set(0.04f, hy - 0.12f, 0.14f, hy + 0.01f);
            cv.drawOval(rect, fill);
            fill.setColor(0xFF388E3C);
            cv.drawCircle(0.11f, hy - 0.05f, 0.03f, fill);
            fill.setColor(0xFF000000);
            cv.drawCircle(0.12f, hy - 0.05f, 0.015f, fill);
            stroke.setColor(0xFF8D1B1B);
            stroke.setStrokeWidth(0.02f);
            cv.drawLine(0.12f, hy + 0.1f, 0.24f, hy + 0.09f, stroke);
        }
    }

    private void hairTuft(Canvas cv, float hy) {
        fill.setColor(C_HAIR);
        path.reset();
        path.moveTo(-0.06f, hy - 0.15f);
        path.lineTo(-0.01f, hy - 0.29f);
        path.lineTo(0.03f, hy - 0.16f);
        path.close();
        path.moveTo(0.01f, hy - 0.16f);
        path.lineTo(0.1f, hy - 0.26f);
        path.lineTo(0.08f, hy - 0.13f);
        path.close();
        cv.drawPath(path, fill);
    }

    void tornado(Canvas cv, float time) {
        float a = time * 30f;
        for (int i = 0; i < 6; i++) {
            float y = -0.08f - i * 0.15f;
            float hw = 0.26f + 0.08f * (float) Math.sin(a * 0.7f + i * 1.3f) + (i == 2 || i == 3 ? 0.06f : 0f);
            boolean alt = (((int) (a / Math.PI) + i) & 1) == 0;
            fill.setColor(i < 2 ? (alt ? C_JEANS : 0xFF3F7BD1) : (alt ? C_ORANGE : 0xFFFFA040));
            rect.set(-hw, y - 0.1f, hw, y + 0.1f);
            cv.drawOval(rect, fill);
        }
        fill.setColor(C_ORANGE);
        cv.drawCircle(0, -0.95f, 0.15f, fill);
        hairTuft(cv, -0.93f);
        stroke.setColor(0xCCFFFFFF);
        stroke.setStrokeWidth(0.03f);
        for (int k = 0; k < 3; k++) {
            float yy = -0.25f - k * 0.25f;
            float st = (a * 40f + k * 120f) % 360f;
            rect.set(-0.5f, yy - 0.12f, 0.5f, yy + 0.12f);
            cv.drawArc(rect, st, 110f, false, stroke);
        }
    }

    void mask(Canvas cv, boolean doubled) {
        if (doubled) {
            fill.setColor(0x55FFEB3B);
            cv.drawCircle(0, 0, 0.3f, fill);
        }
        fill.setColor(0xFFE53935);
        rect.set(-0.14f, -0.36f, -0.06f, -0.12f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFFFFEB3B);
        rect.set(-0.04f, -0.4f, 0.04f, -0.14f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF43A047);
        rect.set(0.06f, -0.36f, 0.14f, -0.12f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF8D5524);
        rect.set(-0.13f, -0.2f, 0.13f, 0.2f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(-0.05f, -0.06f, 0.035f, fill);
        cv.drawCircle(0.05f, -0.06f, 0.035f, fill);
        fill.setColor(0xFF000000);
        cv.drawCircle(-0.05f, -0.06f, 0.017f, fill);
        cv.drawCircle(0.05f, -0.06f, 0.017f, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(0.025f);
        cv.drawLine(-0.06f, 0.09f, 0.06f, 0.09f, stroke);
    }

    // ------------------------------------------------------------------ musuh

    void crab(Canvas cv, float t, boolean crown, boolean flash) {
        int body = flash ? 0xFFFFFFFF : f(0xFFE53935);
        int dark = flash ? 0xFFFFFFFF : f(0xFFB71C1C);
        float walk = (float) Math.sin(t * 14f);
        stroke.setColor(dark);
        stroke.setStrokeWidth(0.05f);
        for (int k = 0; k < 3; k++) {
            float lx = 0.18f + k * 0.07f;
            float ly = walk * 0.03f * (k % 2 == 0 ? 1 : -1);
            cv.drawLine(-lx, -0.22f, -lx - 0.12f, ly, stroke);
            cv.drawLine(lx, -0.22f, lx + 0.12f, -ly, stroke);
        }
        fill.setColor(body);
        rect.set(-0.36f, -0.44f, 0.36f, -0.12f);
        cv.drawOval(rect, fill);
        float claw = (float) Math.sin(t * 6f) * 0.06f;
        cv.drawCircle(-0.44f, -0.42f + claw, 0.13f, fill);
        cv.drawCircle(0.44f, -0.42f - claw, 0.13f, fill);
        fill.setColor(flash ? 0xFFFFFFFF : f(0xFFFF8A80));
        rect.set(-0.2f, -0.4f, 0.2f, -0.28f);
        cv.drawOval(rect, fill);
        stroke.setColor(body);
        cv.drawLine(-0.1f, -0.42f, -0.13f, -0.6f, stroke);
        cv.drawLine(0.1f, -0.42f, 0.13f, -0.6f, stroke);
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(-0.13f, -0.62f, 0.07f, fill);
        cv.drawCircle(0.13f, -0.62f, 0.07f, fill);
        fill.setColor(0xFF000000);
        cv.drawCircle(-0.13f, -0.61f, 0.035f, fill);
        cv.drawCircle(0.13f, -0.61f, 0.035f, fill);
        if (crown) {
            stroke.setColor(0xFF000000);
            stroke.setStrokeWidth(0.025f);
            cv.drawLine(-0.2f, -0.72f, -0.08f, -0.66f, stroke);
            cv.drawLine(0.2f, -0.72f, 0.08f, -0.66f, stroke);
            fill.setColor(0xFFFFC107);
            path.reset();
            path.moveTo(-0.16f, -0.72f);
            path.lineTo(-0.18f, -0.9f);
            path.lineTo(-0.08f, -0.8f);
            path.lineTo(0f, -0.95f);
            path.lineTo(0.08f, -0.8f);
            path.lineTo(0.18f, -0.9f);
            path.lineTo(0.16f, -0.72f);
            path.close();
            cv.drawPath(path, fill);
            fill.setColor(0xFFE91E63);
            cv.drawCircle(0f, -0.78f, 0.025f, fill);
        }
    }

    void hog(Canvas cv, float t) {
        float step = (float) Math.sin(t * 12f) * 0.04f;
        fill.setColor(f(0xFF3E2723));
        rect.set(-0.3f, -0.2f + step, -0.16f, 0f);
        cv.drawRect(rect, fill);
        rect.set(0.16f, -0.2f - step, 0.3f, 0f);
        cv.drawRect(rect, fill);
        fill.setColor(f(0xFF795548));
        rect.set(-0.4f, -0.62f, 0.4f, -0.14f);
        cv.drawOval(rect, fill);
        fill.setColor(f(0xFF4E342E));
        path.reset();
        path.moveTo(-0.28f, -0.56f);
        path.lineTo(-0.36f, -0.78f);
        path.lineTo(-0.14f, -0.6f);
        path.close();
        path.moveTo(0.28f, -0.56f);
        path.lineTo(0.36f, -0.78f);
        path.lineTo(0.14f, -0.6f);
        path.close();
        cv.drawPath(path, fill);
        fill.setColor(f(0xFFF48FB1));
        rect.set(-0.14f, -0.38f, 0.14f, -0.2f);
        cv.drawOval(rect, fill);
        fill.setColor(0xFF3E2723);
        cv.drawCircle(-0.05f, -0.29f, 0.025f, fill);
        cv.drawCircle(0.05f, -0.29f, 0.025f, fill);
        fill.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(-0.14f, -0.26f);
        path.lineTo(-0.22f, -0.42f);
        path.lineTo(-0.1f, -0.3f);
        path.close();
        path.moveTo(0.14f, -0.26f);
        path.lineTo(0.22f, -0.42f);
        path.lineTo(0.1f, -0.3f);
        path.close();
        cv.drawPath(path, fill);
        fill.setColor(0xFFFFEB3B);
        cv.drawCircle(-0.12f, -0.47f, 0.045f, fill);
        cv.drawCircle(0.12f, -0.47f, 0.045f, fill);
        fill.setColor(0xFFD50000);
        cv.drawCircle(-0.12f, -0.47f, 0.022f, fill);
        cv.drawCircle(0.12f, -0.47f, 0.022f, fill);
    }

    /** Bintang berputar di atas kepala (pusing). */
    void stars(Canvas cv, float time, float y) {
        fill.setColor(0xFFFFEB3B);
        for (int k = 0; k < 3; k++) {
            double a = time * 4.0 + k * (Math.PI * 2 / 3);
            float sx = (float) Math.cos(a) * 0.3f;
            float sy = y + (float) Math.sin(a) * 0.08f;
            path.reset();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                float r = (i & 1) == 0 ? 0.07f : 0.03f;
                float px = sx + (float) Math.cos(ang) * r, py = sy + (float) Math.sin(ang) * r;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
            cv.drawPath(path, fill);
        }
    }
}
