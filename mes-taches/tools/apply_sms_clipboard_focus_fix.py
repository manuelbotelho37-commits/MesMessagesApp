from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DETAIL=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"/"TaskDetailActivity.java"
text=DETAIL.read_text(encoding="utf-8")

old=r'''    @Override protected void onResume() {
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

new=r'''    @Override protected void onResume() {
        super.onResume();
        if (store!=null && taskId!=0) refresh();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && taskId!=0 && store!=null) {
            getWindow().getDecorView().postDelayed(() -> checkCopiedSms(false),300L);
        }
    }

    private boolean hasPendingCopiedSms() {
        android.content.SharedPreferences prefs=getSharedPreferences("share_bridge",MODE_PRIVATE);
        long pendingTask=prefs.getLong("pending_sms_task",0);
        long pendingAt=prefs.getLong("pending_sms_at",0);
        return pendingTask==taskId && pendingAt>0
                && System.currentTimeMillis()-pendingAt<=15L*60L*1000L;
    }

    private void checkCopiedSms(boolean force) {
        if (taskId==0 || store==null) return;
        android.content.SharedPreferences prefs=getSharedPreferences("share_bridge",MODE_PRIVATE);
        long pendingTask=prefs.getLong("pending_sms_task",0);
        long pendingAt=prefs.getLong("pending_sms_at",0);
        if (pendingTask!=taskId || pendingAt==0) return;
        if (System.currentTimeMillis()-pendingAt>15L*60L*1000L) {
            clearPendingSms(prefs);
            refresh();
            return;
        }

        String sms="";
        try {
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if (clipboard==null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip()==null
                    || clipboard.getPrimaryClip().getItemCount()==0) {
                if (force) Toast.makeText(this,"Aucun texte copié trouvé. Dans Messages, fais un appui long sur le SMS puis Copier.",Toast.LENGTH_LONG).show();
                return;
            }
            CharSequence copied=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (copied!=null) sms=copied.toString().trim();
        } catch (RuntimeException ex) {
            if (force) Toast.makeText(this,"Je n’ai pas encore accès au SMS copié. Réessaie maintenant avec le bouton.",Toast.LENGTH_LONG).show();
            return;
        }
        if (sms.isEmpty()) {
            if (force) Toast.makeText(this,"Le presse-papiers ne contient pas de SMS.",Toast.LENGTH_LONG).show();
            return;
        }
        String before=prefs.getString("pending_sms_clip_before","");
        if (!force && sms.equals(before)) return;
        String number=prefs.getString("pending_sms_number","");
        final String smsFinal=sms;
        String preview=smsFinal.length()>240?smsFinal.substring(0,240)+"…":smsFinal;
        new AlertDialog.Builder(this)
                .setTitle("Ajouter ce SMS à la tâche ?")
                .setMessage(preview)
                .setNegativeButton("Non",null)
                .setPositiveButton("Ajouter",(d,w)->{
                    String when=new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm",FR)
                            .format(new java.util.Date());
                    String value="SMS copié le "+when;
                    if (number!=null && !number.isEmpty()) value+="\nContact : "+number;
                    value+="\n\n"+smsFinal;
                    store.addAttachment(taskId,"sms","SMS copié",value,"text/plain");
                    clearPendingSms(prefs);
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

if old not in text:
    raise SystemExit("Bloc clipboard SMS attendu introuvable")
text=text.replace(old,new,1)

needle='''        content.addView(add,addParams);\n'''
insert='''        content.addView(add,addParams);\n\n        if (hasPendingCopiedSms()) {\n            Button pasteSms=button("📋  Récupérer le SMS copié",false);\n            pasteSms.setTextSize(17);\n            LinearLayout.LayoutParams ps=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);\n            ps.bottomMargin=dp(14);\n            content.addView(pasteSms,ps);\n            pasteSms.setOnClickListener(v->checkCopiedSms(true));\n        }\n'''
if needle not in text:
    raise SystemExit("Point insertion bouton SMS introuvable")
text=text.replace(needle,insert,1)

DETAIL.write_text(text,encoding="utf-8")
print("Correctif focus presse-papiers SMS appliqué")
