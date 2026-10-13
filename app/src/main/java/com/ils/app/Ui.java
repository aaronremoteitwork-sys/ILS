package com.ils.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the UI in code (no XML layouts). */
final class Ui {

    private Ui() {}

    static final int PRIMARY = 0xFF1F3A5F;
    static final int ACCENT = 0xFFF2A900;
    static final int BG = 0xFFF2F4F7;
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF1B1F24;
    static final int MUTED = 0xFF66707A;
    static final int LINE = 0xFFDDE1E6;
    static final int DANGER = 0xFFB3261E;

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
        GradientDrawable shape = rounded(c, color, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, rounded(c, Color.BLACK, radiusDp));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : PRIMARY);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        if (primary) {
            b.setBackground(pressable(c, PRIMARY, 10));
        } else {
            GradientDrawable g = rounded(c, CARD, 10);
            g.setStroke(dp(c, 1.5f), PRIMARY);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), g, rounded(c, Color.BLACK, 10)));
        }
        return b;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        l.setBackground(rounded(c, CARD, 12));
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
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(a, 10);
        row.setPadding(p, p, p * 2, p);
        row.setBackground(pressable(a, CARD, 12));
        row.setElevation(dp(a, 1));

        int size = dp(a, 64);
        FrameLayout thumbBox = new FrameLayout(a);
        thumbBox.setBackground(rounded(a, 0xFFE7EAEE, 8));
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
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        FrameLayout wrap = new FrameLayout(a);
        int p = dp(a, 22);
        wrap.setPadding(p, dp(a, 8), p, 0);
        wrap.addView(input);

        AlertDialog d = new AlertDialog.Builder(a)
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
        new AlertDialog.Builder(a)
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

    /** Full-screen photo viewer. Tap anywhere to close. */
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
