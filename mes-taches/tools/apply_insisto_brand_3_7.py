from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MANIFEST = APP / "src" / "main" / "AndroidManifest.xml"
GRADLE = APP / "build.gradle"
ICON = APP / "src" / "main" / "res" / "drawable" / "ic_insisto.jpg"

if not ICON.exists():
    raise SystemExit("Logo Insisto introuvable")

# Nom visible et icône : on garde le même package Android pour mettre à jour
# Mes tâches Manu au lieu de créer une seconde application.
manifest = MANIFEST.read_text(encoding="utf-8")
manifest = manifest.replace('android:icon="@drawable/ic_app"', 'android:icon="@drawable/ic_insisto"')
manifest = manifest.replace('android:roundIcon="@drawable/ic_app"', 'android:roundIcon="@drawable/ic_insisto"')
manifest = manifest.replace('android:label="Mes tâches Manu"', 'android:label="Insisto"')
MANIFEST.write_text(manifest, encoding="utf-8")

# Remplacer le nom visible dans les écrans sans toucher à l'identifiant package.
for java_file in (APP / "src" / "main" / "java").rglob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    if "Mes tâches Manu" in text:
        java_file.write_text(text.replace("Mes tâches Manu", "Insisto"), encoding="utf-8")

# Version installable par-dessus la 3.6 avec la même signature.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 106", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.7-insisto-brand'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.7 : nom et logo appliqués à Mes tâches Manu sans changer le package Android")
