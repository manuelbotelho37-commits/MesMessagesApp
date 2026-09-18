package com.messageclient.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AutoBackupManager {
    private static final String PREFS = "message_client_auto_backup";
    private static final String KEY_URI = "backup_uri";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AutoBackupManager() {}

    public static void setBackupUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_URI, uri.toString()).apply();
    }

    public static boolean isConfigured(Context context) {
        return getBackupUri(context) != null;
    }

    public static Uri getBackupUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_URI, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Uri.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_URI).apply();
    }

    public static void writeAsync(Context context, List<MessageStore.MessageTemplate> templates) {
        Uri uri = getBackupUri(context);
        if (uri == null) return;

        Context appContext = context.getApplicationContext();
        List<MessageStore.MessageTemplate> snapshot = new ArrayList<>(templates);
        EXECUTOR.execute(() -> writeNow(appContext, uri, snapshot));
    }

    public static boolean writeNow(Context context, Uri uri,
                                   List<MessageStore.MessageTemplate> templates) {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) return false;
            String data = MessageStore.createBackup(templates);
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
