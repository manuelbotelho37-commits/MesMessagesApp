package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public final class TaskDetailActivity extends Activity {
    public static final String EXTRA_TASK_ID="task_id";
    private static final int PICK_PHOTO=6001, PICK_FILE=6002;
    private static final int INK=0xff152442, MUTED=0xff526078, BLUE=0xff174ccb,
            BORDER=0xffdce3ef, BG=0xfff4f7fc, WHITE=Color.WHITE, RED=0xffa53223;
    private final Locale FR=Locale.FRANCE;
    private TaskStore store;
    private long taskId;
    private LinearLayout content;
    private String pendingKind;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        taskId=getIntent().getLongExtra(EXTRA_TASK_ID,0);
        if (taskId==0) { finish(); return; }
        store=new TaskStore(this);
        build();
        refresh();
    }

    private void build() {
        LinearLayout root=column(); root.setBackgroundColor(BG);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        Button back=button("←  Retour",false); back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll=new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0,0,0,dp(90));
        content=column();
        content.setPadding(0,dp(12),0,dp(24));
        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    private void refresh() {
        TaskStore.Task task=store.get(taskId);
        if (task==null) { finish(); return; }
        content.removeAllViews();

        TextView title=text(task.title,27,INK,true); title.setPadding(0,dp(6),0,dp(6)); content.addView(title);
        TextView date=text(new SimpleDateFormat("EEEE d MMMM yyyy 'à' HH:mm",FR).format(new java.util.Date(task.dueAt)),17,BLUE,true);
        content.addView(date);
        TextView kind=text(task.appointment?"Rendez-vous":"Tâche",15,MUTED,false); kind.setPadding(0,dp(3),0,dp(18)); content.addView(kind);

        TextView heading=text("Tout ce qui est lié à cette tâche",19,INK,true); heading.setPadding(0,0,0,dp(10)); content.addView(heading);

        Button add=button("+  Ajouter un élément",true);
        add.setTextSize(19);
        add.setOnClickListener(v->showAddMenu());
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.topMargin=dp(4);
        addParams.bottomMargin=dp(18);
        content.addView(add,addParams);

        List<TaskStore.Attachment> attachments=store.listAttachments(taskId);
        if (attachments.isEmpty()) {
            TextView empty=text("Aucun élément pour le moment. Ajoute une note, un mail client, une photo, un fichier, un lien, un contact ou une adresse.",16,MUTED,false);
            empty.setPadding(0,dp(4),0,dp(18)); content.addView(empty);
            return;
        }
        for (TaskStore.Attachment item:attachments) content.addView(itemView(item));
    }

    private View itemView(TaskStore.Attachment item) {
        LinearLayout card=column(); card.setPadding(dp(14),dp(12),dp(14),dp(12));
        card.setBackground(shape(WHITE,14,BORDER));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin=dp(10); card.setLayoutParams(p);

        if ("check".equals(item.kind)) {
            boolean checked=item.value.startsWith("1|");
            String value=item.value.length()>2?item.value.substring(2):item.label;
            CheckBox cb=new CheckBox(this); cb.setText(value); cb.setTextSize(17); cb.setTextColor(INK); cb.setChecked(checked);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
            cb.setOnCheckedChangeListener((b,isChecked)->{
                store.updateAttachment(item.id,item.kind,item.label,(isChecked?"1|":"0|")+value,item.mimeType);
            });
            card.addView(cb);
        } else {
            TextView label=text(icon(item.kind)+"  "+item.label,17,INK,true); card.addView(label);
            String preview=preview(item);
            if (!preview.isEmpty()) {
                TextView value=text(preview,15,MUTED,false); value.setPadding(0,dp(5),0,0); value.setMaxLines(4); card.addView(value);
            }
            card.setClickable(true); card.setFocusable(true);
            card.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x16174ccb),null,shape(WHITE,12,WHITE)));
            card.setOnClickListener(v->openItem(item));
        }
        Button remove=button("Supprimer cet élément",false); remove.setTextColor(RED); remove.setTextSize(14);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin=dp(6); card.addView(remove,rp);
        remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Supprimer cet élément ?")
                .setMessage(item.label).setNegativeButton("Annuler",null)
                .setPositiveButton("Supprimer",(d,w)->{ store.deleteAttachment(item.id); refresh(); }).show());
        return card;
    }

    private void showAddMenu() {
        String[] choices={"✅ Sous-tâche à cocher","👤 Client / contact","📝 Note","✉️ Mail client","🌐 Lien Internet / article","📷 Photo","📎 Fichier / document","📞 Téléphone","📧 Adresse e-mail","📍 Adresse / lieu"};
        new AlertDialog.Builder(this).setTitle("Ajouter à cette tâche").setItems(choices,(d,which)->{
            switch(which) {
                case 0: askText("Sous-tâche","Ex. Rappeler le géomètre","check",true); break;
                case 1: askText("Client / contact","Nom, société, informations utiles","contact",true); break;
                case 2: askText("Note","Écris ce que tu ne veux pas oublier","note",true); break;
                case 3: askText("Mail client","Colle ici le mail reçu ou les points importants","mail",true); break;
                case 4: askText("Lien Internet / article","https://…","link",false); break;
                case 5: pickDocument("photo",PICK_PHOTO,"image/*"); break;
                case 6: pickDocument("file",PICK_FILE,"*/*"); break;
                case 7: askText("Téléphone","Numéro du client","phone",false); break;
                case 8: askText("Adresse e-mail","client@exemple.fr","email",false); break;
                case 9: askText("Adresse / lieu","Adresse du terrain ou du rendez-vous","address",true); break;
            }
        }).show();
    }

    private void askText(String title,String hint,String kind,boolean multiline) {
        LinearLayout box=column(); box.setPadding(dp(20),dp(6),dp(20),0);
        EditText input=new EditText(this); input.setHint(hint); input.setTextSize(18); input.setTextColor(INK);
        input.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        if (multiline) { input.setMinLines(3); input.setMaxLines(10); }
        box.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setNegativeButton("Annuler",null).setPositiveButton("Ajouter",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if (value.isEmpty()) { input.setError("Ajoute une information."); return; }
            String label=title;
            String stored=value;
            if ("check".equals(kind)) stored="0|"+value;
            store.addAttachment(taskId,kind,label,stored,null);
            dialog.dismiss(); refresh();
        }));
        dialog.show();
    }

    private void pickDocument(String kind,int request,String type) {
        pendingKind=kind;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        startActivityForResult(intent,request);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        String mime=getContentResolver().getType(uri);
        String name=fileName(uri);
        store.addAttachment(taskId,pendingKind==null?"file":pendingKind,name,uri.toString(),mime);
        refresh();
    }

    private void openItem(TaskStore.Attachment item) {
        try {
            Intent intent;
            switch(item.kind) {
                case "link":
                    String url=item.value;
                    if (!url.startsWith("http://")&&!url.startsWith("https://")) url="https://"+url;
                    intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url)); break;
                case "phone": intent=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+item.value)); break;
                case "email": intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+item.value)); break;
                case "address": intent=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(item.value))); break;
                case "photo":
                case "file":
                    intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.value),item.mimeType==null?"*/*":item.mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); break;
                default:
                    new AlertDialog.Builder(this).setTitle(item.label).setMessage(cleanValue(item)).setPositiveButton("Fermer",null).show(); return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application ne peut ouvrir cet élément.",Toast.LENGTH_LONG).show();
        }
    }

    private String cleanValue(TaskStore.Attachment item) {
        if ("check".equals(item.kind) && item.value.length()>2) return item.value.substring(2);
        return item.value;
    }
    private String preview(TaskStore.Attachment item) {
        if ("photo".equals(item.kind)||"file".equals(item.kind)) return "Appuie pour ouvrir";
        return cleanValue(item);
    }
    private String icon(String kind) {
        switch(kind) {
            case "contact": return "👤"; case "note": return "📝"; case "mail": return "✉️";
            case "link": return "🌐"; case "photo": return "📷"; case "file": return "📎";
            case "phone": return "📞"; case "email": return "📧"; case "address": return "📍";
            default: return "•";
        }
    }
    private String fileName(Uri uri) {
        String name="Fichier";
        try (Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (c!=null && c.moveToFirst()) name=c.getString(0);
        } catch (RuntimeException ignored) {}
        return name==null?"Fichier":name;
    }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); t.setLineSpacing(dp(2),1); return t;
    }
    private Button button(String label,boolean primary) {
        Button b=new Button(this); b.setText(label); b.setTextSize(17); b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setMinHeight(dp(52)); b.setMinimumHeight(dp(52));
        b.setPadding(dp(14),dp(10),dp(14),dp(10)); b.setTextColor(primary?WHITE:INK);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(primary?0x33ffffff:0x16174ccb),
                shape(primary?BLUE:WHITE,12,primary?BLUE:BORDER),null)); b.setStateListAnimator(null); return b;
    }
    private GradientDrawable shape(int fill,int radius,int border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1),border); return d;
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { if (store!=null) store.close(); super.onDestroy(); }
}
