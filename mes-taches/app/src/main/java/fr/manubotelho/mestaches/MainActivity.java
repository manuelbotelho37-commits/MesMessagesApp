package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    static final int ADD=1001, TODO=1002, DONE=1003, TITLE=1004, DATE=1005, TIME=1006;
    private static final int BLUE=0xff174ccb, INK=0xff152442, MUTED=0xff526078,
            BG=0xfff4f7fc, BORDER=0xffdce3ef, RED=0xffa53223, WHITE=Color.WHITE;
    private static final Locale FR=Locale.FRANCE;
    private TaskStore store;
    private boolean showingDone=false;
    private LinearLayout list;
    private ScrollView scroll;
    private TextView summary, today;
    private Button todoTab, doneTab;
    private AlertDialog editorDialog;
    private EditText titleField;
    private RadioButton appointmentField;
    private Calendar draftDue;
    private long editingId;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshClock=new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this,60000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new TaskStore(this);
        if (state!=null) showingDone=state.getBoolean("showingDone",false);
        makeScreen();
        refresh();
        if (state!=null && state.getBoolean("editorOpen",false)) {
            long id=state.getLong("editingId",0);
            TaskStore.Task task=null;
            try { if (id!=0) task=store.get(id); } catch (RuntimeException ex) { error(); }
            openEditor(task,state);
        }
    }

    private void makeScreen() {
        FrameLayout outer=new FrameLayout(this);
        outer.setBackgroundColor(BG);
        LinearLayout body=column();
        FrameLayout.LayoutParams bodyParams=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT,Gravity.CENTER_HORIZONTAL);
        outer.addView(body,bodyParams);
        outer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int available=Math.max(0,r-l-outer.getPaddingLeft()-outer.getPaddingRight());
            int desired=Math.min(available,dp(760));
            if (desired>0 && body.getLayoutParams().width!=desired) {
                bodyParams.width=desired; body.setLayoutParams(bodyParams);
            }
        });
        outer.setOnApplyWindowInsetsListener((v,insets)->{
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(outer);
        outer.requestApplyInsets();

        LinearLayout heading=column(); heading.setPadding(dp(22),dp(20),dp(22),dp(10));
        today=text("",16,MUTED,false);
        heading.addView(today);
        TextView name=text("Mes tâches",32,INK,true);
        name.setPadding(0,dp(6),0,dp(4)); heading.addView(name);
        summary=text("",16,MUTED,false); heading.addView(summary);
        body.addView(heading);

        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(20),dp(10),dp(20),dp(12));
        todoTab=button("À faire",false); todoTab.setId(TODO);
        doneTab=button("Terminées",false); doneTab.setId(DONE);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        left.setMarginEnd(dp(8)); tabs.addView(todoTab,left);
        tabs.addView(doneTab,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        todoTab.setOnClickListener(v->{ showingDone=false; scroll.scrollTo(0,0); refresh(); });
        doneTab.setOnClickListener(v->{ showingDone=true; scroll.scrollTo(0,0); refresh(); });
        body.addView(tabs);

        scroll=new ScrollView(this); scroll.setFillViewport(true);
        scroll.setClipToPadding(false); scroll.setPadding(dp(20),0,dp(20),dp(12));
        list=column();
        scroll.addView(list,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout footer=column(); footer.setPadding(dp(20),dp(8),dp(20),dp(10));
        Button add=button("+  Ajouter",true); add.setId(ADD); add.setTextSize(20);
        add.setOnClickListener(v->openEditor(null,null));
        footer.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView saved=text("Enregistré sur ce téléphone",14,MUTED,false);
        saved.setGravity(Gravity.CENTER); saved.setPadding(0,dp(8),0,0);
        footer.addView(saved); body.addView(footer);
    }

    private void refresh() {
        if (store==null || list==null) return;
        int previousScroll=scroll.getScrollY();
        try {
            List<TaskStore.Task> pending=store.list(false);
            List<TaskStore.Task> completed=store.list(true);
            LocalDate now=LocalDate.now();
            String todayText=now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",FR));
            today.setText(Character.toUpperCase(todayText.charAt(0))+todayText.substring(1));
            summary.setText(pending.isEmpty()?"Tout est à jour":pending.size()+" "+(pending.size()>1?"choses à faire":"chose à faire"));
            todoTab.setText("À faire ("+pending.size()+")");
            doneTab.setText("Terminées ("+completed.size()+")");
            tabStyle(todoTab,!showingDone); tabStyle(doneTab,showingDone);
            list.removeAllViews(); list.setGravity(Gravity.TOP);
            List<TaskStore.Task> tasks=showingDone?completed:pending;
            if (tasks.isEmpty()) {
                list.setGravity(Gravity.CENTER);
                TextView tick=text("✓",48,BLUE,true); tick.setGravity(Gravity.CENTER);
                list.addView(tick);
                TextView empty=text(showingDone?"Rien de terminé":"Rien de prévu",23,INK,true);
                empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(12),0,dp(8)); list.addView(empty);
                TextView help=text(showingDone?"Les tâches cochées se retrouveront ici.":"Appuie sur « + Ajouter » pour commencer.",17,MUTED,false);
                help.setGravity(Gravity.CENTER); help.setPadding(dp(12),0,dp(12),dp(26)); list.addView(help);
            } else {
                LocalDate previous=null;
                for (TaskStore.Task task:tasks) {
                    LocalDate date=Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (!showingDone && !date.equals(previous)) {
                        String day=date.equals(now)?"Aujourd’hui":date.equals(now.plusDays(1))?"Demain":date.format(DateTimeFormatter.ofPattern("EEEE",FR));
                        String label=day+" · "+date.format(DateTimeFormatter.ofPattern("d MMMM yyyy",FR));
                        TextView group=text(label,16,date.isBefore(now)?RED:MUTED,true);
                        group.setPadding(dp(2),dp(14),0,dp(10));
                        if (Build.VERSION.SDK_INT>=28) group.setAccessibilityHeading(true);
                        list.addView(group); previous=date;
                    }
                    list.addView(taskRow(task));
                }
            }
            scroll.post(()->scroll.scrollTo(0,previousScroll));
        } catch (RuntimeException ex) {
            summary.setText("Impossible de lire les tâches. Ferme puis rouvre l’application.");
        }
    }

    private View taskRow(TaskStore.Task task) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(shape(WHITE,16,BORDER));
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin=dp(10); row.setLayoutParams(rowParams);
        row.setPadding(dp(4),dp(6),dp(8),dp(6));
        CheckBox check=new CheckBox(this); check.setChecked(task.done);
        check.setTag("check:"+task.id);
        check.setButtonTintList(ColorStateList.valueOf(BLUE));
        check.setContentDescription((task.done?"Remettre à faire : ":"Terminer : ")+task.title);
        row.addView(check,new LinearLayout.LayoutParams(dp(56),dp(64)));
        check.setOnCheckedChangeListener((button,checked)->{
            try { store.setDone(task.id,checked); refresh(); }
            catch (RuntimeException ex) { refresh(); error(); }
        });

        LinearLayout details=column(); details.setPadding(0,dp(10),dp(10),dp(10));
        details.setTag("task:"+task.id);
        TextView label=text(task.title,19,task.done?MUTED:INK,true);
        if (task.done) label.setPaintFlags(label.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        details.addView(label);
        TextView time=text(format(task.dueAt,"dd/MM/yyyy")+"  ·  "+format(task.dueAt,"HH:mm"),17,BLUE,true);
        time.setPadding(0,dp(6),0,dp(4)); details.addView(time);
        boolean overdue=!task.done && task.dueAt<System.currentTimeMillis();
        TextView kind=text((task.appointment?"Rendez-vous":"Tâche")+(overdue?" · En retard":""),15,overdue?RED:MUTED,false);
        details.addView(kind);
        details.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16174ccb),null,shape(WHITE,10,WHITE)));
        details.setClickable(true); details.setFocusable(true);
        details.setContentDescription("Modifier : "+task.title+". "+format(task.dueAt,"d MMMM yyyy 'à' HH:mm")+". "+kind.getText());
        details.setOnClickListener(v->openEditor(task,null));
        row.addView(details,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        return row;
    }

    private void openEditor(TaskStore.Task task,Bundle saved) {
        if (editorDialog!=null && editorDialog.isShowing()) return;
        editingId=task==null?0:task.id;
        draftDue=Calendar.getInstance();
        if (task!=null) draftDue.setTimeInMillis(task.dueAt);
        else {
            draftDue.set(Calendar.SECOND,0); draftDue.set(Calendar.MILLISECOND,0);
            draftDue.add(Calendar.MINUTE,30-draftDue.get(Calendar.MINUTE)%30);
        }
        if (saved!=null) draftDue.setTimeInMillis(saved.getLong("draftDue",draftDue.getTimeInMillis()));
        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(6));
        RadioGroup types=new RadioGroup(this); types.setOrientation(RadioGroup.VERTICAL);
        RadioButton taskField=radio("Une tâche",2101);
        appointmentField=radio("Un rendez-vous",2102);
        types.addView(taskField); types.addView(appointmentField);
        boolean appointment=saved!=null?saved.getBoolean("draftAppointment"):task!=null&&task.appointment;
        types.check(appointment?2102:2101); form.addView(types);
        form.addView(fieldLabel("Qu’y a-t-il à faire ?"));
        titleField=new EditText(this); titleField.setId(TITLE);
        titleField.setTextSize(19); titleField.setTextColor(INK);
        titleField.setHint("Ex. Appeler un client");
        titleField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        titleField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});
        titleField.setMinLines(2); titleField.setMaxLines(4);
        titleField.setPadding(dp(14),dp(12),dp(14),dp(12));
        titleField.setBackground(shape(WHITE,12,BORDER));
        titleField.setText(saved!=null?saved.getString("draftTitle",""):task==null?"":task.title);
        form.addView(titleField,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        form.addView(fieldLabel("Date"));
        Button dateButton=button("",false); dateButton.setId(DATE);
        dateButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        form.addView(dateButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(fieldLabel("Heure"));
        Button timeButton=button("",false); timeButton.setId(TIME);
        timeButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        form.addView(timeButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable updateDate=()->{
            dateButton.setText(format(draftDue.getTimeInMillis(),"dd MMMM yyyy"));
            timeButton.setText(format(draftDue.getTimeInMillis(),"HH:mm"));
        };
        updateDate.run();
        dateButton.setOnClickListener(v->{
            hideKeyboard();
            DatePickerDialog picker=new DatePickerDialog(this,(view,year,month,day)->{
                draftDue.set(Calendar.DAY_OF_MONTH,1);
                draftDue.set(Calendar.YEAR,year); draftDue.set(Calendar.MONTH,month); draftDue.set(Calendar.DAY_OF_MONTH,day);
                updateDate.run();
            },draftDue.get(Calendar.YEAR),draftDue.get(Calendar.MONTH),draftDue.get(Calendar.DAY_OF_MONTH));
            picker.setButton(AlertDialog.BUTTON_POSITIVE,"Valider",picker);
            picker.setButton(AlertDialog.BUTTON_NEGATIVE,"Annuler",(dialog,which)->dialog.dismiss());
            picker.show();
        });
        timeButton.setOnClickListener(v->{
            hideKeyboard();
            TimePickerDialog picker=new TimePickerDialog(this,(view,hour,minute)->{
                draftDue.set(Calendar.HOUR_OF_DAY,hour); draftDue.set(Calendar.MINUTE,minute);
                updateDate.run();
            },draftDue.get(Calendar.HOUR_OF_DAY),draftDue.get(Calendar.MINUTE),true);
            picker.setButton(AlertDialog.BUTTON_POSITIVE,"Valider",picker);
            picker.setButton(AlertDialog.BUTTON_NEGATIVE,"Annuler",(dialog,which)->dialog.dismiss());
            picker.show();
        });
        if (task!=null) {
            Button delete=button("Supprimer",false); delete.setTextColor(RED);
            LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.topMargin=dp(20); form.addView(delete,deleteParams);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Supprimer cette entrée ?").setMessage(task.title)
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Supprimer",(dialog,which)->{
                        try { store.delete(task.id); editorDialog.dismiss(); refresh(); }
                        catch (RuntimeException ex) { error(); }
                    }).show());
        }
        ScrollView formScroll=new ScrollView(this); formScroll.addView(form);
        editorDialog=new AlertDialog.Builder(this)
                .setTitle(task==null?"Ajouter":"Modifier")
                .setView(formScroll)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Enregistrer",null).create();
        editorDialog.setCanceledOnTouchOutside(false);
        editorDialog.setOnShowListener(dialog->{
            Button save=editorDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setAllCaps(false); save.setTextSize(17);
            editorDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
            save.setOnClickListener(v->{
                String title=titleField.getText().toString().trim();
                if (title.isEmpty()) { titleField.setError("Écris ce que tu dois faire."); titleField.requestFocus(); return; }
                draftDue.set(Calendar.SECOND,0); draftDue.set(Calendar.MILLISECOND,0);
                try {
                    store.save(editingId,title,draftDue.getTimeInMillis(),appointmentField.isChecked());
                    if (editingId==0) showingDone=false;
                    hideKeyboard(); editorDialog.dismiss(); refresh();
                    Toast.makeText(this,"Enregistré",Toast.LENGTH_SHORT).show();
                } catch (RuntimeException ex) { error(); }
            });
            if (editorDialog.getWindow()!=null)
                editorDialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        editorDialog.show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("showingDone",showingDone);
        if (editorDialog!=null && editorDialog.isShowing()) {
            out.putBoolean("editorOpen",true); out.putLong("editingId",editingId);
            out.putString("draftTitle",titleField.getText().toString());
            out.putLong("draftDue",draftDue.getTimeInMillis());
            out.putBoolean("draftAppointment",appointmentField.isChecked());
        }
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() {
        super.onResume(); refresh(); handler.removeCallbacks(refreshClock); handler.postDelayed(refreshClock,60000);
    }
    @Override protected void onPause() { handler.removeCallbacks(refreshClock); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (editorDialog!=null) editorDialog.dismiss();
        if (store!=null) store.close();
        super.onDestroy();
    }
    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager manager=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (manager!=null && titleField!=null) manager.hideSoftInputFromWindow(titleField.getWindowToken(),0);
        if (titleField!=null) titleField.clearFocus();
    }
    private void error() { Toast.makeText(this,"L’enregistrement a échoué. Réessaie avant de fermer.",Toast.LENGTH_LONG).show(); }
    private String format(long instant,String pattern) { return new SimpleDateFormat(pattern,FR).format(new java.util.Date(instant)); }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(true);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setLineSpacing(dp(2),1); return t;
    }
    private TextView fieldLabel(String value) {
        TextView t=text(value,16,INK,true); t.setPadding(0,dp(18),0,dp(8)); return t;
    }
    private RadioButton radio(String title,int id) {
        RadioButton v=new RadioButton(this); v.setId(id); v.setText(title); v.setTextSize(18);
        v.setMinHeight(dp(50)); v.setTextColor(INK); v.setButtonTintList(ColorStateList.valueOf(BLUE));
        return v;
    }
    private Button button(String label,boolean primary) {
        Button b=new Button(this); b.setText(label); b.setTextSize(17); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(54)); b.setMinimumHeight(dp(54)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(14),dp(12),dp(14),dp(12));
        b.setTextColor(primary?WHITE:INK);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33ffffff:0x16174ccb),
                shape(primary?BLUE:WHITE,12,primary?BLUE:BORDER),null));
        b.setStateListAnimator(null); return b;
    }
    private void tabStyle(Button b,boolean active) {
        b.setSelected(active); b.setTextColor(active?WHITE:MUTED);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22174ccb),shape(active?INK:WHITE,12,active?INK:BORDER),null));
    }
    private GradientDrawable shape(int fill,int radius,int border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),border); return d;
    }
}
