package fr.manubotelho.mestaches;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class TaskStore extends SQLiteOpenHelper {
    static final String NAME = "mes_taches.db";
    static final class Task {
        final long id, dueAt;
        final String title;
        final boolean appointment, done;
        Task(long id, String title, long dueAt, boolean appointment, boolean done) {
            this.id=id; this.title=title; this.dueAt=dueAt;
            this.appointment=appointment; this.done=done;
        }
    }

    TaskStore(Context context) { super(context, NAME, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX tasks_due ON tasks(done, due_at, id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Une migration est nécessaire.");
    }
    private Task read(Cursor c) {
        return new Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)==1, c.getInt(4)==1);
    }
    List<Task> list(boolean done) {
        ArrayList<Task> result = new ArrayList<>();
        try (Cursor c=getReadableDatabase().query("tasks",
                new String[]{"id","title","due_at","appointment","done"},
                "done=?", new String[]{done?"1":"0"}, null, null,
                done?"updated_at DESC, id DESC":"due_at ASC, id ASC")) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }
    Task get(long id) {
        try (Cursor c=getReadableDatabase().query("tasks",
                new String[]{"id","title","due_at","appointment","done"},
                "id=?", new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst()?read(c):null;
        }
    }
    long save(long id, String title, long dueAt, boolean appointment) {
        title=title.trim();
        if (title.isEmpty() || title.length()>240) throw new IllegalArgumentException("Indique un texte de 1 à 240 caractères.");
        ContentValues values=new ContentValues();
        values.put("title",title); values.put("due_at",dueAt);
        values.put("appointment",appointment?1:0);
        values.put("updated_at",System.currentTimeMillis());
        if (id==0) return getWritableDatabase().insertOrThrow("tasks",null,values);
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        return id;
    }
    void setDone(long id, boolean done) {
        ContentValues values=new ContentValues();
        values.put("done",done?1:0); values.put("updated_at",System.currentTimeMillis());
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
    }
    void delete(long id) {
        getWritableDatabase().delete("tasks","id=?",new String[]{Long.toString(id)});
    }
}
