from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

text = MAIN.read_text(encoding="utf-8")

def replace_required(old, new, label):
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch contraste introuvable: {label}")
    text = text.replace(old, new, 1)

# Les cartes peuvent garder une couleur claire personnalisée (ex. rose clair)
# même quand l'interface passe en mode sombre. Le texte doit donc dépendre
# de la couleur réelle de la carte, pas de la palette globale sombre.
replace_required(
    'TextView label=text(task.title,wideLayout?16:19,task.done?MUTED:INK,true);',
    'TextView label=text(task.title,wideLayout?16:19,task.done?MUTED:taskCardTextColor(),true);',
    "titre carte large",
)
replace_required(
    'TextView label=text(task.title,14,task.done?MUTED:INK,true);',
    'TextView label=text(task.title,14,task.done?MUTED:taskCardTextColor(),true);',
    "titre carte compacte",
)

# Dates, type et récurrence : même contraste que le titre sur les cartes actives.
replace_required(
    'TextView time=text(when,wideLayout?14:17,overdue?RED:accent,true);',
    'TextView time=text(when,wideLayout?14:17,overdue?RED:(task.done?MUTED:taskCardTextColor()),true);',
    "date carte large",
)
replace_required(
    'TextView time=text(when,11,overdue?RED:(task.done?MUTED:todoColor()),true);',
    'TextView time=text(when,11,overdue?RED:(task.done?MUTED:taskCardTextColor()),true);',
    "date carte compacte",
)
replace_required(
    'TextView kind=text((task.appointment?"Rendez-vous":"Tâche")+(overdue?" · En retard":""),15,overdue?RED:MUTED,false);',
    'TextView kind=text((task.appointment?"Rendez-vous":"Tâche")+(overdue?" · En retard":""),15,overdue?RED:(task.done?MUTED:taskCardTextColor()),false);',
    "type de tâche",
)
replace_required(
    'TextView repeat=text("↻ "+recurrenceLabel(task.recurrence),14,MUTED,true);',
    'TextView repeat=text("↻ "+recurrenceLabel(task.recurrence),14,task.done?MUTED:taskCardTextColor(),true);',
    "récurrence",
)

# Boutons de priorité et Pause : contraste calculé sur leur propre fond.
replace_required(
    'priority.setTextColor(level==0?color:WHITE);',
    'priority.setTextColor(level==0?contrastText(WHITE):contrastText(color));',
    "bouton priorité",
)
replace_required(
    'pause.setTextColor(task.paused?WHITE:ORANGE);',
    'pause.setTextColor(task.paused?contrastText(ORANGE):contrastText(WHITE));',
    "bouton pause",
)

# Helper partagé par les cartes.
anchor = '    private int taskBackgroundColor() { return prefs==null?WHITE:prefs.getInt(PREF_TASK_BG,WHITE); }'
helper = anchor + '\n    private int taskCardTextColor() { return contrastText(taskBackgroundColor()); }'
if 'private int taskCardTextColor()' not in text:
    if anchor not in text:
        raise SystemExit("Lecture du fond des tâches introuvable")
    text = text.replace(anchor, helper, 1)

MAIN.write_text(text, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 114", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.9.4-dark-contrast'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.9.4 : contraste des cartes corrigé en mode sombre")
