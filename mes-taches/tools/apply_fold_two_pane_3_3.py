from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

text = MAIN.read_text(encoding="utf-8")

# Toujours conserver deux panneaux. Sur l'écran extérieur du Fold, utiliser une
# variante compacte spécialement dessinée pour la faible largeur.
text = text.replace(
    "private boolean wideLayout=false;",
    "private boolean wideLayout=false;\n    private boolean compactWide=false;",
    1,
)
text, count = re.subn(
    r"wideLayout\s*=\s*getResources\(\)\.getConfiguration\(\)\.screenWidthDp\s*>=\s*\d+\s*;",
    "int widthDp=getResources().getConfiguration().screenWidthDp;\n        wideLayout=true;\n        compactWide=widthDp<600;",
    text,
    count=1,
)
if count != 1:
    raise SystemExit("Réglage wideLayout introuvable")

# Panneau gauche plus étroit sur l'écran de couverture, panneau tâches plus large.
text = text.replace(
    "body.addView(controls,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,0.36f));",
    "body.addView(controls,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,compactWide?0.30f:0.36f));",
    1,
)
text = text.replace(
    "body.addView(taskPane,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,0.64f));",
    "body.addView(taskPane,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,compactWide?0.70f:0.64f));",
    1,
)

# En-tête compact : évite les mots coupés verticalement.
text = text.replace(
    "heading.setPadding(dp(22),wideLayout?dp(18):dp(20),dp(22),dp(8));",
    "heading.setPadding(compactWide?dp(8):dp(22),compactWide?dp(10):(wideLayout?dp(18):dp(20)),compactWide?dp(8):dp(22),dp(6));",
    1,
)
text = text.replace(
    'today=text("",wideLayout?15:16,MUTED,false); heading.addView(today);',
    'today=text("",compactWide?10:(wideLayout?15:16),MUTED,false); heading.addView(today);',
    1,
)
text = text.replace(
    'TextView name=text("Mes tâches Manu",wideLayout?27:32,INK,true);',
    'TextView name=text("Mes tâches Manu",compactWide?18:(wideLayout?27:32),INK,true);',
    1,
)
text = text.replace(
    'summary=text("",wideLayout?15:16,MUTED,false); heading.addView(summary);',
    'summary=text("",compactWide?11:(wideLayout?15:16),MUTED,false); heading.addView(summary);',
    1,
)
text = text.replace(
    'Button colors=button("🎨  Couleurs",false); colors.setId(COLORS);',
    'Button colors=button("🎨  Couleurs",false); colors.setId(COLORS);\n        if (compactWide) { colors.setTextSize(11); colors.setPadding(dp(5),dp(3),dp(5),dp(3)); }',
    1,
)

# Onglets : empilés verticalement sur l'écran extérieur, horizontaux ailleurs.
tabs_pattern = r"        LinearLayout tabs=new LinearLayout\(this\);.*?        controls\.addView\(tabs\);"
tabs_replacement = '''        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(compactWide?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        tabs.setPadding(compactWide?dp(8):dp(20),dp(6),compactWide?dp(8):dp(20),dp(8));
        todayTab=button("Aujourd’hui",false);
        todoTab=button("À faire",false); todoTab.setId(TODO);
        doneTab=button("Terminées",false); doneTab.setId(DONE);
        if (compactWide) {
            todayTab.setTextSize(10); todoTab.setTextSize(10); doneTab.setTextSize(10);
            todayTab.setMinHeight(dp(38)); todoTab.setMinHeight(dp(38)); doneTab.setMinHeight(dp(38));
            todayTab.setMinimumHeight(dp(38)); todoTab.setMinimumHeight(dp(38)); doneTab.setMinimumHeight(dp(38));
            LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            p1.bottomMargin=dp(4); tabs.addView(todayTab,p1);
            LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            p2.bottomMargin=dp(4); tabs.addView(todoTab,p2);
            tabs.addView(doneTab,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            if (wideLayout) {
                todayTab.setTextSize(13); todoTab.setTextSize(13); doneTab.setTextSize(13);
                todayTab.setMinHeight(dp(48)); todoTab.setMinHeight(dp(48)); doneTab.setMinHeight(dp(48));
            }
            LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            first.setMarginEnd(dp(6)); tabs.addView(todayTab,first);
            LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            middle.setMarginEnd(dp(6)); tabs.addView(todoTab,middle);
            tabs.addView(doneTab,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        }
        todayTab.setOnClickListener(v->{ showingToday=true; showingDone=false; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });
        todoTab.setOnClickListener(v->{ showingToday=false; showingDone=false; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });
        doneTab.setOnClickListener(v->{ showingToday=false; showingDone=true; if(scroll!=null) scroll.scrollTo(0,0); refresh(); });
        controls.addView(tabs);'''
text, count = re.subn(tabs_pattern, tabs_replacement, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit("Bloc onglets introuvable")

# Pied de colonne compact.
text = text.replace(
    "LinearLayout footer=column(); footer.setPadding(dp(20),dp(8),dp(20),dp(12));",
    "LinearLayout footer=column(); footer.setPadding(compactWide?dp(8):dp(20),dp(6),compactWide?dp(8):dp(20),dp(8));",
    1,
)
text = text.replace(
    'addButton=button("+  Ajouter",true); addButton.setId(ADD); addButton.setTextSize(wideLayout?18:20);',
    'addButton=button("+ Ajouter",true); addButton.setId(ADD); addButton.setTextSize(compactWide?12:(wideLayout?18:20));',
    1,
)
text = text.replace(
    'TextView saved=text("Enregistré sur ce téléphone",wideLayout?12:14,MUTED,false);',
    'TextView saved=text("Enregistré sur ce téléphone",compactWide?9:(wideLayout?12:14),MUTED,false);',
    1,
)

# Titre et marges du panneau de droite.
text = text.replace(
    "taskPane.setPadding(dp(12),dp(12),dp(12),dp(8));",
    "taskPane.setPadding(compactWide?dp(6):dp(12),compactWide?dp(8):dp(12),compactWide?dp(6):dp(12),dp(6));",
    1,
)
text = text.replace(
    'paneTitle=text("Toutes les tâches à faire",20,INK,true);',
    'paneTitle=text("Toutes les tâches à faire",compactWide?16:20,INK,true);',
    1,
)
text = text.replace(
    "scroll.setPadding(wideLayout?dp(2):dp(20),0,wideLayout?dp(2):dp(20),wideLayout?dp(4):dp(12));",
    "scroll.setPadding(compactWide?0:(wideLayout?dp(2):dp(20)),0,compactWide?0:(wideLayout?dp(2):dp(20)),compactWide?dp(2):(wideLayout?dp(4):dp(12)));",
    1,
)

# Sur écran étroit, utiliser une carte pensée en deux lignes : contenu au-dessus,
# actions en dessous. Cela empêche le titre/date d'être écrasés par les boutons.
text = text.replace(
    "    private View taskRow(TaskStore.Task task) {\n",
    "    private View taskRow(TaskStore.Task task) {\n        if (compactWide) return compactTaskRow(task);\n",
    1,
)

compact_method = r'''    private View compactTaskRow(TaskStore.Task task) {
        LinearLayout card=column();
        card.setBackground(shape(task.done?WHITE:taskBackgroundColor(),12,BORDER));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin=dp(6); card.setLayoutParams(cardParams);
        card.setPadding(dp(5),dp(5),dp(5),dp(5));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox check=new CheckBox(this); check.setChecked(task.done);
        check.setButtonTintList(ColorStateList.valueOf(task.done?doneColor():todoColor()));
        check.setContentDescription((task.done?"Remettre à faire : ":"Terminer : ")+task.title);
        top.addView(check,new LinearLayout.LayoutParams(dp(40),dp(46)));
        check.setOnCheckedChangeListener((button,checked)->{
            try { store.setDone(task.id,checked); refresh(); }
            catch (RuntimeException ex) { refresh(); error(); }
        });

        LinearLayout details=column();
        details.setPadding(dp(2),dp(3),dp(2),dp(3));
        int fill=task.done?WHITE:taskBackgroundColor();
        details.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16174ccb),null,shape(fill,8,fill)));
        details.setClickable(true); details.setFocusable(true);
        details.setOnClickListener(v->openEditor(task,null));

        TextView label=text(task.title,14,task.done?MUTED:INK,true);
        label.setMaxLines(3); label.setEllipsize(TextUtils.TruncateAt.END);
        if (task.done) label.setPaintFlags(label.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        details.addView(label);

        boolean overdue=!task.done && task.dueAt<System.currentTimeMillis();
        String when=format(task.dueAt,"dd/MM · HH:mm");
        if (overdue) when+=" · EN RETARD";
        if (!"none".equals(task.recurrence)) when+=" · ↻ "+recurrenceLabel(task.recurrence);
        TextView time=text(when,11,overdue?RED:(task.done?MUTED:todoColor()),true);
        time.setPadding(0,dp(3),0,0); details.addView(time);
        top.addView(details,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        int attachmentCount=0;
        try { attachmentCount=store.listAttachments(task.id).size(); } catch (RuntimeException ignored) {}
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(40),dp(3),0,0);

        Button priority=urgentButton(task);
        priority.setTextSize(9); priority.setPadding(dp(3),dp(2),dp(3),dp(2));
        LinearLayout.LayoutParams prioParams=new LinearLayout.LayoutParams(0,dp(36),1.25f);
        prioParams.setMarginEnd(dp(4)); actions.addView(priority,prioParams);

        Button pause=pauseButton(task);
        pause.setTextSize(9); pause.setPadding(dp(3),dp(2),dp(3),dp(2));
        LinearLayout.LayoutParams pauseParams=new LinearLayout.LayoutParams(0,dp(36),0.95f);
        pauseParams.setMarginEnd(dp(4)); actions.addView(pause,pauseParams);

        Button dossier=button(attachmentCount==0?"📎":"📎 "+attachmentCount,false);
        dossier.setTextSize(10); dossier.setPadding(dp(2),dp(2),dp(2),dp(2));
        dossier.setOnClickListener(v->startActivity(new Intent(this,TaskDetailActivity.class).putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)));
        actions.addView(dossier,new LinearLayout.LayoutParams(0,dp(36),0.70f));

        card.addView(actions,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

'''
anchor = "    private Button urgentButton(TaskStore.Task task) {"
if anchor not in text:
    raise SystemExit("Ancre urgentButton introuvable")
text = text.replace(anchor, compact_method + anchor, 1)

MAIN.write_text(text, encoding="utf-8")

# Version 3.5, installable par-dessus la 3.4 avec la même signature.
gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 104", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.5-readable-cover-two-pane'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Mes tâches Manu 3.5 : deux colonnes lisibles sur écran intérieur et écran extérieur")
