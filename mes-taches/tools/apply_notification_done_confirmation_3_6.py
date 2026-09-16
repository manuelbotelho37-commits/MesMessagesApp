from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"
MAIN = JAVA / "MainActivity.java"
ALERT = JAVA / "ReminderAlertActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

# 1) Confirmation obligatoire avant de marquer une tâche comme terminée
# depuis la grande alerte de rappel (bouton ✓ / Fait).
text = ALERT.read_text(encoding="utf-8")
if "import android.app.AlertDialog;" not in text:
    text = text.replace("import android.app.Activity;", "import android.app.Activity;\nimport android.app.AlertDialog;", 1)

old_listener = '''        done.setOnClickListener(v->{
            try {
                store.setDone(task.id,true);
                ReminderScheduler.cancel(this,task.id);
                Toast.makeText(this,"Tâche terminée",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {}
            build();
        });'''
new_listener = '''        done.setOnClickListener(v->{
            new AlertDialog.Builder(this)
                    .setTitle("Terminer cette tâche ?")
                    .setMessage("Êtes-vous sûr de vouloir terminer « "+task.title+" » ?\\n\\nElle sera déplacée dans Tâches terminées.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Oui, terminer",(dialog,which)->{
                        try {
                            store.setDone(task.id,true);
                            ReminderScheduler.cancel(this,task.id);
                            Toast.makeText(this,"Tâche terminée",Toast.LENGTH_SHORT).show();
                        } catch (RuntimeException ignored) {}
                        build();
                    })
                    .show();
        });'''
if new_listener not in text:
    if old_listener not in text:
        raise SystemExit("Bouton Fait de l'alerte introuvable")
    text = text.replace(old_listener, new_listener, 1)
ALERT.write_text(text, encoding="utf-8")

# 2) Même sécurité sur la vue compacte de l'écran extérieur du Fold.
text = MAIN.read_text(encoding="utf-8")
old_compact = '''        check.setOnCheckedChangeListener((button,checked)->{
            try { store.setDone(task.id,checked); refresh(); }
            catch (RuntimeException ex) { refresh(); error(); }
        });'''
new_compact = '''        check.setOnCheckedChangeListener((button,checked)->{
            if (!task.done && checked) {
                new AlertDialog.Builder(this)
                        .setTitle("Terminer cette tâche ?")
                        .setMessage("Êtes-vous sûr de vouloir terminer « "+task.title+" » ?\\n\\nElle sera déplacée dans Tâches terminées.")
                        .setNegativeButton("Annuler",(dialog,which)->button.setChecked(false))
                        .setPositiveButton("Oui, terminer",(dialog,which)->{
                            try { store.setDone(task.id,true); refresh(); }
                            catch (RuntimeException ex) { button.setChecked(false); refresh(); error(); }
                        })
                        .setOnCancelListener(dialog->button.setChecked(false))
                        .show();
                return;
            }
            try { store.setDone(task.id,checked); refresh(); }
            catch (RuntimeException ex) { refresh(); error(); }
        });'''
# Le bloc compact est ajouté par le patch Fold juste avant celui-ci.
# Remplacer seulement la dernière occurrence si le bloc de confirmation principal existe déjà.
if new_compact not in text:
    idx = text.rfind(old_compact)
    if idx == -1:
        raise SystemExit("Case à cocher compacte introuvable")
    text = text[:idx] + new_compact + text[idx+len(old_compact):]
MAIN.write_text(text, encoding="utf-8")

# 3) Version 3.6 installable par-dessus la 3.5 avec la même signature.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 105", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.6-confirm-done-alert'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Mes tâches Manu 3.6 : confirmation avant Fait ajoutée aux rappels et à la vue compacte")
