from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

# Galaxy Z Fold : conserver la vue compacte en deux colonnes sur l'écran intérieur,
# y compris quand l'appareil est tenu en portrait. L'écran de couverture reste en
# une colonne pour éviter de rendre l'interface illisible.
text = MAIN.read_text(encoding="utf-8")
old = "wideLayout=getResources().getConfiguration().screenWidthDp>=700;"
new = "wideLayout=getResources().getConfiguration().screenWidthDp>=560;"
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    text, count = re.subn(
        r"wideLayout\s*=\s*getResources\(\)\.getConfiguration\(\)\.screenWidthDp\s*>=\s*\d+\s*;",
        new,
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Impossible de trouver le seuil de mise en page large dans MainActivity.java")
MAIN.write_text(text, encoding="utf-8")

# Nouvelle version installable par-dessus la 3.2 avec la même clé de signature.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 102", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.3-two-pane-fold'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Mes tâches Manu 3.3 : vue deux colonnes activée sur l'écran intérieur du Fold, portrait ou paysage")
