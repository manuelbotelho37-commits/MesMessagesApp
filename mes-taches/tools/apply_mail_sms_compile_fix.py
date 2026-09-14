from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "TaskDetailActivity.java"
text = p.read_text(encoding="utf-8")

bad_value = '''String value="Reçu le "+when+"
De : "+name+" · "+address+"

"+body;'''
good_value = r'''String value="Reçu le "+when+"\nDe : "+name+" · "+address+"\n\n"+body;'''
if bad_value in text:
    text = text.replace(bad_value, good_value, 1)

text = text.replace(r'replaceAll("\D","")', r'replaceAll("\\D","")')

p.write_text(text, encoding="utf-8")
print("Correctif échappement SMS appliqué")
