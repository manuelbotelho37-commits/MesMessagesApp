package fr.manubotelho.mestaches;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TaskBackupManager {
    private static final String PREFS = "insisto_backup";
    private static final String KEY_URI = "backup_json_uri";
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
        TaskStore store = null;
        try {
            store = new TaskStore(context);
            JSONObject root = new JSONObject();
            root.put("format", "insisto-backup");
            root.put("version", 1);
            root.put("exportedAt", System.currentTimeMillis());

            JSONArray tasks = new JSONArray();
            List<TaskStore.Task> all = new ArrayList<>();
            all.addAll(store.list(false));
            all.addAll(store.list(true));

            for (TaskStore.Task task : all) {
                JSONObject item = new JSONObject();
                item.put("id", task.id);
                item.put("title", task.title);
                item.put("dueAt", task.dueAt);
                item.put("appointment", task.appointment);
                item.put("done", task.done);

                JSONArray attachments = new JSONArray();
                for (TaskStore.Attachment attachment : store.listAttachments(task.id)) {
                    JSONObject a = new JSONObject();
                    a.put("id", attachment.id);
                    a.put("kind", attachment.kind);
                    a.put("label", attachment.label);
                    a.put("value", attachment.value);
                    if (attachment.mimeType == null) a.put("mimeType", JSONObject.NULL);
                    else a.put("mimeType", attachment.mimeType);
                    attachments.put(a);
                }
                item.put("attachments", attachments);
                tasks.put(item);
            }
            root.put("tasks", tasks);

            try (OutputStream rawOut = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (rawOut == null) return false;
                try (OutputStreamWriter out = new OutputStreamWriter(
                        new BufferedOutputStream(rawOut), StandardCharsets.UTF_8)) {
                    out.write(root.toString(2));
                    out.flush();
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            if (store != null) store.close();
        }
    }

    static boolean restoreFrom(Context context, Uri uri) {
        File target = context.getDatabasePath(TaskStore.NAME);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        File temp = new File(context.getCacheDir(), "insisto-restore-" + System.currentTimeMillis() + ".backup");
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

            if (isValidJsonBackup(temp)) return restoreJson(context, temp);
            if (isValidInsistoDatabase(temp)) return restoreLegacyDatabase(context, temp, target);
            return false;
        } catch (Exception ex) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static boolean isValidJsonBackup(File file) {
        try {
            JSONObject root = readJson(file);
            return "insisto-backup".equals(root.optString("format"))
                    && root.optInt("version", 0) >= 1
                    && root.has("tasks");
        } catch (Exception ex) {
            return false;
        }
    }

    private static JSONObject readJson(File file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (read > 0) text.append(buffer, 0, read);
            }
        }
        return new JSONObject(text.toString());
    }

    private static boolean restoreJson(Context context, File file) {
        TaskStore store = null;
        SQLiteDatabase db = null;
        try {
            JSONObject root = readJson(file);
            if (!"insisto-backup".equals(root.optString("format"))) return false;
            JSONArray tasks = root.getJSONArray("tasks");

            store = new TaskStore(context);
            db = store.getWritableDatabase();
            db.beginTransaction();
            db.delete("attachments", null, null);
            db.delete("tasks", null, null);

            long now = System.currentTimeMillis();
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject item = tasks.getJSONObject(i);
                long taskId = item.getLong("id");

                ContentValues taskValues = new ContentValues();
                taskValues.put("id", taskId);
                taskValues.put("title", item.getString("title"));
                taskValues.put("due_at", item.getLong("dueAt"));
                taskValues.put("appointment", item.optBoolean("appointment", false) ? 1 : 0);
                taskValues.put("done", item.optBoolean("done", false) ? 1 : 0);
                taskValues.put("updated_at", now);
                if (db.insertOrThrow("tasks", null, taskValues) < 0) return false;

                JSONArray attachments = item.optJSONArray("attachments");
                if (attachments == null) continue;
                for (int j = 0; j < attachments.length(); j++) {
                    JSONObject a = attachments.getJSONObject(j);
                    ContentValues values = new ContentValues();
                    if (a.has("id")) values.put("id", a.getLong("id"));
                    values.put("task_id", taskId);
                    values.put("kind", a.optString("kind", "note"));
                    values.put("label", a.optString("label", "Élément"));
                    values.put("value", a.optString("value", ""));
                    if (a.isNull("mimeType")) values.putNull("mime_type");
                    else values.put("mime_type", a.optString("mimeType", null));
                    values.put("created_at", now);
                    db.insertOrThrow("attachments", null, values);
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
            if (store != null) store.close();
        }
    }

    private static boolean restoreLegacyDatabase(Context context, File temp, File target) {
        try {
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
