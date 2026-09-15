from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DETAIL=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"/"TaskDetailActivity.java"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

text=DETAIL.read_text(encoding="utf-8")
old='''                case "photo":
                case "file":
                case "mailfile":
                    intent=new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(item.value),item.mimeType==null?"*/*":item.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); break;'''
new='''                case "mailfile":
                    intent=new Intent(this,MailViewerActivity.class)
                            .putExtra(MailViewerActivity.EXTRA_URI,item.value)
                            .putExtra(MailViewerActivity.EXTRA_LABEL,item.label)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); break;
                case "photo":
                case "file":
                    intent=new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(item.value),item.mimeType==null?"*/*":item.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); break;'''
if old not in text:
    raise SystemExit("Bloc openItem mailfile introuvable")
text=text.replace(old,new,1)
text=text.replace('if ("mailfile".equals(item.kind)) return "Appuie pour ouvrir le mail téléchargé";',
                  'if ("mailfile".equals(item.kind)) return "Appuie pour lire le mail";',1)
DETAIL.write_text(text,encoding="utf-8")

manifest=MANIFEST.read_text(encoding="utf-8")
if 'android:name=".MailViewerActivity"' not in manifest:
    anchor='''        <activity
            android:name=".TaskDetailActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize"/>'''
    block=anchor+'''\n\n        <activity
            android:name=".MailViewerActivity"
            android:exported="false"/>'''
    if anchor not in manifest:
        raise SystemExit("Ancre TaskDetailActivity introuvable")
    manifest=manifest.replace(anchor,block,1)
MANIFEST.write_text(manifest,encoding="utf-8")

print("Lecteur de mails propre branché")
