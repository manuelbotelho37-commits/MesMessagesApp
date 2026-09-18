package com.messageclient.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class CleanerActivity extends Activity {
    static final String PREFS = "capture_cleaner";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_ENABLED_SINCE = "enabled_since";

    private Switch enabledSwitch;
    private TextView status;
    private boolean updating;
    private boolean pendingEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingEnable && hasFileAccess()) {
            pendingEnable = false;
            enableCleaner();
        }
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(15, 16, 18));

        TextView title = text("Nettoyeur captures", 30, Color.WHITE, true);
        root.addView(title);

        TextView subtitle = text(
                "Tes captures d’écran sont supprimées automatiquement 10 minutes après leur création.",
                17, Color.rgb(185, 188, 196), false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(8);
        root.addView(subtitle, subtitleLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackgroundColor(Color.rgb(31, 33, 37));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(28);
        root.addView(card, cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("Nettoyage automatique", 20, Color.WHITE, true);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        enabledSwitch = new Switch(this);
        row.addView(enabledSwitch);
        card.addView(row);

        status = text("", 16, Color.rgb(120, 220, 145), false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(8);
        card.addView(status, statusLp);

        TextView delay = text("Délai : 10 minutes", 17, Color.WHITE, true);
        LinearLayout.LayoutParams delayLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        delayLp.topMargin = dp(22);
        card.addView(delay, delayLp);

        TextView note = text(
                "Seules les nouvelles captures créées après l’activation seront supprimées. Tes photos normales ne sont jamais touchées.",
                15, Color.rgb(185, 188, 196), false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(8);
        card.addView(note, noteLp);

        Button permission = new Button(this);
        permission.setText("Autoriser l’accès aux captures");
        permission.setTextSize(16);
        permission.setTextColor(Color.WHITE);
        permission.setBackgroundColor(Color.rgb(255, 126, 31));
        permission.setOnClickListener(v -> requestFileAccess());
        LinearLayout.LayoutParams permissionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        permissionLp.topMargin = dp(24);
        root.addView(permission, permissionLp);

        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = text(
                "Une notification discrète reste active pendant le nettoyage pour qu’Android laisse fonctionner le service.",
                13, Color.rgb(145, 148, 155), false);
        root.addView(footer);

        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updating) return;
            if (isChecked) {
                if (!hasFileAccess()) {
                    pendingEnable = true;
                    updating = true;
                    enabledSwitch.setChecked(false);
                    updating = false;
                    requestFileAccess();
                } else {
                    enableCleaner();
                }
            } else {
                disableCleaner();
            }
        });

        setContentView(root);
    }

    private void enableCleaner() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_ENABLED_SINCE, System.currentTimeMillis())
                .apply();

        Intent service = new Intent(this, CleanerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        Toast.makeText(this, "Nettoyage automatique activé", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void disableCleaner() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply();
        stopService(new Intent(this, CleanerService.class));
        refresh();
    }

    private void refresh() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
        updating = true;
        enabledSwitch.setChecked(enabled);
        updating = false;

        if (enabled && hasFileAccess()) {
            status.setText("Activé ✓");
            status.setTextColor(Color.rgb(120, 220, 145));
        } else if (enabled) {
            status.setText("Autorisation fichiers nécessaire");
            status.setTextColor(Color.rgb(255, 190, 100));
        } else {
            status.setText("Désactivé");
            status.setTextColor(Color.rgb(175, 178, 185));
        }
    }

    private boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 500);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
