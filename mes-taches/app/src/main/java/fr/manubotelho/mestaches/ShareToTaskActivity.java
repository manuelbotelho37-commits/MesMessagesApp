package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import java.util.Calendar;
import java.util.List;

public final class ShareToTaskActivity extends Activity {
    private TaskStore store;
    private String sharedText, subject;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new TaskStore(this);
        Intent intent=getIntent();
        CharSequence text=intent==null?null:intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence sub=intent==null?null:intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        sharedText=text==null?"":text.toString().trim();
        subject=sub==null?"":sub.toString().trim();
        if (sharedText.isEmpty()) {
            Toast.makeText(this,"Aucun texte à ajouter.",Toast.LENGTH_LONG).show(); finish(); return;
        }
        chooseTask();
    }

    private void chooseTask() {
        List<TaskStore.Task> tasks=store.list(false);
        String[] choices=new String[tasks.size()+1];
        choices[0]="+ Créer une nouvelle tâche";
        for (int i=0;i<tasks.size();i++) choices[i+1]=tasks.get(i).title;
        new AlertDialog.Builder(this).setTitle("Ajouter ce mail à…")
                .setItems(choices,(d,which)->{
                    if (which==0) createTask();
                    else attachTo(tasks.get(which-1).id);
                })
                .setOnCancelListener(d->finish()).show();
    }

    private void createTask() {
        Calendar due=Calendar.getInstance(); due.add(Calendar.HOUR_OF_DAY,1); due.set(Calendar.SECOND,0); due.set(Calendar.MILLISECOND,0);
        String title=subject.isEmpty()?"Répondre au mail client":"Répondre : "+subject;
        if (title.length()>240) title=title.substring(0,240);
        long id=store.save(0,title,due.getTimeInMillis(),false);
        store.addAttachment(id,"mail",subject.isEmpty()?"Mail client":"Mail client · "+subject,sharedText,"text/plain");
        openTask(id);
    }

    private void attachTo(long id) {
        store.addAttachment(id,"mail",subject.isEmpty()?"Mail client":"Mail client · "+subject,sharedText,"text/plain");
        openTask(id);
    }

    private void openTask(long id) {
        startActivity(new Intent(this,TaskDetailActivity.class).putExtra(TaskDetailActivity.EXTRA_TASK_ID,id));
        finish();
    }

    @Override protected void onDestroy() { if (store!=null) store.close(); super.onDestroy(); }
}
