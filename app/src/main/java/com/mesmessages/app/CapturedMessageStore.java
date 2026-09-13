package com.mesmessages.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class CapturedMessageStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "captured_messages.db";
    private static final int DB_VERSION = 1;

    public static final int DIRECTION_RECEIVED = 1;
    public static final int DIRECTION_SENT = 2;

    public static final class Item {
        public final String conversation;
        public final String sender;
        public final String body;
        public final long date;
        public final int direction;

        Item(String conversation, String sender, String body, long date, int direction) {
            this.conversation = conversation == null ? "" : conversation;
            this.sender = sender == null ? "" : sender;
            this.body = body == null ? "" : body;
            this.date = date;
            this.direction = direction;
        }
    }

    public CapturedMessageStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "conversation TEXT," +
                "sender TEXT," +
                "body TEXT NOT NULL," +
                "date INTEGER NOT NULL," +
                "direction INTEGER NOT NULL," +
                "fingerprint TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE INDEX idx_messages_date ON messages(date DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    public void save(String conversation, String sender, String body, long date, int direction) {
        if (body == null || body.trim().isEmpty()) return;
        if (date <= 0) date = System.currentTimeMillis();
        String normalizedBody = body.trim();
        String fingerprint = (conversation == null ? "" : conversation) + "|" +
                (sender == null ? "" : sender) + "|" + normalizedBody + "|" + date + "|" + direction;

        ContentValues values = new ContentValues();
        values.put("conversation", conversation == null ? "" : conversation);
        values.put("sender", sender == null ? "" : sender);
        values.put("body", normalizedBody);
        values.put("date", date);
        values.put("direction", direction);
        values.put("fingerprint", fingerprint);
        getWritableDatabase().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Item> getAll() {
        ArrayList<Item> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                "messages",
                new String[]{"conversation", "sender", "body", "date", "direction"},
                null, null, null, null,
                "date DESC");
        try {
            while (cursor.moveToNext()) {
                result.add(new Item(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }
}
