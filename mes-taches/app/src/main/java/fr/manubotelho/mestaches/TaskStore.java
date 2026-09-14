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
    private final Context appContext;

    static final class Task {
        final long id, dueAt;
        final String title;
        final boolean appointment, done;
        Task(long id, String title, long dueAt, boolean appointment, boolean done) {
            this.id=id; this.title=title; this.dueAt=dueAt;
            this.appointment=appointment; this.done=done;
        }
    }

    TaskStore(Context context) {
        super(context, NAME, null, 1);
        Context application=context.getApplicationContext();
        appContext=application==null?context:application;
    }
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
        long savedId;
        if (id==0) {
            savedId=getWritableDatabase().insertOrThrow("tasks",null,values);
        } else {
            if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Cette tâche n’existe plus.");
            savedId=id;
        }
        syncReminder(savedId);
        return savedId;
    }
    void setDone(long id, boolean done) {
        ContentValues values=new ContentValues();
        values.put("done",done?1:0); values.put("updated_at",System.currentTimeMillis());
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        syncReminder(id);
    }
    void delete(long id) {
        try { ReminderScheduler.cancel(appContext,id); } catch (RuntimeException ignored) {}
        getWritableDatabase().delete("tasks","id=?",new String[]{Long.toString(id)});
    }
    private void syncReminder(long id) {
        try { ReminderScheduler.sync(appContext,get(id)); }
        catch (RuntimeException ignored) {
            // La tâche reste enregistrée même si Android refuse temporairement un rappel.
        }
    }
}
