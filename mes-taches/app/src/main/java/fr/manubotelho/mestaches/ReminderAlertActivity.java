package fr.manubotelho.mestaches;

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
                if (!task.done && task.dueAt<=now) pending.add(task);
            }
        } catch (RuntimeException ignored) {}
        if (pending.isEmpty()) { finish(); return; }

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(12),dp(14),dp(12));
        panel.setBackground(roundRect(WHITE,18,BORDER));
        panel.setElevation(dp(12));

        TextView header=text("Tâches à faire  •  "+pending.size(),18,INK,true);
        header.setPadding(dp(4),dp(2),dp(4),dp(8));
        panel.addView(header,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (TaskStore.Task task:pending) rows.addView(taskRow(task));
        scroll.addView(rows,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        int maxHeight=(int)(getResources().getDisplayMetrics().heightPixels*0.48f);
        scroll.setLayoutParams(sp);
        scroll.setClipToPadding(false);
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (scroll.getHeight()>maxHeight) {
                ViewGroup.LayoutParams p=scroll.getLayoutParams();
                p.height=maxHeight;
                scroll.setLayoutParams(p);
            }
        });
        panel.addView(scroll);

        Button close=button("Fermer pour l’instant",WHITE,MUTED,44);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin=dp(8);
        panel.addView(close,cp);
        close.setOnClickListener(v->finish());

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        pp.setMargins(dp(18),dp(34),dp(18),dp(18));
        root.addView(panel,pp);
        setContentView(root);
    }

    private View taskRow(TaskStore.Task task) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12),dp(9),dp(8),dp(9));
        row.setBackground(roundRect(ROW,14,BORDER));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin=dp(7);
        row.setLayoutParams(rp);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView title=text(task.title,16,INK,true);
        title.setMaxLines(2);
        info.addView(title);
        String when=new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new java.util.Date(task.dueAt));
        TextView date=text((task.appointment?"Rendez-vous":"Tâche")+" · "+when,13,BLUE,true);
        date.setPadding(0,dp(2),0,0);
        info.addView(date);
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        Button done=button("✓",ORANGE,WHITE,44);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(52),dp(44));
        dpv.setMarginStart(dp(8));
        row.addView(done,dpv);
        done.setContentDescription("Marquer "+task.title+" comme terminée");
        done.setOnClickListener(v->{
            try {
                store.setDone(task.id,true);
                ReminderScheduler.cancel(this,task.id);
                Toast.makeText(this,"Tâche terminée",Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {}
            build();
        });

        row.setOnClickListener(v->{
            startActivity(new Intent(this,TaskDetailActivity.class)
                    .putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        return row;
    }

    private Button button(String label,int fill,int textColor,int minHeight) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(textColor);
        b.setMinHeight(dp(minHeight));
        GradientDrawable shape=roundRect(fill,14,fill==WHITE?BORDER:fill);
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
