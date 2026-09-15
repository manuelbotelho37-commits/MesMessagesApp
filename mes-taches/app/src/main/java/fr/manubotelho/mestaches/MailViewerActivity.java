package fr.manubotelho.mestaches;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MailViewerActivity extends Activity {
    public static final String EXTRA_URI="uri";
    public static final String EXTRA_LABEL="label";
    private static final int INK=0xff152442, MUTED=0xff526078, BLUE=0xff174ccb, BG=0xfff4f7fc;

    private static final class ParsedMail {
        String subject="Mail";
        String from="";
        String date="";
        String body="";
        boolean html=false;
    }

    private static final class MimePart {
        Map<String,String> headers=new LinkedHashMap<>();
        String body="";
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildLoading();
        String rawUri=getIntent().getStringExtra(EXTRA_URI);
        if (rawUri==null || rawUri.trim().isEmpty()) {
            showError("Impossible d’ouvrir ce mail.");
            return;
        }
        new Thread(()->{
            try {
                Uri uri=Uri.parse(rawUri);
                byte[] bytes=readAll(getContentResolver().openInputStream(uri));
                ParsedMail mail=parseMail(bytes);
                runOnUiThread(()->showMail(mail));
            } catch (Exception e) {
                runOnUiThread(()->showError("Ce mail n’a pas pu être affiché proprement."));
            }
        }).start();
    }

    private void buildLoading() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackgroundColor(BG);
        Button back=button("←  Retour",false);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView loading=text("Ouverture du mail…",18,MUTED,false);
        loading.setPadding(0,dp(24),0,0);
        root.addView(loading);
        setContentView(root);
    }

    private void showMail(ParsedMail mail) {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackgroundColor(BG);

        Button back=button("←  Retour",false);
        back.setOnClickListener(v->finish());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,dp(18),0,dp(30));

        TextView title=text(empty(mail.subject)?"Sans objet":mail.subject.trim(),24,INK,true);
        title.setPadding(0,0,0,dp(10));
        content.addView(title);

        if (!empty(mail.from)) {
            TextView from=text("De : "+mail.from.trim(),16,BLUE,true);
            from.setPadding(0,0,0,dp(4));
            content.addView(from);
        }
        if (!empty(mail.date)) {
            TextView date=text(mail.date.trim(),14,MUTED,false);
            date.setPadding(0,0,0,dp(16));
            content.addView(date);
        }

        TextView body=text("",17,INK,false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(0,1.08f);
        body.setPadding(0,dp(4),0,dp(24));
        if (mail.html) {
            String cleaned=cleanHtml(mail.body);
            Spanned styled=Html.fromHtml(cleaned,Html.FROM_HTML_MODE_LEGACY);
            body.setText(styled);
        } else {
            body.setText(cleanPlain(mail.body));
        }
        content.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    private ParsedMail parseMail(byte[] bytes) throws Exception {
        String raw=new String(bytes,StandardCharsets.ISO_8859_1);
        MimePart root=parsePart(raw);
        ParsedMail mail=new ParsedMail();
        mail.subject=decodeHeader(root.headers.get("subject"));
        mail.from=decodeHeader(root.headers.get("from"));
        mail.date=decodeHeader(root.headers.get("date"));
        String[] chosen=findBody(root);
        mail.body=chosen[0];
        mail.html="html".equals(chosen[1]);
        if (empty(mail.body)) {
            mail.body="Le contenu de ce mail n’a pas pu être extrait proprement.";
            mail.html=false;
        }
        if (empty(mail.subject)) {
            String fallback=getIntent().getStringExtra(EXTRA_LABEL);
            mail.subject=fallback==null?"Mail":fallback.replaceFirst("^Mail (Gmail|Outlook) · ","");
        }
        return mail;
    }

    private MimePart parsePart(String raw) {
        MimePart p=new MimePart();
        int split=raw.indexOf("\r\n\r\n");
        int jump=4;
        if (split<0) { split=raw.indexOf("\n\n"); jump=2; }
        String head=split>=0?raw.substring(0,split):raw;
        p.body=split>=0?raw.substring(split+jump):"";
        String unfolded=head.replaceAll("\r?\n[ \\t]+"," ");
        for (String line:unfolded.split("\r?\n")) {
            int colon=line.indexOf(':');
            if (colon<=0) continue;
            p.headers.put(line.substring(0,colon).trim().toLowerCase(Locale.ROOT),line.substring(colon+1).trim());
        }
        return p;
    }

    private String[] findBody(MimePart part) {
        String contentType=value(part.headers,"content-type","text/plain");
        String lower=contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("multipart/")) {
            String boundary=param(contentType,"boundary");
            if (!empty(boundary)) {
                String marker="--"+boundary;
                String[] chunks=part.body.split(Pattern.quote(marker));
                String[] bestHtml=null;
                String[] bestPlain=null;
                for (String chunk:chunks) {
                    String c=chunk.trim();
                    if (c.isEmpty() || c.equals("--")) continue;
                    if (c.endsWith("--")) c=c.substring(0,c.length()-2);
                    MimePart child=parsePart(c);
                    String[] found=findBody(child);
                    if (empty(found[0])) continue;
                    if ("html".equals(found[1]) && bestHtml==null) bestHtml=found;
                    if ("plain".equals(found[1]) && bestPlain==null) bestPlain=found;
                }
                if (bestHtml!=null) return bestHtml;
                if (bestPlain!=null) return bestPlain;
            }
        }
        if (lower.startsWith("text/plain") || lower.startsWith("text/html")) {
            byte[] decoded=decodeTransfer(part.body,value(part.headers,"content-transfer-encoding",""));
            String charset=param(contentType,"charset");
            String body=decodeTextBest(decoded,charset);
            return new String[]{body,lower.startsWith("text/html")?"html":"plain"};
        }
        return new String[]{"",""};
    }

    private String decodeTextBest(byte[] bytes,String declared) {
        List<Charset> candidates=new ArrayList<>();
        if (!empty(declared)) {
            try { candidates.add(Charset.forName(declared.replace("\"","").trim())); } catch (Exception ignored) {}
        }
        addCharset(candidates,StandardCharsets.UTF_8);
        try { addCharset(candidates,Charset.forName("windows-1252")); } catch (Exception ignored) {}
        addCharset(candidates,StandardCharsets.ISO_8859_1);

        String best="";
        int bestScore=Integer.MAX_VALUE;
        for (Charset cs:candidates) {
            String s=new String(bytes,cs);
            int score=textScore(s);
            if (score<bestScore) { best=s; bestScore=score; }
        }
        String repaired=repairUtf8Mojibake(best);
        if (textScore(repaired)<bestScore) best=repaired;
        return best;
    }

    private void addCharset(List<Charset> list,Charset cs) {
        for (Charset existing:list) if (existing.name().equalsIgnoreCase(cs.name())) return;
        list.add(cs);
    }

    private int textScore(String s) {
        if (s==null) return Integer.MAX_VALUE/2;
        int score=0;
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (c=='\uFFFD') score+=100;
            else if (c<0x20 && c!='\n' && c!='\r' && c!='\t') score+=15;
        }
        score+=count(s,"Ã")*25;
        score+=count(s,"Â")*20;
        score+=count(s,"â€")*25;
        score+=count(s,"ðŸ")*25;
        return score;
    }

    private int count(String s,String needle) {
        int n=0, p=0;
        while ((p=s.indexOf(needle,p))>=0) { n++; p+=needle.length(); }
        return n;
    }

    private String repairUtf8Mojibake(String s) {
        if (s==null) return "";
        if (!(s.contains("Ã")||s.contains("Â")||s.contains("â€")||s.contains("ðŸ"))) return s;
        try {
            String fixed=new String(s.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
            return textScore(fixed)<textScore(s)?fixed:s;
        } catch (Exception e) { return s; }
    }

    private byte[] decodeTransfer(String body,String transfer) {
        String t=transfer==null?"":transfer.toLowerCase(Locale.ROOT);
        try {
            if (t.contains("base64")) return Base64.decode(body.replaceAll("\\s+",""),Base64.DEFAULT);
            if (t.contains("quoted-printable")) return decodeQuotedPrintable(body.getBytes(StandardCharsets.ISO_8859_1));
        } catch (Exception ignored) {}
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] decodeQuotedPrintable(byte[] input) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for (int i=0;i<input.length;i++) {
            int b=input[i]&0xff;
            if (b=='=' && i+1<input.length) {
                if (input[i+1]=='\r' && i+2<input.length && input[i+2]=='\n') { i+=2; continue; }
                if (input[i+1]=='\n') { i+=1; continue; }
                if (i+2<input.length) {
                    int hi=hex(input[i+1]), lo=hex(input[i+2]);
                    if (hi>=0&&lo>=0) { out.write((hi<<4)|lo); i+=2; continue; }
                }
            }
            out.write(b);
        }
        return out.toByteArray();
    }

    private String decodeHeader(String value) {
        if (value==null) return "";
        Pattern p=Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=");
        Matcher m=p.matcher(value);
        StringBuffer out=new StringBuffer();
        while (m.find()) {
            String replacement=m.group(0);
            try {
                Charset cs=Charset.forName(m.group(1));
                byte[] decoded;
                if ("B".equalsIgnoreCase(m.group(2))) decoded=Base64.decode(m.group(3),Base64.DEFAULT);
                else decoded=decodeHeaderQ(m.group(3));
                replacement=new String(decoded,cs);
            } catch (Exception ignored) {}
            m.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        String result=repairUtf8Mojibake(out.toString()).replaceAll("\\s+"," ").trim();
        return Html.fromHtml(result,Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private byte[] decodeHeaderQ(String s) {
        return decodeQuotedPrintable(s.replace('_',' ').getBytes(StandardCharsets.ISO_8859_1));
    }

    private String cleanHtml(String html) {
        if (html==null) return "";
        String s=html
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>","")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>","")
                .replaceAll("(?is)<head\\b[^>]*>.*?</head>","")
                .replaceAll("(?is)<noscript\\b[^>]*>.*?</noscript>","")
                .replaceAll("(?is)<svg\\b[^>]*>.*?</svg>","")
                .replaceAll("(?is)<img\\b[^>]*>","");
        return repairUtf8Mojibake(s);
    }

    private String cleanPlain(String body) {
        if (body==null) return "";
        String s=repairUtf8Mojibake(body);
        s=Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString();
        s=s.replaceAll("(?im)^\\s*\\[Photo de [^\\]]+\\]\\s*$","");
        s=s.replaceAll("(?im)^\\s*\\[https?://[^\\]]+\\](?:<[^>]*>)?\\s*$","");
        s=s.replaceAll("(?i)<mailto:[^>]*>","");
        s=s.replaceAll("(?i)<tel:[^>]*>","");
        s=s.replaceAll("(?im)^\\s*https?://\\S+\\s*$","");
        s=s.replaceAll("(?im)^\\s*mailto:\\S+\\s*$","");
        s=s.replaceAll("[ \\t]+\\n","\\n");
        s=s.replaceAll("\\n{3,}","\\n\\n");
        return s.trim();
    }

    private String param(String contentType,String name) {
        if (contentType==null) return null;
        Pattern p=Pattern.compile("(?i)(?:^|;)\\s*"+Pattern.quote(name)+"\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))");
        Matcher m=p.matcher(contentType);
        if (!m.find()) return null;
        return m.group(1)!=null?m.group(1):m.group(2);
    }

    private String value(Map<String,String> map,String key,String fallback) {
        String v=map.get(key);
        return v==null?fallback:v;
    }

    private boolean empty(String s) { return s==null || s.trim().isEmpty(); }

    private int hex(byte b) {
        int c=b&0xff;
        if (c>='0'&&c<='9') return c-'0';
        if (c>='A'&&c<='F') return c-'A'+10;
        if (c>='a'&&c<='f') return c-'a'+10;
        return -1;
    }

    private byte[] readAll(InputStream in) throws Exception {
        if (in==null) throw new Exception("stream");
        try (InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];
            int n;
            while ((n=input.read(buf))!=-1) out.write(buf,0,n);
            return out.toByteArray();
        }
    }

    private void showError(String message) {
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        finish();
    }

    private Button button(String label,boolean primary) {
        Button b=new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?Color.WHITE:INK);
        b.setBackgroundColor(primary?BLUE:Color.WHITE);
        b.setPadding(dp(14),dp(10),dp(14),dp(10));
        return b;
    }

    private TextView text(String value,float size,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
}
