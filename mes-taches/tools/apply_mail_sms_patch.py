from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Autorisation de lire le dernier SMS reçu après accord explicite de l'utilisateur.
replace_once(
    MANIFEST,
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>',
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>\n    <uses-permission android:name="android.permission.READ_SMS"/>',
    'permission READ_SMS',
)

# Dossier de tâche : un seul bouton Mail + un bouton SMS qui importe uniquement le dernier reçu.
detail = JAVA / "TaskDetailActivity.java"
replace_once(detail,
    'import android.app.Activity;\nimport android.app.AlertDialog;',
    'import android.app.Activity;\nimport android.app.AlertDialog;\nimport android.Manifest;',
    'import Manifest')
replace_once(detail,
    'import android.content.Intent;\nimport android.database.Cursor;',
    'import android.content.Intent;\nimport android.content.pm.PackageManager;\nimport android.database.Cursor;',
    'import PackageManager')
replace_once(detail,
    'import android.provider.OpenableColumns;\nimport android.text.InputType;',
    'import android.provider.OpenableColumns;\nimport android.telephony.PhoneNumberUtils;\nimport android.text.InputType;',
    'import PhoneNumberUtils')
replace_once(detail,
    'private static final int PICK_PHOTO=6001, PICK_FILE=6002, PICK_MAIL_FILE=6003, PICK_CONTACT=6004;',
    'private static final int PICK_PHOTO=6001, PICK_FILE=6002, PICK_MAIL_FILE=6003, PICK_CONTACT=6004, PICK_SMS_CONTACT=6005, REQUEST_READ_SMS=7001;',
    'codes SMS')

replace_once(detail,
    '                "✉️ Mail reçu - coller le texte",\n                "📨 Mail enregistré (.eml/.msg)",',
    '                "✉️ Importer un mail - Gmail / Outlook",\n                "💬 Texto / SMS - dernier reçu",',
    'menu mail SMS')
replace_once(detail,
    '                case 3: askText("Mail reçu - texte","Colle ici le texte du mail reçu ou les points importants","mail",true); break;\n                case 4: showMailFileHelp(); break;',
    '                case 3: showImportMail(); break;\n                case 4: importLastSms(); break;',
    'actions mail SMS')

replace_once(detail,
    '    private void showMailFileHelp() {',
    '''    private void showImportMail() {
        String[] options={"Ouvrir Gmail", "Ouvrir Outlook", "Choisir un mail enregistré (.eml/.msg)"};
        new AlertDialog.Builder(this)
                .setTitle("Importer un mail")
                .setMessage("Choisis Gmail ou Outlook, ouvre le mail voulu puis utilise Partager → Mes tâches Manu. S’il est enregistré comme fichier .eml/.msg, choisis la troisième option.")
                .setItems(options,(d,which)->{
                    if (which==0) launchMailApp("com.google.android.gm","https://mail.google.com");
                    else if (which==1) launchMailApp("com.microsoft.office.outlook","https://outlook.office.com/mail/");
                    else showMailFileHelp();
                }).show();
    }

    private void launchMailApp(String packageName,String fallbackUrl) {
        try {
            Intent launch=getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch!=null) startActivity(launch);
            else startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(fallbackUrl)));
            Toast.makeText(this,"Ouvre le mail puis fais Partager → Mes tâches Manu.",Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Cette application mail n’est pas disponible.",Toast.LENGTH_LONG).show();
        }
    }

    private void importLastSms() {
        if (Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_SMS},REQUEST_READ_SMS);
            return;
        }
        pickSmsContact();
    }

    private void pickSmsContact() {
        try {
            Intent intent=new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent,PICK_SMS_CONTACT);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Le répertoire téléphonique n’est pas disponible.",Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==REQUEST_READ_SMS) {
            if (grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) pickSmsContact();
            else Toast.makeText(this,"Autorise l’accès aux SMS pour importer le dernier message reçu.",Toast.LENGTH_LONG).show();
        }
    }

    private void importLastSmsFromContact(Uri uri) {
        String name="Contact", number=null;
        try (Cursor c=getContentResolver().query(uri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},
                null,null,null)) {
            if (c!=null && c.moveToFirst()) {
                if (c.getString(0)!=null && !c.getString(0).trim().isEmpty()) name=c.getString(0).trim();
                number=c.getString(1);
            }
        } catch (RuntimeException ignored) {}
        if (number==null || number.trim().isEmpty()) {
            Toast.makeText(this,"Je n’ai pas trouvé le numéro de ce contact.",Toast.LENGTH_LONG).show();
            return;
        }
        String wanted=number.trim();
        try (Cursor sms=getContentResolver().query(Uri.parse("content://sms/inbox"),
                new String[]{"address","body","date"},null,null,"date DESC")) {
            if (sms!=null) {
                while (sms.moveToNext()) {
                    String address=sms.getString(0);
                    if (!samePhone(wanted,address)) continue;
                    String body=sms.getString(1)==null?"":sms.getString(1);
                    long date=sms.getLong(2);
                    String when=new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm",FR).format(new java.util.Date(date));
                    String value="Reçu le "+when+"\nDe : "+name+" · "+address+"\n\n"+body;
                    store.addAttachment(taskId,"sms","Dernier SMS reçu · "+name,value,"text/plain");
                    refresh();
                    Toast.makeText(this,"Dernier SMS de "+name+" ajouté à la tâche.",Toast.LENGTH_LONG).show();
                    return;
                }
            }
        } catch (SecurityException denied) {
            Toast.makeText(this,"Samsung n’autorise pas la lecture des SMS pour cette application.",Toast.LENGTH_LONG).show();
            return;
        } catch (RuntimeException ignored) {}
        Toast.makeText(this,"Aucun SMS reçu trouvé pour "+name+".",Toast.LENGTH_LONG).show();
    }

    private boolean samePhone(String first,String second) {
        if (first==null || second==null) return false;
        String a=PhoneNumberUtils.normalizeNumber(first);
        String b=PhoneNumberUtils.normalizeNumber(second);
        if (a.equals(b)) return true;
        String ad=a.replaceAll("\\D","");
        String bd=b.replaceAll("\\D","");
        if (ad.length()>=9 && bd.length()>=9)
            return ad.substring(ad.length()-9).equals(bd.substring(bd.length()-9));
        return false;
    }

    private void showMailFileHelp() {''',
    'import mail et SMS')

replace_once(detail,
    '        if (requestCode==PICK_CONTACT) {\n            savePickedContact(uri);\n            return;\n        }',
    '        if (requestCode==PICK_SMS_CONTACT) {\n            importLastSmsFromContact(uri);\n            return;\n        }\n        if (requestCode==PICK_CONTACT) {\n            savePickedContact(uri);\n            return;\n        }',
    'résultat contact SMS')

replace_once(detail,
    '            case "mailfile": return "📨"; case "link": return "🌐"; case "photo": return "📷"; case "file": return "📎";',
    '            case "mailfile": return "📨"; case "sms": return "💬"; case "link": return "🌐"; case "photo": return "📷"; case "file": return "📎";',
    'icône SMS')

print("Patch import mail + dernier SMS appliqué")
