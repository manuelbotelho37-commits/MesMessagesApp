package fr.manubotelho.mestaches;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TaskBackupManager {
    private static final String PREFS = "insisto_backup";
    private static final String KEY_URI = "backup_uri";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TaskBackupManager() {}

    static void setBackupUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_URI, uri == null ? null : uri.toString()).apply();
    }

    static Uri getBackupUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_URI, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return Uri.parse(raw); }
        catch (RuntimeException ex) { return null; }
    }

    static boolean isConfigured(Context context) {
        return getBackupUri(context) != null;
    }

    static void scheduleBackup(Context context) {
        Uri uri = getBackupUri(context);
        if (uri == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context safeContext = app;
        EXECUTOR.execute(() -> backupNow(safeContext, uri));
    }

    static boolean backupNow(Context context) {
        Uri uri = getBackupUri(context);
        return uri != null && backupNow(context, uri);
    }

    static boolean backupNow(Context context, Uri uri) {
        File database = context.getDatabasePath(TaskStore.NAME);
        if (!database.exists() || database.length() <= 0) return false;

        try (InputStream in = new BufferedInputStream(new FileInputStream(database));
             OutputStream rawOut = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (rawOut == null) return false;
            try (OutputStream out = new BufferedOutputStream(rawOut)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) out.write(buffer, 0, read);
                }
                out.flush();
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean restoreFrom(Context context, Uri uri) {
        File target = context.getDatabasePath(TaskStore.NAME);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        File temp = new File(context.getCacheDir(), "insisto-restore-" + System.currentTimeMillis() + ".db");
        try {
            try (InputStream rawIn = context.getContentResolver().openInputStream(uri)) {
                if (rawIn == null) return false;
                try (InputStream in = new BufferedInputStream(rawIn);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read > 0) out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            }

            if (!isValidInsistoDatabase(temp)) return false;

            deleteSidecar(target, "-wal");
            deleteSidecar(target, "-shm");
            deleteSidecar(target, "-journal");

            try (InputStream in = new BufferedInputStream(new FileInputStream(temp));
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(target, false))) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) out.write(buffer, 0, read);
                }
                out.flush();
            }
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static boolean isValidInsistoDatabase(File file) {
        if (!file.exists() || file.length() < 100) return false;
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='tasks'",
                    null);
            return c.moveToFirst();
        } catch (Exception ex) {
            return false;
        } finally {
            if (c != null) c.close();
            if (db != null) db.close();
        }
    }

    private static void deleteSidecar(File target, String suffix) {
        File sidecar = new File(target.getAbsolutePath() + suffix);
        if (sidecar.exists()) {
            //noinspection ResultOfMethodCallIgnored
            sidecar.delete();
        }
    }
}
