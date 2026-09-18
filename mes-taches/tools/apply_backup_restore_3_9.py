from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"
MAIN = JAVA / "MainActivity.java"
STORE = JAVA / "TaskStore.java"
GRADLE = ROOT / "app" / "build.gradle"

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Patch sauvegarde introuvable: {label}")
    return text.replace(old, new, 1)

# --- TaskStore: sauvegarde automatique après chaque modification ---
store = STORE.read_text(encoding="utf-8")

store = replace_once(
    store,
    '''        syncReminder(savedId);
        return savedId;''',
    '''        syncReminder(savedId);
        backup();
        return savedId;''',
    "saveWithOptions",
)

store = replace_once(
    store,
    '''        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
    }

    void setUrgent''',
    '''        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        backup();
    }

    void setUrgent''',
    "setPriority",
)

store = replace_once(
    store,
    '''        } else syncReminder(id);
    }

    void postpone''',
    '''        } else syncReminder(id);
        backup();
    }

    void postpone''',
    "setPaused",
)

store = replace_once(
    store,
    '''        syncReminder(id);
    }

    void setDone''',
    '''        syncReminder(id);
        backup();
    }

    void setDone''',
    "postpone",
)

store = replace_once(
    store,
    '''            syncReminder(id);
            return;
        }''',
    '''            syncReminder(id);
            backup();
            return;
        }''',
    "setDone recurrent",
)

store = replace_once(
    store,
    '''        syncReminder(id);
    }

    private static long nextOccurrence''',
    '''        syncReminder(id);
        backup();
    }

    private static long nextOccurrence''',
    "setDone standard",
)

store = replace_once(
    store,
    '''        } finally { db.endTransaction(); }
    }

    long addAttachment''',
    '''        } finally { db.endTransaction(); }
        backup();
    }

    long addAttachment''',
    "delete",
)

store = replace_once(
    store,
    '''        return getWritableDatabase().insertOrThrow("attachments",null,values);
    }''',
    '''        long attachmentId=getWritableDatabase().insertOrThrow("attachments",null,values);
        backup();
        return attachmentId;
    }''',
    "addAttachment",
)

store = replace_once(
    store,
    '''        getWritableDatabase().update("attachments",values,"id=?",new String[]{Long.toString(id)});
    }''',
    '''        getWritableDatabase().update("attachments",values,"id=?",new String[]{Long.toString(id)});
        backup();
    }''',
    "updateAttachment",
)

store = replace_once(
    store,
    '''        getWritableDatabase().delete("attachments","id=?",new String[]{Long.toString(id)});
    }

    private void syncReminder''',
    '''        getWritableDatabase().delete("attachments","id=?",new String[]{Long.toString(id)});
        backup();
    }

    private void backup() {
        TaskBackupManager.scheduleBackup(appContext);
    }

    private void syncReminder''',
    "deleteAttachment + backup helper",
)

STORE.write_text(store, encoding="utf-8")

# --- MainActivity: boutons Sauvegarde / Restaurer + sélecteur Drive ---
main = MAIN.read_text(encoding="utf-8")

if "import android.net.Uri;" not in main:
    main = main.replace("import android.os.Build;\n", "import android.net.Uri;\nimport android.os.Build;\n", 1)

if "REQ_BACKUP_CREATE" not in main:
    anchor = "public final class MainActivity extends Activity {\n"
    main = main.replace(
        anchor,
        anchor + "    private static final int REQ_BACKUP_CREATE=4901, REQ_BACKUP_OPEN=4902;\n",
        1,
    )

button_anchor = '''        addButton=button("+ Ajouter",true); addButton.setId(ADD); addButton.setTextSize(compactWide?12:(wideLayout?18:20));'''
if "Button backupButton=button(" not in main:
    backup_ui = '''        LinearLayout backupRow=new LinearLayout(this);
        backupRow.setOrientation(compactWide?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        Button backupButton=button(TaskBackupManager.isConfigured(this)?"Sauvegarde ✓":"Sauvegarde",false);
        Button restoreButton=button("Restaurer",false);
        backupButton.setOnClickListener(v->startBackup());
        restoreButton.setOnClickListener(v->startRestore());
        if (compactWide) {
            backupButton.setTextSize(9); restoreButton.setTextSize(9);
            backupButton.setPadding(dp(2),dp(2),dp(2),dp(2));
            restoreButton.setPadding(dp(2),dp(2),dp(2),dp(2));
            backupRow.addView(backupButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(36)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(36));
            rp.topMargin=dp(4); backupRow.addView(restoreButton,rp);
        } else {
            backupButton.setTextSize(13); restoreButton.setTextSize(13);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(44),1f);
            bp.setMarginEnd(dp(6)); backupRow.addView(backupButton,bp);
            backupRow.addView(restoreButton,new LinearLayout.LayoutParams(0,dp(44),1f));
        }
        LinearLayout.LayoutParams backupRowParams=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        backupRowParams.bottomMargin=dp(7);
        footer.addView(backupRow,backupRowParams);

'''
    if button_anchor not in main:
        raise SystemExit("Ancre bouton Ajouter introuvable pour sauvegarde")
    main = main.replace(button_anchor, backup_ui + button_anchor, 1)

methods = r'''    private void startBackup() {
        Uri configured=TaskBackupManager.getBackupUri(this);
        if (configured!=null) {
            boolean ok=TaskBackupManager.backupNow(this);
            Toast.makeText(this,ok?"Sauvegarde mise à jour ✓":"Échec de la sauvegarde",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE,"Insisto-sauvegarde.db");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                |Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,REQ_BACKUP_CREATE);
    }

    private void startRestore() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,REQ_BACKUP_OPEN);
    }

    private void configureBackup(Uri uri,Intent data) {
        try {
            int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    |Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri,flags); }
            catch (SecurityException ignored) {}
            if (!TaskBackupManager.backupNow(this,uri)) {
                Toast.makeText(this,"Impossible de créer la sauvegarde",Toast.LENGTH_LONG).show();
                return;
            }
            TaskBackupManager.setBackupUri(this,uri);
            Toast.makeText(this,"Sauvegarde automatique activée ✓",Toast.LENGTH_LONG).show();
            recreate();
        } catch (RuntimeException ex) {
            Toast.makeText(this,"Impossible d’activer la sauvegarde",Toast.LENGTH_LONG).show();
        }
    }

    private void confirmRestore(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Restaurer la sauvegarde")
                .setMessage("Les tâches actuellement dans Insisto seront remplacées par celles de cette sauvegarde.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Restaurer",(dialog,which)->performRestore(uri))
                .show();
    }

    private void performRestore(Uri uri) {
        List<TaskStore.Task> oldTasks=new java.util.ArrayList<>();
        try {
            oldTasks.addAll(store.list(false));
            oldTasks.addAll(store.list(true));
            for (TaskStore.Task task:oldTasks) {
                try { ReminderScheduler.cancel(this,task.id); } catch (RuntimeException ignored) {}
            }
            store.close();

            boolean ok=TaskBackupManager.restoreFrom(this,uri);
            store=new TaskStore(this);
            if (!ok) {
                for (TaskStore.Task task:store.list(false)) {
                    try { ReminderScheduler.sync(this,task); } catch (RuntimeException ignored) {}
                }
                Toast.makeText(this,"Ce fichier n’est pas une sauvegarde Insisto valide",
                        Toast.LENGTH_LONG).show();
                refresh();
                return;
            }

            for (TaskStore.Task task:store.list(false)) {
                try { ReminderScheduler.sync(this,task); } catch (RuntimeException ignored) {}
            }
            refresh();
            Toast.makeText(this,"Sauvegarde restaurée ✓",Toast.LENGTH_LONG).show();
        } catch (RuntimeException ex) {
            try {
                store=new TaskStore(this);
                for (TaskStore.Task task:store.list(false)) {
                    try { ReminderScheduler.sync(this,task); } catch (RuntimeException ignored) {}
                }
            } catch (RuntimeException ignored) {}
            Toast.makeText(this,"Échec de la restauration",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        if (requestCode==REQ_BACKUP_CREATE) configureBackup(uri,data);
        else if (requestCode==REQ_BACKUP_OPEN) confirmRestore(uri);
    }

'''
if "private void startBackup()" not in main:
    marker = "    private void refresh() {"
    if marker not in main:
        raise SystemExit("Ancre refresh introuvable pour méthodes sauvegarde")
    main = main.replace(marker, methods + marker, 1)

MAIN.write_text(main, encoding="utf-8")

# Version 3.9
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 110", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.9.0-drive-backup'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.9 : sauvegarde automatique Drive + boutons Sauvegarde / Restaurer")
