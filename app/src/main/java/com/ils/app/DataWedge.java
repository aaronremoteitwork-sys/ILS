package com.ils.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Sets up a DataWedge profile named "ILS" on Zebra devices so the hardware scan trigger
 * sends barcodes to this app as a broadcast intent instead of typing them as keystrokes.
 * On non-Zebra devices these broadcasts are simply ignored.
 */
final class DataWedge {

    static final String SCAN_ACTION = "com.ils.app.SCAN";
    static final String EXTRA_DATA = "com.symbol.datawedge.data_string";

    private static final String DW_ACTION = "com.symbol.datawedge.api.ACTION";
    private static final String PROFILE = "ILS";
    private static final int CONFIG_VERSION = 1;

    private DataWedge() {}

    /** Configures the profile the first time the app runs (and after config changes in new versions). */
    static void configureOnce(Context c) {
        SharedPreferences p = c.getSharedPreferences("ils", Context.MODE_PRIVATE);
        if (p.getInt("dw_config", 0) >= CONFIG_VERSION) return;
        configure(c);
        p.edit().putInt("dw_config", CONFIG_VERSION).apply();
    }

    static void configure(Context c) {
        // 1. Make sure the profile exists.
        send(c, "com.symbol.datawedge.api.CREATE_PROFILE", PROFILE);

        // 2. Tie it to this app and turn on intent output.
        Bundle app = new Bundle();
        app.putString("PACKAGE_NAME", c.getPackageName());
        app.putStringArray("ACTIVITY_LIST", new String[]{"*"});

        Bundle intentParams = new Bundle();
        intentParams.putString("intent_output_enabled", "true");
        intentParams.putString("intent_action", SCAN_ACTION);
        intentParams.putString("intent_category", "");
        intentParams.putString("intent_delivery", "2"); // 2 = broadcast

        Bundle cfg = profileBundle("UPDATE");
        cfg.putParcelableArray("APP_LIST", new Bundle[]{app});
        cfg.putBundle("PLUGIN_CONFIG", plugin("INTENT", intentParams));
        sendConfig(c, cfg);

        // 3. Turn off keystroke output so scans don't also get typed into text fields.
        Bundle keyParams = new Bundle();
        keyParams.putString("keystroke_output_enabled", "false");
        Bundle cfg2 = profileBundle("UPDATE");
        cfg2.putBundle("PLUGIN_CONFIG", plugin("KEYSTROKE", keyParams));
        sendConfig(c, cfg2);

        // 4. Make sure the scanner itself is enabled.
        Bundle barcodeParams = new Bundle();
        barcodeParams.putString("scanner_selection", "auto");
        barcodeParams.putString("scanner_input_enabled", "true");
        Bundle cfg3 = profileBundle("UPDATE");
        cfg3.putBundle("PLUGIN_CONFIG", plugin("BARCODE", barcodeParams));
        sendConfig(c, cfg3);
    }

    private static Bundle profileBundle(String mode) {
        Bundle b = new Bundle();
        b.putString("PROFILE_NAME", PROFILE);
        b.putString("PROFILE_ENABLED", "true");
        b.putString("CONFIG_MODE", mode);
        return b;
    }

    private static Bundle plugin(String name, Bundle params) {
        Bundle b = new Bundle();
        b.putString("PLUGIN_NAME", name);
        b.putString("RESET_CONFIG", "false");
        b.putBundle("PARAM_LIST", params);
        return b;
    }

    private static void sendConfig(Context c, Bundle cfg) {
        Intent i = new Intent(DW_ACTION);
        i.putExtra("com.symbol.datawedge.api.SET_CONFIG", cfg);
        c.sendBroadcast(i);
    }

    private static void send(Context c, String extra, String value) {
        Intent i = new Intent(DW_ACTION);
        i.putExtra(extra, value);
        c.sendBroadcast(i);
    }
}
