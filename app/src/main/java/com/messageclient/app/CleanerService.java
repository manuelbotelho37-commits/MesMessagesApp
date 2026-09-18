package com.messageclient.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CleanerService extends Service {
    private static final String CHANNEL_ID = "capture_cleaner";
    private static final int NOTIFICATION_ID = 7412;
    private static final long TEN_MINUTES = 10L * 60L * 1000L;
    private static final long SCAN_INTERVAL = 30L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable scanner = new Runnable() {
        @Override
        public void run() {
            try {
                cleanExpiredScreenshots();
            } finally {
                handler.postDelayed(this, SCAN_INTERVAL);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(CleanerActivity.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(CleanerActivity.KEY_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        handler.removeCallbacks(scanner);
        handler.post(scanner);
        return START_STICKY;
    }

    private void cleanExpiredScreenshots() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return;

        SharedPreferences prefs = getSharedPreferences(CleanerActivity.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(CleanerActivity.KEY_ENABLED, false)) return;

        long enabledSince = prefs.getLong(CleanerActivity.KEY_ENABLED_SINCE, Long.MAX_VALUE);
        long cutoff = System.currentTimeMillis() - TEN_MINUTES;

        for (File dir : screenshotDirectories()) {
            cleanDirectory(dir, enabledSince, cutoff);
        }
    }

    private List<File> screenshotDirectories() {
        File root = Environment.getExternalStorageDirectory();
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(root, "DCIM/Screenshots"));
        dirs.add(new File(root, "DCIM/ScreenShots"));
        dirs.add(new File(root, "Pictures/Screenshots"));
        dirs.add(new File(root, "Pictures/ScreenShots"));
        dirs.add(new File(root, "Screenshots"));
        dirs.add(new File(root, "Pictures/Screenshot"));
        return dirs;
    }

    private void cleanDirectory(File dir, long enabledSince, long cutoff) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                cleanDirectory(file, enabledSince, cutoff);
                continue;
            }

            long modified = file.lastModified();
            if (modified <= 0) continue;
            if (modified < enabledSince) continue;
            if (modified > cutoff) continue;

            String path = file.getAbsolutePath();
            if (file.delete()) {
                MediaScannerConnection.scanFile(
                        getApplicationContext(),
                        new String[]{path},
                        null,
                        null);
            }
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, CleanerActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Nettoyeur captures actif")
                .setContentText("Suppression automatique après 10 minutes")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nettoyeur captures",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Maintient le nettoyage automatique des captures d’écran actif.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(scanner);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
