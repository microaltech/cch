package com.microaltech.bandirush;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;

/**
 * Editor level di dalam game: grid 2D dilihat dari atas (baris jauh di atas), palet petak di kanan,
 * 3 slot simpanan, uji main, serta salin/tempel sebagai teks untuk berbagi level.
 */
final class Editor {
    static final int ACT_NONE = 0, ACT_BACK = 1, ACT_PLAY = 2, ACT_COPY = 3, ACT_PASTE = 4;

    static final char[] TILES = {'.', ' ', 'S', 'G', 'C', '?', 'B', 'L', 'A', 'T', 'N', 'K', 'I',
            '2', '3', 'W', 'w', 'E', 'H', 'm', 'U', 'i', 'b', '!', 'o', 'O', 'J', 'V', 'Y', 'r'};
    static final String[] TILE_NAMES = {"Tanah", "Jurang", "Posisi awal", "Finish", "Peti kayu", "Peti ?",
            "Peti pantul", "Peti nyawa", "Peti topeng", "TNT", "Nitro", "Checkpoint", "Peti besi",
            "Tumpukan 2 peti", "Tumpukan 3 peti", "Wumpa", "Wumpa melayang", "Kepiting", "Babi hutan",
            "Pijakan bergerak", "Palang rendah", "Besi di atas jurang", "Pantul di atas jurang", "Peti !",
            "Peti bergaris", "Bergaris di atas jurang", "Pinguin", "Kelelawar", "Piranha", "Kayu apung"};
    static final Theme[] THEMES = {Theme.JUNGLE, Theme.SUNSET, Theme.TEMPLE, Theme.SNOW, Theme.BEACH,
            Theme.RIVER, Theme.CAVE, Theme.ICE};
    static final String[] THEME_NAMES = {"Hutan", "Senja", "Kuil", "Salju", "Pantai", "Sungai", "Gua", "Gletser"};
    private static final int[] THEME_MUSIC = {Music.JUNGLE, Music.SUNSET, Music.TEMPLE, Music.SNOW, Music.JUNGLE,
            Music.JUNGLE, Music.TEMPLE, Music.SNOW};
    private static final String[] BUTTONS = {"UJI MAIN", "SIMPAN", "LEBAR", "TEMA", "+ BARIS", "- BARIS", "SALIN", "TEMPEL"};
    private static final int MIN_ROWS = 8, MAX_ROWS = 150;
    private static final String HEADER = "BANDIRUSH tema=";

    private final Save save;
    private final Ui ui;
    private final RectF r = new RectF();

    int width = 5;
    final ArrayList<char[]> rows = new ArrayList<>(); // indeks 0 = baris awal (paling dekat)
    int scroll;
    int sel;
    int slot;
    int theme;
    private String msg = "";
    private float msgT;
    private boolean confirmClear;

    // tata letak (dihitung di layout())
    private int w, h;
    private float cell, gx0, gy0, gy1, px0, py0, ps, arrowX;
    private int visible;

    Editor(Save save, Ui ui) {
        this.save = save;
        this.ui = ui;
        loadSlot(0);
    }

    void loadSlot(int s) {
        slot = s;
        String d = save.custom(s);
        if (d == null || !load(d)) resetGrid();
        scroll = 0;
        confirmClear = false;
    }

    void saveSlot() {
        save.setCustom(slot, serialize());
    }

    void resetGrid() {
        rows.clear();
        for (int i = 0; i < 30; i++) {
            char[] row = new char[width];
            java.util.Arrays.fill(row, '.');
            rows.add(row);
        }
        rows.get(1)[width / 2] = 'S';
        rows.get(rows.size() - 1)[width / 2] = 'G';
        scroll = 0;
    }

    /** Membaca teks level (format hasil serialize(), atau peta mentah). */
    boolean load(String data) {
        String[] lines = data.replace("\r", "").split("\n", -1);
        int first = 0;
        int th = theme;
        if (lines.length > 0 && lines[0].startsWith(HEADER)) {
            try {
                th = Integer.parseInt(lines[0].substring(HEADER.length()).trim());
            } catch (NumberFormatException ignored) {
            }
            first = 1;
        }
        ArrayList<String> map = new ArrayList<>();
        int maxW = 0;
        for (int i = first; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.trim().isEmpty() && ln.length() == 0) continue;
            for (int k = 0; k < ln.length(); k++) {
                if (indexOf(ln.charAt(k)) < 0) return false;
            }
            map.add(ln);
            maxW = Math.max(maxW, ln.length());
        }
        if (map.size() < 2 || maxW < 3 || maxW > 9) return false;
        rows.clear();
        for (int i = map.size() - 1; i >= 0; i--) {
            char[] row = new char[maxW];
            java.util.Arrays.fill(row, ' ');
            String ln = map.get(i);
            for (int k = 0; k < ln.length(); k++) row[k] = ln.charAt(k);
            rows.add(row);
        }
        width = maxW;
        theme = Math.max(0, Math.min(THEMES.length - 1, th));
        scroll = 0;
        return true;
    }

    String serialize() {
        StringBuilder sb = new StringBuilder(HEADER).append(theme);
        for (int i = rows.size() - 1; i >= 0; i--) sb.append('\n').append(rows.get(i));
        return sb.toString();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < TILES.length; i++) if (TILES[i] == c) return i;
        return -1;
    }

    /** @return definisi level siap main, atau null (dan pesan) kalau belum valid. */
    Level.Def toDef() {
        int s = 0, g = 0;
        for (char[] row : rows) {
            for (char c : row) {
                if (c == 'S') s++;
                if (c == 'G') g++;
            }
        }
        if (s != 1 || g < 1) {
            showMsg("Level butuh tepat 1 'S' (awal) dan minimal 1 'G' (finish)");
            return null;
        }
        String[] map = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) map[rows.size() - 1 - i] = new String(rows.get(i));
        Level.Def def = new Level.Def("Level Kustom " + (slot + 1), THEMES[theme], Level.MODE_NORMAL, THEME_MUSIC[theme], 0f,
                "Level buatanmu! Tekan jeda untuk kembali ke editor.", map);
        if (THEMES[theme] == Theme.RIVER) def.water();
        if (THEMES[theme] == Theme.CAVE) def.dark();
        if (THEMES[theme] == Theme.ICE) def.ice();
        return def;
    }

    void showMsg(String m) {
        msg = m;
        msgT = 2.5f;
    }

    void update(float dt) {
        if (msgT > 0) msgT -= dt;
    }

    // ------------------------------------------------------------------ tata letak

    private void layout(int w, int h) {
        this.w = w;
        this.h = h;
        gx0 = h * 0.12f;
        gy0 = h * 0.17f;
        gy1 = h * 0.97f;
        cell = Math.min((w * 0.44f - gx0) / width, h * 0.085f);
        visible = Math.max(1, (int) ((gy1 - gy0) / cell));
        arrowX = gx0 + cell * width + h * 0.08f;
        px0 = Math.max(w * 0.55f, arrowX + h * 0.09f);
        py0 = h * 0.17f;
        ps = Math.min((w * 0.98f - px0) / 6f, h * 0.11f);
        clampScroll();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
    }

    private void buttonRect(int i, RectF out) {
        float bw = (w * 0.98f - px0) / 4f;
        float y = i < 4 ? h * 0.76f : h * 0.87f;
        float x = px0 + (i % 4) * bw;
        out.set(x + bw * 0.04f, y, x + bw * 0.96f, y + h * 0.09f);
    }

    private void clearRect(RectF out) {
        out.set(w * 0.62f, h * 0.035f, w * 0.8f, h * 0.115f);
    }

    // ------------------------------------------------------------------ input

    int tap(float x, float y, int w, int h) {
        layout(w, h);
        if (dist(x, y, h * 0.08f, h * 0.08f) < h * 0.07f) return ACT_BACK;
        for (int i = 0; i < Save.CUSTOM_SLOTS; i++) {
            if (dist(x, y, w * 0.36f + i * h * 0.13f, h * 0.075f) < h * 0.055f) {
                saveSlot();
                loadSlot(i);
                showMsg("Slot " + (i + 1));
                return ACT_NONE;
            }
        }
        clearRect(r);
        if (r.contains(x, y)) {
            if (confirmClear) {
                resetGrid();
                confirmClear = false;
                showMsg("Grid dikosongkan");
            } else {
                confirmClear = true;
                showMsg("Ketuk KOSONGKAN sekali lagi untuk menghapus semua");
            }
            return ACT_NONE;
        }
        confirmClear = false;
        if (dist(x, y, arrowX, gy0 + h * 0.07f) < h * 0.07f) {
            scroll += Math.max(1, visible / 2);
            clampScroll();
            return ACT_NONE;
        }
        if (dist(x, y, arrowX, gy1 - h * 0.07f) < h * 0.07f) {
            scroll -= Math.max(1, visible / 2);
            clampScroll();
            return ACT_NONE;
        }
        // palet
        for (int i = 0; i < TILES.length; i++) {
            float tx = px0 + (i % 6) * ps, ty = py0 + (i / 6) * ps;
            if (x >= tx && x < tx + ps && y >= ty && y < ty + ps) {
                sel = i;
                return ACT_NONE;
            }
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            buttonRect(i, r);
            if (!r.contains(x, y)) continue;
            switch (i) {
                case 0: return ACT_PLAY;
                case 1: saveSlot(); showMsg("Tersimpan di slot " + (slot + 1)); return ACT_NONE;
                case 2: toggleWidth(); return ACT_NONE;
                case 3: theme = (theme + 1) % THEMES.length; return ACT_NONE;
                case 4:
                    if (rows.size() < MAX_ROWS) {
                        char[] row = new char[width];
                        java.util.Arrays.fill(row, '.');
                        rows.add(row);
                    }
                    return ACT_NONE;
                case 5:
                    if (rows.size() > MIN_ROWS) rows.remove(rows.size() - 1);
                    clampScroll();
                    return ACT_NONE;
                case 6: return ACT_COPY;
                default: return ACT_PASTE;
            }
        }
        paintAt(x, y);
        return ACT_NONE;
    }

    void drag(float x, float y, int w, int h) {
        layout(w, h);
        paintAt(x, y);
    }

    private void paintAt(float x, float y) {
        if (x < gx0 || x >= gx0 + cell * width || y < gy0 || y >= gy0 + visible * cell) return;
        int c = (int) ((x - gx0) / cell);
        int k = (int) ((y - gy0) / cell);
        int rr = scroll + (visible - 1 - k);
        if (rr < 0 || rr >= rows.size() || c < 0 || c >= width) return;
        char t = TILES[sel];
        if (t == 'S') {
            for (char[] row : rows) for (int i = 0; i < row.length; i++) if (row[i] == 'S') row[i] = '.';
        }
        rows.get(rr)[c] = t;
    }

    private void toggleWidth() {
        int nw = width == 5 ? 7 : 5;
        for (int i = 0; i < rows.size(); i++) {
            char[] old = rows.get(i);
            char[] row = new char[nw];
            if (nw > width) {
                java.util.Arrays.fill(row, '.');
                System.arraycopy(old, 0, row, 1, width);
                if (old[0] == ' ') row[0] = ' ';
                if (old[width - 1] == ' ') row[nw - 1] = ' ';
            } else {
                System.arraycopy(old, 1, row, 0, nw);
            }
            rows.set(i, row);
        }
        width = nw;
        boolean hasS = false;
        for (char[] row : rows) for (char c : row) if (c == 'S') hasS = true;
        if (!hasS) rows.get(Math.min(1, rows.size() - 1))[width / 2] = 'S';
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------ gambar

    void draw(Canvas cv, int w, int h, float time) {
        layout(w, h);
        ui.cv = cv;
        ui.fill.setColor(0xFF1B2733);
        cv.drawRect(0, 0, w, h, ui.fill);
        float u = h * 0.01f;

        ui.backButton(h * 0.08f, h * 0.08f, h * 0.055f);
        ui.outlined("EDITOR", h * 0.16f, h * 0.1f, u * 6, 0xFFFFD54F, Paint.Align.LEFT);
        for (int i = 0; i < Save.CUSTOM_SLOTS; i++) {
            float sx = w * 0.36f + i * h * 0.13f, sy = h * 0.075f;
            ui.fill.setColor(i == slot ? 0xFF2E7D32 : 0x66FFFFFF);
            cv.drawCircle(sx, sy, h * 0.045f, ui.fill);
            ui.outlined(String.valueOf(i + 1), sx, sy + u * 1.8f, u * 5, 0xFFFFFFFF, Paint.Align.CENTER);
        }
        clearRect(r);
        ui.button(r, confirmClear ? "YAKIN?" : "KOSONGKAN", 0xCCC62828);
        ui.outlined(Lang.t("Tema: ") + Lang.t(THEME_NAMES[theme]) + "  |  " + width + " x " + rows.size(),
                w * 0.98f, h * 0.1f, u * 3.6f, 0xFFB0BEC5, Paint.Align.RIGHT);

        // grid
        for (int k = 0; k < visible; k++) {
            int rr = scroll + (visible - 1 - k);
            if (rr >= rows.size()) continue;
            float y = gy0 + k * cell;
            char[] row = rows.get(rr);
            for (int c = 0; c < width; c++) drawTile(cv, row[c], gx0 + c * cell, y, cell);
            if (rr % 5 == 0) {
                ui.outlined(String.valueOf(rr), gx0 - u, y + cell * 0.7f, cell * 0.45f, 0xFF90A4AE, Paint.Align.RIGHT);
            }
        }
        ui.stroke.setColor(0x66FFFFFF);
        ui.stroke.setStrokeWidth(1f);
        for (int c = 0; c <= width; c++) cv.drawLine(gx0 + c * cell, gy0, gx0 + c * cell, gy0 + visible * cell, ui.stroke);
        for (int k = 0; k <= visible; k++) cv.drawLine(gx0, gy0 + k * cell, gx0 + width * cell, gy0 + k * cell, ui.stroke);

        // panah gulir
        for (int d = 0; d < 2; d++) {
            float ay = d == 0 ? gy0 + h * 0.07f : gy1 - h * 0.07f;
            ui.fill.setColor(0x88FFFFFF);
            cv.drawCircle(arrowX, ay, h * 0.055f, ui.fill);
            ui.fill.setColor(0xFF1B2733);
            float t = h * 0.025f;
            ui.path.reset();
            if (d == 0) {
                ui.path.moveTo(arrowX, ay - t);
                ui.path.lineTo(arrowX + t, ay + t * 0.6f);
                ui.path.lineTo(arrowX - t, ay + t * 0.6f);
            } else {
                ui.path.moveTo(arrowX, ay + t);
                ui.path.lineTo(arrowX + t, ay - t * 0.6f);
                ui.path.lineTo(arrowX - t, ay - t * 0.6f);
            }
            ui.path.close();
            cv.drawPath(ui.path, ui.fill);
        }
        ui.outlined("jauh", arrowX, gy0 + h * 0.15f, u * 2.6f, 0xFF90A4AE, Paint.Align.CENTER);
        ui.outlined("awal", arrowX, gy1 - h * 0.13f, u * 2.6f, 0xFF90A4AE, Paint.Align.CENTER);

        // palet
        for (int i = 0; i < TILES.length; i++) {
            float tx = px0 + (i % 6) * ps, ty = py0 + (i / 6) * ps;
            drawTile(cv, TILES[i], tx + ps * 0.08f, ty + ps * 0.08f, ps * 0.84f);
            if (i == sel) {
                ui.stroke.setColor(0xFFFFEB3B);
                ui.stroke.setStrokeWidth(ps * 0.07f);
                cv.drawRect(tx + ps * 0.04f, ty + ps * 0.04f, tx + ps * 0.96f, ty + ps * 0.96f, ui.stroke);
            }
        }
        ui.outlined(Lang.t("Dipilih: ") + Lang.t(TILE_NAMES[sel]), px0, py0 - u * 1.5f, u * 3.8f, 0xFFFFFFFF, Paint.Align.LEFT);

        for (int i = 0; i < BUTTONS.length; i++) {
            buttonRect(i, r);
            String label = i == 2 ? "LEBAR " + (width == 5 ? 7 : 5) : BUTTONS[i];
            ui.button(r, label, i == 0 ? 0xCC2E7D32 : 0xCC37474F);
        }

        if (msgT > 0f) {
            ui.fill.setColor(0xCC000000);
            cv.drawRect(0, h * 0.44f, w, h * 0.54f, ui.fill);
            ui.outlined(msg, w * 0.5f, h * 0.51f, u * 4.5f, 0xFFFFF176, Paint.Align.CENTER);
        }
    }

    private void drawTile(Canvas cv, char t, float x, float y, float s) {
        boolean pit = t == ' ' || t == 'w' || t == 'm' || t == 'i' || t == 'b' || t == 'O' || t == 'Y' || t == 'r';
        ui.fill.setColor(pit ? (THEMES[theme] == Theme.RIVER ? 0xFF1565C0 : 0xFF14231A) : 0xFFCFA86E);
        cv.drawRect(x, y, x + s, y + s, ui.fill);
        float cx = x + s / 2, cy = y + s / 2, in = s * 0.14f;
        int box = 0;
        String label = null;
        int labelColor = 0xFF3E2210;
        switch (t) {
            case 'S': label = "S"; labelColor = 0xFF0D47A1; break;
            case 'G':
                ui.fill.setColor(0xFFFF80D0);
                ui.path.reset();
                ui.path.moveTo(cx, y + in);
                ui.path.lineTo(x + s - in * 1.5f, cy);
                ui.path.lineTo(cx, y + s - in);
                ui.path.lineTo(x + in * 1.5f, cy);
                ui.path.close();
                cv.drawPath(ui.path, ui.fill);
                break;
            case 'C': box = 0xFFC07A3A; break;
            case '2': box = 0xFFC07A3A; label = "2"; break;
            case '3': box = 0xFFC07A3A; label = "3"; break;
            case '?': box = 0xFFC07A3A; label = "?"; break;
            case 'B': case 'b': box = 0xFFC07A3A; label = "^"; break;
            case 'L': box = 0xFFC07A3A; label = "1UP"; labelColor = 0xFFB71C1C; break;
            case 'A': box = 0xFFC07A3A; label = "A"; labelColor = 0xFF6A1B9A; break;
            case 'K': box = 0xFFD6A15E; label = "C"; labelColor = 0xFF0D47A1; break;
            case 'T': box = 0xFFF4511E; label = "T"; break;
            case 'N': box = 0xFF43A047; label = "N"; labelColor = 0xFFCCFF90; break;
            case 'I': case 'i': box = 0xFF90A4AE; break;
            case '!': box = 0xFFCFD8DC; label = "!"; labelColor = 0xFFD32F2F; break;
            case 'o': case 'O':
                ui.stroke.setColor(t == 'o' ? 0xFFFFFFFF : 0xFFB3E5FC);
                ui.stroke.setStrokeWidth(s * 0.06f);
                cv.drawRect(x + in, y + in, x + s - in, y + s - in, ui.stroke);
                break;
            case 'W': case 'w':
                ui.wumpa(cx, cy, s * 0.22f);
                break;
            case 'E':
                ui.fill.setColor(0xFFE53935);
                cv.drawCircle(cx, cy, s * 0.3f, ui.fill);
                break;
            case 'H':
                ui.fill.setColor(0xFF795548);
                cv.drawCircle(cx, cy, s * 0.3f, ui.fill);
                break;
            case 'm':
                ui.fill.setColor(0xFFA1887F);
                cv.drawRect(x + in * 0.5f, cy - s * 0.2f, x + s - in * 0.5f, cy + s * 0.2f, ui.fill);
                break;
            case 'r':
                ui.fill.setColor(0xFF6D4C41);
                cv.drawRoundRect(x + in * 0.5f, cy - s * 0.18f, x + s - in * 0.5f, cy + s * 0.18f, s * 0.15f, s * 0.15f, ui.fill);
                break;
            case 'J':
                ui.fill.setColor(0xFF263238);
                cv.drawCircle(cx, cy, s * 0.3f, ui.fill);
                ui.fill.setColor(0xFFFAFAFA);
                cv.drawCircle(cx, cy + s * 0.05f, s * 0.17f, ui.fill);
                break;
            case 'V':
                ui.fill.setColor(0xFF4A148C);
                cv.drawCircle(cx, cy, s * 0.14f, ui.fill);
                cv.drawRect(x + in, cy - s * 0.06f, x + s - in, cy + s * 0.02f, ui.fill);
                break;
            case 'Y':
                ui.fill.setColor(0xFF00897B);
                cv.drawOval(x + in, cy - s * 0.16f, x + s - in, cy + s * 0.16f, ui.fill);
                ui.fill.setColor(0xFFFF7043);
                cv.drawCircle(x + s - in * 1.8f, cy + s * 0.05f, s * 0.08f, ui.fill);
                break;
            case 'U':
                ui.fill.setColor(0xFF6D6A7A);
                cv.drawRect(x, y + in, x + s, cy, ui.fill);
                ui.fill.setColor(0xFFFFD600);
                cv.drawCircle(cx, cy + s * 0.18f, s * 0.08f, ui.fill);
                break;
            default:
                break;
        }
        if (box != 0) {
            ui.fill.setColor(box);
            cv.drawRect(x + in, y + in, x + s - in, y + s - in, ui.fill);
            if (t == 'C') {
                ui.stroke.setColor(0xFF6B3E1A);
                ui.stroke.setStrokeWidth(s * 0.05f);
                cv.drawLine(x + in, y + in, x + s - in, y + s - in, ui.stroke);
                cv.drawLine(x + s - in, y + in, x + in, y + s - in, ui.stroke);
            }
        }
        if (label != null) {
            ui.text.setStyle(Paint.Style.FILL);
            float ts = s * (label.length() > 1 ? 0.3f : 0.5f);
            ui.text.setTextSize(ts);
            ui.text.setColor(labelColor);
            cv.drawText(label, cx, cy + ts * 0.36f, ui.text);
        }
    }
}
