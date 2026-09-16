from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

text = MAIN.read_text(encoding="utf-8")
old = "wideLayout=getResources().getConfiguration().screenWidthDp>=700;"
new = "wideLayout=getResources().getConfiguration().screenWidthDp>=520;"
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("Seuil de mise en page introuvable dans MainActivity.java")
MAIN.write_text(text, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 102", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.3-two-pane-fold'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Mes tâches Manu 3.3 : affichage deux colonnes activé dès 520 dp pour le Fold ouvert")
