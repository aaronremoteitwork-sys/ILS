package com.ils.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

/** Base screen that receives barcodes from the TC52 scanner (via DataWedge) while visible. */
public abstract class ScanActivity extends Activity {

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(DataWedge.EXTRA_DATA);
            if (data != null && !data.trim().isEmpty()) onScan(data.trim());
        }
    };

    private boolean registered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyTheme(this);
        super.onCreate(savedInstanceState);
        Ui.paintSystemBars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.paintSystemBars(this);
        IntentFilter f = new IntentFilter(DataWedge.SCAN_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
        registered = true;
    }

    @Override
    protected void onPause() {
        if (registered) {
            unregisterReceiver(receiver);
            registered = false;
        }
        super.onPause();
    }

    /** Called on the main thread with the scanned barcode text. */
    protected abstract void onScan(String data);
}
