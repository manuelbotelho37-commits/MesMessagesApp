from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Stockage persistant du niveau URGENT.
store = JAVA / "TaskStore.java"
replace_once(store,
    'final boolean appointment, done;',
    'final boolean appointment, done, urgent;',
    'champ urgent')
replace_once(store,
    'Task(long id, String title, long dueAt, boolean appointment, boolean done) {\n            this.id=id; this.title=title; this.dueAt=dueAt;\n            this.appointment=appointment; this.done=done;\n        }',
    'Task(long id, String title, long dueAt, boolean appointment, boolean done, boolean urgent) {\n            this.id=id; this.title=title; this.dueAt=dueAt;\n            this.appointment=appointment; this.done=done; this.urgent=urgent;\n        }',
    'constructeur urgent')
replace_once(store,
    'super(context, NAME, null, 2);',
    'super(context, NAME, null, 3);',
    'version base urgent')
replace_once(store,
    'db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");',
    'db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL, appointment INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, urgent INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");',
    'colonne urgent création')
replace_once(store,
    'if (oldVersion < 2) createAttachments(db);',
    'if (oldVersion < 2) createAttachments(db);\n        if (oldVersion < 3) db.execSQL("ALTER TABLE tasks ADD COLUMN urgent INTEGER NOT NULL DEFAULT 0");',
    'migration urgent')
replace_once(store,
    'return new Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)==1, c.getInt(4)==1);',
    'return new Task(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)==1, c.getInt(4)==1, c.getInt(5)==1);',
    'lecture urgent')
replace_once(store,
    'new String[]{"id","title","due_at","appointment","done"},\n                "done=?", new String[]{done?"1":"0"}, null, null,\n                done?"updated_at DESC, id DESC":"due_at ASC, id ASC"',
    'new String[]{"id","title","due_at","appointment","done","urgent"},\n                "done=?", new String[]{done?"1":"0"}, null, null,\n                done?"updated_at DESC, id DESC":"urgent DESC, due_at ASC, id ASC"',
    'tri urgent')
replace_once(store,
    'new String[]{"id","title","due_at","appointment","done"},\n                "id=?", new String[]{Long.toString(id)}, null, null, null)',
    'new String[]{"id","title","due_at","appointment","done","urgent"},\n                "id=?", new String[]{Long.toString(id)}, null, null, null)',
    'get urgent')
replace_once(store,
    '    void delete(long id) {',
    '    void setUrgent(long id, boolean urgent) {\n        ContentValues values=new ContentValues();\n        values.put("urgent",urgent?1:0); values.put("updated_at",System.currentTimeMillis());\n        if (getWritableDatabase().update("tasks",values,"id=?",new String[]{Long.toString(id)})!=1)\n            throw new IllegalStateException("Cette tâche n’existe plus.");\n    }\n\n    void delete(long id) {',
    'setter urgent')

# Pastille URGENT directement dans chaque tâche.
main = JAVA / "MainActivity.java"
replace_once(main,
    '            details.addView(kind);\n            Button dossier=button(attachmentCount==0?"📎  Dossier":"📎  Dossier ("+attachmentCount+")",false);',
    '            details.addView(kind);\n            Button urgent=urgentButton(task);\n            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);\n            urgentParams.topMargin=dp(7); details.addView(urgent,urgentParams);\n            Button dossier=button(attachmentCount==0?"📎  Dossier":"📎  Dossier ("+attachmentCount+")",false);',
    'pastille urgent mobile')
replace_once(main,
    '        if (wideLayout) {\n            Button dossier=button(attachmentCount==0?"📎":"📎 "+attachmentCount,false);',
    '        if (wideLayout) {\n            Button urgent=urgentButton(task);\n            LinearLayout.LayoutParams urgentParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));\n            urgentParams.setMarginEnd(dp(6)); row.addView(urgent,urgentParams);\n            Button dossier=button(attachmentCount==0?"📎":"📎 "+attachmentCount,false);',
    'pastille urgent large')
replace_once(main,
    '    private void openEditor(TaskStore.Task task,Bundle saved) {',
    '    private Button urgentButton(TaskStore.Task task) {\n        Button urgent=button(task.urgent?"⚠ URGENT":"Urgent",false);\n        urgent.setTextSize(wideLayout?12:13);\n        urgent.setMinHeight(dp(38)); urgent.setMinimumHeight(dp(38));\n        urgent.setPadding(dp(10),dp(4),dp(10),dp(4));\n        urgent.setTextColor(task.urgent?WHITE:RED);\n        urgent.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22a53223),\n                shape(task.urgent?RED:WHITE,18,RED),null));\n        urgent.setContentDescription(task.urgent?"Retirer la priorité urgente":"Mettre cette tâche en priorité urgente");\n        urgent.setOnClickListener(v->{\n            try { store.setUrgent(task.id,!task.urgent); refresh(); }\n            catch (RuntimeException ex) { error(); }\n        });\n        return urgent;\n    }\n\n    private void openEditor(TaskStore.Task task,Bundle saved) {',
    'bouton urgent helper')

print("Patch URGENT appliqué")
