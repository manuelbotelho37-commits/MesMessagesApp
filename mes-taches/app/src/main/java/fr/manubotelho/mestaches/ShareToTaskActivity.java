package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareToTaskActivity extends Activity {
    private static final Pattern URL=Pattern.compile("https?://\\S+");
    private static final String BRIDGE_PREFS="share_bridge";
    private static final String PENDING_SMS_TASK="pending_sms_task";
    private static final String PENDING_SMS_AT="pending_sms_at";
    private TaskStore store;
    private String sharedText, subject, detectedUrl;
    private boolean sharedLink, sharedMap;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new TaskStore(this);
        Intent intent=getIntent();
        CharSequence text=intent==null?null:intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence sub=intent==null?null:intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        sharedText=text==null?"":text.toString().trim();
        subject=sub==null?"":sub.toString().trim();
        if (sharedText.isEmpty()) {
            Toast.makeText(this,"Aucun contenu à ajouter.",Toast.LENGTH_LONG).show(); finish(); return;
        }

        if (attachPendingSmsIfAny()) return;

        Matcher matcher=URL.matcher(sharedText);
        if (matcher.find()) {
            detectedUrl=matcher.group();
            sharedMap=detectedUrl.contains("maps.app.goo.gl") || detectedUrl.contains("google.com/maps") || detectedUrl.contains("goo.gl/maps");
            sharedLink=sharedMap || sharedText.equals(detectedUrl) || (!subject.isEmpty() && sharedText.length()<detectedUrl.length()+20);
        }
        chooseTask();
    }

    private boolean attachPendingSmsIfAny() {
        SharedPreferences prefs=getSharedPreferences(BRIDGE_PREFS,MODE_PRIVATE);
        long taskId=prefs.getLong(PENDING_SMS_TASK,0);
        long startedAt=prefs.getLong(PENDING_SMS_AT,0);
        if (taskId==0 || startedAt==0 || System.currentTimeMillis()-startedAt>5L*60L*1000L) {
            prefs.edit().remove(PENDING_SMS_TASK).remove(PENDING_SMS_AT).apply();
            return false;
        }
        TaskStore.Task task;
        try { task=store.get(taskId); }
        catch (RuntimeException ex) { task=null; }
        if (task==null || task.done) {
            prefs.edit().remove(PENDING_SMS_TASK).remove(PENDING_SMS_AT).apply();
            return false;
        }
        String label=subject.isEmpty()?"SMS partagé":"SMS · "+subject;
        store.addAttachment(taskId,"sms",label,sharedText,"text/plain");
        prefs.edit().remove(PENDING_SMS_TASK).remove(PENDING_SMS_AT).apply();
        Toast.makeText(this,"SMS ajouté à « "+task.title+" ».",Toast.LENGTH_LONG).show();
        openTask(taskId);
        return true;
    }

    private void chooseTask() {
        List<TaskStore.Task> tasks=store.list(false);
        String[] choices=new String[tasks.size()+1];
        choices[0]="+ Créer une nouvelle tâche";
        for (int i=0;i<tasks.size();i++) choices[i+1]=tasks.get(i).title;
        new AlertDialog.Builder(this).setTitle("Ajouter ce contenu à…")
                .setItems(choices,(d,which)->{
                    if (which==0) createTask();
                    else attachTo(tasks.get(which-1).id);
                })
                .setOnCancelListener(d->finish()).show();
    }

    private void createTask() {
        Calendar due=Calendar.getInstance(); due.add(Calendar.HOUR_OF_DAY,1); due.set(Calendar.SECOND,0); due.set(Calendar.MILLISECOND,0);
        String title;
        if (sharedMap) title=subject.isEmpty()?"Voir le lieu partagé":"Voir : "+subject;
        else if (sharedLink) title=subject.isEmpty()?"Voir l’article partagé":"Voir : "+subject;
        else title=subject.isEmpty()?"Répondre au mail client":"Répondre : "+subject;
        if (title.length()>240) title=title.substring(0,240);
        long id=store.save(0,title,due.getTimeInMillis(),false);
        addSharedAttachment(id);
        openTask(id);
    }

    private void attachTo(long id) {
        addSharedAttachment(id);
        openTask(id);
    }

    private void addSharedAttachment(long id) {
        if (sharedLink && detectedUrl!=null) {
            String label=subject.isEmpty()?(sharedMap?"Lieu Google Maps":"Lien Internet / article"):subject;
            store.addAttachment(id,"link",label,detectedUrl,"text/uri-list");
        } else {
            store.addAttachment(id,"mail",subject.isEmpty()?"Mail client":"Mail client · "+subject,sharedText,"text/plain");
        }
    }

    private void openTask(long id) {
        startActivity(new Intent(this,TaskDetailActivity.class).putExtra(TaskDetailActivity.EXTRA_TASK_ID,id));
        finish();
    }

    @Override protected void onDestroy() { if (store!=null) store.close(); super.onDestroy(); }
}
