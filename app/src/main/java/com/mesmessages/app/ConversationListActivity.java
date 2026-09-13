package com.mesmessages.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConversationListActivity extends Activity {
    private static final int REQ = 20;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String,String> names = new HashMap<>();
    private final Map<String,String> phonesByName = new HashMap<>();
    private Adapter adapter;
    private TextView status;
    private Button rcsButton;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("Mes Messages");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        rcsButton = new Button(this);
        rcsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(rcsButton);
        updateRcsButton();

        TextView help = new TextView(this);
        help.setText("Une ligne par conversation. Appuie dessus pour ouvrir la conversation et répondre dans Google Messages.");
        help.setPadding(0, dp(4), 0, dp(8));
        root.addView(help);

        ListView list = new ListView(this);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id) -> open(rows.get(pos).address));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);

        if (ok()) load(); else requestPermissions(new String[]{Manifest.permission.READ_SMS,Manifest.permission.READ_CONTACTS},REQ);
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r==REQ && ok()) load();
    }

    @Override protected void onResume() {
        super.onResume();
        updateRcsButton();
        if (ok()) load();
    }

    private void updateRcsButton() {
        String enabled = Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        boolean on = enabled != null && enabled.contains(getPackageName());
        rcsButton.setText(on ? "RCS : ACCÈS ACTIVÉ" : "ACTIVER L’ACCÈS RCS");
    }

    private boolean ok() {
        return checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
    }

    private void load() {
        status.setText("Chargement des conversations…");
        new Thread(() -> {
            loadNames();
            ArrayList<Msg> all = new ArrayList<>();
            loadSms(all);
            loadMms(all);
            loadRcs(all);
            Collections.sort(all,(a,b)->Long.compare(b.date,a.date));
            LinkedHashMap<String,Row> latest = new LinkedHashMap<>();
            for (Msg m: all) {
                String key = canon(m.address);
                if (key.isEmpty()) key = m.address == null ? "" : m.address.toLowerCase(Locale.ROOT);
                if (!latest.containsKey(key)) {
                    String n = names.get(key);
                    latest.put(key,new Row(n==null || n.isEmpty()?m.displayName:n,m.address,m.body,m.date,m.sent,m.source));
                }
            }
            ArrayList<Row> r = new ArrayList<>(latest.values());
            runOnUiThread(() -> {
                rows.clear(); rows.addAll(r); adapter.notifyDataSetChanged();
                status.setText(rows.size()+" conversations • dernier message affiché pour chacune");
            });
        }).start();
    }

    private void open(String address) {
        if (address==null || address.trim().isEmpty()) {
            Toast.makeText(this,"Je n’ai pas de numéro pour cette conversation",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"+Uri.encode(address.trim())));
        i.setPackage("com.google.android.apps.messaging");
        try { startActivity(i); }
        catch (Throwable e) {
            try { startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(address.trim())))); }
            catch (Throwable x) { Toast.makeText(this,"Impossible d’ouvrir Messages",Toast.LENGTH_SHORT).show(); }
        }
    }

    private void loadNames() {
        names.clear(); phonesByName.clear();
        Cursor c=null;
        try {
            c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
            if(c==null)return;
            int ni=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int pi=c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while(c.moveToNext()) {
                String n=ni>=0?c.getString(ni):"";
                String p=pi>=0?c.getString(pi):"";
                String k=canon(p);
                if(!k.isEmpty() && n!=null && !n.isEmpty()) {
                    names.put(k,n);
                    phonesByName.put(n.trim().toLowerCase(Locale.ROOT),p);
                }
            }
        } finally { if(c!=null)c.close(); }
    }

    private void loadSms(List<Msg> out) {
        Cursor c=null;
        try {
            c=getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.TYPE},null,null,Telephony.Sms.DATE+" DESC");
            if(c==null)return;
            int a=c.getColumnIndex(Telephony.Sms.ADDRESS), b=c.getColumnIndex(Telephony.Sms.BODY), d=c.getColumnIndex(Telephony.Sms.DATE), t=c.getColumnIndex(Telephony.Sms.TYPE);
            while(c.moveToNext()) {
                String address=c.getString(a);
                String key=canon(address);
                String display=names.get(key);
                out.add(new Msg(address,display==null?address:display,c.getString(b),c.getLong(d),c.getInt(t)==Telephony.Sms.MESSAGE_TYPE_SENT,"SMS"));
            }
        } finally { if(c!=null)c.close(); }
    }

    private void loadMms(List<Msg> out) {
        Cursor c=null;
        try {
            c=getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","date","msg_box"},null,null,"date DESC");
            if(c==null)return;
            int i=c.getColumnIndex("_id"),d=c.getColumnIndex("date"),b=c.getColumnIndex("msg_box");
            while(c.moveToNext()) {
                String id=c.getString(i); long date=c.getLong(d); if(date<100000000000L)date*=1000L;
                boolean sent=c.getInt(b)==2;
                String address=mmsAddress(id,sent), key=canon(address), display=names.get(key);
                out.add(new Msg(address,display==null?address:display,mmsText(id),date,sent,"MMS"));
            }
        } finally { if(c!=null)c.close(); }
    }

    private String mmsText(String id) {
        Cursor c=null; StringBuilder s=new StringBuilder();
        try {
            c=getContentResolver().query(Uri.parse("content://mms/part"),new String[]{"ct","text"},"mid=?",new String[]{id},null);
            if(c==null)return "";
            int ct=c.getColumnIndex("ct"), tx=c.getColumnIndex("text");
            while(c.moveToNext()) if("text/plain".equals(c.getString(ct))) { String x=c.getString(tx); if(x!=null && !x.isEmpty()) { if(s.length()>0)s.append(" "); s.append(x); } }
        } finally { if(c!=null)c.close(); }
        return s.length()==0?"(MMS)":s.toString();
    }

    private String mmsAddress(String id, boolean sent) {
        Cursor c=null;
        try {
            c=getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},null,null,null);
            if(c==null)return "";
            int a=c.getColumnIndex("address"),t=c.getColumnIndex("type"),want=sent?151:137;
            while(c.moveToNext()) if(c.getInt(t)==want) { String x=c.getString(a); if(x!=null && !x.contains("insert-address-token")) return x; }
        } finally { if(c!=null)c.close(); }
        return "";
    }

    private void loadRcs(List<Msg> out) {
        CapturedMessageStore store=new CapturedMessageStore(this);
        try {
            for(CapturedMessageStore.Item x:store.getAll()) {
                String who=!x.sender.isEmpty()?x.sender:x.conversation;
                String phone=phonesByName.get(who.trim().toLowerCase(Locale.ROOT));
                String address=phone==null?who:phone;
                out.add(new Msg(address,who,x.body,x.date,x.direction==CapturedMessageStore.DIRECTION_SENT,"RCS"));
            }
        } finally { store.close(); }
    }

    private String canon(String v) {
        if(v==null)return "";
        StringBuilder s=new StringBuilder(); for(int i=0;i<v.length();i++) if(Character.isDigit(v.charAt(i))) s.append(v.charAt(i));
        String d=s.toString();
        if(d.startsWith("0033")) d="0"+d.substring(4); else if(d.startsWith("33")&&d.length()==11)d="0"+d.substring(2);
        return d;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private static class Msg { String address,displayName,body,source; long date; boolean sent; Msg(String a,String n,String b,long d,boolean s,String src){address=a==null?"":a;displayName=n==null||n.isEmpty()?address:n;body=b==null?"":b;date=d;sent=s;source=src;} }
    private static class Row { String name,address,body,source; long date; boolean sent; Row(String n,String a,String b,long d,boolean s,String src){name=n==null||n.isEmpty()?a:n;address=a;body=b;date=d;sent=s;source=src;} }

    private class Adapter extends BaseAdapter {
        SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.FRANCE);
        public int getCount(){return rows.size();}
        public Object getItem(int p){return rows.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int p,View v,ViewGroup parent){
            LinearLayout l; TextView n,m,d;
            if(v==null){ l=new LinearLayout(ConversationListActivity.this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(12),dp(12),dp(12),dp(12)); n=new TextView(ConversationListActivity.this);n.setTextSize(17);n.setTypeface(null,android.graphics.Typeface.BOLD);l.addView(n);m=new TextView(ConversationListActivity.this);m.setTextSize(15);m.setMaxLines(2);m.setPadding(0,dp(4),0,dp(4));l.addView(m);d=new TextView(ConversationListActivity.this);d.setTextSize(12);l.addView(d);l.setTag(new Holder(n,m,d));v=l;}
            Holder h=(Holder)v.getTag(); Row r=rows.get(p); h.n.setText(r.name); h.m.setText((r.sent?"Vous : ":"")+r.body); h.d.setText(f.format(new Date(r.date))+"  •  "+r.source); return v;
        }
    }
    private static class Holder { TextView n,m,d; Holder(TextView a,TextView b,TextView c){n=a;m=b;d=c;} }
}
