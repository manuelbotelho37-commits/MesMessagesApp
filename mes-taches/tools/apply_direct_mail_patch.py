from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DETAIL=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"/"TaskDetailActivity.java"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

# Manifest: Internet, mail import activity and FileProvider.
manifest=MANIFEST.read_text(encoding="utf-8")
if 'android.permission.INTERNET' not in manifest:
    manifest=manifest.replace('    <uses-permission android:name="android.permission.VIBRATE"/>',
                              '    <uses-permission android:name="android.permission.VIBRATE"/>\n    <uses-permission android:name="android.permission.INTERNET"/>',1)
if 'android:name=".MailImportActivity"' not in manifest:
    anchor='''        <activity\n            android:name=".ShareToTaskActivity"'''
    block='''        <activity\n            android:name=".MailImportActivity"\n            android:exported="false"\n            android:windowSoftInputMode="adjustResize"/>\n\n        <provider\n            android:name="androidx.core.content.FileProvider"\n            android:authorities="${applicationId}.files"\n            android:exported="false"\n            android:grantUriPermissions="true">\n            <meta-data\n                android:name="android.support.FILE_PROVIDER_PATHS"\n                android:resource="@xml/file_paths"/>\n        </provider>\n\n        <activity\n            android:name=".ShareToTaskActivity"'''
    if anchor not in manifest:
        raise SystemExit("Ancre ShareToTaskActivity introuvable")
    manifest=manifest.replace(anchor,block,1)
MANIFEST.write_text(manifest,encoding="utf-8")

# Task dossier: one direct mail screen, no Chrome/share instructions.
text=DETAIL.read_text(encoding="utf-8")
start=text.find('    private void showImportMail() {')
end=text.find('    private void launchMailApp(',start)
if start<0 or end<0:
    raise SystemExit("Bloc showImportMail introuvable")
new='''    private void showImportMail() {\n        Intent intent=new Intent(this,MailImportActivity.class);\n        intent.putExtra(MailImportActivity.EXTRA_TASK_ID,taskId);\n        startActivity(intent);\n    }\n\n'''
text=text[:start]+new+text[end:]

# Refresh the dossier automatically after returning from the mail importer.
if '    @Override protected void onResume() {' not in text:
    marker='    @Override protected void onDestroy() { if (store!=null) store.close(); super.onDestroy(); }'
    resume='''    @Override protected void onResume() {\n        super.onResume();\n        if (store!=null && taskId!=0) refresh();\n    }\n\n'''
    if marker not in text:
        raise SystemExit("onDestroy introuvable")
    text=text.replace(marker,resume+marker,1)
DETAIL.write_text(text,encoding="utf-8")

print("Import direct Gmail/Outlook branché")
