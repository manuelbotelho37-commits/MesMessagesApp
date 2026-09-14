from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "TaskDetailActivity.java"
text = p.read_text(encoding="utf-8")

start = text.find('    private void importLastSmsFromContact(Uri uri) {')
end = text.find('    private boolean samePhone(', start)
if start < 0 or end < 0:
    raise SystemExit("Bloc importLastSmsFromContact introuvable")

new_block = r'''    private void importLastSmsFromContact(Uri uri) {
        String name="Contact";
        String selectedNumber=null;
        long contactId=-1;
        java.util.ArrayList<String> numbers=new java.util.ArrayList<>();

        try (Cursor c=getContentResolver().query(uri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID},
                null,null,null)) {
            if (c!=null && c.moveToFirst()) {
                if (c.getString(0)!=null && !c.getString(0).trim().isEmpty()) name=c.getString(0).trim();
                selectedNumber=c.getString(1);
                contactId=c.getLong(2);
                if (selectedNumber!=null && !selectedNumber.trim().isEmpty()) numbers.add(selectedNumber.trim());
            }
        } catch (RuntimeException ignored) {}

        if (contactId>=0) {
            try (Cursor phones=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID+"=?",
                    new String[]{String.valueOf(contactId)},null)) {
                if (phones!=null) {
                    while (phones.moveToNext()) {
                        String n=phones.getString(0);
                        if (n==null || n.trim().isEmpty()) continue;
                        boolean exists=false;
                        for (String old:numbers) if (samePhone(old,n)) { exists=true; break; }
                        if (!exists) numbers.add(n.trim());
                    }
                }
            } catch (RuntimeException ignored) {}
        }

        if (numbers.isEmpty()) {
            Toast.makeText(this,"Je n’ai pas trouvé le numéro de ce contact.",Toast.LENGTH_LONG).show();
            return;
        }

        String conversationNumber=selectedNumber!=null?selectedNumber:numbers.get(0);
        boolean imported=false;
        try (Cursor sms=getContentResolver().query(Uri.parse("content://sms/inbox"),
                new String[]{"address","body","date"},null,null,"date DESC")) {
            if (sms!=null) {
                while (sms.moveToNext()) {
                    String address=sms.getString(0);
                    boolean match=false;
                    for (String n:numbers) {
                        if (samePhone(n,address)) { match=true; break; }
                    }
                    if (!match) continue;
                    String body=sms.getString(1)==null?"":sms.getString(1);
                    long date=sms.getLong(2);
                    String when=new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm",FR).format(new java.util.Date(date));
                    String value="Reçu le "+when+"\nDe : "+name+" · "+address+"\n\n"+body;
                    store.addAttachment(taskId,"sms","Dernier SMS reçu · "+name,value,"text/plain");
                    refresh();
                    conversationNumber=address;
                    imported=true;
                    Toast.makeText(this,"Dernier SMS de "+name+" ajouté à la tâche.",Toast.LENGTH_LONG).show();
                    break;
                }
            }
        } catch (SecurityException denied) {
            Toast.makeText(this,"J’ouvre directement la conversation. Android ne permet pas de lire ce message.",Toast.LENGTH_LONG).show();
        } catch (RuntimeException ignored) {}

        if (!imported) {
            Toast.makeText(this,"J’ouvre la conversation de "+name+". Si c’est un message RCS/Chat, Android ne permet pas de l’importer automatiquement.",Toast.LENGTH_LONG).show();
        }
        openSmsConversation(conversationNumber);
    }

    private void openSmsConversation(String number) {
        if (number==null || number.trim().isEmpty()) return;
        try {
            Intent smsIntent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number.trim())));
            startActivity(smsIntent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application Messages n’est disponible.",Toast.LENGTH_LONG).show();
        }
    }

'''

text = text[:start] + new_block + text[end:]
p.write_text(text, encoding="utf-8")
print("Correctif contact SMS appliqué")
