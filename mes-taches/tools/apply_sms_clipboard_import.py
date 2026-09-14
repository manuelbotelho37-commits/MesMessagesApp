from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DETAIL=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"/"TaskDetailActivity.java"

text=DETAIL.read_text(encoding="utf-8")

old=r'''    private void openSmsConversation(String number) {
        if (number==null || number.trim().isEmpty()) return;
        getSharedPreferences("share_bridge",MODE_PRIVATE).edit()
                .putLong("pending_sms_task",taskId)
                .putLong("pending_sms_at",System.currentTimeMillis())
                .apply();
        try {
            Intent smsIntent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number.trim())));
            startActivity(smsIntent);
            Toast.makeText(this,"Dans Messages : appui long sur le SMS → Partager → Mes tâches Manu. Il sera ajouté directement à cette tâche.",Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application Messages n’est disponible.",Toast.LENGTH_LONG).show();
        }
    }
'''
new=r'''    private void openSmsConversation(String number) {
        if (number==null || number.trim().isEmpty()) return;
        String before="";
        try {
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard!=null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip()!=null
                    && clipboard.getPrimaryClip().getItemCount()>0) {
                CharSequence old=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (old!=null) before=old.toString();
            }
        } catch (RuntimeException ignored) {}
        getSharedPreferences("share_bridge",MODE_PRIVATE).edit()
                .putLong("pending_sms_task",taskId)
                .putLong("pending_sms_at",System.currentTimeMillis())
                .putString("pending_sms_number",number.trim())
                .putString("pending_sms_clip_before",before)
                .apply();
        try {
            Intent smsIntent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number.trim())));
            startActivity(smsIntent);
            Toast.makeText(this,"Dans Messages : appui long sur le SMS → Copier → reviens dans Mes tâches Manu.",Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application Messages n’est disponible.",Toast.LENGTH_LONG).show();
        }
    }
'''
if old not in text:
    raise SystemExit("Bloc openSmsConversation final introuvable")
text=text.replace(old,new,1)

old_resume=r'''    @Override protected void onResume() {
        super.onResume();
        if (store!=null && taskId!=0) refresh();
    }
'''
new_resume=r'''    @Override protected void onResume() {
        super.onResume();
        if (store!=null && taskId!=0) refresh();
        checkCopiedSms();
    }

    private void checkCopiedSms() {
        if (taskId==0 || store==null) return;
        android.content.SharedPreferences prefs=getSharedPreferences("share_bridge",MODE_PRIVATE);
        long pendingTask=prefs.getLong("pending_sms_task",0);
        long pendingAt=prefs.getLong("pending_sms_at",0);
        if (pendingTask!=taskId || pendingAt==0) return;
        if (System.currentTimeMillis()-pendingAt>15L*60L*1000L) {
            clearPendingSms(prefs);
            return;
        }

        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard==null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip()==null
                || clipboard.getPrimaryClip().getItemCount()==0) return;
        CharSequence copied=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (copied==null) return;
        String sms=copied.toString().trim();
        if (sms.isEmpty()) return;
        String before=prefs.getString("pending_sms_clip_before","");
        if (sms.equals(before)) return;
        String number=prefs.getString("pending_sms_number","");

        clearPendingSms(prefs);
        String preview=sms.length()>240?sms.substring(0,240)+"…":sms;
        new AlertDialog.Builder(this)
                .setTitle("Ajouter ce SMS à la tâche ?")
                .setMessage(preview)
                .setNegativeButton("Non",null)
                .setPositiveButton("Ajouter",(d,w)->{
                    String when=new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm",FR)
                            .format(new java.util.Date());
                    String value="SMS copié le "+when;
                    if (number!=null && !number.isEmpty()) value+="\nContact : "+number;
                    value+="\n\n"+sms;
                    store.addAttachment(taskId,"sms","SMS copié",value,"text/plain");
                    refresh();
                    Toast.makeText(this,"SMS ajouté à la tâche.",Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void clearPendingSms(android.content.SharedPreferences prefs) {
        prefs.edit()
                .remove("pending_sms_task")
                .remove("pending_sms_at")
                .remove("pending_sms_number")
                .remove("pending_sms_clip_before")
                .apply();
    }
'''
if old_resume not in text:
    raise SystemExit("Bloc onResume final introuvable")
text=text.replace(old_resume,new_resume,1)

DETAIL.write_text(text,encoding="utf-8")
print("Import SMS depuis le presse-papiers appliqué")
