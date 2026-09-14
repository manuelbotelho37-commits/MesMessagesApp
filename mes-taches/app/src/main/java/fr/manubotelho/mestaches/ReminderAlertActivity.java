package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Locale;

public final class ReminderAlertActivity extends Activity {
    private static final int BG=0xfff4f7fc, INK=0xff152442, MUTED=0xff526078,
            ORANGE=0xfff28c28, BLUE=0xff174ccb, WHITE=Color.WHITE;
    private long taskId;
    private TaskStore store;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        taskId=getIntent().getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        if (taskId==0) { finish(); return; }
        store=new TaskStore(this);
        TaskStore.Task task=store.get(taskId);
        if (task==null || task.done) { finish(); return; }
        build(task);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        taskId=intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        TaskStore.Task task=store==null?null:store.get(taskId);
        if (task==null || task.done) { finish(); return; }
        build(task);
    }

    private void build(TaskStore.Task task) {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28),dp(34),dp(28),dp(28));
        root.setBackgroundColor(BG);

        TextView badge=text(task.appointment?"RENDEZ-VOUS":"TÂCHE À FAIRE",16,ORANGE,true);
        badge.setGravity(Gravity.CENTER);
        root.addView(badge,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title=text(task.title,32,INK,true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,dp(26),0,dp(18));
        root.addView(title,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        String when=new SimpleDateFormat("EEEE d MMMM 'à' HH:mm",Locale.FRANCE).format(new java.util.Date(task.dueAt));
        TextView date=text(when,19,BLUE,true);
        date.setGravity(Gravity.CENTER);
        root.addView(date,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView help=text("Ce rappel restera dans tes notifications tant que la tâche n’est pas terminée.",16,MUTED,false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(8),dp(26),dp(8),dp(26));
        root.addView(help,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        Button done=button("✓  Marquer comme terminée",ORANGE,WHITE);
        done.setOnClickListener(v->{
            try {
                store.setDone(taskId,true);
                ReminderScheduler.cancelNotification(this,taskId);
                Toast.makeText(this,"Tâche terminée",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {}
            finish();
        });
        root.addView(done,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        Button open=button("Ouvrir Mes tâches Manu",WHITE,INK);
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin=dp(12);
        root.addView(open,op);
        open.setOnClickListener(v->{
            startActivity(new Intent(this,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        Button later=button("Fermer pour l’instant",WHITE,MUTED);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin=dp(8);
        root.addView(later,lp);
        later.setOnClickListener(v->finish());
        setContentView(root);
    }

    private Button button(String label,int fill,int textColor) {
        Button b=new Button(this); b.setText(label); b.setTextSize(18); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(textColor); b.setMinHeight(dp(58));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1),fill==WHITE?0xffdce3ef:fill);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));
        return b;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { if (store!=null) store.close(); super.onDestroy(); }
}
