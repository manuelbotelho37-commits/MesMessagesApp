from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

# Galaxy Z Fold : toujours utiliser la vue compacte en deux colonnes,
# aussi bien sur l'ecran interieur que sur l'ecran de couverture,
# en portrait comme en paysage.
text = MAIN.read_text(encoding="utf-8")
old = "wideLayout=getResources().getConfiguration().screenWidthDp>=700;"
new = "wideLayout=true;"
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
        raise SystemExit("Impossible de trouver le reglage de mise en page dans MainActivity.java")
MAIN.write_text(text, encoding="utf-8")

# Nouvelle version installable par-dessus la 3.3 avec la meme cle de signature.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 103", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.4-two-pane-all-screens'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Mes taches Manu 3.4 : vue deux colonnes forcee sur tous les ecrans du Fold")
