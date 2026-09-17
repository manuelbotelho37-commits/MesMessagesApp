from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"
ACTIVITY=JAVA/"ReminderAlertActivity.java"

ACTIVITY.write_text(r'''package fr.manubotelho.mestaches;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReminderAlertActivity extends Activity {
    private static final int INK=0xff152442, MUTED=0xff526078, ORANGE=0xfff28c28,
            BLUE=0xff174ccb, WHITE=Color.WHITE, BORDER=0xffdce3ef, ROW=0xfff7f9fd;
    private TaskStore store;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        configureWindow();
        store=new TaskStore(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store!=null) build();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        build();
    }

    private void configureWindow() {
        Window window=getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT>=27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        setFinishOnTouchOutside(false);
    }

    private void build() {
        if (store==null) return;
        List<TaskStore.Task> pending=new ArrayList<>();
        long now=System.currentTimeMillis();
        try {
            for (TaskStore.Task task:store.list(false)) {
                if (!task.done && !task.paused && task.dueAt<=now) pending.add(task);
            }
        } catch (RuntimeException ignored) {}
        if (pending.isEmpty()) { finish(); return; }

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10),dp(8),dp(10),dp(8));
        panel.setBackground(roundRect(WHITE,16,BORDER));
        panel.setElevation(dp(10));

        TextView header=text("À faire  •  "+pending.size(),16,INK,true);
        header.setPadding(dp(3),0,dp(3),dp(1));
        panel.addView(header,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView rule=text("Sans choix : rappel dans 1 h",11,MUTED,false);
        rule.setPadding(dp(3),0,dp(3),dp(5));
        panel.addView(rule,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (TaskStore.Task task:pending) rows.addView(taskCard(task));
        scroll.addView(rows,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxHeight=(int)(getResources().getDisplayMetrics().heightPixels*0.78f);
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (scroll.getHeight()>maxHeight) {
                ViewGroup.LayoutParams p=scroll.getLayoutParams();
                p.height=maxHeight;
                scroll.setLayoutParams(p);
            }
        });
        panel.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        Button close=button("Voir plus tard",WHITE,MUTED,34,12);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34));
        cp.topMargin=dp(4);
        panel.addView(close,cp);
        close.setOnClickListener(v->finish());

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        pp.setMargins(dp(10),dp(14),dp(10),dp(10));
        root.addView(panel,pp);
        setContentView(root);
    }

    private View taskCard(TaskStore.Task task) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(6),dp(9),dp(6));
        card.setBackground(roundRect(ROW,12,BORDER));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin=dp(5);
        card.setLayoutParams(cp);

        TextView title=text(task.title,14,INK,true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);

        String when=new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new java.util.Date(task.dueAt));
        TextView date=text((task.appointment?"Rendez-vous":"Tâche")+" · "+when,11,BLUE,true);
        date.setPadding(0,0,0,dp(4));
        card.addView(date);

        LinearLayout snoozeRow=new LinearLayout(this);
        snoozeRow.setOrientation(LinearLayout.HORIZONTAL);
        snoozeRow.setGravity(Gravity.CENTER_VERTICAL);
        addSnoozeButton(snoozeRow,task,"3 h",3);
        addSnoozeButton(snoozeRow,task,"5 h",5);
        addSnoozeButton(snoozeRow,task,"10 h",10);
        addSnoozeButton(snoozeRow,task,"24 h",24);
        card.addView(snoozeRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(32)));

        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams arp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34));
        arp.topMargin=dp(4);

        Button pause=button("⏸ Pause",WHITE,ORANGE,34,12);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(34),1f);
        pp.setMarginEnd(dp(3));
        actionRow.addView(pause,pp);
        pause.setOnClickListener(v->{
            try {
                store.setPaused(task.id,true);
                ReminderScheduler.cancel(this,task.id);
                Toast.makeText(this,"Rappels en pause",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {}
            build();
        });

        Button done=button("✓ Fait",ORANGE,WHITE,34,12);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(0,dp(34),1f);
        dpv.setMarginStart(dp(3));
        actionRow.addView(done,dpv);
        done.setOnClickListener(v->{
            try {
                store.setDone(task.id,true);
                ReminderScheduler.cancel(this,task.id);
                Toast.makeText(this,"Tâche terminée",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {}
            build();
        });
        card.addView(actionRow,arp);

        title.setOnClickListener(v->openTask(task));
        date.setOnClickListener(v->openTask(task));
        return card;
    }

    private void addSnoozeButton(LinearLayout row,TaskStore.Task task,String label,int hours) {
        Button b=button(label,WHITE,BLUE,32,11);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(32),1f);
        if (row.getChildCount()>0) p.setMarginStart(dp(3));
        row.addView(b,p);
        b.setOnClickListener(v->snooze(task,hours));
    }

    private void snooze(TaskStore.Task task,int hours) {
        try {
            long when=System.currentTimeMillis()+hours*60L*60L*1000L;
            store.postpone(task.id,when);
            ReminderScheduler.cancelNotification(this,task.id);
            Toast.makeText(this,"Nouveau rappel dans "+hours+" h",Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ignored) {}
        build();
    }

    private void openTask(TaskStore.Task task) {
        startActivity(new Intent(this,TaskDetailActivity.class)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private Button button(String label,int fill,int textColor,int minHeight,float textSize) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(textSize);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(textColor);
        b.setMinHeight(dp(minHeight));
        b.setMinimumHeight(dp(minHeight));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(3),0,dp(3),0);
        GradientDrawable shape=roundRect(fill,11,fill==WHITE?BORDER:fill);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));
        return b;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }

    private GradientDrawable roundRect(int fill,float radius,int stroke) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),stroke);
        return g;
    }

    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (store!=null) store.close();
        super.onDestroy();
    }
}
''',encoding="utf-8")
print("Choix 3 h / 5 h / 10 h / 24 h conservés, affichage compact multi-rappels appliqué, défaut 1 h conservé")
