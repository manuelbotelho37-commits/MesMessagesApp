package fr.manubotelho.mesnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_PHOTO=7101;
    private static final int PICK_DOCUMENT=7102;
    private static final int PICK_BACKUP=7103;
    private static final int PICK_RESTORE=7104;

    private static final int BG=0xfff5f7fb;
    private static final int WHITE=Color.WHITE;
    private static final int INK=0xff172033;
    private static final int MUTED=0xff667085;
    private static final int BLUE=0xff2556d8;
    private static final int BORDER=0xffdce3ef;
    private static final int RED=0xffb42318;
    private static final int GOLD=0xffb7791f;

    private NoteStore store;
    private LinearLayout noteList;
    private EditText search;
    private boolean twoPane;
    private long selectedId;
    private long importTargetId;

    private TextView selectedTitle;
    private TextView selectedPreview;
    private TextView selectedCount;
    private Button editButton;
    private Button copyButton;
    private Button favoriteButton;
    private Button lockButton;
    private Button importButton;
    private Button elementsButton;
    private Button deleteButton;
    private Button backupButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new NoteStore(this);
        if(state!=null) selectedId=state.getLong("selectedId",0);
        build(state==null?"":state.getString("query",""));
        refresh();
        if(state==null) handleIncomingShare(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShare(intent);
    }

    private void build(String initialQuery) {
        twoPane=getResources().getConfiguration().screenWidthDp>=600;

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(twoPane?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(twoPane?8:9),dp(twoPane?5:9),dp(twoPane?8:9),dp(twoPane?5:9));

        LinearLayout notesPane=column();
        notesPane.setPadding(twoPane?dp(10):dp(3),0,dp(3),0);

        TextView appTitle=text("MNM",twoPane?30:26,INK,true);
        appTitle.setPadding(dp(2),dp(2),0,dp(8));
        notesPane.addView(appTitle);

        search=new EditText(this);
        search.setHint("Rechercher une note…");
        search.setSingleLine(true);
        search.setTextSize(twoPane?18:17);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setPadding(dp(15),dp(8),dp(15),dp(8));
        search.setBackground(shape(WHITE,14,BORDER));
        search.setText(initialQuery);
        notesPane.addView(search,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(twoPane?52:48)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if(!twoPane) notesPane.addView(compactTopActions());

        ScrollView listScroll=new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setClipToPadding(false);
        listScroll.setPadding(0,dp(7),0,dp(16));
        noteList=column();
        listScroll.addView(noteList,new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        notesPane.addView(listScroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        if(twoPane) {
            ScrollView actionsScroll=new ScrollView(this);
            actionsScroll.setFillViewport(false);
            actionsScroll.setClipToPadding(false);
            actionsScroll.setPadding(0,0,0,dp(8));
            LinearLayout actionPane=buildActionPane();
            actionsScroll.addView(actionPane,new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(actionsScroll,new LinearLayout.LayoutParams(
                    dp(270),ViewGroup.LayoutParams.MATCH_PARENT));

            root.addView(notesPane,new LinearLayout.LayoutParams(
                    0,ViewGroup.LayoutParams.MATCH_PARENT,1));
        } else {
            root.addView(notesPane,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        }

        setContentView(root);
    }

    private View compactTopActions() {
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setPadding(0,dp(7),0,0);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button add=button("+ Ajouter",true);
        add.setOnClickListener(v->showEditor(null));
        row.addView(add,compactButtonParams());

        backupButton=button(BackupManager.isConfigured(this)?"Sauvegarde ✓":"Sauvegarde",false);
        backupButton.setOnClickListener(v->startBackup());
        row.addView(backupButton,compactButtonParams());

        Button restore=button("Récupérer",false);
        restore.setOnClickListener(v->chooseRestore());
        row.addView(restore,compactButtonParams());

        scroller.addView(row);
        return scroller;
    }

    private LinearLayout buildActionPane() {
        LinearLayout pane=column();
        pane.setPadding(dp(10),dp(5),dp(10),dp(8));
        pane.setBackground(shape(0xffeef2f8,18,0xffe0e6ef));

        TextView head=text("Commandes",19,INK,true);
        head.setPadding(0,0,0,dp(5));
        pane.addView(head);

        Button add=button("+ Ajouter une note",true);
        add.setOnClickListener(v->showEditor(null));
        pane.addView(add,fullButtonParams());

        selectedTitle=text("Choisis une note à droite",16,INK,true);
        selectedTitle.setPadding(dp(2),dp(7),dp(2),dp(2));
        pane.addView(selectedTitle);

        selectedPreview=text("",13,MUTED,false);
        selectedPreview.setMaxLines(2);
        selectedPreview.setEllipsize(TextUtils.TruncateAt.END);
        pane.addView(selectedPreview);

        selectedCount=text("",12,MUTED,false);
        selectedCount.setPadding(dp(2),dp(2),dp(2),dp(5));
        pane.addView(selectedCount);

        editButton=button("Modifier",false);
        editButton.setOnClickListener(v->editSelected());
        pane.addView(editButton,fullButtonParams());

        copyButton=button("Copier",false);
        copyButton.setOnClickListener(v->copySelected());
        pane.addView(copyButton,fullButtonParams());

        favoriteButton=button("★ Mettre en favori",false);
        favoriteButton.setOnClickListener(v->toggleFavorite());
        pane.addView(favoriteButton,fullButtonParams());

        lockButton=button("🔓 Verrou OFF",false);
        lockButton.setOnClickListener(v->toggleLockSelected());
        pane.addView(lockButton,fullButtonParams());

        importButton=button("+ Ajouter / Importer",false);
        importButton.setOnClickListener(v->showImportMenu(selectedId));
        pane.addView(importButton,fullButtonParams());

        elementsButton=button("Voir les éléments",false);
        elementsButton.setOnClickListener(v->showAttachments(selectedId));
        pane.addView(elementsButton,fullButtonParams());

        deleteButton=button("Supprimer",false);
        deleteButton.setTextColor(RED);
        deleteButton.setOnClickListener(v->deleteSelected());
        pane.addView(deleteButton,fullButtonParams());

        TextView backupHead=text("Sauvegarde",14,INK,true);
        backupHead.setPadding(dp(2),dp(7),dp(2),dp(3));
        pane.addView(backupHead);

        backupButton=button(BackupManager.isConfigured(this)
                ?"Sauvegarde automatique ✓"
                :"Activer sauvegarde",false);
        backupButton.setOnClickListener(v->startBackup());
        pane.addView(backupButton,fullButtonParams());

        Button restore=button("Récupérer sauvegarde",false);
        restore.setOnClickListener(v->chooseRestore());
        pane.addView(restore,fullButtonParams());

        updateSelectionPanel();
        return pane;
    }

    private LinearLayout.LayoutParams compactButtonParams() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(46));
        p.rightMargin=dp(7);
        return p;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(38));
        p.bottomMargin=dp(3);
        return p;
    }

    private void refresh() {
        if(noteList==null||store==null) return;
        String q=search==null?"":search.getText().toString();
        List<NoteStore.Note> notes=store.list(q);
        noteList.removeAllViews();

        if(notes.isEmpty()) {
            TextView empty=text(q.trim().isEmpty()
                    ?"Aucune note. Appuie sur « + Ajouter »."
                    :"Aucune note trouvée.",17,MUTED,false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14),dp(38),dp(14),dp(38));
            noteList.addView(empty);
        } else {
            for(NoteStore.Note note:notes) noteList.addView(noteCard(note));
        }
        updateSelectionPanel();
    }

    private View noteCard(NoteStore.Note note) {
        LinearLayout card=column();
        card.setPadding(dp(14),dp(10),dp(14),dp(10));
        boolean selected=twoPane&&note.id==selectedId;
        card.setBackground(shape(selected?0xffe8efff:WHITE,14,selected?BLUE:BORDER));

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin=dp(8);
        card.setLayoutParams(p);

        TextView title=text((note.locked?"🔒  ":"")+(note.favorite?"★  ":"")+note.title,
                twoPane?17:18,note.favorite?GOLD:INK,true);
        card.addView(title);

        if(!note.content.trim().isEmpty()) {
            TextView preview=text(note.content.trim(),twoPane?14:15,MUTED,false);
            preview.setMaxLines(twoPane?2:3);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            preview.setPadding(0,dp(3),0,dp(2));
            card.addView(preview);
        }

        int count=store.attachmentCount(note.id);
        String meta=new SimpleDateFormat("dd/MM/yyyy · HH:mm",Locale.FRANCE)
                .format(new Date(note.updatedAt));
        if(note.locked) meta+="   ·   Verrouillée";
        if(count>0) meta+="   ·   "+count+" élément"+(count>1?"s":"");
        TextView info=text(meta,12,0xff8992a3,false);
        card.addView(info);

        card.setClickable(true);
        card.setFocusable(true);
        card.setForeground(new RippleDrawable(ColorStateList.valueOf(0x182556d8),null,null));
        card.setOnClickListener(v->{
            selectedId=note.id;
            if(twoPane) refresh();
            showNoteDetails(note.id);
        });
        return card;
    }

    private void updateSelectionPanel() {
        if(!twoPane||selectedTitle==null) return;
        NoteStore.Note note=selectedId==0?null:store.get(selectedId);
        boolean has=note!=null;
        selectedTitle.setText(has?note.title:"Choisis une note à droite");
        selectedPreview.setText(has?note.content:"");
        int count=has?store.attachmentCount(note.id):0;
        selectedCount.setText(has?(count+" élément"+(count>1?"s":"")+" importé"+(count>1?"s":"")):"");

        setEnabled(editButton,has && !note.locked);
        setEnabled(copyButton,has);
        setEnabled(favoriteButton,has);
        setEnabled(lockButton,has);
        setEnabled(importButton,has && !note.locked);
        setEnabled(elementsButton,has);
        setEnabled(deleteButton,has);
        if(has) {
            favoriteButton.setText(note.favorite?"★ Retirer des favoris":"★ Mettre en favori");
            lockButton.setText(note.locked?"🔒 Verrou ON":"🔓 Verrou OFF");
            selectedCount.setText((note.locked?"🔒 Note verrouillée   ·   ":"")
                    +count+" élément"+(count>1?"s":"")+" importé"+(count>1?"s":""));
        } else if(lockButton!=null) {
            lockButton.setText("🔓 Verrou OFF");
        }
        if(has) elementsButton.setText(count==0?"Voir les éléments":"Voir les éléments ("+count+")");
    }

    private void setEnabled(Button b,boolean enabled) {
        if(b==null) return;
        b.setEnabled(enabled);
        b.setAlpha(enabled?1f:0.42f);
    }

    private void showNoteDetails(long noteId) {
        NoteStore.Note note=store.get(noteId);
        if(note==null) return;

        LinearLayout body=column();
        body.setPadding(dp(18),dp(8),dp(18),dp(14));

        TextView title=text(note.title,22,INK,true);
        title.setPadding(0,0,0,dp(8));
        body.addView(title);

        Button detailLock=button(note.locked?"🔒 Verrou ON":"🔓 Verrou OFF",false);
        detailLock.setOnClickListener(v->{
            NoteStore.Note latest=store.get(note.id);
            if(latest==null) return;
            boolean next=!latest.locked;
            store.setLocked(note.id,next);
            detailLock.setText(next?"🔒 Verrou ON":"🔓 Verrou OFF");
            BackupManager.scheduleBackup(this);
            refresh();
            Toast.makeText(this,next?"Note verrouillée":"Note déverrouillée",Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lockParams=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(44));
        lockParams.bottomMargin=dp(10);
        body.addView(detailLock,lockParams);

        TextView content=text("",17,INK,false);
        content.setText(note.content.trim().isEmpty()?"(Aucun texte)":formattedContent(note));
        content.setTextIsSelectable(true);
        content.setPadding(0,0,0,dp(12));
        body.addView(content);

        List<NoteStore.Attachment> items=store.listAttachments(noteId);
        if(!items.isEmpty()) {
            TextView h=text("Éléments importés",16,INK,true);
            h.setPadding(0,dp(4),0,dp(7));
            body.addView(h);

            for(NoteStore.Attachment a:items) {
                Button item=button(kindIcon(a.kind)+"  "+a.label,false);
                item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                item.setOnClickListener(v->openAttachment(a));
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,dp(46));
                ip.bottomMargin=dp(6);
                body.addView(item,ip);
            }
        }

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body,new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton("Fermer",null)
                .setNeutralButton("+ Importer",(d,w)->{
                    NoteStore.Note latest=store.get(noteId);
                    if(latest!=null && latest.locked) {
                        Toast.makeText(this,"Déverrouille d’abord la note.",Toast.LENGTH_LONG).show();
                    } else showImportMenu(noteId);
                })
                .setPositiveButton("Modifier",(d,w)->{
                    NoteStore.Note latest=store.get(noteId);
                    if(latest!=null && latest.locked) {
                        Toast.makeText(this,"Déverrouille d’abord la note.",Toast.LENGTH_LONG).show();
                    } else showEditor(latest);
                })
                .create();
        dialog.setOnShowListener(d->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button neutral=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Button negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if(positive!=null) positive.setAllCaps(false);
            if(neutral!=null) neutral.setAllCaps(false);
            if(negative!=null) negative.setAllCaps(false);
        });
        dialog.show();
    }

    private void showCompactActions(long noteId) {
        NoteStore.Note note=store.get(noteId);
        if(note==null) return;
        int count=store.attachmentCount(noteId);
        String[] choices={
                note.locked?"Modifier (verrouillé)":"Modifier",
                "Copier",
                note.favorite?"★ Retirer des favoris":"★ Mettre en favori",
                note.locked?"🔒 Verrou ON":"🔓 Verrou OFF",
                "+ Ajouter / Importer",
                count==0?"Voir les éléments":"Voir les éléments ("+count+")",
                "Supprimer"
        };
        new AlertDialog.Builder(this)
                .setTitle(note.title)
                .setMessage(note.content.trim().isEmpty()?null:formattedContent(note))
                .setItems(choices,(d,which)->{
                    if(which==0) {
                        if(note.locked) Toast.makeText(this,"Déverrouille d’abord la note.",Toast.LENGTH_LONG).show();
                        else showEditor(note);
                    } else if(which==1) copyNote(note);
                    else if(which==2) {
                        store.setFavorite(note.id,!note.favorite);
                        BackupManager.scheduleBackup(this);
                        refresh();
                    } else if(which==3) {
                        store.setLocked(note.id,!note.locked);
                        BackupManager.scheduleBackup(this);
                        refresh();
                    } else if(which==4) {
                        if(note.locked) Toast.makeText(this,"Déverrouille d’abord la note.",Toast.LENGTH_LONG).show();
                        else showImportMenu(note.id);
                    } else if(which==5) showAttachments(note.id);
                    else deleteNoteTwoSteps(note);
                })
                .setNegativeButton("Fermer",null)
                .show();
    }

    private void showEditor(NoteStore.Note existing) {
        if(existing!=null && existing.locked) {
            Toast.makeText(this,"Cette note est verrouillée. Mets le verrou sur OFF pour la modifier.",Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form=column();
        form.setPadding(dp(20),dp(8),dp(20),dp(6));

        TextView l1=text("Nom de la note",15,INK,true);
        l1.setPadding(0,dp(2),0,dp(6));
        form.addView(l1);

        EditText title=new EditText(this);
        title.setHint("Ex. Wi-Fi maison, Adresse Patrick…");
        title.setTextSize(18);
        title.setSingleLine(true);
        title.setTextColor(INK);
        title.setBackground(shape(WHITE,12,BORDER));
        title.setPadding(dp(12),dp(9),dp(12),dp(9));
        if(existing!=null) title.setText(existing.title);
        form.addView(title,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        TextView l2=text("Ce que tu veux retenir",15,INK,true);
        l2.setPadding(0,dp(15),0,dp(6));
        form.addView(l2);

        EditText content=new EditText(this);
        content.setHint("Écris tout ce que tu veux garder ici…");
        content.setTextSize(17);
        content.setTextColor(INK);
        content.setGravity(Gravity.TOP|Gravity.START);
        content.setInputType(InputType.TYPE_CLASS_TEXT
                |InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                |InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.setMinLines(7);
        content.setMaxLines(14);
        content.setVerticalScrollBarEnabled(true);
        content.setBackground(shape(WHITE,12,BORDER));
        content.setPadding(dp(12),dp(10),dp(12),dp(10));
        if(existing!=null) content.setText(formattedContent(existing));

        HorizontalScrollView formatScroll=new HorizontalScrollView(this);
        formatScroll.setHorizontalScrollBarEnabled(false);
        formatScroll.setFillViewport(false);
        formatScroll.setPadding(0,0,0,dp(7));

        LinearLayout formatRow=new LinearLayout(this);
        formatRow.setOrientation(LinearLayout.HORIZONTAL);

        Button highlight=formatButton("Surligner");
        highlight.setOnClickListener(v->applyTextFormat(content,"highlight"));
        formatRow.addView(highlight,formatButtonParams());

        Button underline=formatButton("Souligner");
        underline.setOnClickListener(v->applyTextFormat(content,"underline"));
        formatRow.addView(underline,formatButtonParams());

        Button red=formatButton("Rouge");
        red.setTextColor(RED);
        red.setOnClickListener(v->applyTextFormat(content,"red"));
        formatRow.addView(red,formatButtonParams());

        Button bold=formatButton("Gras");
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        bold.setOnClickListener(v->applyTextFormat(content,"bold"));
        formatRow.addView(bold,formatButtonParams());

        Button normal=formatButton("Normal");
        normal.setOnClickListener(v->applyTextFormat(content,"normal"));
        formatRow.addView(normal,formatButtonParams());

        formatScroll.addView(formatRow);
        form.addView(formatScroll,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,dp(45)));

        form.addView(content,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView wrap=new ScrollView(this);
        wrap.addView(form);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(existing==null?"Nouvelle note":"Modifier la note")
                .setView(wrap)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Enregistrer",null)
                .create();

        dialog.setOnShowListener(d->{
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setAllCaps(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
            save.setOnClickListener(v->{
                String name=title.getText().toString().trim();
                String body=content.getText().toString().trim();
                String bodyHtml=Html.toHtml((Spanned)content.getText(),
                        Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
                if(name.isEmpty()) {
                    title.setError("Donne un nom à la note.");
                    title.requestFocus();
                    return;
                }
                long id=store.save(existing==null?0:existing.id,name,body,bodyHtml,
                        existing!=null&&existing.favorite);
                selectedId=id;
                BackupManager.scheduleBackup(this);
                dialog.dismiss();
                refresh();
                Toast.makeText(this,"Note enregistrée",Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }

    private CharSequence formattedContent(NoteStore.Note note) {
        if(note==null) return "";
        if(note.contentHtml==null||note.contentHtml.trim().isEmpty()) return note.content;
        try {
            return Html.fromHtml(note.contentHtml,
                    Html.FROM_HTML_MODE_LEGACY|Html.FROM_HTML_OPTION_USE_CSS_COLORS);
        } catch(Exception ex) {
            return note.content;
        }
    }

    private Button formatButton(String label) {
        Button b=button(label,false);
        b.setTextSize(13);
        b.setPadding(dp(10),dp(4),dp(10),dp(4));
        return b;
    }

    private LinearLayout.LayoutParams formatButtonParams() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,dp(38));
        p.rightMargin=dp(6);
        return p;
    }

    private void applyTextFormat(EditText editor,String type) {
        Editable e=editor.getText();
        int a=editor.getSelectionStart();
        int b=editor.getSelectionEnd();
        if(a<0||b<0||a==b) {
            Toast.makeText(this,"Sélectionne d’abord un mot ou une phrase.",Toast.LENGTH_SHORT).show();
            return;
        }
        int start=Math.min(a,b);
        int end=Math.max(a,b);

        if("normal".equals(type)) {
            removeSpans(e,start,end,BackgroundColorSpan.class);
            removeSpans(e,start,end,ForegroundColorSpan.class);
            removeSpans(e,start,end,UnderlineSpan.class);
            removeSpans(e,start,end,StyleSpan.class);
        } else if("highlight".equals(type)) {
            e.setSpan(new BackgroundColorSpan(0xfffff59d),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if("underline".equals(type)) {
            e.setSpan(new UnderlineSpan(),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if("red".equals(type)) {
            e.setSpan(new ForegroundColorSpan(0xffd32f2f),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if("bold".equals(type)) {
            e.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        editor.requestFocus();
        editor.setSelection(end);
    }

    private <T> void removeSpans(Editable e,int start,int end,Class<T> clazz) {
        T[] spans=e.getSpans(start,end,clazz);
        for(T span:spans) e.removeSpan(span);
    }

    private void editSelected() {
        NoteStore.Note note=store.get(selectedId);
        if(note==null) return;
        if(note.locked) {
            Toast.makeText(this,"Déverrouille d’abord la note.",Toast.LENGTH_LONG).show();
            return;
        }
        showEditor(note);
    }

    private void toggleLockSelected() {
        NoteStore.Note note=store.get(selectedId);
        if(note==null) return;
        store.setLocked(note.id,!note.locked);
        BackupManager.scheduleBackup(this);
        refresh();
        Toast.makeText(this,note.locked?"Note déverrouillée":"Note verrouillée",Toast.LENGTH_SHORT).show();
    }

    private void copySelected() {
        NoteStore.Note note=store.get(selectedId);
        if(note!=null) copyNote(note);
    }

    private void copyNote(NoteStore.Note note) {
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard==null) return;
        String value=note.content.trim().isEmpty()?note.title:note.content;
        clipboard.setPrimaryClip(ClipData.newPlainText(note.title,value));
        Toast.makeText(this,"Copié",Toast.LENGTH_SHORT).show();
    }

    private void toggleFavorite() {
        NoteStore.Note note=store.get(selectedId);
        if(note==null) return;
        store.setFavorite(note.id,!note.favorite);
        BackupManager.scheduleBackup(this);
        refresh();
    }

    private void deleteSelected() {
        NoteStore.Note note=store.get(selectedId);
        if(note!=null) deleteNoteTwoSteps(note);
    }

    private void deleteNoteTwoSteps(NoteStore.Note note) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer cette note ?")
                .setMessage(note.title+"\n\nConfirmation 1 sur 2.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Continuer",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("Dernière confirmation")
                        .setMessage("Confirmation 2 sur 2 : supprimer définitivement « "+note.title+" » ?")
                        .setNegativeButton("Garder la note",null)
                        .setPositiveButton("Supprimer définitivement",(d2,w2)->{
                            deleteLocalFiles(note.id);
                            store.delete(note.id);
                            if(selectedId==note.id) selectedId=0;
                            BackupManager.scheduleBackup(this);
                            refresh();
                            Toast.makeText(this,"Note supprimée",Toast.LENGTH_SHORT).show();
                        }).show())
                .show();
    }

    private void deleteLocalFiles(long noteId) {
        for(NoteStore.Attachment a:store.listAttachments(noteId)) {
            if(a.localPath!=null&&!a.localPath.isEmpty()) {
                try {
                    File f=new File(a.localPath);
                    if(f.exists()) f.delete();
                } catch(Exception ignored) {}
            }
        }
    }

    private void showImportMenu(long noteId) {
        if(noteId==0) return;
        NoteStore.Note target=store.get(noteId);
        if(target==null) return;
        if(target.locked) {
            Toast.makeText(this,"Cette note est verrouillée. Mets le verrou sur OFF pour ajouter un élément.",Toast.LENGTH_LONG).show();
            return;
        }
        String[] items={"Photo","Document","Mail","SMS","Article Internet","Vidéo Internet"};
        new AlertDialog.Builder(this)
                .setTitle("Ajouter / Importer")
                .setItems(items,(d,which)->{
                    importTargetId=noteId;
                    if(which==0) pickPhoto();
                    else if(which==1) pickDocument();
                    else if(which==2) showTextImport(noteId,"mail","Mail","Colle ici le mail que tu veux garder.");
                    else if(which==3) showTextImport(noteId,"sms","SMS","Colle ici le SMS que tu veux garder.");
                    else if(which==4) showLinkImport(noteId,"article","Article Internet");
                    else showLinkImport(noteId,"video","Vidéo Internet");
                })
                .setNegativeButton("Annuler",null)
                .show();
    }

    private void pickPhoto() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,PICK_PHOTO);
    }

    private void pickDocument() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK_DOCUMENT);
    }

    private void showTextImport(long noteId,String kind,String label,String hint) {
        EditText field=new EditText(this);
        field.setHint(hint);
        field.setTextSize(17);
        field.setMinLines(6);
        field.setMaxLines(14);
        field.setGravity(Gravity.TOP|Gravity.START);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setPadding(dp(14),dp(12),dp(14),dp(12));

        new AlertDialog.Builder(this)
                .setTitle("Importer : "+label)
                .setView(field)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Ajouter",(d,w)->{
                    String value=field.getText().toString().trim();
                    if(value.isEmpty()) return;
                    store.addAttachment(noteId,kind,label,value,"text/plain",null);
                    BackupManager.scheduleBackup(this);
                    refresh();
                    Toast.makeText(this,label+" ajouté",Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showLinkImport(long noteId,String kind,String label) {
        EditText field=new EditText(this);
        field.setHint("Colle le lien ici");
        field.setTextSize(17);
        field.setSingleLine(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        field.setPadding(dp(14),dp(12),dp(14),dp(12));

        new AlertDialog.Builder(this)
                .setTitle("Importer : "+label)
                .setView(field)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Ajouter",(d,w)->{
                    String value=field.getText().toString().trim();
                    if(value.isEmpty()) return;
                    store.addAttachment(noteId,kind,label,value,"text/uri-list",null);
                    BackupManager.scheduleBackup(this);
                    refresh();
                    Toast.makeText(this,label+" ajouté",Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void importPickedUri(long noteId,Uri uri,String kind) {
        if(noteId==0||uri==null) return;
        try {
            ImportedFile imported=copyIntoApp(uri);
            String label="photo".equals(kind)?"Photo":imported.displayName;
            store.addAttachment(noteId,kind,label,"",imported.mimeType,imported.file.getAbsolutePath());
            BackupManager.scheduleBackup(this);
            refresh();
            Toast.makeText(this,"Import terminé",Toast.LENGTH_SHORT).show();
        } catch(Exception ex) {
            Toast.makeText(this,"Impossible d’importer ce fichier.",Toast.LENGTH_LONG).show();
        }
    }

    private ImportedFile copyIntoApp(Uri uri) throws Exception {
        ContentResolver resolver=getContentResolver();
        String displayName=queryDisplayName(uri);
        if(displayName==null||displayName.trim().isEmpty()) displayName="fichier";
        displayName=safeFileName(displayName);
        String mime=resolver.getType(uri);

        File dir=new File(getFilesDir(),"mnm_files");
        if(!dir.exists()&&!dir.mkdirs()) throw new IllegalStateException("Dossier inaccessible");
        File dest=new File(dir,System.currentTimeMillis()+"-"+displayName);
        int n=1;
        while(dest.exists()) dest=new File(dir,System.currentTimeMillis()+"-"+(n++)+"-"+displayName);

        try(InputStream raw=resolver.openInputStream(uri)) {
            if(raw==null) throw new IllegalStateException("Fichier inaccessible");
            try(InputStream in=new BufferedInputStream(raw);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(dest))) {
                byte[] buffer=new byte[32768];
                int read;
                while((read=in.read(buffer))>=0) if(read>0) out.write(buffer,0,read);
                out.flush();
            }
        }
        return new ImportedFile(dest,displayName,mime);
    }

    private String queryDisplayName(Uri uri) {
        Cursor c=null;
        try {
            c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
                    null,null,null);
            if(c!=null&&c.moveToFirst()) return c.getString(0);
        } catch(Exception ignored) {
        } finally {
            if(c!=null) c.close();
        }
        return null;
    }

    private String safeFileName(String name) {
        String safe=name.replaceAll("[^a-zA-Z0-9À-ÿ._ -]","_").trim();
        return safe.isEmpty()?"fichier":safe;
    }

    private void showAttachments(long noteId) {
        NoteStore.Note note=store.get(noteId);
        if(note==null) return;
        List<NoteStore.Attachment> items=store.listAttachments(noteId);

        LinearLayout list=column();
        list.setPadding(dp(12),dp(6),dp(12),dp(10));

        if(items.isEmpty()) {
            TextView empty=text("Aucun élément importé.",16,MUTED,false);
            empty.setPadding(dp(8),dp(18),dp(8),dp(18));
            list.addView(empty);
        } else {
            for(NoteStore.Attachment a:items) {
                LinearLayout card=column();
                card.setPadding(dp(12),dp(9),dp(12),dp(9));
                card.setBackground(shape(WHITE,12,BORDER));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.bottomMargin=dp(8);
                card.setLayoutParams(cp);

                TextView label=text(kindIcon(a.kind)+"  "+a.label,16,INK,true);
                card.addView(label);
                if(a.value!=null&&!a.value.trim().isEmpty()) {
                    TextView value=text(a.value,14,MUTED,false);
                    value.setMaxLines(3);
                    value.setEllipsize(TextUtils.TruncateAt.END);
                    value.setPadding(0,dp(3),0,dp(5));
                    card.addView(value);
                }

                LinearLayout actions=new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);

                Button open=button("Ouvrir",false);
                open.setTextSize(14);
                open.setOnClickListener(v->openAttachment(a));
                actions.addView(open,new LinearLayout.LayoutParams(0,dp(43),1));

                Button remove=button("Supprimer",false);
                remove.setTextColor(RED);
                remove.setTextSize(14);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(43),1);
                rp.leftMargin=dp(7);
                actions.addView(remove,rp);
                remove.setOnClickListener(v->deleteAttachmentTwoSteps(a));

                card.addView(actions);
                list.addView(card);
            }
        }

        ScrollView scroll=new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle(note.title+" · Éléments")
                .setView(scroll)
                .setPositiveButton("Fermer",null)
                .show();
    }

    private String kindIcon(String kind) {
        if("photo".equals(kind)) return "🖼";
        if("document".equals(kind)) return "📄";
        if("mail".equals(kind)) return "✉";
        if("sms".equals(kind)) return "💬";
        if("article".equals(kind)) return "🌐";
        if("video".equals(kind)) return "▶";
        return "📎";
    }

    private void openAttachment(NoteStore.Attachment a) {
        try {
            if(a.localPath!=null&&!a.localPath.isEmpty()) {
                File file=new File(a.localPath);
                if(!file.exists()) {
                    Toast.makeText(this,"Le fichier n’est plus disponible.",Toast.LENGTH_LONG).show();
                    return;
                }
                Uri uri=FileProvider.getUriForFile(this,"fr.manubotelho.mesnotes.files",file);
                Intent i=new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri,a.mimeType==null?"*/*":a.mimeType);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
                return;
            }

            if(("article".equals(a.kind)||"video".equals(a.kind))&&a.value!=null) {
                Uri uri=Uri.parse(a.value.trim());
                startActivity(new Intent(Intent.ACTION_VIEW,uri));
                return;
            }

            showTextElement(a);
        } catch(ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application ne peut ouvrir cet élément.",Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            Toast.makeText(this,"Impossible d’ouvrir cet élément.",Toast.LENGTH_LONG).show();
        }
    }

    private void showTextElement(NoteStore.Attachment a) {
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setMessage(a.value)
                .setNegativeButton("Fermer",null)
                .setPositiveButton("Copier",(d,w)->{
                    ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    if(cb!=null) cb.setPrimaryClip(ClipData.newPlainText(a.label,a.value));
                    Toast.makeText(this,"Copié",Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void deleteAttachmentTwoSteps(NoteStore.Attachment a) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer cet élément ?")
                .setMessage(a.label+"\n\nConfirmation 1 sur 2.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Continuer",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("Dernière confirmation")
                        .setMessage("Confirmation 2 sur 2 : supprimer définitivement cet élément ?")
                        .setNegativeButton("Garder",null)
                        .setPositiveButton("Supprimer définitivement",(d2,w2)->{
                            if(a.localPath!=null&&!a.localPath.isEmpty()) {
                                try {
                                    File f=new File(a.localPath);
                                    if(f.exists()) f.delete();
                                } catch(Exception ignored) {}
                            }
                            store.deleteAttachment(a.id);
                            BackupManager.scheduleBackup(this);
                            refresh();
                            Toast.makeText(this,"Élément supprimé",Toast.LENGTH_SHORT).show();
                        }).show())
                .show();
    }

    private void startBackup() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"MNM-sauvegarde.json");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                |Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_BACKUP);
    }

    private void chooseRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Récupérer une sauvegarde ?")
                .setMessage("Confirmation 1 sur 2. Tu choisiras ensuite le fichier MNM à récupérer.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Continuer",(d,w)->{
                    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    startActivityForResult(i,PICK_RESTORE);
                })
                .show();
    }

    private void finalRestoreConfirmation(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Dernière confirmation")
                .setMessage("Confirmation 2 sur 2. Les notes actuelles seront remplacées par celles de cette sauvegarde.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Récupérer",(d,w)->{
                    boolean ok=BackupManager.restoreFrom(this,uri);
                    if(ok) {
                        selectedId=0;
                        BackupManager.scheduleBackup(this);
                        refresh();
                        Toast.makeText(this,"Sauvegarde récupérée",Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,"Impossible de récupérer cette sauvegarde.",Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null) return;
        Uri uri=data.getData();

        if(requestCode==PICK_PHOTO) {
            importPickedUri(importTargetId,uri,"photo");
        } else if(requestCode==PICK_DOCUMENT) {
            importPickedUri(importTargetId,uri,"document");
        } else if(requestCode==PICK_BACKUP) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch(Exception ignored) {}
            BackupManager.setBackupUri(this,uri);
            boolean ok=BackupManager.backupNow(this,uri);
            if(backupButton!=null) backupButton.setText(twoPane
                    ?"Sauvegarde automatique ✓":"Sauvegarde ✓");
            Toast.makeText(this,ok?"Sauvegarde automatique activée":"La sauvegarde n’a pas pu être créée.",
                    Toast.LENGTH_LONG).show();
        } else if(requestCode==PICK_RESTORE) {
            finalRestoreConfirmation(uri);
        }
    }

    private void handleIncomingShare(Intent intent) {
        if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction())) return;
        intent.setAction(null);

        String type=intent.getType();
        String subject=intent.getStringExtra(Intent.EXTRA_SUBJECT);
        String sharedText=intent.getStringExtra(Intent.EXTRA_TEXT);
        Uri stream=null;
        try {
            if(android.os.Build.VERSION.SDK_INT>=33)
                stream=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
            else
                stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } catch(Exception ignored) {}

        try {
            if(stream!=null) {
                String name=queryDisplayName(stream);
                boolean image=type!=null&&type.startsWith("image/");
                String title=(subject!=null&&!subject.trim().isEmpty())
                        ?subject.trim()
                        :(image?"Photo importée":(name==null?"Document importé":name));
                long noteId=store.save(0,title,sharedText==null?"":sharedText.trim(),false);
                ImportedFile imported=copyIntoApp(stream);
                store.addAttachment(noteId,image?"photo":"document",
                        image?"Photo":imported.displayName,"",imported.mimeType,imported.file.getAbsolutePath());
                selectedId=noteId;
                BackupManager.scheduleBackup(this);
                refresh();
                Toast.makeText(this,"Ajouté dans MNM",Toast.LENGTH_LONG).show();
                return;
            }

            if(sharedText!=null&&!sharedText.trim().isEmpty()) {
                String value=sharedText.trim();
                String kind=guessSharedKind(value);
                String defaultTitle=sharedTitle(kind);
                String title=(subject!=null&&!subject.trim().isEmpty())?subject.trim():defaultTitle;
                long noteId=store.save(0,title,value,false);
                store.addAttachment(noteId,kind,defaultTitle,value,
                        ("article".equals(kind)||"video".equals(kind))?"text/uri-list":"text/plain",null);
                selectedId=noteId;
                BackupManager.scheduleBackup(this);
                refresh();
                Toast.makeText(this,"Ajouté dans MNM",Toast.LENGTH_LONG).show();
            }
        } catch(Exception ex) {
            Toast.makeText(this,"Impossible d’importer ce partage.",Toast.LENGTH_LONG).show();
        }
    }

    private String guessSharedKind(String text) {
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.contains("youtube.com")||lower.contains("youtu.be")||lower.contains("vimeo.com")
                ||lower.contains("tiktok.com")) return "video";
        if(lower.startsWith("http://")||lower.startsWith("https://")) return "article";
        return "partage";
    }

    private String sharedTitle(String kind) {
        if("video".equals(kind)) return "Vidéo Internet";
        if("article".equals(kind)) return "Article Internet";
        return "Élément partagé";
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putLong("selectedId",selectedId);
        out.putString("query",search==null?"":search.getText().toString());
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if(store!=null) store.close();
        super.onDestroy();
    }

    private LinearLayout column() {
        LinearLayout v=new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(true);
        t.setLineSpacing(dp(1),1f);
        return t;
    }

    private Button button(String label,boolean primary) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(twoPane?13:16);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(twoPane?7:12),dp(twoPane?4:7),dp(twoPane?7:12),dp(twoPane?4:7));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(primary?WHITE:INK);
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf(primary?0x33ffffff:0x182556d8),
                shape(primary?BLUE:WHITE,12,primary?BLUE:BORDER),null));
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable shape(int fill,int radius,int border) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),border);
        return d;
    }

    private int dp(float value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    private static final class ImportedFile {
        final File file;
        final String displayName,mimeType;
        ImportedFile(File file,String displayName,String mimeType) {
            this.file=file; this.displayName=displayName; this.mimeType=mimeType;
        }
    }
}
