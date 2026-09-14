from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"


def replace_once(path: Path, old: str, new: str, label: str):
    text=path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old,new,1),encoding="utf-8")

# 1) Etat PAUSE persistant dans la base.
store=JAVA/"TaskStore.java"
replace_once(store,
    'final boolean appointment, done, urgent;',
    'final boolean appointment, done, urgent, paused;',
    'champ paused')
replace_once(store,
    'Task(long id, String title, long dueAt, boolean appointment, boolean done, boolean urgent) {\n            this.id=id; this.title=title; this.dueAt=dueAt;\n            this.appointment=appointment; this.done=done; this.urgent=urgent;\n        }',
    'Task(long id, String title, long dueAt, boolean appointment, boolean done, boolean urgent, boolean paused) {\n            this.id=id; this.title=title; this.dueAt=dueAt;\n            this.appointment=appointment; this.done=done; this.urgent=urgent; this.paused=paused;\n        }',
    'constructeur paused')
replace_once(store,
    'super(context, NAME, null, 3);',
    'super(context, NAME, null, 4);',
    'version base paused')
replace_once(store,
    'db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, urgent INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");',
    'db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, urgent INTEGER NOT NULL DEFAULT 0, paused INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");',
    'colonne paused creation')
replace_once(store,
    'if (oldVersion < 2) createAttachments(db);\n        if (oldVersion < 3) db.execSQL("ALTER TABLE tasks ADD COLUMN urgent INTEGER NOT NULL DEFAULT 0");',
    'if (oldVersion < 2) createAttachments(db);\n        if (oldVersion < 3) db.execSQL("ALTER TABLE tasks ADD COLUMN urgent INTEGER NOT NULL DEFAULT 0");\n        if (oldVersion < 4) db.execSQL("ALTER TABLE tasks ADD COLUMN paused INTEGER NOT NULL DEFAULT 0");',
    'migration paused')
replace_once(store,
    'return new Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)==1, c.getInt(4)==1, c.getInt(5)==1);',
    'return new Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)==1, c.getInt(4)==1, c.getInt(5)==1, c.getInt(6)==1);',
    'lecture paused')
replace_once(store,
    'new String[]{"id","title","due_at","appointment","done","urgent"},\n                "done=?", new String[]{done?"1":"0"}, null, null,',
    'new String[]{"id","title","due_at","appointment","done","urgent","paused"},\n                "done=?", new String[]{done?"1":"0"}, null, null,',
    'liste paused')
replace_once(store,
    'new String[]{"id","title","due_at","appointment","done","urgent"},\n                "id=?", new String[]{Long.toString(id)}, null, null, null)',
    'new String[]{"id","title","due_at","appointment","done","urgent","paused"},\n                "id=?", new String[]{Long.toString(id)}, null, null, null)',
    'get paused')
replace_once(store,
    '    void delete(long id) {',
    '''    void setPaused(long id, boolean paused) {\n        ContentValues values=new ContentValues();\n        values.put("paused",paused?1:0); values.put("updated_at",System.currentTimeMillis());\n        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)\n            throw new IllegalStateException("Cette tâche n’existe plus.");\n        if (paused) {\n            try { ReminderScheduler.cancel(appContext,id); } catch (RuntimeException ignored) {}\n        } else {\n            syncReminder(id);\n        }\n    }\n\n    void delete(long id) {''',
    'setter paused')

# 2) Aucun rappel pour une tâche en pause.
scheduler=JAVA/"ReminderScheduler.java"
replace_once(scheduler,
    'if (task.done) {\n            cancelNotification(context,task.id);\n            return;\n        }',
    'if (task.done || task.paused) {\n            cancelNotification(context,task.id);\n            return;\n        }',
    'sync pause')
replace_once(scheduler,
    'if (task==null || task.done) return;\n        long hour=60L*60L*1000L;',
    'if (task==null || task.done || task.paused) return;\n        long hour=60L*60L*1000L;',
    'horaire pause')

receiver=JAVA/"ReminderReceiver.java"
text=receiver.read_text(encoding="utf-8")
text=text.replace('if (task==null || task.done) return;','if (task==null || task.done || task.paused) return;')
receiver.write_text(text,encoding="utf-8")

# 3) Petit bouton Pause à côté d'Urgent dans la liste.
main=JAVA/"MainActivity.java"
replace_once(main,
    '''            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);\n            urgentParams.topMargin=dp(7); details.addView(urgent,urgentParams);\n            Button dossier=button(attachmentCount==0?"📎  Dossier":"📎  Dossier ("+attachmentCount+")",false);''',
    '''            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);\n            urgentParams.topMargin=dp(7); details.addView(urgent,urgentParams);\n            Button pause=pauseButton(task);\n            LinearLayout.LayoutParams pauseParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);\n            pauseParams.topMargin=dp(7); details.addView(pause,pauseParams);\n            Button dossier=button(attachmentCount==0?"📎  Dossier":"📎  Dossier ("+attachmentCount+")",false);''',
    'pause mobile')
replace_once(main,
    '''            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));\n            urgentParams.setMarginEnd(dp(6)); row.addView(urgent,urgentParams);\n            Button dossier=button(attachmentCount==0?"📎":"📎 "+attachmentCount,false);''',
    '''            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));\n            urgentParams.setMarginEnd(dp(6)); row.addView(urgent,urgentParams);\n            Button pause=pauseButton(task);\n            LinearLayout.LayoutParams pauseParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));\n            pauseParams.setMarginEnd(dp(6)); row.addView(pause,pauseParams);\n            Button dossier=button(attachmentCount==0?"📎":"📎 "+attachmentCount,false);''',
    'pause large')
replace_once(main,
    '    private void openEditor(TaskStore.Task task,Bundle saved) {',
    '''    private Button pauseButton(TaskStore.Task task) {\n        Button pause=button(task.paused?"⏸ PAUSE":"Pause",false);\n        pause.setTextSize(wideLayout?12:13);\n        pause.setMinHeight(dp(38)); pause.setMinimumHeight(dp(38));\n        pause.setPadding(dp(10),dp(4),dp(10),dp(4));\n        pause.setTextColor(task.paused?WHITE:ORANGE);\n        pause.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22f28c28),\n                shape(task.paused?ORANGE:WHITE,18,ORANGE),null));\n        pause.setContentDescription(task.paused?"Reprendre les rappels de cette tâche":"Mettre les rappels de cette tâche en pause");\n        pause.setOnClickListener(v->{\n            try { store.setPaused(task.id,!task.paused); refresh(); }\n            catch (RuntimeException ex) { error(); }\n        });\n        return pause;\n    }\n\n    private void openEditor(TaskStore.Task task,Bundle saved) {''',
    'helper pause')

# 4) Bouton Pause directement dans la fenêtre d'alerte.
alert=JAVA/"ReminderAlertActivity.java"
replace_once(alert,
    'if (!task.done && task.dueAt<=now) pending.add(task);',
    'if (!task.done && !task.paused && task.dueAt<=now) pending.add(task);',
    'filtre alertes pause')
replace_once(alert,
    '''        Button done=button("✓",ORANGE,WHITE,44);\n        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(52),dp(44));\n        dpv.setMarginStart(dp(8));\n        row.addView(done,dpv);''',
    '''        Button pause=button("⏸",WHITE,ORANGE,44);\n        LinearLayout.LayoutParams ppv=new LinearLayout.LayoutParams(dp(58),dp(44));\n        ppv.setMarginStart(dp(8));\n        row.addView(pause,ppv);\n        pause.setContentDescription("Mettre "+task.title+" en pause");\n        pause.setOnClickListener(v->{\n            try {\n                store.setPaused(task.id,true);\n                Toast.makeText(this,"Rappels en pause",Toast.LENGTH_SHORT).show();\n            } catch (RuntimeException ignored) {}\n            build();\n        });\n\n        Button done=button("✓",ORANGE,WHITE,44);\n        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(52),dp(44));\n        dpv.setMarginStart(dp(8));\n        row.addView(done,dpv);''',
    'bouton pause alerte')

print("Bouton Pause rappels appliqué")
