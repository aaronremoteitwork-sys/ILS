package com.ils.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/** Photo files live in the app's private folder files/photos. */
final class Photos {

    private Photos() {}

    private static final Random RNG = new Random();

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static String newName() {
        return "p_" + System.currentTimeMillis() + "_" + RNG.nextInt(100000) + ".jpg";
    }

    static File file(Context c, String name) {
        return new File(dir(c), name);
    }

    static void delete(Context c, String name) {
        if (name == null) return;
        //noinspection ResultOfMethodCallIgnored
        file(c, name).delete();
    }

    /**
     * The TC52 camera takes 13 MP photos. Shrink to at most maxSide pixels, bake in the
     * EXIF rotation, and re-save, so storage stays small and lists scroll smoothly.
     */
    static void shrink(File f, int maxSide) {
        try {
            int rotation = 0;
            try {
                ExifInterface exif = new ExifInterface(f.getAbsolutePath());
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            } catch (IOException ignored) {
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (b == null) return;

            float scale = Math.min(1f, (float) maxSide / Math.max(b.getWidth(), b.getHeight()));
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            if (rotation != 0) m.postRotate(rotation);
            Bitmap out = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);

            try (FileOutputStream fos = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            }
            if (out != b) out.recycle();
            b.recycle();
        } catch (Throwable ignored) {
            // Keep the original file if anything goes wrong.
        }
    }

    /** Loads a photo scaled down to roughly maxPx on its longest side. Cached for thumbnails. */
    static Bitmap load(Context c, String name, int maxPx) {
        if (name == null) return null;
        String key = name + "@" + maxPx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        File f = file(c, name);
        if (!f.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx);
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        if (b != null && maxPx <= 400) CACHE.put(key, b);
        return b;
    }

    private static int sampleSize(int w, int h, int target) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= target) s *= 2;
        return s;
    }
}
