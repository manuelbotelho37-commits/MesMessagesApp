from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DETAIL = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "TaskDetailActivity.java"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    MANIFEST,
    '    <uses-permission android:name="android.permission.READ_SMS"/>',
    '    <uses-permission android:name="android.permission.READ_SMS"/>\n    <uses-feature android:name="android.hardware.telephony" android:required="false"/>',
    'uses-feature telephony facultatif',
)

new_menu = '''    private static final String MENU_PREFS="task_detail_menu";
    private static final String MENU_ORDER="add_order";
    private static final String[] DEFAULT_MENU_KEYS={"check","contact","note","mail","sms","internet","photo","file","phone","email","place"};

    private void showAddMenu() {
        String[] order=getMenuOrder();
        String[] choices=new String[order.length+1];
        for (int i=0;i<order.length;i++) choices[i]=menuLabel(order[i]);
        choices[order.length]="↕  Réorganiser les éléments";
        new AlertDialog.Builder(this).setTitle("Ajouter à cette tâche").setItems(choices,(d,which)->{
            if (which==order.length) { showReorderMenu(); return; }
            runMenuAction(order[which]);
        }).show();
    }

    private String[] getMenuOrder() {
        String saved=getSharedPreferences(MENU_PREFS,MODE_PRIVATE).getString(MENU_ORDER,"");
        if (saved==null || saved.trim().isEmpty()) return DEFAULT_MENU_KEYS.clone();
        String[] parts=saved.split(",");
        if (parts.length!=DEFAULT_MENU_KEYS.length) return DEFAULT_MENU_KEYS.clone();
        for (String key:DEFAULT_MENU_KEYS) {
            boolean found=false;
            for (String part:parts) if (key.equals(part)) { found=true; break; }
            if (!found) return DEFAULT_MENU_KEYS.clone();
        }
        return parts;
    }

    private void saveMenuOrder(String[] order) {
        StringBuilder value=new StringBuilder();
        for (String key:order) {
            if (value.length()>0) value.append(',');
            value.append(key);
        }
        getSharedPreferences(MENU_PREFS,MODE_PRIVATE).edit().putString(MENU_ORDER,value.toString()).apply();
    }

    private String menuLabel(String key) {
        switch(key) {
            case "check": return "✅ Sous-tâche à cocher";
            case "contact": return "👤 Client / contact";
            case "note": return "📝 Note";
            case "mail": return "✉️ Importer un mail - Gmail / Outlook";
            case "sms": return "💬 Texto / SMS - dernier reçu";
            case "internet": return "🌐 Internet / article";
            case "photo": return "📷 Photo - galerie";
            case "file": return "📎 Fichier / document";
            case "phone": return "📞 Téléphone - répertoire";
            case "email": return "📧 Adresse e-mail";
            case "place": return "📍 Lieu - Google Maps";
            default: return key;
        }
    }

    private void runMenuAction(String key) {
        switch(key) {
            case "check": askText("Sous-tâche","Ex. Rappeler le géomètre","check",true); break;
            case "contact": askText("Client / contact","Nom, société, informations utiles","contact",true); break;
            case "note": askText("Note","Écris ce que tu ne veux pas oublier","note",true); break;
            case "mail": showImportMail(); break;
            case "sms": importLastSms(); break;
            case "internet": openInternet(); break;
            case "photo": pickPhoto(); break;
            case "file": pickDocument("file",PICK_FILE,"*/*"); break;
            case "phone": pickContact(); break;
            case "email": askText("Adresse e-mail","client@exemple.fr","email",false); break;
            case "place": openGoogleMaps(); break;
        }
    }

    private void showReorderMenu() {
        final String[] order=getMenuOrder();
        LinearLayout box=column();
        box.setPadding(dp(10),dp(4),dp(10),0);
        for (int i=0;i<order.length;i++) {
            final int index=i;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name=text(menuLabel(order[i]),16,INK,false);
            row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            Button up=button("↑",false); up.setContentDescription("Monter");
            Button down=button("↓",false); down.setContentDescription("Descendre");
            row.addView(up,new LinearLayout.LayoutParams(dp(54),ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(down,new LinearLayout.LayoutParams(dp(54),ViewGroup.LayoutParams.WRAP_CONTENT));
            up.setEnabled(index>0);
            down.setEnabled(index<order.length-1);
            up.setOnClickListener(v->{
                if (index<=0) return;
                String tmp=order[index-1]; order[index-1]=order[index]; order[index]=tmp;
                saveMenuOrder(order);
                ((AlertDialog)box.getTag()).dismiss();
                showReorderMenu();
            });
            down.setOnClickListener(v->{
                if (index>=order.length-1) return;
                String tmp=order[index+1]; order[index+1]=order[index]; order[index]=tmp;
                saveMenuOrder(order);
                ((AlertDialog)box.getTag()).dismiss();
                showReorderMenu();
            });
            box.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Choisis ton ordre")
                .setMessage("Utilise ↑ et ↓. Ton ordre sera gardé pour toutes les tâches.")
                .setView(box)
                .setNeutralButton("Ordre d’origine",(d,w)->getSharedPreferences(MENU_PREFS,MODE_PRIVATE).edit().remove(MENU_ORDER).apply())
                .setPositiveButton("Terminé",null)
                .create();
        box.setTag(dialog);
        dialog.show();
    }

'''

text = DETAIL.read_text(encoding="utf-8")
if 'private static final String MENU_PREFS="task_detail_menu";' not in text:
    start = text.find('    private void showAddMenu() {')
    end = text.find('    private void askText(', start)
    if start < 0 or end < 0:
        raise SystemExit(f"Bloc showAddMenu introuvable dans {DETAIL}")
    text = text[:start] + new_menu + text[end:]
    DETAIL.write_text(text, encoding="utf-8")

print("Patch ordre personnalisable du menu appliqué")
