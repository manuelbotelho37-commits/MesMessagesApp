from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MANIFEST = APP / "src" / "main" / "AndroidManifest.xml"
GRADLE = APP / "build.gradle"
ICON = APP / "src" / "main" / "res" / "drawable" / "ic_insisto_logo.jpg"

if not ICON.exists():
    raise SystemExit("Logo Insisto introuvable")

# Utiliser directement le logo final comme icône de l'application.
# On garde exactement le même package Android pour mettre à jour l'appli existante.
manifest = MANIFEST.read_text(encoding="utf-8")
for old in ['@drawable/ic_app', '@drawable/ic_insisto', '@mipmap/ic_launcher', '@mipmap/ic_launcher_round']:
    manifest = manifest.replace(f'android:icon="{old}"', 'android:icon="@drawable/ic_insisto_logo"')
    manifest = manifest.replace(f'android:roundIcon="{old}"', 'android:roundIcon="@drawable/ic_insisto_logo"')
manifest = manifest.replace('android:label="Mes tâches Manu"', 'android:label="Insisto"')
MANIFEST.write_text(manifest, encoding="utf-8")

for java_file in (APP / "src" / "main" / "java").rglob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    if "Mes tâches Manu" in text:
        java_file.write_text(text.replace("Mes tâches Manu", "Insisto"), encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 108", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.7.2-insisto-logo'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.7.2 : logo final direct, même package Android")
