package com.messageclient.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;

public class CleanerBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences(CleanerActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(CleanerActivity.KEY_ENABLED, false);
        if (!enabled) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return;

        Intent service = new Intent(context, CleanerService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }
}
