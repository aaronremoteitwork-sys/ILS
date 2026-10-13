package com.ils.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Minimal Code 128 (subset B) barcode generator. The TC52 scanner reads Code 128 out of the box. */
final class Code128 {

    private Code128() {}

    // Bar/space widths for symbol values 0..106 (106 = stop).
    static final String[] PATTERNS = {
            "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
            "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
            "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
            "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
            "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
            "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
            "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
            "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
            "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
            "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
            "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    };

    private static final int START_B = 104;
    private static final int STOP = 106;

    /** Returns the module pattern (true = black) without quiet zones. */
    static boolean[] encode(String text) {
        int n = text.length();
        int[] codes = new int[n + 3];
        codes[0] = START_B;
        int sum = START_B;
        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch < 32 || ch > 126) throw new IllegalArgumentException("Unsupported character in barcode");
            int v = ch - 32;
            codes[i + 1] = v;
            sum += v * (i + 1);
        }
        codes[n + 1] = sum % 103;
        codes[n + 2] = STOP;

        int total = 0;
        for (int code : codes) for (char w : PATTERNS[code].toCharArray()) total += w - '0';
        boolean[] modules = new boolean[total];
        int pos = 0;
        for (int code : codes) {
            String p = PATTERNS[code];
            for (int k = 0; k < p.length(); k++) {
                int w = p.charAt(k) - '0';
                boolean black = (k % 2 == 0);
                for (int j = 0; j < w; j++) modules[pos++] = black;
            }
        }
        return modules;
    }

    /**
     * Draws a printable label: optional title on top, bars, and the code in text underneath.
     * White background so it prints and scans cleanly.
     */
    static Bitmap render(String code, String title) {
        boolean[] m = encode(code);
        int module = 4;
        int quiet = 12 * module;
        int barsW = m.length * module + quiet * 2;
        int barH = 170;
        int pad = 28;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titlePaint.setTextSize(46);

        Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        codePaint.setColor(Color.BLACK);
        codePaint.setTypeface(Typeface.MONOSPACE);
        codePaint.setTextSize(36);
        codePaint.setLetterSpacing(0.15f);

        int maxW = 1100;
        if (title != null) {
            while (titlePaint.measureText(title) > maxW - pad * 2 && titlePaint.getTextSize() > 22) {
                titlePaint.setTextSize(titlePaint.getTextSize() - 2);
            }
        }
        int width = barsW;
        if (title != null) width = Math.max(width, (int) titlePaint.measureText(title) + pad * 2);
        width = Math.max(width, (int) codePaint.measureText(code) + pad * 2);

        Paint.FontMetrics tf = titlePaint.getFontMetrics();
        Paint.FontMetrics cf = codePaint.getFontMetrics();
        int titleH = title == null ? 0 : (int) Math.ceil(tf.descent - tf.ascent) + 16;
        int codeH = (int) Math.ceil(cf.descent - cf.ascent);
        int height = pad + titleH + barH + 12 + codeH + pad;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        float y = pad;
        if (title != null) {
            String t = title;
            while (titlePaint.measureText(t) > width - pad * 2 && t.length() > 1) {
                t = t.substring(0, t.length() - 2) + "…";
            }
            canvas.drawText(t, (width - titlePaint.measureText(t)) / 2f, y - tf.ascent, titlePaint);
            y += titleH;
        }

        Paint bar = new Paint();
        bar.setColor(Color.BLACK);
        int x0 = (width - barsW) / 2 + quiet;
        for (int i = 0; i < m.length; i++) {
            if (m[i]) canvas.drawRect(x0 + i * module, y, x0 + (i + 1) * module, y + barH, bar);
        }
        y += barH + 12;
        canvas.drawText(code, (width - codePaint.measureText(code)) / 2f, y - cf.ascent, codePaint);
        return bmp;
    }
}
