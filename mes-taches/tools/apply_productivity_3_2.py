from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle"


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Base de données : récurrence + 3 niveaux de priorité + historique.
store = JAVA / "TaskStore.java"
store.write_text(r'''package fr.manubotelho.mestaches;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

final class TaskStore extends SQLiteOpenHelper {
    static final String NAME = "mes_taches.db";
    private final Context appContext;

    static final class Task {
        final long id, dueAt;
        final String title, recurrence;
        final boolean appointment, done, urgent, paused;
        final int priority;
        Task(long id, String title, long dueAt, boolean appointment, boolean done,
             boolean urgent, boolean paused, int priority, String recurrence) {
            this.id=id; this.title=title; this.dueAt=dueAt;
            this.appointment=appointment; this.done=done;
            this.urgent=urgent; this.paused=paused;
            this.priority=priority; this.recurrence=normalizeRecurrence(recurrence);
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
        super(context, NAME, null, 5);
        Context application=context.getApplicationContext();
        appContext=application==null?context:application;
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createTasks(db);
        createAttachments(db);
    }

    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, urgent INTEGER NOT NULL DEFAULT 0, paused INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT 'none', updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX tasks_due ON tasks(done, due_at, id)");
    }

    private void createAttachments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, value TEXT NOT NULL, mime_type TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS attachments_task ON attachments(task_id, created_at, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createAttachments(db);
        if (oldVersion < 3) db.execSQL("ALTER TABLE tasks ADD COLUMN urgent INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 4) db.execSQL("ALTER TABLE tasks ADD COLUMN paused INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'none'");
            db.execSQL("UPDATE tasks SET priority=2 WHERE urgent=1");
        }
    }

    private Task read(Cursor c) {
        return new Task(c.getLong(0), c.getString(1), c.getLong(2),
                c.getInt(3)==1, c.getInt(4)==1, c.getInt(5)==1,
                c.getInt(6)==1, c.getInt(7), c.getString(8));
    }

    List<Task> list(boolean done) {
        ArrayList<Task> result=new ArrayList<>();
        String order;
        if (done) order="updated_at DESC, id DESC";
        else {
            long now=System.currentTimeMillis();
            order="CASE WHEN due_at<"+now+" THEN 0 ELSE 1 END, priority DESC, due_at ASC, id ASC";
        }
        try (Cursor c=getReadableDatabase().query("tasks",
                new String[]{"id","title","due_at","appointment","done","urgent","paused","priority","recurrence"},
                "done=?",new String[]{done?"1":"0"},null,null,order)) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }

    Task get(long id) {
        try (Cursor c=getReadableDatabase().query("tasks",
                new String[]{"id","title","due_at","appointment","done","urgent","paused","priority","recurrence"},
                "id=?",new String[]{Long.toString(id)},null,null,null)) {
            return c.moveToFirst()?read(c):null;
        }
    }

    long save(long id,String title,long dueAt,boolean appointment) {
        Task current=id==0?null:get(id);
        return saveWithOptions(id,title,dueAt,appointment,
                current==null?"none":current.recurrence,
                current==null?0:current.priority);
    }

    long saveWithOptions(long id,String title,long dueAt,boolean appointment,String recurrence,int priority) {
        title=title==null?"":title.trim();
        if (title.isEmpty() || title.length()>240)
            throw new IllegalArgumentException("Indique un texte de 1 à 240 caractères.");
        recurrence=normalizeRecurrence(recurrence);
        priority=Math.max(0,Math.min(2,priority));
        ContentValues values=new ContentValues();
        values.put("title",title); values.put("due_at",dueAt);
        values.put("appointment",appointment?1:0);
        values.put("priority",priority); values.put("urgent",priority>=2?1:0);
        values.put("recurrence",recurrence);
        values.put("updated_at",System.currentTimeMillis());
        long savedId;
        if (id==0) {
            values.put("done",0); values.put("paused",0);
            savedId=getWritableDatabase().insertOrThrow("tasks",null,values);
        } else {
            if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("Cette tâche n’existe plus.");
            savedId=id;
        }
        syncReminder(savedId);
        return savedId;
    }

    void setPriority(long id,int priority) {
        priority=Math.max(0,Math.min(2,priority));
        ContentValues values=new ContentValues();
        values.put("priority",priority); values.put("urgent",priority>=2?1:0);
        values.put("updated_at",System.currentTimeMillis());
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
    }

    void setUrgent(long id,boolean urgent) { setPriority(id,urgent?2:0); }

    void setPaused(long id,boolean paused) {
        ContentValues values=new ContentValues();
        values.put("paused",paused?1:0); values.put("updated_at",System.currentTimeMillis());
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        if (paused) {
            try { ReminderScheduler.cancel(appContext,id); } catch (RuntimeException ignored) {}
        } else syncReminder(id);
    }

    void postpone(long id,long newDueAt) {
        ContentValues values=new ContentValues();
        values.put("due_at",newDueAt); values.put("done",0); values.put("paused",0);
        values.put("updated_at",System.currentTimeMillis());
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        syncReminder(id);
    }

    void setDone(long id,boolean done) {
        Task task=get(id);
        if (task==null) throw new IllegalStateException("Cette tâche n’existe plus.");
        if (done && !"none".equals(task.recurrence)) {
            SQLiteDatabase db=getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues history=new ContentValues();
                history.put("title",task.title); history.put("due_at",task.dueAt);
                history.put("appointment",task.appointment?1:0); history.put("done",1);
                history.put("urgent",task.priority>=2?1:0); history.put("paused",0);
                history.put("priority",task.priority); history.put("recurrence","none");
                history.put("updated_at",System.currentTimeMillis());
                db.insertOrThrow("tasks",null,history);

                ContentValues next=new ContentValues();
                next.put("due_at",nextOccurrence(task.dueAt,task.recurrence));
                next.put("done",0); next.put("paused",0);
                next.put("updated_at",System.currentTimeMillis());
                if (db.update("tasks",next,"id=?",new String[]{Long.toString(id)})!=1)
                    throw new IllegalStateException("Cette tâche n’existe plus.");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            try { ReminderScheduler.cancelNotification(appContext,id); } catch (RuntimeException ignored) {}
            syncReminder(id);
            return;
        }
        ContentValues values=new ContentValues();
        values.put("done",done?1:0); values.put("updated_at",System.currentTimeMillis());
        if (!done) values.put("paused",0);
        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Cette tâche n’existe plus.");
        syncReminder(id);
    }

    private static long nextOccurrence(long from,String recurrence) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(from);
        long now=System.currentTimeMillis();
        do {
            switch (normalizeRecurrence(recurrence)) {
                case "daily": c.add(Calendar.DAY_OF_YEAR,1); break;
                case "weekly": c.add(Calendar.WEEK_OF_YEAR,1); break;
                case "monthly": c.add(Calendar.MONTH,1); break;
                case "yearly": c.add(Calendar.YEAR,1); break;
                default: return from;
            }
        } while (c.getTimeInMillis()<=now);
        return c.getTimeInMillis();
    }

    static String normalizeRecurrence(String recurrence) {
        if ("daily".equals(recurrence) || "weekly".equals(recurrence)
                || "monthly".equals(recurrence) || "yearly".equals(recurrence)) return recurrence;
        return "none";
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

    void updateAttachment(long id,String kind,String label,String value,String mimeType) {
        ContentValues values=new ContentValues();
        values.put("kind",kind); values.put("label",label); values.put("value",value); values.put("mime_type",mimeType);
        getWritableDatabase().update("attachments",values,"id=?",new String[]{Long.toString(id)});
    }

    void deleteAttachment(long id) {
        getWritableDatabase().delete("attachments","id=?",new String[]{Long.toString(id)});
    }

    private void syncReminder(long id) {
        try { ReminderScheduler.sync(appContext,get(id)); }
        catch (RuntimeException ignored) {}
    }
}
''',encoding="utf-8")

# 2) Actions dans la notification : Fait / Reporter 1 h / Demain.
action_receiver = JAVA / "ReminderActionReceiver.java"
action_receiver.write_text(r'''package fr.manubotelho.mestaches;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

public final class ReminderActionReceiver extends BroadcastReceiver {
    static final String ACTION_DONE="fr.manubotelho.mestaches.ACTION_DONE";
    static final String ACTION_HOUR="fr.manubotelho.mestaches.ACTION_SNOOZE_HOUR";
    static final String ACTION_TOMORROW="fr.manubotelho.mestaches.ACTION_SNOOZE_TOMORROW";

    @Override public void onReceive(Context context,Intent intent) {
        long taskId=intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        if (taskId==0) return;
        try (TaskStore store=new TaskStore(context)) {
            TaskStore.Task task=store.get(taskId);
            if (task==null || task.done) {
                ReminderScheduler.cancelNotification(context,taskId);
                return;
            }
            String action=intent.getAction();
            if (ACTION_DONE.equals(action)) {
                store.setDone(taskId,true);
            } else if (ACTION_HOUR.equals(action)) {
                store.postpone(taskId,System.currentTimeMillis()+60L*60L*1000L);
            } else if (ACTION_TOMORROW.equals(action)) {
                Calendar original=Calendar.getInstance(); original.setTimeInMillis(task.dueAt);
                Calendar next=Calendar.getInstance();
                next.add(Calendar.DAY_OF_YEAR,1);
                next.set(Calendar.HOUR_OF_DAY,original.get(Calendar.HOUR_OF_DAY));
                next.set(Calendar.MINUTE,original.get(Calendar.MINUTE));
                next.set(Calendar.SECOND,0); next.set(Calendar.MILLISECOND,0);
                store.postpone(taskId,next.getTimeInMillis());
            }
        } catch (RuntimeException ignored) {}
        ReminderScheduler.cancelNotification(context,taskId);
    }
}
''',encoding="utf-8")

receiver = JAVA / "ReminderReceiver.java"
receiver.write_text(r'''package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        long taskId=intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        if (taskId==0) return;
        TaskStore.Task task;
        try (TaskStore store=new TaskStore(context)) { task=store.get(taskId); }
        catch (RuntimeException ex) { return; }
        if (task==null || task.done || task.paused) return;
        showReminder(context,task);
        ReminderScheduler.scheduleNextHourly(context,task);
    }

    static void showReminder(Context context,TaskStore.Task task) {
        if (task==null || task.done || task.paused) return;
        if (Build.VERSION.SDK_INT>=33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        ReminderScheduler.createChannel(context);

        Intent open=new Intent(context,TaskDetailActivity.class)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id),open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Intent alert=new Intent(context,ReminderAlertActivity.class)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent alertIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id)+100000,alert,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        PendingIntent done=actionIntent(context,task.id,ReminderActionReceiver.ACTION_DONE,210000);
        PendingIntent hour=actionIntent(context,task.id,ReminderActionReceiver.ACTION_HOUR,220000);
        PendingIntent tomorrow=actionIntent(context,task.id,ReminderActionReceiver.ACTION_TOMORROW,230000);

        String detail=task.appointment
                ? "Rendez-vous à faire maintenant · rappel dans 1 h"
                : "Tâche à faire maintenant · rappel dans 1 h";
        if (!"none".equals(task.recurrence)) detail+=" · récurrente";

        Notification.Builder builder=new Notification.Builder(context,ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setSubText("Mes tâches Manu")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(fr.manubotelho.mestaches.R.drawable.ic_notification,"Fait",done).build())
                .addAction(new Notification.Action.Builder(fr.manubotelho.mestaches.R.drawable.ic_notification,"Reporter 1 h",hour).build())
                .addAction(new Notification.Action.Builder(fr.manubotelho.mestaches.R.drawable.ic_notification,"Demain",tomorrow).build());

        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        boolean locked=keyguard!=null && keyguard.isKeyguardLocked();
        boolean screenOff=power!=null && !power.isInteractive();
        if (locked || screenOff) {
            builder.setFullScreenIntent(alertIntent,true);
            if (power!=null && !power.isInteractive()) {
                try {
                    PowerManager.WakeLock wake=power.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "MesTachesManu:compact-alert");
                    wake.acquire(12000L);
                } catch (RuntimeException ignored) {}
            }
        }

        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager!=null) {
            int id=ReminderScheduler.notificationId(task.id);
            manager.cancel(id);
            manager.notify(id,builder.build());
        }

        if (!locked && !screenOff && android.provider.Settings.canDrawOverlays(context)) {
            try { context.startActivity(alert); } catch (RuntimeException ignored) {}
        }
    }

    private static PendingIntent actionIntent(Context context,long taskId,String action,int offset) {
        Intent intent=new Intent(context,ReminderActionReceiver.class)
                .setAction(action)
                .setData(Uri.parse("mestachesmanu://action/"+offset+"/"+taskId))
                .putExtra(ReminderScheduler.EXTRA_TASK_ID,taskId);
        return PendingIntent.getBroadcast(context,ReminderScheduler.notificationId(taskId)+offset,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
''',encoding="utf-8")

manifest=MANIFEST.read_text(encoding="utf-8")
if 'android:name=".ReminderActionReceiver"' not in manifest:
    manifest=manifest.replace('        <receiver\n            android:name=".ReminderReceiver"\n            android:exported="false"/>','        <receiver\n            android:name=".ReminderReceiver"\n            android:exported="false"/>\n\n        <receiver\n            android:name=".ReminderActionReceiver"\n            android:exported="false"/>',1)
MANIFEST.write_text(manifest,encoding="utf-8")

# 3) Écran principal : onglet Aujourd'hui, récurrence, priorité Normal/Important/Urgent.
main = JAVA / "MainActivity.java"
text=main.read_text(encoding="utf-8")
text=text.replace('private boolean showingDone=false;','private boolean showingDone=false;\n    private boolean showingToday=false;',1)
text=text.replace('private Button todoTab, doneTab, addButton;','private Button todayTab, todoTab, doneTab, addButton;',1)
text=text.replace('private RadioButton appointmentField;','private RadioButton appointmentField;\n    private RadioGroup recurrenceField, priorityField;',1)
text=text.replace('if (state!=null) showingDone=state.getBoolean("showingDone",false);','if (state!=null) {\n            showingDone=state.getBoolean("showingDone",false);\n            showingToday=!showingDone && state.getBoolean("showingToday",false);\n        }',1)

old_tabs='''        todoTab=button("À faire",false); todoTab.setId(TODO);\n        doneTab=button("Terminées",false); doneTab.setId(DONE);\n        if (wideLayout) {\n            todoTab.setTextSize(15); doneTab.setTextSize(15);\n            todoTab.setMinHeight(dp(48)); doneTab.setMinHeight(dp(48));\n        }\n        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);\n        left.setMarginEnd(dp(7)); tabs.addView(todoTab,left);\n        tabs.addView(doneTab,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));\n        todoTab.setOnClickListener(v->{ showingDone=false; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });\n        doneTab.setOnClickListener(v->{ showingDone=true; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });'''
new_tabs='''        todayTab=button("Aujourd’hui",false);\n        todoTab=button("À faire",false); todoTab.setId(TODO);\n        doneTab=button("Terminées",false); doneTab.setId(DONE);\n        if (wideLayout) {\n            todayTab.setTextSize(13); todoTab.setTextSize(13); doneTab.setTextSize(13);\n            todayTab.setMinHeight(dp(48)); todoTab.setMinHeight(dp(48)); doneTab.setMinHeight(dp(48));\n        }\n        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);\n        first.setMarginEnd(dp(6)); tabs.addView(todayTab,first);\n        LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);\n        middle.setMarginEnd(dp(6)); tabs.addView(todoTab,middle);\n        tabs.addView(doneTab,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));\n        todayTab.setOnClickListener(v->{ showingToday=true; showingDone=false; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });\n        todoTab.setOnClickListener(v->{ showingToday=false; showingDone=false; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });\n        doneTab.setOnClickListener(v->{ showingToday=false; showingDone=true; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });'''
if old_tabs not in text: raise SystemExit("Bloc onglets introuvable")
text=text.replace(old_tabs,new_tabs,1)

old_refresh='''            List<TaskStore.Task> pending=store.list(false);\n            List<TaskStore.Task> completed=store.list(true);\n            LocalDate now=LocalDate.now();'''
new_refresh='''            List<TaskStore.Task> pending=store.list(false);\n            List<TaskStore.Task> completed=store.list(true);\n            LocalDate now=LocalDate.now();\n            List<TaskStore.Task> todayTasks=filterToday(pending,now);'''
if old_refresh not in text: raise SystemExit("Début refresh introuvable")
text=text.replace(old_refresh,new_refresh,1)
text=text.replace('todoTab.setText("À faire ("+pending.size()+")");\n            doneTab.setText("Terminées ("+completed.size()+")");','todayTab.setText("Aujourd’hui ("+todayTasks.size()+")");\n            todoTab.setText("À faire ("+pending.size()+")");\n            doneTab.setText("Terminées ("+completed.size()+")");',1)
text=text.replace('if (paneTitle!=null) paneTitle.setText(showingDone?"Tâches terminées":"Toutes les tâches à faire");','if (paneTitle!=null) paneTitle.setText(showingDone?"Tâches terminées":showingToday?"Aujourd’hui et en retard":"Toutes les tâches à faire");',1)
text=text.replace('List<TaskStore.Task> tasks=showingDone?completed:pending;','List<TaskStore.Task> tasks=showingDone?completed:(showingToday?todayTasks:pending);',1)
text=text.replace('TextView empty=text(showingDone?"Rien de terminé":"Rien de prévu",wideLayout?20:23,INK,true);','TextView empty=text(showingDone?"Rien de terminé":showingToday?"Rien à faire aujourd’hui":"Rien de prévu",wideLayout?20:23,INK,true);',1)
text=text.replace('if (wideLayout && overdue) when+="  ·  EN RETARD";','if (wideLayout && overdue) when+="  ·  EN RETARD";\n        if (wideLayout && !"none".equals(task.recurrence)) when+="  ·  ↻ "+recurrenceLabel(task.recurrence);',1)
text=text.replace('details.addView(kind);\n            Button urgent=urgentButton(task);','details.addView(kind);\n            if (!"none".equals(task.recurrence)) {\n                TextView repeat=text("↻ "+recurrenceLabel(task.recurrence),14,MUTED,true);\n                repeat.setPadding(0,dp(4),0,0); details.addView(repeat);\n            }\n            Button urgent=urgentButton(task);',1)

pattern=r'    private Button urgentButton\(TaskStore\.Task task\) \{.*?\n    \}\n\n    private Button pauseButton'
replacement='''    private Button urgentButton(TaskStore.Task task) {\n        int level=Math.max(0,Math.min(2,task.priority));\n        String label=level==0?"Normal":level==1?"Important":"⚠ URGENT";\n        int color=level==0?todoColor():level==1?ORANGE:RED;\n        Button priority=button(label,false);\n        priority.setTextSize(wideLayout?12:13);\n        priority.setMinHeight(dp(38)); priority.setMinimumHeight(dp(38));\n        priority.setPadding(dp(10),dp(4),dp(10),dp(4));\n        priority.setTextColor(level==0?color:WHITE);\n        priority.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(level==0?WHITE:color,18,color),null));\n        priority.setContentDescription("Priorité "+label+". Appuyer pour changer");\n        priority.setOnClickListener(v->{\n            try { store.setPriority(task.id,(level+1)%3); refresh(); }\n            catch (RuntimeException ex) { error(); }\n        });\n        return priority;\n    }\n\n    private Button pauseButton'''
text,count=re.subn(pattern,replacement,text,count=1,flags=re.S)
if count!=1: raise SystemExit("Méthode priorité introuvable")

anchor='''        form.addView(timeButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n        Runnable updateDate=()->{'''
insert='''        form.addView(timeButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        String selectedRepeat=saved!=null?saved.getString("draftRecurrence",task==null?"none":task.recurrence):(task==null?"none":task.recurrence);\n        form.addView(fieldLabel("Répétition"));\n        recurrenceField=new RadioGroup(this); recurrenceField.setOrientation(RadioGroup.VERTICAL);\n        String[] repeatNames={"Jamais","Tous les jours","Chaque semaine","Tous les mois","Tous les ans"};\n        String[] repeatValues={"none","daily","weekly","monthly","yearly"};\n        for (int i=0;i<repeatNames.length;i++) {\n            RadioButton option=new RadioButton(this); option.setId(View.generateViewId());\n            option.setText(repeatNames[i]); option.setTextSize(16); option.setTag(repeatValues[i]);\n            option.setChecked(repeatValues[i].equals(selectedRepeat)); recurrenceField.addView(option);\n        }\n        form.addView(recurrenceField,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        int selectedLevel=saved!=null?saved.getInt("draftPriority",task==null?0:task.priority):(task==null?0:task.priority);\n        form.addView(fieldLabel("Priorité"));\n        priorityField=new RadioGroup(this); priorityField.setOrientation(RadioGroup.HORIZONTAL);\n        String[] priorityNames={"Normal","Important","Urgent"};\n        for (int i=0;i<priorityNames.length;i++) {\n            RadioButton option=new RadioButton(this); option.setId(View.generateViewId());\n            option.setText(priorityNames[i]); option.setTextSize(15); option.setTag(i); option.setChecked(i==selectedLevel);\n            priorityField.addView(option,new RadioGroup.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));\n        }\n        form.addView(priorityField,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        Runnable updateDate=()->{'''
if anchor not in text: raise SystemExit("Insertion récurrence introuvable")
text=text.replace(anchor,insert,1)
text=text.replace('long savedId=store.save(editingId,title,draftDue.getTimeInMillis(),appointmentField.isChecked());','long savedId=store.saveWithOptions(editingId,title,draftDue.getTimeInMillis(),appointmentField.isChecked(),selectedRecurrence(),selectedPriority());',1)
text=text.replace('if (editingId==0) showingDone=false;','if (editingId==0) { showingDone=false; showingToday=false; }',1)

helper_anchor='''    private void startVoiceInput() {'''
helpers='''    private List<TaskStore.Task> filterToday(List<TaskStore.Task> tasks,LocalDate todayDate) {\n        ArrayList<TaskStore.Task> result=new ArrayList<>();\n        for (TaskStore.Task task:tasks) {\n            LocalDate date=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate();\n            if (!date.isAfter(todayDate)) result.add(task);\n        }\n        return result;\n    }\n\n    private String recurrenceLabel(String recurrence) {\n        switch (TaskStore.normalizeRecurrence(recurrence)) {\n            case "daily": return "Tous les jours";\n            case "weekly": return "Chaque semaine";\n            case "monthly": return "Tous les mois";\n            case "yearly": return "Tous les ans";\n            default: return "Jamais";\n        }\n    }\n\n    private String selectedRecurrence() {\n        if (recurrenceField==null) return "none";\n        for (int i=0;i<recurrenceField.getChildCount();i++) {\n            View child=recurrenceField.getChildAt(i);\n            if (child instanceof RadioButton && ((RadioButton)child).isChecked()) {\n                Object tag=child.getTag(); return tag==null?"none":tag.toString();\n            }\n        }\n        return "none";\n    }\n\n    private int selectedPriority() {\n        if (priorityField==null) return 0;\n        for (int i=0;i<priorityField.getChildCount();i++) {\n            View child=priorityField.getChildAt(i);\n            if (child instanceof RadioButton && ((RadioButton)child).isChecked()) {\n                Object tag=child.getTag(); return tag instanceof Integer?(Integer)tag:0;\n            }\n        }\n        return 0;\n    }\n\n    private void startVoiceInput() {'''
if helper_anchor not in text: raise SystemExit("Ancre helpers introuvable")
text=text.replace(helper_anchor,helpers,1)
text=text.replace('out.putBoolean("showingDone",showingDone);','out.putBoolean("showingDone",showingDone);\n        out.putBoolean("showingToday",showingToday);',1)
text=text.replace('out.putBoolean("draftAppointment",appointmentField.isChecked());','out.putBoolean("draftAppointment",appointmentField.isChecked());\n            out.putString("draftRecurrence",selectedRecurrence());\n            out.putInt("draftPriority",selectedPriority());',1)

old_colors='''        if (todoTab==null || doneTab==null || addButton==null) return;\n        styleTab(todoTab,todoColor(),!showingDone);\n        styleTab(doneTab,doneColor(),showingDone);'''
new_colors='''        if (todayTab==null || todoTab==null || doneTab==null || addButton==null) return;\n        styleTab(todayTab,YELLOW,showingToday);\n        styleTab(todoTab,todoColor(),!showingDone && !showingToday);\n        styleTab(doneTab,doneColor(),showingDone);'''
if old_colors not in text: raise SystemExit("Couleurs onglets introuvables")
text=text.replace(old_colors,new_colors,1)
main.write_text(text,encoding="utf-8")

# 4) Version 3.2. Les règles de sauvegarde Android déjà présentes sont conservées.
gradle=GRADLE.read_text(encoding="utf-8")
gradle=re.sub(r"versionCode\s+\d+","versionCode 101",gradle,count=1)
gradle=re.sub(r"versionName\s+'[^']+'","versionName '3.2-recurrence-priorities'",gradle,count=1)
GRADLE.write_text(gradle,encoding="utf-8")

print("Mes tâches Manu 3.2 : récurrence, Aujourd’hui, priorités et actions notification appliqués")
