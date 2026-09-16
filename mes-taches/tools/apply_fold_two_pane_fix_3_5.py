from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"

text = MAIN.read_text(encoding="utf-8")
old = '''        CheckBox check=new CheckBox(this); check.setChecked(task.done);\n        check.setButtonTintList(ColorStateList.valueOf(task.done?doneColor():todoColor()));\n        check.setContentDescription((task.done?"Remettre à faire : ":"Terminer : ")+task.title);\n        top.addView(check,new LinearLayout.LayoutParams(dp(40),dp(46)));\n        check.setOnCheckedChangeListener((button,checked)->{\n            try { store.setDone(task.id,checked); refresh(); }\n            catch (RuntimeException ex) { refresh(); error(); }\n        });'''
new = '''        CheckBox check=new CheckBox(this); check.setChecked(task.done);\n        check.setTag("check:"+task.id);\n        check.setButtonTintList(ColorStateList.valueOf(task.done?doneColor():todoColor()));\n        check.setContentDescription((task.done?"Remettre à faire : ":"Terminer : ")+task.title);\n        top.addView(check,new LinearLayout.LayoutParams(dp(40),dp(46)));\n        check.setOnCheckedChangeListener((button,checked)->{\n            if (!task.done && checked) {\n                new AlertDialog.Builder(this)\n                        .setTitle("Terminer cette tâche ?")\n                        .setMessage("Êtes-vous sûr de vouloir terminer « "+task.title+" » ?\\n\\nElle sera déplacée dans Tâches terminées.")\n                        .setNegativeButton("Annuler",(dialog,which)->button.setChecked(false))\n                        .setPositiveButton("Oui, terminer",(dialog,which)->{\n                            try { store.setDone(task.id,true); refresh(); }\n                            catch (RuntimeException ex) { button.setChecked(false); refresh(); error(); }\n                        })\n                        .setOnCancelListener(dialog->button.setChecked(false))\n                        .show();\n                return;\n            }\n            try { store.setDone(task.id,checked); refresh(); }\n            catch (RuntimeException ex) { refresh(); error(); }\n        });'''
if old not in text:
    raise SystemExit("Bloc case compacte introuvable")
text = text.replace(old, new, 1)
MAIN.write_text(text, encoding="utf-8")
print("Correctif 3.5 compact : case cochée + confirmation restaurées")
