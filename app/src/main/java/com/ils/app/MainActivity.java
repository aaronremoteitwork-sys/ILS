package com.ils.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Home screen with two tabs: Bins (create and browse) and Search (scan a bin label). */
public class MainActivity extends ScanActivity {

    private static final int TAB_BINS = 0;
    private static final int TAB_SEARCH = 1;

    private static final int MENU_SCANNER = 1;
    private static final int MENU_ABOUT = 2;
    private static final int MENU_UPDATE = 3;
    private static final int MENU_THEME_LIGHT = 11;
    private static final int MENU_THEME_DARK = 12;
    private static final int MENU_THEME_AUTO = 13;
    private static final int THEME_GROUP = 1;

    private Db db;
    private int tab = TAB_BINS;
    private String lastQuery;
    private boolean lastFromScan;

    private View binsPage, searchPage;
    private TextView tabBinsView, tabSearchView;
    private BinsAdapter binsAdapter;
    private TextView binsEmpty;
    private EditText searchField;
    private LinearLayout results;
    private ScrollView searchScroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Db.get(this);
        if (state != null) {
            tab = state.getInt("tab", TAB_BINS);
            lastQuery = state.getString("q");
            lastFromScan = state.getBoolean("scan");
        }
        setContentView(buildRoot());
        DataWedge.configureOnce(this);
        showTab(tab);
        Updater.checkOnLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.showWhatsNew(this);
        Updater.resumePendingInstall(this);
        refreshBins();
        if (lastQuery != null) runSearch(lastQuery, lastFromScan);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("q", lastQuery);
        out.putBoolean("scan", lastFromScan);
    }

    @Override
    protected void onScan(String data) {
        showTab(TAB_SEARCH);
        searchField.setText(data);
        runSearch(data, true);
    }

    // ---------- layout ----------

    private View buildRoot() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);

        FrameLayout pages = new FrameLayout(this);
        binsPage = buildBinsPage();
        searchPage = buildSearchPage();
        pages.addView(binsPage);
        pages.addView(searchPage);
        root.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Ui.CARD);
        tabBinsView = tabButton("Bins");
        tabSearchView = tabButton("Search");
        tabBinsView.setOnClickListener(v -> showTab(TAB_BINS));
        tabSearchView.setOnClickListener(v -> showTab(TAB_SEARCH));
        bar.addView(tabBinsView, new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1));
        bar.addView(tabSearchView, new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1));
        root.addView(bar);
        return root;
    }

    private TextView tabButton(String label) {
        TextView t = Ui.text(this, label, 16, Ui.MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.pressable(this, Ui.CARD, 0));
        return t;
    }

    private View buildBinsPage() {
        LinearLayout page = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        page.setPadding(p, p, p, 0);

        Button add = Ui.button(this, "+  New bin", true);
        add.setOnClickListener(v -> newBin());
        page.addView(add, Ui.matchWrap(this, 0));

        FrameLayout listBox = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setCacheColorHint(Ui.BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.dp(this, 10));
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 16));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        binsAdapter = new BinsAdapter();
        list.setAdapter(binsAdapter);
        list.setOnItemClickListener((parent, view, pos, id) -> BinActivity.open(this, binsAdapter.get(pos).id));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            binOptions(binsAdapter.get(pos));
            return true;
        });
        listBox.addView(list);

        binsEmpty = Ui.text(this, "No bins yet.\nTap \"New bin\" to make your first one.", 16, Ui.MUTED, false);
        binsEmpty.setGravity(Gravity.CENTER);
        listBox.addView(binsEmpty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(listBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private View buildSearchPage() {
        searchScroll = new ScrollView(this);
        searchScroll.setFillViewport(true);
        searchScroll.setBackgroundColor(Ui.BG);
        LinearLayout page = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        page.setPadding(p, p, p, p);

        LinearLayout prompt = Ui.card(this);
        prompt.addView(Ui.text(this, "Scan a bin label", 20, Ui.TEXT, true));
        TextView hint = Ui.text(this, "Press the yellow scan button on the side of the TC52 and aim at a bin's barcode.", 14, Ui.MUTED, false);
        hint.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        prompt.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        searchField = new EditText(this);
        searchField.setHint("Or type a code or item name");
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        // Also handles a scanner that "types" the code followed by Enter.
        Ui.styleField(searchField);
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) submitSearch();
                return true;
            }
            return false;
        });
        row.addView(searchField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button go = Ui.button(this, "Go", true);
        go.setOnClickListener(v -> submitSearch());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = Ui.dp(this, 8);
        row.addView(go, glp);
        prompt.addView(row);
        page.addView(prompt);

        results = Ui.vertical(this);
        page.addView(results, Ui.matchWrap(this, 16));
        showSearchIdle();

        searchScroll.addView(page);
        return searchScroll;
    }

    private void showTab(int t) {
        tab = t;
        binsPage.setVisibility(t == TAB_BINS ? View.VISIBLE : View.GONE);
        searchPage.setVisibility(t == TAB_SEARCH ? View.VISIBLE : View.GONE);
        styleTab(tabBinsView, t == TAB_BINS);
        styleTab(tabSearchView, t == TAB_SEARCH);
        if (t == TAB_BINS) hideKeyboard();
    }

    private void styleTab(TextView v, boolean selected) {
        v.setTextColor(selected ? Ui.ACCENT : Ui.MUTED);
        if (selected) {
            android.graphics.drawable.GradientDrawable bar = new android.graphics.drawable.GradientDrawable();
            bar.setColor(Ui.CARD);
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{bar, new ColorDrawable(Ui.ACCENT)});
            ld.setLayerHeight(1, Ui.dp(this, 4));
            ld.setLayerGravity(1, Gravity.TOP);
            v.setBackground(ld);
        } else {
            v.setBackground(Ui.pressable(this, Ui.CARD, 0));
        }
    }

    // ---------- bins ----------

    private void refreshBins() {
        binsAdapter.set(db.bins());
        binsEmpty.setVisibility(binsAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void newBin() {
        Ui.prompt(this, "New bin", null, "Bin name (e.g. Garage shelf 2)", "Create", name -> {
            long id = db.createBin(name);
            refreshBins();
            BinActivity.open(this, id);
        });
    }

    private void binOptions(Db.Bin b) {
        Ui.dialog(this)
                .setTitle(b.name)
                .setItems(new String[]{"Open", "Rename", "Delete"}, (d, which) -> {
                    if (which == 0) BinActivity.open(this, b.id);
                    else if (which == 1) Ui.prompt(this, "Rename bin", b.name, "Bin name", "Save", n -> {
                        db.renameBin(b.id, n);
                        refreshBins();
                    });
                    else Ui.confirm(this, "Delete \"" + b.name + "\"?",
                                "This deletes the bin and its " + b.itemCount + " item(s) and photos. Printed labels for " + b.code + " will stop working.",
                                "Delete", () -> {
                                    for (String photo : db.deleteBin(b.id)) Photos.delete(this, photo);
                                    refreshBins();
                                });
                })
                .show();
    }

    private class BinsAdapter extends BaseAdapter {
        private List<Db.Bin> bins = new ArrayList<>();

        void set(List<Db.Bin> b) {
            bins = b;
            notifyDataSetChanged();
        }

        Db.Bin get(int pos) {
            return bins.get(pos);
        }

        @Override
        public int getCount() {
            return bins.size();
        }

        @Override
        public Object getItem(int pos) {
            return bins.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return bins.get(pos).id;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            return Ui.binRow(MainActivity.this, bins.get(pos));
        }
    }

    // ---------- search ----------

    private void submitSearch() {
        String q = searchField.getText().toString().trim();
        if (q.isEmpty()) return;
        hideKeyboard();
        runSearch(q, false);
    }

    private void showSearchIdle() {
        results.removeAllViews();
        TextView t = Ui.text(this, "Results show up here.", 15, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 24), 0, 0);
        results.addView(t);
    }

    private void runSearch(String raw, boolean fromScan) {
        String q = raw.trim();
        if (q.isEmpty()) return;
        lastQuery = q;
        lastFromScan = fromScan;
        results.removeAllViews();

        Db.Bin bin = db.binByCode(q);
        if (bin != null) {
            showBinResult(bin);
        } else {
            List<Db.Bin> bins = db.searchBins(q);
            List<Db.Item> items = db.searchItems(q);
            if (bins.isEmpty() && items.isEmpty()) {
                LinearLayout card = Ui.card(this);
                card.addView(Ui.text(this, fromScan ? "Unknown barcode" : "No matches", 18, Ui.TEXT, true));
                TextView msg = Ui.text(this, fromScan
                        ? "\"" + q + "\" isn't an ILS bin label."
                        : "No bin or item matches \"" + q + "\".", 15, Ui.MUTED, false);
                msg.setPadding(0, Ui.dp(this, 4), 0, 0);
                card.addView(msg);
                results.addView(card);
            } else {
                if (!bins.isEmpty()) {
                    results.addView(section("Bins"));
                    for (Db.Bin b : bins) {
                        View row = Ui.binRow(this, b);
                        row.setOnClickListener(v -> BinActivity.open(this, b.id));
                        results.addView(row, Ui.matchWrap(this, 8));
                    }
                }
                if (!items.isEmpty()) {
                    results.addView(section("Items"));
                    for (Db.Item it : items) {
                        View row = Ui.itemRow(this, it, "In " + it.binName + "  ·  " + it.binCode);
                        row.setOnClickListener(v -> BinActivity.open(this, it.binId));
                        results.addView(row, Ui.matchWrap(this, 8));
                    }
                }
            }
        }
        searchScroll.post(() -> searchScroll.scrollTo(0, 0));
    }

    private void showBinResult(Db.Bin bin) {
        LinearLayout card = Ui.card(this);
        android.graphics.drawable.GradientDrawable face = Ui.rounded(this, Ui.CARD, 12);
        face.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        card.setBackground(face);
        TextView code = Ui.text(this, bin.code, 13, Ui.ACCENT, true);
        code.setLetterSpacing(0.1f);
        card.addView(code);
        TextView name = Ui.text(this, bin.name, 26, Ui.TEXT, true);
        name.setPadding(0, Ui.dp(this, 2), 0, 0);
        card.addView(name);
        TextView count = Ui.text(this, bin.itemCount == 0 ? "Empty"
                : bin.itemCount + (bin.itemCount == 1 ? " item inside" : " items inside"), 15, Ui.MUTED, false);
        count.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 12));
        card.addView(count);
        Button open = Ui.button(this, "Open bin to edit", true);
        open.setOnClickListener(v -> BinActivity.open(this, bin.id));
        card.addView(open);
        results.addView(card);

        List<Db.Item> items = db.items(bin.id);
        if (items.isEmpty()) {
            TextView t = Ui.text(this, "Nothing has been added to this bin yet.", 15, Ui.MUTED, false);
            t.setPadding(0, Ui.dp(this, 16), 0, 0);
            results.addView(t);
            return;
        }
        results.addView(section("What's inside"));
        for (Db.Item it : items) {
            View row = Ui.itemRow(this, it, it.photo != null ? "Tap to see photo" : null);
            if (it.photo != null) row.setOnClickListener(v -> Ui.showPhoto(this, it.photo, it.name));
            results.addView(row, Ui.matchWrap(this, 8));
        }
    }

    private TextView section(String label) {
        TextView t = Ui.text(this, label.toUpperCase(), 13, Ui.MUTED, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 2));
        return t;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && searchField != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
    }

    // ---------- menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu theme = menu.addSubMenu(0, 10, 0, "Theme");
        theme.add(THEME_GROUP, MENU_THEME_LIGHT, 0, "Light").setCheckable(true);
        theme.add(THEME_GROUP, MENU_THEME_DARK, 0, "Dark").setCheckable(true);
        theme.add(THEME_GROUP, MENU_THEME_AUTO, 0, "Auto").setCheckable(true);
        theme.setGroupCheckable(THEME_GROUP, true, true);
        menu.add(0, MENU_UPDATE, 0, "Check for updates");
        menu.add(0, MENU_SCANNER, 0, "Set up scanner again");
        menu.add(0, MENU_ABOUT, 0, "About");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        String mode = Ui.themeMode(this);
        MenuItem light = menu.findItem(MENU_THEME_LIGHT);
        MenuItem dark = menu.findItem(MENU_THEME_DARK);
        MenuItem auto = menu.findItem(MENU_THEME_AUTO);
        if (light != null) light.setChecked(Ui.THEME_LIGHT.equals(mode));
        if (dark != null) dark.setChecked(Ui.THEME_DARK.equals(mode));
        if (auto != null) auto.setChecked(Ui.THEME_AUTO.equals(mode));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_THEME_LIGHT || id == MENU_THEME_DARK || id == MENU_THEME_AUTO) {
            String mode = id == MENU_THEME_LIGHT ? Ui.THEME_LIGHT
                    : id == MENU_THEME_DARK ? Ui.THEME_DARK : Ui.THEME_AUTO;
            if (!mode.equals(Ui.themeMode(this))) {
                Ui.saveTheme(this, mode);
                recreate();
            }
            return true;
        }
        if (id == MENU_SCANNER) {
            DataWedge.configure(this);
            Toast.makeText(this, "Scanner profile \"ILS\" sent to DataWedge", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == MENU_UPDATE) {
            Updater.checkNow(this);
            return true;
        }
        if (id == MENU_ABOUT) {
            Ui.dialog(this)
                    .setTitle("ILS " + Updater.installedName(this))
                    .setMessage("Inventory of bins and what's inside them.\n\nMake a bin, add items with photos, print its label, then scan the label from the Search tab.\n\nTheme (Light, Dark, or Auto) is in this menu. Auto follows the device's night mode.\n\nILS checks for updates each time it opens. Updates keep all your bins and photos.")
                    .setPositiveButton("OK", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
