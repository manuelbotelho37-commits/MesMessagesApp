from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"

store = JAVA / "TaskStore.java"
text = store.read_text(encoding="utf-8")
old = '''        title=title==null?"":title.trim();\n        if (title.isEmpty() || title.length()>240)\n            throw new IllegalArgumentException("Indique un texte de 1 à 240 caractères.");'''
new = '''        title=title==null?"":title.trim();\n        if (title.isEmpty())\n            throw new IllegalArgumentException("Indique un texte pour la tâche.");'''
if old in text:
    text = text.replace(old, new, 1)
elif 'title.length()>240' in text or '1 à 240 caractères' in text:
    raise SystemExit("Ancienne limite de texte détectée mais format inattendu")
store.write_text(text, encoding="utf-8")

main = JAVA / "MainActivity.java"
text = main.read_text(encoding="utf-8")
text = text.replace('titleField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});', 'titleField.setFilters(new InputFilter[0]);')
text = text.replace('titleField.setMinLines(2); titleField.setMaxLines(4);', 'titleField.setMinLines(3); titleField.setMaxLines(10);\n        titleField.setVerticalScrollBarEnabled(true);')
main.write_text(text, encoding="utf-8")

print("Texte long des tâches : limite supprimée définitivement")
