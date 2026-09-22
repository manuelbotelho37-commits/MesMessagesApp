package com.mesmessages.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FactureActivity extends Activity {
    private static final int PICK_FILE = 501, PICK_PHOTO = 502;
    private static final String PREFS = "facture_v1", KEY_CLIENTS = "clients", KEY_COUNTER = "invoice_counter";

    private final int BG = Color.rgb(18,18,18), CARD = Color.rgb(36,36,36),
            TEXT = Color.rgb(245,245,245), MUTED = Color.rgb(180,180,180),
            ACCENT = Color.rgb(55,120,245), DANGER = Color.rgb(180,55,55);

    private final List<Client> clients = new ArrayList<>();
    private SharedPreferences prefs;
    private Client current;
    private LinearLayout docsBox;
    private EditText name, ged, saleDate, salePrice, discountPct, discountEuro, pcDate, greenDate, notes;
    private TextView netText;
    private boolean calcLock;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        load();
        showHome();
    }

    @Override public void onBackPressed() {
        if (current != null) {
            saveCurrent(false);
            current = null;
            showHome();
        } else super.onBackPressed();
    }

    private void showHome() {
        current = null;
        LinearLayout root = root();
        TextView t = text("Facture", 27, TEXT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(t);
        TextView sub = text("Fiches clients • Documents • Factures", 14, MUTED);
        sub.setPadding(0,0,0,dp(14));
        root.addView(sub);

        Button add = button("+ NOUVEAU CLIENT", ACCENT);
        add.setOnClickListener(v -> {
            Client c = new Client();
            c.id = String.valueOf(System.currentTimeMillis());
            c.updated = System.currentTimeMillis();
            clients.add(0, c);
            current = c;
            showClient(c);
        });
        root.addView(add, full());

        EditText search = edit("Rechercher un client ou un n° GED");
        LinearLayout.LayoutParams sp = full(); sp.setMargins(0,dp(10),0,dp(10));
        root.addView(search, sp);

        ScrollView sc = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        Runnable refresh = () -> renderList(list, search.getText().toString());
        search.addTextChangedListener(watcher(refresh));
        refresh.run();
        setContentView(root);
    }

    private void renderList(LinearLayout list, String q0) {
        list.removeAllViews();
        String q = q0 == null ? "" : q0.trim().toLowerCase(Locale.FRANCE);
        ArrayList<Client> ordered = new ArrayList<>(clients);
        Collections.sort(ordered, (a,b) -> Long.compare(b.updated, a.updated));
        int shown = 0;
        for (Client c : ordered) {
            String hay = (safe(c.name) + " " + safe(c.ged)).toLowerCase(Locale.FRANCE);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shown++;
            LinearLayout card = card();
            TextView n = text(TextUtils.isEmpty(c.name) ? "Client sans nom" : c.name, 18, TEXT);
            n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(n);
            card.addView(text(TextUtils.isEmpty(c.ged) ? "GED non renseigné" : "GED : " + c.ged, 14, MUTED));
            String p = TextUtils.isEmpty(c.salePrice) ? "" : " • " + money(parse(c.salePrice));
            card.addView(text((TextUtils.isEmpty(c.saleDate) ? "Date de vente non renseignée" : c.saleDate) + p, 13, MUTED));
            card.addView(text(c.docs.size() + " document" + (c.docs.size() > 1 ? "s" : ""), 12, MUTED));
            card.setOnClickListener(v -> { current = c; showClient(c); });
            LinearLayout.LayoutParams cp = full(); cp.setMargins(0,0,0,dp(9));
            list.addView(card, cp);
        }
        if (shown == 0) {
            TextView e = text(q.isEmpty() ? "Aucun client pour le moment." : "Aucun résultat.", 15, MUTED);
            e.setPadding(dp(5),dp(18),dp(5),dp(18));
            list.addView(e);
        }
    }

    private void showClient(Client c) {
        current = c;
        LinearLayout root = root();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        Button back = button("←", CARD);
        back.setTextSize(22);
        back.setOnClickListener(v -> { saveCurrent(false); current = null; showHome(); });
        top.addView(back, new LinearLayout.LayoutParams(dp(58),dp(50)));
        TextView title = text(TextUtils.isEmpty(c.name) ? "Fiche client" : c.name, 23, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,dp(50),1); tp.setMargins(dp(8),0,0,0);
        top.addView(title, tp);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0,dp(8),0,dp(28));
        sc.addView(form);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        name = field(form, "Nom du client", c.name, false);
        ged = field(form, "Numéro de GED", c.ged, false);
        saleDate = dateField(form, "Date de la vente", c.saleDate);
        salePrice = numberField(form, "Prix de la vente", c.salePrice);

        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rr = new LinearLayout(this); rr.setOrientation(LinearLayout.VERTICAL);
        discountPct = field(l, "Remise %", c.discountPct, true);
        discountPct.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        discountEuro = numberField(rr, "Remise €", c.discountEuro);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); lp.setMargins(0,0,dp(5),0);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); rp.setMargins(dp(5),0,0,0);
        r.addView(l,lp); r.addView(rr,rp); form.addView(r);

        netText = text("Montant après remise : " + money(net(c)), 16, TEXT);
        netText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        netText.setPadding(dp(3),dp(4),dp(3),dp(14));
        form.addView(netText);

        pcDate = dateField(form, "Date du dépôt de PC", c.pcDate);
        greenDate = dateField(form, "Date du Feu vert", c.greenDate);
        notes = field(form, "Annotations", c.notes, false);
        notes.setSingleLine(false); notes.setMinLines(5); notes.setGravity(Gravity.TOP);

        attachCalc();

        TextView sec = text("DOCUMENTS ET FACTURES", 13, MUTED);
        sec.setTypeface(Typeface.DEFAULT, Typeface.BOLD); sec.setPadding(dp(2),dp(16),dp(2),dp(8));
        form.addView(sec);

        LinearLayout docBtns = new LinearLayout(this); docBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button addFile = button("+ FICHIER", CARD), addPhoto = button("+ PHOTO", CARD);
        addFile.setOnClickListener(v -> pick("*/*", PICK_FILE));
        addPhoto.setOnClickListener(v -> pick("image/*", PICK_PHOTO));
        docBtns.addView(addFile, new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0,dp(52),1); pp.setMargins(dp(8),0,0,0);
        docBtns.addView(addPhoto, pp); form.addView(docBtns);

        docsBox = new LinearLayout(this); docsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams db = full(); db.setMargins(0,dp(8),0,dp(12));
        form.addView(docsBox, db); renderDocs();

        Button invoice = button("CRÉER LA FACTURE", ACCENT);
        invoice.setOnClickListener(v -> { saveCurrent(false); invoiceDialog(); });
        form.addView(invoice, full());

        Button save = button("ENREGISTRER", ACCENT);
        LinearLayout.LayoutParams sv = full(); sv.setMargins(0,dp(10),0,0);
        save.setOnClickListener(v -> saveCurrent(true));
        form.addView(save, sv);

        Button del = button("SUPPRIMER LA FICHE", DANGER);
        LinearLayout.LayoutParams dl = full(); dl.setMargins(0,dp(18),0,0);
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Supprimer cette fiche ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (d,w) -> {
                    clients.remove(current); persist(); current = null; showHome();
                }).show());
        form.addView(del, dl);

        setContentView(root);
    }

    private EditText field(LinearLayout p, String lab, String val, boolean compact) {
        TextView l = text(lab, 13, MUTED); l.setPadding(dp(3),dp(3),dp(3),dp(3)); p.addView(l);
        EditText e = edit(lab); e.setText(val == null ? "" : val); e.setTextSize(compact ? 16 : 17);
        e.setPadding(dp(10),dp(9),dp(10),dp(9));
        LinearLayout.LayoutParams ep = full(); ep.setMargins(0,0,0,dp(8)); p.addView(e,ep); return e;
    }

    private EditText numberField(LinearLayout p, String lab, String val) {
        EditText e = field(p,lab,val,true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private EditText dateField(LinearLayout p, String lab, String val) {
        EditText e = field(p,lab,val,true); e.setFocusable(false);
        e.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            String[] s = e.getText().toString().split("/");
            if (s.length == 3) try { c.set(Integer.parseInt(s[2]),Integer.parseInt(s[1])-1,Integer.parseInt(s[0])); } catch(Exception ignored) {}
            new DatePickerDialog(this,(x,y,m,d) -> e.setText(String.format(Locale.FRANCE,"%02d/%02d/%04d",d,m+1,y)),
                    c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
        return e;
    }

    private void attachCalc() {
        TextWatcher a = new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) {}
            public void afterTextChanged(Editable e) {
                if (calcLock) return; calcLock = true;
                double price = parse(salePrice.getText().toString()), pct = parse(discountPct.getText().toString());
                double euro = price * pct / 100d;
                discountEuro.setText(clean(euro)); updateNet(price,euro); calcLock = false;
            }
        };
        salePrice.addTextChangedListener(a); discountPct.addTextChangedListener(a);
        discountEuro.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int b,int c) {}
            public void afterTextChanged(Editable e) {
                if (calcLock) return; calcLock = true;
                double price = parse(salePrice.getText().toString()), euro = parse(discountEuro.getText().toString());
                discountPct.setText(clean(price == 0 ? 0 : euro * 100d / price));
                updateNet(price,euro); calcLock = false;
            }
        });
        updateNet(parse(salePrice.getText().toString()), parse(discountEuro.getText().toString()));
    }

    private void updateNet(double price, double euro) {
        if (netText != null) netText.setText("Montant après remise : " + money(Math.max(0,price-euro)));
    }

    private void saveCurrent(boolean toast) {
        if (current == null || name == null) return;
        current.name = s(name); current.ged = s(ged); current.saleDate = s(saleDate); current.salePrice = s(salePrice);
        current.discountPct = s(discountPct); current.discountEuro = s(discountEuro); current.pcDate = s(pcDate);
        current.greenDate = s(greenDate); current.notes = s(notes); current.updated = System.currentTimeMillis();
        persist();
        if (toast) Toast.makeText(this,"Fiche enregistrée",Toast.LENGTH_SHORT).show();
    }

    private void pick(String type, int code) {
        saveCurrent(false);
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, code);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null || current == null) return;
        if (requestCode == PICK_FILE || requestCode == PICK_PHOTO) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
            Doc d = new Doc(); d.name = displayName(u); d.uri = u.toString();
            current.docs.add(0,d); current.updated = System.currentTimeMillis(); persist(); renderDocs();
            Toast.makeText(this,"Document ajouté",Toast.LENGTH_SHORT).show();
        }
    }

    private String displayName(Uri u) {
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
            if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i); }
        } catch(Exception ignored) {} finally { if (c != null) c.close(); }
        return TextUtils.isEmpty(u.getLastPathSegment()) ? "Document" : u.getLastPathSegment();
    }

    private void renderDocs() {
        if (docsBox == null || current == null) return;
        docsBox.removeAllViews();
        if (current.docs.isEmpty()) { docsBox.addView(text("Aucun document pour ce client.",13,MUTED)); return; }
        for (Doc d : current.docs) {
            LinearLayout row = card();
            row.addView(text((d.invoice ? "FACTURE • " : "") + d.name,14,TEXT));
            row.setOnClickListener(v -> openDoc(d));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("Retirer ce document ?").setMessage(d.name)
                        .setNegativeButton("Annuler",null).setPositiveButton("Retirer",(x,w) -> {
                            current.docs.remove(d); persist(); renderDocs();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams rp = full(); rp.setMargins(0,0,0,dp(6)); docsBox.addView(row,rp);
        }
    }

    private void openDoc(Doc d) {
        try {
            Uri u = TextUtils.isEmpty(d.path) ? Uri.parse(d.uri)
                    : FileProvider.getUriForFile(this,getPackageName()+".files",new File(d.path));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u,d.invoice ? "application/pdf" : "*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch(ActivityNotFoundException e) {
            Toast.makeText(this,"Aucune application pour ouvrir ce fichier",Toast.LENGTH_SHORT).show();
        } catch(Exception e) {
            Toast.makeText(this,"Impossible d’ouvrir ce document",Toast.LENGTH_SHORT).show();
        }
    }

    private void invoiceDialog() {
        int next = prefs.getInt(KEY_COUNTER,1), year = Calendar.getInstance().get(Calendar.YEAR);
        String def = String.format(Locale.FRANCE,"FAC-%04d-%04d",year,next);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(4),dp(18),0);
        box.addView(text("Client : " + safe(current.name) + "\nGED : " + safe(current.ged),14,MUTED));
        EditText no = edit("Numéro de facture"); no.setText(def); box.addView(no,full());
        EditText amount = edit("Montant de la facture"); amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(clean(net(current))); box.addView(amount,full());
        EditText label = edit("Libellé"); label.setText("Construction maison individuelle"); box.addView(label,full());

        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Créer la facture")
                .setView(box).setNegativeButton("Annuler",null).setPositiveButton("Créer PDF",null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (no.getText().toString().trim().isEmpty()) { no.setError("Numéro obligatoire"); return; }
            try {
                File f = makePdf(no.getText().toString().trim(),parse(amount.getText().toString()),label.getText().toString().trim());
                Doc d = new Doc(); d.name = f.getName(); d.path = f.getAbsolutePath(); d.invoice = true;
                current.docs.add(0,d); current.updated = System.currentTimeMillis(); persist();
                prefs.edit().putInt(KEY_COUNTER,next+1).apply();
                renderDocs(); dlg.dismiss(); Toast.makeText(this,"Facture PDF créée",Toast.LENGTH_SHORT).show(); openDoc(d);
            } catch(Exception e) {
                Toast.makeText(this,"Erreur pendant la création du PDF",Toast.LENGTH_LONG).show();
            }
        }));
        dlg.show();
    }

    private File makePdf(String number, double amount, String label) throws Exception {
        File dir = new File(getFilesDir(),"invoices"); if (!dir.exists() && !dir.mkdirs()) throw new Exception("mkdir");
        String cleanName = (TextUtils.isEmpty(current.name) ? "client" : current.name).replaceAll("[^a-zA-Z0-9À-ÿ_-]","_");
        File out = new File(dir,number+"_"+cleanName+".pdf");

        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
        Canvas c = page.getCanvas(); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK);
        int x = 48, y = 64;
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(28); c.drawText("FACTURE",x,y,p); y += 34;
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(12);
        c.drawText("N° "+number,x,y,p); y += 18;
        c.drawText("Date : "+new SimpleDateFormat("dd/MM/yyyy",Locale.FRANCE).format(new Date()),x,y,p); y += 30;
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(15); c.drawText("Client",x,y,p); y += 22;
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(12);
        y = line(c,p,safe(current.name),x,y); y = line(c,p,"N° GED : "+safe(current.ged),x,y); y += 14;
        y = line(c,p,"Date de vente : "+safe(current.saleDate),x,y);
        y = line(c,p,"Prix de vente : "+money(parse(current.salePrice)),x,y);
        y = line(c,p,"Remise : "+clean(parse(current.discountPct))+" % / "+money(parse(current.discountEuro)),x,y);
        y = line(c,p,"Dépôt de PC : "+safe(current.pcDate),x,y);
        y = line(c,p,"Feu vert : "+safe(current.greenDate),x,y); y += 18;
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(16); c.drawText("Montant",x,y,p); y += 30;
        p.setTextSize(24); c.drawText(money(amount),x,y,p); y += 30;
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(12); if (!TextUtils.isEmpty(label)) c.drawText(label,x,y,p);
        p.setTextSize(9); p.setColor(Color.DKGRAY); c.drawText("Document généré par l’application Facture",x,810,p);
        pdf.finishPage(page);
        FileOutputStream fos = new FileOutputStream(out); pdf.writeTo(fos); fos.close(); pdf.close();
        return out;
    }

    private int line(Canvas c, Paint p, String s, int x, int y) { c.drawText(s,x,y,p); return y+19; }

    private void load() {
        clients.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_CLIENTS,"[]"));
            for (int i=0;i<a.length();i++) clients.add(Client.from(a.getJSONObject(i)));
        } catch(Exception ignored) {}
    }

    private void persist() {
        JSONArray a = new JSONArray();
        for (Client c : clients) a.put(c.json());
        prefs.edit().putString(KEY_CLIENTS,a.toString()).apply();
    }

    private LinearLayout root() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(BG); r.setPadding(dp(14),dp(18),dp(14),dp(10)); return r;
    }
    private LinearLayout card() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(CARD); r.setPadding(dp(14),dp(12),dp(14),dp(12)); return r;
    }
    private TextView text(String s,float size,int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private EditText edit(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setSingleLine(true); return e; }
    private Button button(String s,int color) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackgroundColor(color); return b; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private String s(EditText e) { return e == null ? "" : e.getText().toString().trim(); }
    private String safe(String s) { return TextUtils.isEmpty(s) ? "—" : s; }
    private double parse(String s) { try { return Double.parseDouble((s==null?"":s).replace(" ","").replace(',','.')); } catch(Exception e) { return 0; } }
    private String clean(double d) { String s = String.format(Locale.FRANCE,"%.2f",d); while(s.endsWith("0"))s=s.substring(0,s.length()-1); if(s.endsWith(","))s=s.substring(0,s.length()-1); return s; }
    private String money(double d) { DecimalFormat f = (DecimalFormat)DecimalFormat.getNumberInstance(Locale.FRANCE); f.applyPattern("#,##0.00"); return f.format(d)+" €"; }
    private double net(Client c) { return Math.max(0,parse(c.salePrice)-parse(c.discountEuro)); }
    private TextWatcher watcher(Runnable r) { return new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){r.run();} public void afterTextChanged(Editable e){} }; }

    private static class Client {
        String id="", name="", ged="", saleDate="", salePrice="", discountPct="", discountEuro="", pcDate="", greenDate="", notes="";
        long updated; final List<Doc> docs = new ArrayList<>();
        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("id",id); o.put("name",name); o.put("ged",ged); o.put("saleDate",saleDate); o.put("salePrice",salePrice);
                o.put("discountPct",discountPct); o.put("discountEuro",discountEuro); o.put("pcDate",pcDate); o.put("greenDate",greenDate);
                o.put("notes",notes); o.put("updated",updated); JSONArray a = new JSONArray(); for(Doc d:docs)a.put(d.json()); o.put("docs",a);
            } catch(Exception ignored) {}
            return o;
        }
        static Client from(JSONObject o) {
            Client c = new Client();
            c.id=o.optString("id"); c.name=o.optString("name"); c.ged=o.optString("ged"); c.saleDate=o.optString("saleDate");
            c.salePrice=o.optString("salePrice"); c.discountPct=o.optString("discountPct"); c.discountEuro=o.optString("discountEuro");
            c.pcDate=o.optString("pcDate"); c.greenDate=o.optString("greenDate"); c.notes=o.optString("notes"); c.updated=o.optLong("updated");
            JSONArray a=o.optJSONArray("docs"); if(a!=null) for(int i=0;i<a.length();i++){JSONObject d=a.optJSONObject(i);if(d!=null)c.docs.add(Doc.from(d));}
            return c;
        }
    }

    private static class Doc {
        String name="", uri="", path=""; boolean invoice;
        JSONObject json() { JSONObject o=new JSONObject(); try{o.put("name",name);o.put("uri",uri);o.put("path",path);o.put("invoice",invoice);}catch(Exception ignored){} return o; }
        static Doc from(JSONObject o) { Doc d=new Doc(); d.name=o.optString("name"); d.uri=o.optString("uri"); d.path=o.optString("path"); d.invoice=o.optBoolean("invoice"); return d; }
    }
}
