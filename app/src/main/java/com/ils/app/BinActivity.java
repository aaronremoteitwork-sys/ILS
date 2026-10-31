package com.ils.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** One bin: its barcode label and the items inside, with photos. */
public class BinActivity extends ScanActivity {

    static final String EXTRA_ID = "bin_id";
    private static final int REQ_PHOTO = 1;

    private Db db;
    private long binId;
    private Db.Bin bin;

    private TextView nameView, codeView, itemsHeader;
    private ImageView barcodeView;
    private ItemsAdapter adapter;
    private TextView emptyView;

    // Camera hand-off state (survives the activity being recreated while the camera is open).
    private String pendingPhoto;
    private String pendingName;
    private long pendingItemId = -1;

    static void open(Activity from, long id) {
        from.startActivity(new Intent(from, BinActivity.class).putExtra(EXTRA_ID, id));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Db.get(this);
        binId = getIntent().getLongExtra(EXTRA_ID, -1);
        if (state != null) {
            pendingPhoto = state.getString("pPhoto");
            pendingName = state.getString("pName");
            pendingItemId = state.getLong("pItem", -1);
        }
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        ListView list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setCacheColorHint(Ui.BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.dp(this, 8));
        int p = Ui.dp(this, 16);
        list.setPadding(p, 0, p, p);
        list.setClipToPadding(false);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.addHeaderView(buildHeader(), null, false);
        adapter = new ItemsAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Db.Item it = itemAt(list, pos);
            if (it == null) return;
            if (it.photo != null) Ui.showPhoto(this, it.photo, it.name);
            else takePhotoFor(it);
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            Db.Item it = itemAt(list, pos);
            if (it != null) itemOptions(it);
            return it != null;
        });
        setContentView(list);
    }

    private Db.Item itemAt(ListView list, int pos) {
        int i = pos - list.getHeaderViewsCount();
        return (i >= 0 && i < adapter.getCount()) ? adapter.get(i) : null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pPhoto", pendingPhoto);
        out.putString("pName", pendingName);
        out.putLong("pItem", pendingItemId);
    }

    private View buildHeader() {
        LinearLayout h = Ui.vertical(this);
        h.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));

        LinearLayout label = Ui.card(this);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        nameView = Ui.text(this, "", 24, Ui.TEXT, true);
        nameView.setGravity(Gravity.CENTER);
        label.addView(nameView);
        barcodeView = new ImageView(this);
        barcodeView.setAdjustViewBounds(true);
        barcodeView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 120));
        blp.topMargin = Ui.dp(this, 10);
        label.addView(barcodeView, blp);
        codeView = Ui.text(this, "", 13, Ui.MUTED, false);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        label.addView(codeView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button share = Ui.button(this, "Share / print label", false);
        share.setOnClickListener(v -> shareLabel());
        Button rename = Ui.button(this, "Rename", false);
        rename.setOnClickListener(v -> renameBin());
        buttons.addView(share, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = Ui.dp(this, 8);
        buttons.addView(rename, rlp);
        label.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.addView(label);

        Button add = Ui.button(this, "+  Add item", true);
        add.setOnClickListener(v -> addItem());
        h.addView(add, Ui.matchWrap(this, 16));

        itemsHeader = Ui.text(this, "", 13, Ui.MUTED, true);
        itemsHeader.setLetterSpacing(0.08f);
        itemsHeader.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 4));
        h.addView(itemsHeader);

        emptyView = Ui.text(this, "No items yet. Tap \"Add item\" to name something and snap a photo of it.", 15, Ui.MUTED, false);
        emptyView.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), 0);
        h.addView(emptyView);

        TextView tip = Ui.text(this, "Tip: tap an item to see its photo, press and hold to rename or delete it.", 12, Ui.MUTED, false);
        tip.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, Ui.dp(this, 6));
        h.addView(tip);
        return h;
    }

    private void refresh() {
        bin = db.bin(binId);
        if (bin == null) {
            finish();
            return;
        }
        setTitle(bin.name);
        nameView.setText(bin.name);
        codeView.setText(bin.code);
        barcodeView.setImageBitmap(Code128.render(bin.code, null));
        List<Db.Item> items = db.items(binId);
        adapter.set(items);
        itemsHeader.setText(("Items (" + items.size() + ")").toUpperCase());
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------- items ----------

    private void addItem() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Item name (e.g. Christmas lights)");
        Ui.styleField(input);
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 22);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input);

        AlertDialog d = Ui.dialog(this)
                .setTitle("Add item to " + bin.name)
                .setView(wrap)
                .setPositiveButton("Take photo", null)
                .setNeutralButton("No photo", null)
                .setNegativeButton("Cancel", null)
                .create();
        Ui.showKeyboard(d);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter a name");
                return;
            }
            d.dismiss();
            pendingName = name;
            pendingItemId = -1;
            launchCamera();
        });
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Enter a name");
                return;
            }
            d.dismiss();
            db.addItem(binId, name, null);
            refresh();
        });
    }

    private void takePhotoFor(Db.Item it) {
        pendingName = it.name;
        pendingItemId = it.id;
        launchCamera();
    }

    private void launchCamera() {
        pendingPhoto = Photos.newName();
        Uri uri = FileProviderLite.uriFor("photos", pendingPhoto);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.setClipData(ClipData.newRawUri("photo", uri));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show();
            if (pendingItemId < 0 && pendingName != null) {
                db.addItem(binId, pendingName, null);
                refresh();
            }
            clearPending();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PHOTO || pendingPhoto == null) return;

        final String photo = pendingPhoto;
        final String name = pendingName;
        final long itemId = pendingItemId;
        clearPending();

        final File f = Photos.file(this, photo);
        if (resultCode != RESULT_OK || !f.exists() || f.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (itemId < 0) Toast.makeText(this, "Photo cancelled, item not added", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Photos.shrink(f, 1600);
            runOnUiThread(() -> {
                if (itemId < 0) {
                    if (name != null) db.addItem(binId, name, photo);
                } else {
                    Photos.delete(this, db.setItemPhoto(itemId, photo));
                }
                if (!isFinishing()) refresh();
            });
        }).start();
    }

    private void clearPending() {
        pendingPhoto = null;
        pendingName = null;
        pendingItemId = -1;
    }

    private void itemOptions(Db.Item it) {
        String photoLabel = it.photo != null ? "Take a new photo" : "Add a photo";
        Ui.dialog(this)
                .setTitle(it.name)
                .setItems(new String[]{"Rename", photoLabel, "Move to another bin", "Delete"}, (d, which) -> {
                    if (which == 0) {
                        Ui.prompt(this, "Rename item", it.name, "Item name", "Save", n -> {
                            db.renameItem(it.id, n);
                            refresh();
                        });
                    } else if (which == 1) {
                        takePhotoFor(it);
                    } else if (which == 2) {
                        moveItem(it);
                    } else {
                        Ui.confirm(this, "Delete \"" + it.name + "\"?", "The item and its photo will be removed.", "Delete", () -> {
                            Photos.delete(this, db.deleteItem(it.id));
                            refresh();
                        });
                    }
                })
                .show();
    }

    private void moveItem(Db.Item it) {
        List<Db.Bin> all = db.bins();
        List<Db.Bin> others = new ArrayList<>();
        for (Db.Bin b : all) if (b.id != binId) others.add(b);
        if (others.isEmpty()) {
            Toast.makeText(this, "Make another bin first", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).name + "  (" + others.get(i).code + ")";
        Ui.dialog(this)
                .setTitle("Move \"" + it.name + "\" to…")
                .setItems(names, (d, which) -> {
                    db.moveItem(it.id, others.get(which).id);
                    Toast.makeText(this, "Moved to " + others.get(which).name, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    // ---------- bin actions ----------

    private void renameBin() {
        Ui.prompt(this, "Rename bin", bin.name, "Bin name", "Save", n -> {
            db.renameBin(binId, n);
            refresh();
        });
    }

    private void deleteBin() {
        Ui.confirm(this, "Delete \"" + bin.name + "\"?",
                "This deletes the bin and its " + bin.itemCount + " item(s) and photos. The printed label " + bin.code + " will stop working.",
                "Delete", () -> {
                    for (String photo : db.deleteBin(binId)) Photos.delete(this, photo);
                    finish();
                });
    }

    /** Saves the label as a PNG and opens the share sheet (print app, email, Drive, etc.). */
    private void shareLabel() {
        try {
            Bitmap bmp = Code128.render(bin.code, bin.name);
            File dir = new File(getFilesDir(), "labels");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, bin.code + ".png");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProviderLite.uriFor("labels", f.getName());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "ILS label: " + bin.name);
            send.setClipData(ClipData.newRawUri("label", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share label"));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't create the label: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- scanning while a bin is open ----------

    @Override
    protected void onScan(String data) {
        Db.Bin scanned = db.binByCode(data);
        if (scanned == null) {
            Toast.makeText(this, "\"" + data + "\" isn't an ILS bin label", Toast.LENGTH_SHORT).show();
        } else if (scanned.id == binId) {
            Toast.makeText(this, "That's this bin", Toast.LENGTH_SHORT).show();
        } else {
            open(this, scanned.id);
            finish();
        }
    }

    // ---------- menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Share / print label");
        menu.add(0, 2, 0, "Rename bin");
        menu.add(0, 3, 0, "Delete bin");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case 1:
                shareLabel();
                return true;
            case 2:
                renameBin();
                return true;
            case 3:
                deleteBin();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class ItemsAdapter extends BaseAdapter {
        private List<Db.Item> items = new ArrayList<>();

        void set(List<Db.Item> l) {
            items = l;
            notifyDataSetChanged();
        }

        Db.Item get(int i) {
            return items.get(i);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return items.get(i).id;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Db.Item it = items.get(i);
            return Ui.itemRow(BinActivity.this, it,
                    it.photo != null ? "Tap to see photo" : "No photo · tap to take one");
        }
    }
}
