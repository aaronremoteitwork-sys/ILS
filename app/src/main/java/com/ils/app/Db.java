package com.ils.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local SQLite storage for bins and the items inside them. */
final class Db extends SQLiteOpenHelper {

    static final class Bin {
        long id;
        String name;
        String code;
        int itemCount;
    }

    static final class Item {
        long id;
        long binId;
        String name;
        String photo;   // file name inside files/photos, or null
        String binName; // filled in by search queries
        String binCode;
    }

    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "ils.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bins (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "code TEXT NOT NULL UNIQUE," +
                "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE items (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bin_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "photo TEXT," +
                "created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX items_bin ON items(bin_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Version 1 only. Future schema changes go here.
    }

    /** Bin codes look like ILS00001. Scans and typed codes are normalised to that form. */
    static String formatCode(long id) {
        return String.format(Locale.US, "ILS%05d", id);
    }

    // ---------- Bins ----------

    long createBin(String name) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("name", name.trim());
            v.put("code", "TMP" + System.nanoTime());
            v.put("created", System.currentTimeMillis());
            long id = db.insertOrThrow("bins", null, v);
            ContentValues c = new ContentValues();
            c.put("code", formatCode(id));
            db.update("bins", c, "_id=?", args(id));
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    void renameBin(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        getWritableDatabase().update("bins", v, "_id=?", args(id));
    }

    /** Deletes the bin and its items. Returns the photo file names that should be removed. */
    List<String> deleteBin(long id) {
        List<String> photos = new ArrayList<>();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT photo FROM items WHERE bin_id=? AND photo IS NOT NULL", args(id))) {
                while (c.moveToNext()) photos.add(c.getString(0));
            }
            db.delete("items", "bin_id=?", args(id));
            db.delete("bins", "_id=?", args(id));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return photos;
    }

    private static final String BIN_SELECT =
            "SELECT b._id, b.name, b.code, (SELECT COUNT(*) FROM items i WHERE i.bin_id=b._id) FROM bins b ";

    List<Bin> bins() {
        return readBins(getReadableDatabase().rawQuery(
                BIN_SELECT + "ORDER BY b.name COLLATE NOCASE", null));
    }

    Bin bin(long id) {
        List<Bin> l = readBins(getReadableDatabase().rawQuery(BIN_SELECT + "WHERE b._id=?", args(id)));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Looks up a bin by its barcode. Accepts "ILS00001", "ils1", or just "1". */
    Bin binByCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.US);
        if (s.isEmpty()) return null;
        List<Bin> l = readBins(getReadableDatabase().rawQuery(BIN_SELECT + "WHERE b.code=?", new String[]{s}));
        if (!l.isEmpty()) return l.get(0);
        String digits = s.startsWith("ILS") ? s.substring(3) : s;
        if (digits.matches("\\d{1,9}")) {
            l = readBins(getReadableDatabase().rawQuery(BIN_SELECT + "WHERE b.code=?",
                    new String[]{formatCode(Long.parseLong(digits))}));
            if (!l.isEmpty()) return l.get(0);
        }
        return null;
    }

    List<Bin> searchBins(String q) {
        return readBins(getReadableDatabase().rawQuery(
                BIN_SELECT + "WHERE b.name LIKE ? ESCAPE '\\' ORDER BY b.name COLLATE NOCASE",
                new String[]{like(q)}));
    }

    private static List<Bin> readBins(Cursor c) {
        List<Bin> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                Bin b = new Bin();
                b.id = c.getLong(0);
                b.name = c.getString(1);
                b.code = c.getString(2);
                b.itemCount = c.getInt(3);
                out.add(b);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---------- Items ----------

    List<Item> items(long binId) {
        return readItems(getReadableDatabase().rawQuery(
                "SELECT i._id, i.bin_id, i.name, i.photo, b.name, b.code FROM items i " +
                        "JOIN bins b ON b._id=i.bin_id WHERE i.bin_id=? ORDER BY i.name COLLATE NOCASE",
                args(binId)));
    }

    Item item(long id) {
        List<Item> l = readItems(getReadableDatabase().rawQuery(
                "SELECT i._id, i.bin_id, i.name, i.photo, b.name, b.code FROM items i " +
                        "JOIN bins b ON b._id=i.bin_id WHERE i._id=?", args(id)));
        return l.isEmpty() ? null : l.get(0);
    }

    /** Items whose name contains the query, across every bin. */
    List<Item> searchItems(String q) {
        return readItems(getReadableDatabase().rawQuery(
                "SELECT i._id, i.bin_id, i.name, i.photo, b.name, b.code FROM items i " +
                        "JOIN bins b ON b._id=i.bin_id WHERE i.name LIKE ? ESCAPE '\\' " +
                        "ORDER BY i.name COLLATE NOCASE",
                new String[]{like(q)}));
    }

    long addItem(long binId, String name, String photo) {
        ContentValues v = new ContentValues();
        v.put("bin_id", binId);
        v.put("name", name.trim());
        if (photo != null) v.put("photo", photo);
        else v.putNull("photo");
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("items", null, v);
    }

    void renameItem(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        getWritableDatabase().update("items", v, "_id=?", args(id));
    }

    void moveItem(long id, long newBinId) {
        ContentValues v = new ContentValues();
        v.put("bin_id", newBinId);
        getWritableDatabase().update("items", v, "_id=?", args(id));
    }

    /** Sets a new photo and returns the old file name (or null) so it can be deleted. */
    String setItemPhoto(long id, String photo) {
        Item old = item(id);
        ContentValues v = new ContentValues();
        v.put("photo", photo);
        getWritableDatabase().update("items", v, "_id=?", args(id));
        return old == null ? null : old.photo;
    }

    /** Deletes an item and returns its photo file name (or null). */
    String deleteItem(long id) {
        Item old = item(id);
        getWritableDatabase().delete("items", "_id=?", args(id));
        return old == null ? null : old.photo;
    }

    private static List<Item> readItems(Cursor c) {
        List<Item> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                Item it = new Item();
                it.id = c.getLong(0);
                it.binId = c.getLong(1);
                it.name = c.getString(2);
                it.photo = c.isNull(3) ? null : c.getString(3);
                it.binName = c.getString(4);
                it.binCode = c.getString(5);
                out.add(it);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---------- helpers ----------

    private static String[] args(long id) {
        return new String[]{String.valueOf(id)};
    }

    private static String like(String q) {
        String e = q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + e + "%";
    }
}
