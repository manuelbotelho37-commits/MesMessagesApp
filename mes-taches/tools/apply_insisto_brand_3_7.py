from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MANIFEST = APP / "src" / "main" / "AndroidManifest.xml"
GRADLE = APP / "build.gradle"
ICON = APP / "src" / "main" / "res" / "drawable" / "ic_insisto.jpg"

if not ICON.exists():
    raise SystemExit("Logo Insisto introuvable")

# Conserver le même package Android, mais utiliser la vraie icône adaptative Samsung/Android.
manifest = MANIFEST.read_text(encoding="utf-8")
manifest = manifest.replace('android:icon="@drawable/ic_app"', 'android:icon="@mipmap/ic_launcher"')
manifest = manifest.replace('android:roundIcon="@drawable/ic_app"', 'android:roundIcon="@mipmap/ic_launcher_round"')
manifest = manifest.replace('android:icon="@drawable/ic_insisto"', 'android:icon="@mipmap/ic_launcher"')
manifest = manifest.replace('android:roundIcon="@drawable/ic_insisto"', 'android:roundIcon="@mipmap/ic_launcher_round"')
manifest = manifest.replace('android:label="Mes tâches Manu"', 'android:label="Insisto"')
MANIFEST.write_text(manifest, encoding="utf-8")

for java_file in (APP / "src" / "main" / "java").rglob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    if "Mes tâches Manu" in text:
        java_file.write_text(text.replace("Mes tâches Manu", "Insisto"), encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 107", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.7.1-insisto-icon'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.7.1 : icône adaptative et nom appliqués sans changer le package Android")
