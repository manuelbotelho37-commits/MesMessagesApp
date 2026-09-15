from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"

text = MAIN.read_text(encoding="utf-8")
old = '''        check.setOnCheckedChangeListener((button,checked)->{\n            try { store.setDone(task.id,checked); refresh(); }\n            catch (RuntimeException ex) { refresh(); error(); }\n        });'''
new = '''        check.setOnCheckedChangeListener((button,checked)->{\n            if (!task.done && checked) {\n                new AlertDialog.Builder(this)\n                        .setTitle("Terminer cette tâche ?")\n                        .setMessage("Êtes-vous sûr de vouloir terminer « "+task.title+" » ?\\n\\nElle sera déplacée dans Tâches terminées.")\n                        .setNegativeButton("Annuler",(dialog,which)->button.setChecked(false))\n                        .setPositiveButton("Oui, terminer",(dialog,which)->{\n                            try { store.setDone(task.id,true); refresh(); }\n                            catch (RuntimeException ex) { button.setChecked(false); refresh(); error(); }\n                        })\n                        .setOnCancelListener(dialog->button.setChecked(false))\n                        .show();\n                return;\n            }\n            try { store.setDone(task.id,checked); refresh(); }\n            catch (RuntimeException ex) { refresh(); error(); }\n        });'''

if new in text:
    print("Confirmation de fin de tâche déjà présente")
elif old in text:
    text = text.replace(old, new, 1)
    MAIN.write_text(text, encoding="utf-8")
    print("Confirmation de fin de tâche ajoutée")
else:
    raise SystemExit("Bloc de validation de tâche introuvable")
