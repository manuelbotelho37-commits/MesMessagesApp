package fr.manubotelho.mestaches;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MailImportActivity extends Activity {
    public static final String EXTRA_TASK_ID="task_id";
    private static final int GOOGLE_AUTH=8101, PICK_MAIL_FILE=8102;
    private static final String GMAIL_SCOPE="https://www.googleapis.com/auth/gmail.readonly";
    private static final String PREFS="mail_connections";
    private static final String OUTLOOK_CLIENT_ID="outlook_client_id";
    private static final String OUTLOOK_ACCESS="outlook_access";
    private static final String OUTLOOK_REFRESH="outlook_refresh";
    private static final String OUTLOOK_EXPIRES="outlook_expires";
    private static final int INK=0xff152442, MUTED=0xff526078, BLUE=0xff174ccb, BG=0xfff4f7fc;

    private final Locale FR=Locale.FRANCE;
    private long taskId;
    private TextView status;
    private SharedPreferences prefs;

    private static final class MailItem {
        String id, subject, from, when, preview;
        MailItem(String id,String subject,String from,String when,String preview) {
            this.id=id;
            this.subject=subject;
            this.from=from;
            this.when=when;
            this.preview=preview;
        }
    }

    private static final class HttpResult {
        final int code;
        final byte[] body;
        HttpResult(int code,byte[] body) { this.code=code; this.body=body; }
        String text() { return new String(body,StandardCharsets.UTF_8); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        taskId=getIntent().getLongExtra(EXTRA_TASK_ID,0);
        if (taskId==0) { finish(); return; }
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(20),dp(20),dp(28));
        root.setBackgroundColor(BG);

        Button back=button("←  Retour",false);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title=text("Importer un mail",28,INK,true);
        title.setPadding(0,dp(20),0,dp(6));
        root.addView(title);

        TextView help=text("Choisis ta boîte. Tu verras une liste simple avec l’expéditeur, l’objet, la date et un aperçu. Appuie sur le mail à ajouter à la tâche.",17,MUTED,false);
        help.setPadding(0,0,0,dp(18));
        root.addView(help);

        Button gmail=button("✉️  Gmail",true);
        gmail.setOnClickListener(v->connectGmail());
        root.addView(gmail,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        Button outlook=button("📨  Outlook",true);
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin=dp(12);
        root.addView(outlook,op);
        outlook.setOnClickListener(v->connectOutlook());

        Button file=button("📎  Mail déjà enregistré (.eml/.msg)",false);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin=dp(16);
        root.addView(file,fp);
        file.setOnClickListener(v->pickMailFile());

        status=text("",16,MUTED,false);
        status.setPadding(0,dp(18),0,0);
        root.addView(status);
        setContentView(root);
    }

    private void connectGmail() {
        setStatus("Connexion à Gmail…");
        AuthorizationRequest request=AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(GMAIL_SCOPE)))
                .build();
        Identity.getAuthorizationClient(this).authorize(request)
                .addOnSuccessListener(result->{
                    if (result.hasResolution()) {
                        PendingIntent pi=result.getPendingIntent();
                        if (pi==null) { setStatus("Impossible d’ouvrir la connexion Gmail."); return; }
                        try {
                            startIntentSenderForResult(pi.getIntentSender(),GOOGLE_AUTH,null,0,0,0);
                        } catch (IntentSender.SendIntentException e) {
                            setStatus("Impossible d’ouvrir la connexion Gmail.");
                        }
                    } else {
                        String token=result.getAccessToken();
                        if (token==null||token.isEmpty()) setStatus("Gmail n’a pas fourni d’autorisation.");
                        else loadGmailMessages(token);
                    }
                })
                .addOnFailureListener(e->setStatus("Connexion Gmail à finaliser. Vérifie l’autorisation Google."));
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==GOOGLE_AUTH) {
            if (resultCode!=RESULT_OK || data==null) { setStatus("Connexion Gmail annulée."); return; }
            try {
                AuthorizationResult result=Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                String token=result.getAccessToken();
                if (token==null||token.isEmpty()) setStatus("Gmail n’a pas fourni d’autorisation.");
                else loadGmailMessages(token);
            } catch (ApiException e) {
                setStatus("Connexion Gmail impossible. Vérifie l’autorisation Google.");
            }
            return;
        }
        if (requestCode==PICK_MAIL_FILE && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) {}
            String name=fileName(uri);
            String mime=getContentResolver().getType(uri);
            try (TaskStore store=new TaskStore(this)) {
                store.addAttachment(taskId,"mailfile","Mail · "+name,uri.toString(),mime==null?"message/rfc822":mime);
            }
            Toast.makeText(this,"Mail ajouté à la tâche.",Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadGmailMessages(String token) {
        setStatus("Chargement de tes derniers mails Gmail…");
        new Thread(()->{
            try {
                HttpResult listResult=get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15",token);
                if (listResult.code<200 || listResult.code>=300) throw new Exception("gmail-list");
                JSONObject list=new JSONObject(listResult.text());
                JSONArray messages=list.optJSONArray("messages");
                ArrayList<MailItem> items=new ArrayList<>();
                if (messages!=null) {
                    for (int i=0;i<messages.length();i++) {
                        String id=messages.getJSONObject(i).optString("id","");
                        if (id.isEmpty()) continue;
                        String url="https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date";
                        HttpResult messageResult=get(url,token);
                        if (messageResult.code<200 || messageResult.code>=300) continue;
                        JSONObject m=new JSONObject(messageResult.text());
                        JSONObject payload=m.optJSONObject("payload");
                        String from="", subject="";
                        if (payload!=null) {
                            JSONArray headers=payload.optJSONArray("headers");
                            if (headers!=null) for (int h=0;h<headers.length();h++) {
                                JSONObject header=headers.getJSONObject(h);
                                String n=header.optString("name","");
                                if ("From".equalsIgnoreCase(n)) from=header.optString("value","");
                                if ("Subject".equalsIgnoreCase(n)) subject=header.optString("value","");
                            }
                        }
                        long date=0;
                        try { date=Long.parseLong(m.optString("internalDate","0")); } catch (NumberFormatException ignored) {}
                        String when=date>0?new SimpleDateFormat("dd/MM HH:mm",FR).format(new Date(date)):"";
                        String preview=m.optString("snippet","");
                        items.add(new MailItem(id,subject,from,when,preview));
                    }
                }
                runOnUiThread(()->showMailList("Gmail",items,item->downloadGmailMail(token,item)));
            } catch (Exception e) {
                runOnUiThread(()->setStatus("Impossible de charger Gmail. Vérifie la connexion du compte."));
            }
        }).start();
    }

    private interface MailChoice { void choose(MailItem item); }

    private void showMailList(String provider,List<MailItem> items,MailChoice choice) {
        if (items.isEmpty()) {
            setStatus("Aucun mail récent trouvé dans "+provider+".");
            return;
        }
        setStatus("Choisis le mail à importer.");

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14),dp(8),dp(14),dp(10));
        list.setBackgroundColor(Color.WHITE);

        TextView help=text("Appuie sur un mail pour l’ajouter à ta tâche.",14,MUTED,false);
        help.setPadding(dp(4),dp(2),dp(4),dp(8));
        list.addView(help);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("15 derniers mails · "+provider)
                .setNegativeButton("Fermer",null)
                .create();

        for (MailItem item:items) {
            Button row=new Button(this);
            row.setAllCaps(false);
            row.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            row.setTextSize(15);
            row.setTextColor(INK);
            row.setBackgroundColor(0xfff7f9fd);
            row.setPadding(dp(14),dp(11),dp(14),dp(11));
            row.setText(buildReadableMailLabel(item));
            row.setOnClickListener(v->{
                dialog.dismiss();
                choice.choose(item);
            });
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin=dp(8);
            list.addView(row,rp);
        }

        ScrollView scroll=new ScrollView(this);
        scroll.addView(list,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setView(scroll);
        dialog.setOnShowListener(d->{
            int width=(int)(getResources().getDisplayMetrics().widthPixels*0.94f);
            int height=(int)(getResources().getDisplayMetrics().heightPixels*0.82f);
            if (dialog.getWindow()!=null) dialog.getWindow().setLayout(width,height);
        });
        dialog.show();
    }

    private String buildReadableMailLabel(MailItem item) {
        String sender=cleanSender(item.from);
        String subject=cleanSubject(item.subject);
        String preview=cleanPreview(item.preview);
        StringBuilder out=new StringBuilder();
        out.append(sender);
        if (item.when!=null && !item.when.trim().isEmpty()) out.append("   ·   ").append(item.when.trim());
        out.append("\n").append(subject);
        if (!preview.isEmpty() && !preview.equalsIgnoreCase(subject)) out.append("\n").append(preview);
        return out.toString();
    }

    private String cleanSender(String raw) {
        if (raw==null || raw.trim().isEmpty()) return "Expéditeur inconnu";
        String value=raw.trim();
        String lower=value.toLowerCase(Locale.ROOT);
        if (lower.contains("notifications@github.com")) return "GitHub";
        int lt=value.indexOf('<');
        if (lt>0) value=value.substring(0,lt).trim();
        value=value.replace("\"","").trim();
        if (value.isEmpty() && lt>=0) {
            int gt=raw.indexOf('>',lt);
            if (gt>lt) value=raw.substring(lt+1,gt).trim();
        }
        if (value.length()>42) value=value.substring(0,39)+"…";
        return value.isEmpty()?"Expéditeur inconnu":value;
    }

    private String cleanSubject(String raw) {
        String s=(raw==null)?"":raw.trim();
        if (s.isEmpty()) return "Sans objet";
        if (s.startsWith("[") && s.contains("]")) {
            int end=s.indexOf(']');
            if (end>=0 && end<s.length()-1) s=s.substring(end+1).trim();
        }
        s=s.replaceFirst("(?i)^Run failed:\\s*","Échec de compilation : ");
        s=s.replaceFirst("(?i)^Run cancelled:\\s*","Compilation annulée : ");
        s=s.replaceFirst("(?i)^Run completed:\\s*","Compilation terminée : ");
        s=s.replaceAll("\\s+"," ").trim();
        if (s.length()>90) s=s.substring(0,87)+"…";
        return s;
    }

    private String cleanPreview(String raw) {
        if (raw==null) return "";
        String s=raw.replaceAll("\\s+"," ").trim();
        s=s.replace("&nbsp;"," ");
        if (s.length()>110) s=s.substring(0,107)+"…";
        return s;
    }

    private void downloadGmailMail(String token,MailItem item) {
        setStatus("Import du mail Gmail complet…");
        new Thread(()->{
            try {
                HttpResult r=get("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+item.id+"?format=raw",token);
                if (r.code<200 || r.code>=300) throw new Exception("gmail-raw");
                JSONObject raw=new JSONObject(r.text());
                byte[] eml=Base64.decode(raw.getString("raw"),Base64.URL_SAFE|Base64.NO_WRAP);
                saveMail("Gmail",item.subject,eml);
            } catch (Exception e) {
                runOnUiThread(()->setStatus("Impossible d’importer ce mail Gmail."));
            }
        }).start();
    }

    private void connectOutlook() {
        String clientId=prefs.getString(OUTLOOK_CLIENT_ID,"");
        if (clientId==null||clientId.trim().isEmpty()) { askOutlookClientId(); return; }
        ensureOutlookToken(clientId.trim());
    }

    private void askOutlookClientId() {
        EditText input=new EditText(this);
        input.setHint("ID d’application Microsoft");
        input.setSingleLine(true);
        input.setTextSize(17);
        int pad=dp(20);
        LinearLayout box=new LinearLayout(this);
        box.setPadding(pad,0,pad,0);
        box.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Première connexion Outlook")
                .setMessage("Il faut une seule fois l’ID Microsoft de Mes tâches Manu. Une fois enregistré, tu n’auras plus à le saisir.")
                .setView(box)
                .setNegativeButton("Plus tard",null)
                .setPositiveButton("Enregistrer",(d,w)->{
                    String value=input.getText().toString().trim();
                    if (value.isEmpty()) { setStatus("Connexion Outlook à configurer."); return; }
                    prefs.edit().putString(OUTLOOK_CLIENT_ID,value).apply();
                    ensureOutlookToken(value);
                }).show();
    }

    private void ensureOutlookToken(String clientId) {
        long expires=prefs.getLong(OUTLOOK_EXPIRES,0);
        String access=prefs.getString(OUTLOOK_ACCESS,"");
        if (access!=null&&!access.isEmpty()&&System.currentTimeMillis()<expires-60000) {
            loadOutlookMessages(access);
            return;
        }
        String refresh=prefs.getString(OUTLOOK_REFRESH,"");
        if (refresh!=null&&!refresh.isEmpty()) refreshOutlookToken(clientId,refresh);
        else startOutlookDeviceLogin(clientId);
    }

    private void refreshOutlookToken(String clientId,String refresh) {
        setStatus("Reconnexion à Outlook…");
        new Thread(()->{
            try {
                String form="client_id="+enc(clientId)+"&grant_type=refresh_token&refresh_token="+enc(refresh)+"&scope="+enc("offline_access Mail.Read User.Read");
                HttpResult r=postForm("https://login.microsoftonline.com/common/oauth2/v2.0/token",form);
                if (r.code<200||r.code>=300) throw new Exception("refresh");
                JSONObject json=new JSONObject(r.text());
                saveOutlookTokens(json);
                String token=json.optString("access_token","");
                if (token.isEmpty()) throw new Exception("token");
                loadOutlookMessages(token);
            } catch (Exception e) {
                prefs.edit().remove(OUTLOOK_ACCESS).remove(OUTLOOK_REFRESH).remove(OUTLOOK_EXPIRES).apply();
                startOutlookDeviceLogin(clientId);
            }
        }).start();
    }

    private void startOutlookDeviceLogin(String clientId) {
        setStatus("Préparation de la connexion Outlook…");
        new Thread(()->{
            try {
                String form="client_id="+enc(clientId)+"&scope="+enc("offline_access Mail.Read User.Read");
                HttpResult r=postForm("https://login.microsoftonline.com/common/oauth2/v2.0/devicecode",form);
                if (r.code<200||r.code>=300) throw new Exception("device");
                JSONObject json=new JSONObject(r.text());
                String deviceCode=json.getString("device_code");
                String userCode=json.getString("user_code");
                String verification=json.optString("verification_uri","https://microsoft.com/devicelogin");
                int interval=Math.max(5,json.optInt("interval",5));
                int expires=Math.max(300,json.optInt("expires_in",900));
                runOnUiThread(()->showOutlookCode(userCode,verification));
                pollOutlookToken(clientId,deviceCode,interval,expires);
            } catch (Exception e) {
                runOnUiThread(()->setStatus("Connexion Outlook à finaliser. Vérifie la configuration Microsoft."));
            }
        }).start();
    }

    private void showOutlookCode(String code,String url) {
        new AlertDialog.Builder(this)
                .setTitle("Connecter Outlook")
                .setMessage("Une seule fois : le code Microsoft est\n\n"+code+"\n\nAppuie ci-dessous : le code sera copié et Microsoft s’ouvrira. Colle le code puis accepte l’accès aux mails.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Copier le code et ouvrir Microsoft",(d,w)->{
                    ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb!=null) cb.setPrimaryClip(ClipData.newPlainText("Code Microsoft",code));
                    try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
                    catch (Exception ignored) {}
                }).show();
    }

    private void pollOutlookToken(String clientId,String deviceCode,int interval,int expires) {
        long stop=System.currentTimeMillis()+expires*1000L;
        int wait=interval;
        while (System.currentTimeMillis()<stop) {
            try { Thread.sleep(wait*1000L); } catch (InterruptedException e) { return; }
            try {
                String form="client_id="+enc(clientId)+"&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code="+enc(deviceCode);
                HttpResult r=postForm("https://login.microsoftonline.com/common/oauth2/v2.0/token",form);
                JSONObject json=new JSONObject(r.text());
                if (r.code>=200&&r.code<300&&json.has("access_token")) {
                    saveOutlookTokens(json);
                    String token=json.getString("access_token");
                    runOnUiThread(()->Toast.makeText(this,"Outlook est connecté.",Toast.LENGTH_LONG).show());
                    loadOutlookMessages(token);
                    return;
                }
                String error=json.optString("error","");
                if ("slow_down".equals(error)) wait+=5;
                else if (!"authorization_pending".equals(error)) break;
            } catch (Exception ignored) {}
        }
        runOnUiThread(()->setStatus("Connexion Outlook non terminée. Tu peux recommencer quand tu veux."));
    }

    private void saveOutlookTokens(JSONObject json) {
        String access=json.optString("access_token","");
        String refresh=json.optString("refresh_token",prefs.getString(OUTLOOK_REFRESH,""));
        long expires=Math.max(300,json.optLong("expires_in",3600));
        prefs.edit().putString(OUTLOOK_ACCESS,access).putString(OUTLOOK_REFRESH,refresh)
                .putLong(OUTLOOK_EXPIRES,System.currentTimeMillis()+expires*1000L).apply();
    }

    private void loadOutlookMessages(String token) {
        runOnUiThread(()->setStatus("Chargement de tes derniers mails Outlook…"));
        new Thread(()->{
            try {
                String url="https://graph.microsoft.com/v1.0/me/messages?$top=15&$select=id,subject,from,receivedDateTime,bodyPreview&$orderby=receivedDateTime%20desc";
                HttpResult r=get(url,token);
                if (r.code<200 || r.code>=300) throw new Exception("outlook-list");
                JSONObject json=new JSONObject(r.text());
                JSONArray values=json.optJSONArray("value");
                ArrayList<MailItem> items=new ArrayList<>();
                if (values!=null) for (int i=0;i<values.length();i++) {
                    JSONObject m=values.getJSONObject(i);
                    JSONObject fromObj=m.optJSONObject("from");
                    JSONObject addr=fromObj==null?null:fromObj.optJSONObject("emailAddress");
                    String from=addr==null?"":addr.optString("name",addr.optString("address",""));
                    String received=m.optString("receivedDateTime","");
                    String when=received.length()>=16?received.substring(8,10)+"/"+received.substring(5,7)+" "+received.substring(11,16):"";
                    items.add(new MailItem(
                            m.optString("id",""),
                            m.optString("subject",""),
                            from,
                            when,
                            m.optString("bodyPreview","")
                    ));
                }
                runOnUiThread(()->showMailList("Outlook",items,item->downloadOutlookMail(token,item)));
            } catch (Exception e) {
                runOnUiThread(()->setStatus("Impossible de charger Outlook. Reconnecte ton compte si nécessaire."));
            }
        }).start();
    }

    private void downloadOutlookMail(String token,MailItem item) {
        setStatus("Import du mail Outlook complet…");
        new Thread(()->{
            try {
                String id=encPath(item.id);
                HttpResult r=get("https://graph.microsoft.com/v1.0/me/messages/"+id+"/$value",token);
                if (r.code<200||r.code>=300) throw new Exception("mime");
                saveMail("Outlook",item.subject,r.body);
            } catch (Exception e) {
                runOnUiThread(()->setStatus("Impossible d’importer ce mail Outlook."));
            }
        }).start();
    }

    private void pickMailFile() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"message/rfc822","application/vnd.ms-outlook","application/octet-stream"});
        startActivityForResult(intent,PICK_MAIL_FILE);
    }

    private void saveMail(String provider,String subject,byte[] bytes) throws Exception {
        File dir=new File(getFilesDir(),"mail");
        if (!dir.exists()&&!dir.mkdirs()) throw new Exception("dir");
        String safe=safeName(subject);
        File file=new File(dir,System.currentTimeMillis()+"-"+safe+".eml");
        try (FileOutputStream out=new FileOutputStream(file)) { out.write(bytes); }
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
        try (TaskStore store=new TaskStore(this)) {
            store.addAttachment(taskId,"mailfile","Mail "+provider+" · "+((subject==null||subject.trim().isEmpty())?"Sans objet":subject.trim()),uri.toString(),"message/rfc822");
        }
        runOnUiThread(()->{
            Toast.makeText(this,"Mail complet ajouté à la tâche.",Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private HttpResult get(String address,String token) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setRequestProperty("Accept","application/json, message/rfc822, */*");
        return readResponse(connection);
    }

    private HttpResult postForm(String address,String form) throws Exception {
        byte[] data=form.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length",String.valueOf(data.length));
        try (java.io.OutputStream out=connection.getOutputStream()) { out.write(data); }
        return readResponse(connection);
    }

    private HttpResult readResponse(HttpURLConnection connection) throws Exception {
        int code=connection.getResponseCode();
        InputStream in=(code>=200&&code<400)?connection.getInputStream():connection.getErrorStream();
        byte[] body=in==null?new byte[0]:readAll(in);
        connection.disconnect();
        return new HttpResult(code,body);
    }

    private byte[] readAll(InputStream in) throws Exception {
        try (InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];
            int n;
            while ((n=input.read(buffer))!=-1) out.write(buffer,0,n);
            return out.toByteArray();
        }
    }

    private String enc(String value) {
        try { return URLEncoder.encode(value==null?"":value,"UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private String encPath(String value) {
        return enc(value).replace("+","%20");
    }

    private String safeName(String value) {
        String s=(value==null||value.trim().isEmpty())?"mail":value.trim();
        s=s.replaceAll("[^a-zA-Z0-9._-]+","-");
        if (s.length()>55) s=s.substring(0,55);
        return s.isEmpty()?"mail":s;
    }

    private String fileName(Uri uri) {
        String name="mail.eml";
        android.database.Cursor cursor=null;
        try {
            cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
            if (cursor!=null&&cursor.moveToFirst()) {
                int col=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col>=0) {
                    String value=cursor.getString(col);
                    if (value!=null&&!value.trim().isEmpty()) name=value.trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor!=null) cursor.close();
        }
        return name;
    }

    private void setStatus(String value) {
        runOnUiThread(()->{
            if (status!=null) status.setText(value==null?"":value);
        });
    }

    private Button button(String label,boolean primary) {
        Button button=new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        button.setTextColor(primary?Color.WHITE:INK);
        button.setBackgroundColor(primary?BLUE:Color.WHITE);
        button.setPadding(dp(16),dp(12),dp(16),dp(12));
        return button;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
}
