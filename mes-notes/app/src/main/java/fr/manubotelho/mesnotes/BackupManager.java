package fr.manubotelho.mesnotes;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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

final class BackupManager {
    private static final String PREFS="mnm_backup";
    private static final String KEY_URI="backup_json_uri";
    private static final long MAX_EMBEDDED_FILE=20L*1024L*1024L;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();

    private BackupManager() {}

    static void setBackupUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(KEY_URI,uri==null?null:uri.toString()).apply();
    }

    static Uri getBackupUri(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String raw=p.getString(KEY_URI,null);
        if(raw==null||raw.trim().isEmpty()) return null;
        try { return Uri.parse(raw); } catch(Exception ex) { return null; }
    }

    static boolean isConfigured(Context context) { return getBackupUri(context)!=null; }

    static void scheduleBackup(Context context) {
        Uri uri=getBackupUri(context);
        if(uri==null) return;
        Context app=context.getApplicationContext();
        if(app==null) app=context;
        final Context safe=app;
        EXECUTOR.execute(() -> backupNow(safe,uri));
    }

    static boolean backupNow(Context context) {
        Uri uri=getBackupUri(context);
        return uri!=null && backupNow(context,uri);
    }

    static boolean backupNow(Context context,Uri uri) {
        NoteStore store=null;
        try {
            store=new NoteStore(context);
            JSONObject root=new JSONObject();
            root.put("format","mnm-backup");
            root.put("version",1);
            root.put("exportedAt",System.currentTimeMillis());

            JSONArray notes=new JSONArray();
            for(NoteStore.Note note:store.list("")) {
                JSONObject n=new JSONObject();
                n.put("id",note.id);
                n.put("title",note.title);
                n.put("content",note.content);
                n.put("favorite",note.favorite);
                n.put("updatedAt",note.updatedAt);

                JSONArray attachments=new JSONArray();
                for(NoteStore.Attachment a:store.listAttachments(note.id)) {
                    JSONObject j=new JSONObject();
                    j.put("id",a.id);
                    j.put("kind",a.kind);
                    j.put("label",a.label);
                    j.put("value",a.value);
                    if(a.mimeType==null) j.put("mimeType",JSONObject.NULL); else j.put("mimeType",a.mimeType);
                    j.put("createdAt",a.createdAt);

                    boolean embedded=false;
                    if(a.localPath!=null&&!a.localPath.trim().isEmpty()) {
                        File file=new File(a.localPath);
                        if(file.exists()&&file.isFile()&&file.length()<=MAX_EMBEDDED_FILE) {
                            j.put("fileName",file.getName());
                            j.put("fileBase64",encodeFile(file));
                            embedded=true;
                        }
                    }
                    j.put("fileIncluded",embedded);
                    attachments.put(j);
                }
                n.put("attachments",attachments);
                notes.put(n);
            }
            root.put("notes",notes);

            try(OutputStream raw=context.getContentResolver().openOutputStream(uri,"wt")) {
                if(raw==null) return false;
                try(OutputStreamWriter out=new OutputStreamWriter(new BufferedOutputStream(raw),StandardCharsets.UTF_8)) {
                    out.write(root.toString(2));
                    out.flush();
                }
            }
            return true;
        } catch(Exception ex) {
            return false;
        } finally {
            if(store!=null) store.close();
        }
    }

    static boolean restoreFrom(Context context,Uri uri) {
        File tempJson=new File(context.getCacheDir(),"mnm-restore-"+System.currentTimeMillis()+".json");
        File stagedDir=new File(context.getCacheDir(),"mnm-stage-"+System.currentTimeMillis());
        ArrayList<File> newFiles=new ArrayList<>();
        NoteStore store=null;
        SQLiteDatabase db=null;
        try {
            copyUriToFile(context,uri,tempJson);
            JSONObject root=readJson(tempJson);
            if(!"mnm-backup".equals(root.optString("format"))) return false;
            JSONArray notes=root.optJSONArray("notes");
            if(notes==null) return false;

            if(!stagedDir.mkdirs()&&!stagedDir.exists()) return false;
            File finalDir=new File(context.getFilesDir(),"mnm_files");
            if(!finalDir.exists()&&!finalDir.mkdirs()) return false;

            ArrayList<PreparedAttachment> prepared=new ArrayList<>();
            for(int i=0;i<notes.length();i++) {
                JSONObject n=notes.getJSONObject(i);
                JSONArray at=n.optJSONArray("attachments");
                if(at==null) continue;
                for(int j=0;j<at.length();j++) {
                    JSONObject a=at.getJSONObject(j);
                    String finalPath=null;
                    if(a.optBoolean("fileIncluded",false)&&a.has("fileBase64")) {
                        String fileName=safeFileName(a.optString("fileName","piece-jointe"));
                        File staged=new File(stagedDir,i+"-"+j+"-"+fileName);
                        decodeToFile(a.getString("fileBase64"),staged);
                        File dest=uniqueFile(finalDir,fileName);
                        copyFile(staged,dest);
                        newFiles.add(dest);
                        finalPath=dest.getAbsolutePath();
                    }
                    prepared.add(new PreparedAttachment(i,j,finalPath));
                }
            }

            ArrayList<String> oldFiles=new ArrayList<>();
            store=new NoteStore(context);
            for(NoteStore.Note old:store.list("")) {
                for(NoteStore.Attachment a:store.listAttachments(old.id)) {
                    if(a.localPath!=null&&!a.localPath.isEmpty()) oldFiles.add(a.localPath);
                }
            }

            db=store.getWritableDatabase();
            db.beginTransaction();
            db.delete("attachments",null,null);
            db.delete("notes",null,null);

            int preparedIndex=0;
            for(int i=0;i<notes.length();i++) {
                JSONObject n=notes.getJSONObject(i);
                long noteId=n.optLong("id",i+1L);

                ContentValues nv=new ContentValues();
                nv.put("id",noteId);
                nv.put("title",n.optString("title","Note"));
                nv.put("content",n.optString("content",""));
                nv.put("favorite",n.optBoolean("favorite",false)?1:0);
                nv.put("updated_at",n.optLong("updatedAt",System.currentTimeMillis()));
                db.insertOrThrow("notes",null,nv);

                JSONArray at=n.optJSONArray("attachments");
                if(at==null) continue;
                for(int j=0;j<at.length();j++) {
                    JSONObject a=at.getJSONObject(j);
                    String localPath=null;
                    if(preparedIndex<prepared.size()) localPath=prepared.get(preparedIndex).finalPath;
                    preparedIndex++;

                    ContentValues av=new ContentValues();
                    if(a.has("id")) av.put("id",a.optLong("id"));
                    av.put("note_id",noteId);
                    av.put("kind",a.optString("kind","texte"));
                    av.put("label",a.optString("label","Élément"));
                    av.put("value",a.optString("value",""));
                    if(a.isNull("mimeType")) av.putNull("mime_type"); else av.put("mime_type",a.optString("mimeType",null));
                    if(localPath==null) av.putNull("local_path"); else av.put("local_path",localPath);
                    av.put("created_at",a.optLong("createdAt",System.currentTimeMillis()));
                    db.insertOrThrow("attachments",null,av);
                }
            }

            db.setTransactionSuccessful();
            db.endTransaction();
            db=null;

            for(String path:oldFiles) {
                try {
                    File f=new File(path);
                    if(f.exists()&&!newFiles.contains(f)) f.delete();
                } catch(Exception ignored) {}
            }
            return true;
        } catch(Exception ex) {
            if(db!=null&&db.inTransaction()) {
                try { db.endTransaction(); } catch(Exception ignored) {}
            }
            for(File f:newFiles) {
                try { if(f.exists()) f.delete(); } catch(Exception ignored) {}
            }
            return false;
        } finally {
            if(store!=null) store.close();
            deleteRecursively(stagedDir);
            if(tempJson.exists()) tempJson.delete();
        }
    }

    private static String encodeFile(File file) throws Exception {
        try(InputStream in=new BufferedInputStream(new FileInputStream(file));
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[32768];
            int read;
            while((read=in.read(buffer))>=0) if(read>0) out.write(buffer,0,read);
            return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }
    }

    private static void decodeToFile(String encoded,File file) throws Exception {
        byte[] bytes=Base64.decode(encoded,Base64.DEFAULT);
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(bytes); out.flush();
        }
    }

    private static JSONObject readJson(File file) throws Exception {
        StringBuilder text=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))) {
            char[] buf=new char[8192];
            int read;
            while((read=r.read(buf))>=0) if(read>0) text.append(buf,0,read);
        }
        return new JSONObject(text.toString());
    }

    private static void copyUriToFile(Context context,Uri uri,File file) throws Exception {
        try(InputStream raw=context.getContentResolver().openInputStream(uri)) {
            if(raw==null) throw new IllegalStateException("Fichier inaccessible");
            try(InputStream in=new BufferedInputStream(raw);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(file))) {
                byte[] buffer=new byte[32768];
                int read;
                while((read=in.read(buffer))>=0) if(read>0) out.write(buffer,0,read);
                out.flush();
            }
        }
    }

    private static void copyFile(File from,File to) throws Exception {
        try(InputStream in=new BufferedInputStream(new FileInputStream(from));
            OutputStream out=new BufferedOutputStream(new FileOutputStream(to))) {
            byte[] buffer=new byte[32768];
            int read;
            while((read=in.read(buffer))>=0) if(read>0) out.write(buffer,0,read);
            out.flush();
        }
    }

    private static File uniqueFile(File dir,String wanted) {
        String safe=safeFileName(wanted);
        File candidate=new File(dir,System.currentTimeMillis()+"-"+safe);
        int i=1;
        while(candidate.exists()) candidate=new File(dir,System.currentTimeMillis()+"-"+(i++)+"-"+safe);
        return candidate;
    }

    private static String safeFileName(String name) {
        String safe=name==null?"piece-jointe":name.replaceAll("[^a-zA-Z0-9._ -]","_").trim();
        return safe.isEmpty()?"piece-jointe":safe;
    }

    private static void deleteRecursively(File f) {
        if(f==null||!f.exists()) return;
        if(f.isDirectory()) {
            File[] children=f.listFiles();
            if(children!=null) for(File c:children) deleteRecursively(c);
        }
        try { f.delete(); } catch(Exception ignored) {}
    }

    private static final class PreparedAttachment {
        final int noteIndex,attachmentIndex;
        final String finalPath;
        PreparedAttachment(int noteIndex,int attachmentIndex,String finalPath) {
            this.noteIndex=noteIndex; this.attachmentIndex=attachmentIndex; this.finalPath=finalPath;
        }
    }
}
