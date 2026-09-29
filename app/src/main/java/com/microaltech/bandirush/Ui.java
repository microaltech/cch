package com.microaltech.bandirush;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Alat gambar antarmuka 2D (teks bergaris tepi, tombol, ikon) yang dipakai semua layar menu. */
final class Ui {
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Path path = new Path();
    Canvas cv;

    Ui() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void outlined(String s, float x, float y, float size, int color, Paint.Align align) {
        text.setTextAlign(align);
        text.setTextSize(size);
        text.setStyle(Paint.Style.STROKE);
        text.setStrokeWidth(size * 0.14f);
        text.setColor((color & 0xFF000000) | 0x1A1A1A);
        cv.drawText(s, x, y, text);
        text.setStyle(Paint.Style.FILL);
        text.setColor(color);
        cv.drawText(s, x, y, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void button(RectF r, String label, int color) {
        fill.setColor(color);
        float rr = r.height() * 0.3f;
        cv.drawRoundRect(r, rr, rr, fill);
        stroke.setColor(0xCCFFFFFF);
        stroke.setStrokeWidth(r.height() * 0.05f);
        cv.drawRoundRect(r, rr, rr, stroke);
        float size = Math.min(r.height() * 0.45f, r.width() / Math.max(4, label.length()) * 1.6f);
        outlined(label, r.centerX(), r.centerY() + size * 0.37f, size, 0xFFFFFFFF, Paint.Align.CENTER);
    }

    void backButton(float x, float y, float r) {
        fill.setColor(0x88000000);
        cv.drawCircle(x, y, r, fill);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(r * 0.14f);
        cv.drawLine(x + r * 0.35f, y, x - r * 0.35f, y, stroke);
        cv.drawLine(x - r * 0.35f, y, x - r * 0.05f, y - r * 0.3f, stroke);
        cv.drawLine(x - r * 0.35f, y, x - r * 0.05f, y + r * 0.3f, stroke);
    }

    void wumpa(float x, float y, float r) {
        fill.setColor(0xFFFF6F00);
        cv.drawCircle(x, y, r, fill);
        fill.setColor(0xFFFFA726);
        cv.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.55f, fill);
    }

    void trophy(float x, float y, float r, boolean gold) {
        int c = gold ? 0xFFFFC107 : 0xFF616161;
        fill.setColor(c);
        rect.set(x - r * 0.6f, y - r, x + r * 0.6f, y + r * 0.1f);
        cv.drawArc(rect, 0, 180, true, fill);
        cv.drawRect(x - r * 0.6f, y - r, x + r * 0.6f, y - r * 0.45f, fill);
        cv.drawRect(x - r * 0.12f, y, x + r * 0.12f, y + r * 0.55f, fill);
        cv.drawRect(x - r * 0.45f, y + r * 0.5f, x + r * 0.45f, y + r * 0.75f, fill);
        stroke.setColor(c);
        stroke.setStrokeWidth(r * 0.15f);
        rect.set(x - r * 0.95f, y - r * 0.85f, x - r * 0.35f, y - r * 0.2f);
        cv.drawArc(rect, 90, 180, false, stroke);
        rect.set(x + r * 0.35f, y - r * 0.85f, x + r * 0.95f, y - r * 0.2f);
        cv.drawArc(rect, 270, 180, false, stroke);
    }

    void dim(int w, int h, int color) {
        fill.setColor(color);
        cv.drawRect(0, 0, w, h, fill);
    }
}
