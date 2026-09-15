from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle"
text = GRADLE.read_text(encoding="utf-8")
text = re.sub(r"versionCode\s+\d+", "versionCode 100", text, count=1)
text = re.sub(r"versionName\s+'[^']+'", "versionName '3.1.1-confirm-task'", text, count=1)
GRADLE.write_text(text, encoding="utf-8")
print("Version 3.1.1 prête")
