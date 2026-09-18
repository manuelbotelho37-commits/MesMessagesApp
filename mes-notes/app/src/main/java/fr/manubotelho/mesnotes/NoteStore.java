package fr.manubotelho.mesnotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class NoteStore extends SQLiteOpenHelper {
    static final String NAME = "mes_notes.db";

    static final class Note {
        final long id;
        final String title, content, contentHtml;
        final boolean favorite, locked;
        final long updatedAt;
        Note(long id, String title, String content, String contentHtml, boolean favorite, boolean locked, long updatedAt) {
            this.id=id; this.title=title; this.content=content; this.contentHtml=contentHtml;
            this.favorite=favorite; this.locked=locked; this.updatedAt=updatedAt;
        }
    }

    static final class Attachment {
        final long id, noteId;
        final String kind, label, value, mimeType, localPath;
        final long createdAt;
        Attachment(long id,long noteId,String kind,String label,String value,String mimeType,String localPath,long createdAt) {
            this.id=id; this.noteId=noteId; this.kind=kind; this.label=label; this.value=value;
            this.mimeType=mimeType; this.localPath=localPath; this.createdAt=createdAt;
        }
    }

    NoteStore(Context context) { super(context, NAME, null, 4); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, content TEXT NOT NULL DEFAULT '', content_html TEXT, favorite INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX notes_order ON notes(favorite DESC, updated_at DESC, id DESC)");
        db.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, value TEXT NOT NULL DEFAULT '', mime_type TEXT, local_path TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX attachments_note ON attachments(note_id, created_at, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, value TEXT NOT NULL DEFAULT '', mime_type TEXT, local_path TEXT, created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS attachments_note ON attachments(note_id, created_at, id)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE notes ADD COLUMN locked INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE notes ADD COLUMN content_html TEXT");
        }
    }

    long save(long id,String title,String content,boolean favorite) {
        return save(id,title,content,null,favorite);
    }

    long save(long id,String title,String content,String contentHtml,boolean favorite) {
        title=title==null?"":title.trim();
        content=content==null?"":content.trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Donne un nom à la note.");
        ContentValues v=new ContentValues();
        v.put("title",title);
        v.put("content",content);
        if(contentHtml==null||contentHtml.trim().isEmpty()) v.putNull("content_html");
        else v.put("content_html",contentHtml);
        v.put("favorite",favorite?1:0);
        v.put("updated_at",System.currentTimeMillis());
        if (id==0) return getWritableDatabase().insertOrThrow("notes",null,v);
        if (getWritableDatabase().update("notes",v,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Note introuvable.");
        return id;
    }

    Note get(long id) {
        try (Cursor c=getReadableDatabase().query("notes",
                new String[]{"id","title","content","content_html","favorite","locked","updated_at"},
                "id=?",new String[]{Long.toString(id)},null,null,null)) {
            if (!c.moveToFirst()) return null;
            return new Note(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)==1,c.getInt(5)==1,c.getLong(6));
        }
    }

    List<Note> list(String query) {
        ArrayList<Note> out=new ArrayList<>();
        String q=query==null?"":query.trim();
        String selection=null;
        String[] args=null;
        if (!q.isEmpty()) {
            selection="title LIKE ? COLLATE NOCASE OR content LIKE ? COLLATE NOCASE OR id IN (SELECT note_id FROM attachments WHERE label LIKE ? COLLATE NOCASE OR value LIKE ? COLLATE NOCASE)";
            String like="%"+q+"%";
            args=new String[]{like,like,like,like};
        }
        try (Cursor c=getReadableDatabase().query("notes",
                new String[]{"id","title","content","content_html","favorite","locked","updated_at"},
                selection,args,null,null,"favorite DESC, updated_at DESC, id DESC")) {
            while(c.moveToNext()) out.add(new Note(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)==1,c.getInt(5)==1,c.getLong(6)));
        }
        return out;
    }

    void setFavorite(long id,boolean favorite) {
        ContentValues v=new ContentValues();
        v.put("favorite",favorite?1:0); v.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().update("notes",v,"id=?",new String[]{Long.toString(id)});
    }

    void setLocked(long id,boolean locked) {
        ContentValues v=new ContentValues();
        v.put("locked",locked?1:0);
        getWritableDatabase().update("notes",v,"id=?",new String[]{Long.toString(id)});
    }

    void delete(long id) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("attachments","note_id=?",new String[]{Long.toString(id)});
            db.delete("notes","id=?",new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    long addAttachment(long noteId,String kind,String label,String value,String mimeType,String localPath) {
        if (get(noteId)==null) throw new IllegalArgumentException("Note introuvable.");
        ContentValues v=new ContentValues();
        v.put("note_id",noteId);
        v.put("kind",kind==null?"texte":kind);
        v.put("label",label==null||label.trim().isEmpty()?"Élément":label.trim());
        v.put("value",value==null?"":value);
        if (mimeType==null) v.putNull("mime_type"); else v.put("mime_type",mimeType);
        if (localPath==null) v.putNull("local_path"); else v.put("local_path",localPath);
        v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("attachments",null,v);
    }

    List<Attachment> listAttachments(long noteId) {
        ArrayList<Attachment> out=new ArrayList<>();
        try (Cursor c=getReadableDatabase().query("attachments",
                new String[]{"id","note_id","kind","label","value","mime_type","local_path","created_at"},
                "note_id=?",new String[]{Long.toString(noteId)},null,null,"created_at ASC, id ASC")) {
            while(c.moveToNext()) out.add(new Attachment(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),
                    c.getString(4),c.getString(5),c.getString(6),c.getLong(7)));
        }
        return out;
    }

    Attachment getAttachment(long id) {
        try (Cursor c=getReadableDatabase().query("attachments",
                new String[]{"id","note_id","kind","label","value","mime_type","local_path","created_at"},
                "id=?",new String[]{Long.toString(id)},null,null,null)) {
            if (!c.moveToFirst()) return null;
            return new Attachment(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),
                    c.getString(4),c.getString(5),c.getString(6),c.getLong(7));
        }
    }

    void deleteAttachment(long id) {
        getWritableDatabase().delete("attachments","id=?",new String[]{Long.toString(id)});
    }

    int attachmentCount(long noteId) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM attachments WHERE note_id=?",new String[]{Long.toString(noteId)})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
}
