from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle"
text = GRADLE.read_text(encoding="utf-8")
text = text.replace("versionCode 21", "versionCode 22", 1)
text = text.replace("versionName '3.0-mail-clean'", "versionName '3.1-confirm-task'", 1)
GRADLE.write_text(text, encoding="utf-8")
print("Version 3.1 prête")
