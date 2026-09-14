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

    static final class Attachment {
        final long id, taskId;
        final String kind, label, value, mimeType;
        Attachment(long id,long taskId,String kind,String label,String value,String mimeType) {
            this.id=id; this.taskId=taskId; this.kind=kind; this.label=label;
            this.value=value; this.mimeType=mimeType;
        }
    }

    TaskStore(Context context) {
        super(context, NAME, null, 2);
        Context application=context.getApplicationContext();
        appContext=application==null?context:application;
    }
    @Override public void onCreate(SQLiteDatabase db) {
        createTasks(db);
        createAttachments(db);
    }
    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX tasks_due ON tasks(done, due_at, id)");
    }
    private void createAttachments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, value TEXT NOT NULL, mime_type TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS attachments_task ON attachments(task_id, created_at, id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createAttachments(db);
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
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("attachments","task_id=?",new String[]{Long.toString(id)});
            db.delete("tasks","id=?",new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    long addAttachment(long taskId,String kind,String label,String value,String mimeType) {
        if (get(taskId)==null) throw new IllegalArgumentException("Tâche introuvable.");
        ContentValues values=new ContentValues();
        values.put("task_id",taskId);
        values.put("kind",kind==null?"note":kind);
        values.put("label",label==null||label.trim().isEmpty()?"Élément":label.trim());
        values.put("value",value==null?"":value.trim());
        values.put("mime_type",mimeType);
        values.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("attachments",null,values);
    }
    List<Attachment> listAttachments(long taskId) {
        ArrayList<Attachment> result=new ArrayList<>();
        try (Cursor c=getReadableDatabase().query("attachments",
                new String[]{"id","task_id","kind","label","value","mime_type"},
                "task_id=?",new String[]{Long.toString(taskId)},null,null,"created_at ASC, id ASC")) {
            while (c.moveToNext()) result.add(new Attachment(
                    c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)));
        }
        return result;
    }
    void deleteAttachment(long id) {
        getWritableDatabase().delete("attachments","id=?",new String[]{Long.toString(id)});
    }

    private void syncReminder(long id) {
        try { ReminderScheduler.sync(appContext,get(id)); }
        catch (RuntimeException ignored) {
            // La tâche reste enregistrée même si Android refuse temporairement un rappel.
        }
    }
}
