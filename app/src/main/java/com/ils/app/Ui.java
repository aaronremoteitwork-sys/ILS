package com.ils.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small helpers for building the UI in code (no XML layouts).
 * Screen colors live here. {@link #applyTheme} installs them before the window is drawn.
 */
final class Ui {

    private Ui() {}

    /** Same prefs file DataWedge uses for {@code dw_config}. Theme keys are separate. */
    static final String PREFS = "ils";
    static final String PREF_THEME = "theme";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";
    static final String THEME_AUTO = "auto";

    static int ACCENT;
    static int ON_ACCENT;
    static int BG;
    static int CARD;
    static int TEXT;
    static int MUTED;
    static int LINE;
    static int DANGER;
    static int THUMB;
    static int RIPPLE;
    static int BAR;
    static int STATUS;

    static boolean DARK;

    private static boolean ready;
    private static boolean boundDark;
    private static String boundMode;

    static {
        bind(false);
    }

    private static void bind(boolean dark) {
        DARK = dark;
        if (dark) {
            BG = 0xFF1E1F22;
            CARD = 0xFF2B2D31;
            TEXT = 0xFFF2F3F5;
            MUTED = 0xFFB5BAC1;
            LINE = 0xFF3F4147;
            ACCENT = 0xFF4A90E2;
            THUMB = 0xFF3A3D44;
            RIPPLE = 0x33FFFFFF;
            BAR = 0xFF1E1F22;
            STATUS = 0xFF1E1F22;
            DANGER = 0xFFFFB4AB;
        } else {
            BG = 0xFFFFFFFF;
            CARD = 0xFFFFFFFF;
            TEXT = 0xFF1B1F24;
            MUTED = 0xFF5E6A75;
            LINE = 0xFFE1E4E8;
            ACCENT = 0xFF1E6FD9;
            THUMB = 0xFFE7EAEE;
            RIPPLE = 0x22000000;
            BAR = 0xFFFFFFFF;
            STATUS = 0xFFFFFFFF;
            DANGER = 0xFFB3261E;
        }
        ON_ACCENT = Color.WHITE;
    }

    static String themeMode(Context c) {
        String v = prefs(c).getString(PREF_THEME, THEME_AUTO);
        if (THEME_LIGHT.equals(v) || THEME_DARK.equals(v)) return v;
        return THEME_AUTO;
    }

    static void saveTheme(Context c, String mode) {
        if (!THEME_LIGHT.equals(mode) && !THEME_DARK.equals(mode)) mode = THEME_AUTO;
        prefs(c).edit().putString(PREF_THEME, mode).apply();
        ready = false;
    }

    /** Light, Dark, or Auto (system night mode). Default is Auto. */
    static boolean isDark(Context c) {
        String mode = themeMode(c);
        if (THEME_DARK.equals(mode)) return true;
        if (THEME_LIGHT.equals(mode)) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static void ensure(Context c) {
        String mode = themeMode(c);
        boolean dark = isDark(c);
        if (ready && mode.equals(boundMode) && boundDark == dark) return;
        bind(dark);
        boundMode = mode;
        boundDark = dark;
        ready = true;
    }

    /** Call before {@code super.onCreate} so dialogs, menus, and the action bar pick up the theme. */
    static void applyTheme(Activity a) {
        ensure(a);
        a.setTheme(DARK ? R.style.AppTheme_Dark : R.style.AppTheme);
    }

    /** Paints the status bar, navigation bar, and action bar from the current palette. */
    @SuppressWarnings("deprecation")
    static void paintSystemBars(Activity a) {
        ensure(a);
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(STATUS);
        w.setNavigationBarColor(BG);
        w.setBackgroundDrawable(new ColorDrawable(BG));
        ActionBar bar = a.getActionBar();
        if (bar != null) {
            bar.setBackgroundDrawable(new ColorDrawable(BAR));
            bar.setElevation(dp(a, DARK ? 0 : 1));
        }
        int titleId = a.getResources().getIdentifier("action_bar_title", "id", "android");
        if (titleId != 0) {
            TextView title = w.getDecorView().findViewById(titleId);
            if (title != null) title.setTextColor(TEXT);
        }
        View decor = w.getDecorView();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int vis = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(DARK ? (vis & ~light) : (vis | light));
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(DARK ? 0 : mask, mask);
            }
        }
    }

    static AlertDialog.Builder dialog(Context c) {
        ensure(c);
        return new AlertDialog.Builder(c, DARK ? R.style.IlsDialog_Dark : R.style.IlsDialog_Light);
    }

    static void styleField(EditText input) {
        ensure(input.getContext());
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHighlightColor((ACCENT & 0x00FFFFFF) | 0x33000000);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    interface TextCallback {
        void onText(String text);
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Drawable pressable(Context c, int color, float radiusDp) {
        ensure(c);
        GradientDrawable shape = rounded(c, color, radiusDp);
        if (!DARK && color == CARD && radiusDp > 0) shape.setStroke(dp(c, 1), LINE);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, rounded(c, Color.BLACK, radiusDp));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        ensure(c);
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String label, boolean primary) {
        ensure(c);
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? ON_ACCENT : ACCENT);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        if (primary) {
            b.setBackground(pressable(c, ACCENT, 10));
        } else {
            GradientDrawable g = rounded(c, CARD, 10);
            g.setStroke(dp(c, 1.5f), ACCENT);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE), g, rounded(c, Color.BLACK, 10)));
        }
        return b;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        ensure(c);
        LinearLayout l = vertical(c);
        GradientDrawable g = rounded(c, CARD, 12);
        if (!DARK) g.setStroke(dp(c, 1), LINE);
        l.setBackground(g);
        l.setElevation(dp(c, 1.5f));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    /** A row with a square photo thumbnail and a title + subtitle. */
    static View itemRow(Activity a, Db.Item item, String subtitle) {
        ensure(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(a, 10);
        row.setPadding(p, p, p * 2, p);
        row.setBackground(pressable(a, CARD, 12));
        row.setElevation(dp(a, 1));

        int size = dp(a, 64);
        FrameLayout thumbBox = new FrameLayout(a);
        thumbBox.setBackground(rounded(a, THUMB, 8));
        thumbBox.setClipToOutline(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(size, size);
        Bitmap thumb = Photos.load(a, item.photo, 256);
        if (thumb != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            thumbBox.addView(iv, new FrameLayout.LayoutParams(size, size));
        } else {
            TextView none = text(a, "No\nphoto", 11, MUTED, false);
            none.setGravity(Gravity.CENTER);
            thumbBox.addView(none, new FrameLayout.LayoutParams(size, size));
        }
        row.addView(thumbBox, tlp);

        LinearLayout texts = vertical(a);
        texts.setPadding(dp(a, 14), 0, 0, 0);
        TextView title = text(a, item.name, 17, TEXT, true);
        texts.addView(title);
        if (subtitle != null) {
            TextView sub = text(a, subtitle, 13, MUTED, false);
            sub.setPadding(0, dp(a, 2), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /** A row for a bin: name and "ILS00001 · 3 items". */
    static View binRow(Context c, Db.Bin b) {
        ensure(c);
        LinearLayout row = vertical(c);
        int p = dp(c, 16);
        row.setPadding(p, dp(c, 14), p, dp(c, 14));
        row.setBackground(pressable(c, CARD, 12));
        row.setElevation(dp(c, 1));
        row.addView(text(c, b.name, 18, TEXT, true));
        TextView sub = text(c, b.code + "  ·  " + b.itemCount + (b.itemCount == 1 ? " item" : " items"), 14, MUTED, false);
        sub.setPadding(0, dp(c, 3), 0, 0);
        row.addView(sub);
        return row;
    }

    static void prompt(Activity a, String title, String initial, String hint, String okLabel, TextCallback cb) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(hint);
        styleField(input);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        FrameLayout wrap = new FrameLayout(a);
        int p = dp(a, 22);
        wrap.setPadding(p, dp(a, 8), p, 0);
        wrap.addView(input);

        AlertDialog d = dialog(a)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton(okLabel, null)
                .setNegativeButton("Cancel", null)
                .create();
        showKeyboard(d);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) {
                input.setError("Enter a name");
                return;
            }
            d.dismiss();
            cb.onText(s);
        });
    }

    static void confirm(Activity a, String title, String message, String okLabel, Runnable onOk) {
        dialog(a)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okLabel, (dlg, w) -> onOk.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void showKeyboard(Dialog d) {
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    /** Full-screen photo viewer. Tap anywhere to close. Stays black so the photo is easy to see. */
    static void showPhoto(Activity a, String photoName, String caption) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        Bitmap b = Photos.load(a, photoName, Math.max(dm.widthPixels, dm.heightPixels));
        if (b == null) return;

        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = vertical(a);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        ImageView iv = new ImageView(a);
        iv.setImageBitmap(b);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (caption != null) {
            TextView t = text(a, caption, 18, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            int p = dp(a, 16);
            t.setPadding(p, p, p, p);
            root.addView(t);
        }
        root.setOnClickListener(v -> d.dismiss());
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        d.show();
    }
}
