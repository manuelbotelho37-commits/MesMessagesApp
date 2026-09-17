package fr.manubotelho.pokermanu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=0xff08232d, TOP=0xff071b22, PANEL=0xff0c303b, CARD=0xff0a2832,
            BORDER=0xff315362, ACCENT=0xff47c9ff, TEXT=0xfff5f8fa, MUTED=0xff91a9b2,
            GREEN=0xff49d28a, RED=0xffff6b6b, ORANGE=0xffffb454;
    private static final String PREFS="poker_manu_state";
    private final Locale FR=Locale.FRANCE;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PokerStore store;
    private SharedPreferences prefs;
    private FrameLayout content;
    private TextView title;
    private int currentScreen=0;
    private Runnable liveTicker;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Window w=getWindow(); w.setStatusBarColor(TOP); w.setNavigationBarColor(TOP);
        store=new PokerStore(this);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        buildShell();
        showHome();
    }

    private void buildShell(){
        LinearLayout root=column(); root.setBackgroundColor(BG);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(18),dp(10),dp(18),dp(10)); top.setBackgroundColor(TOP);
        TextView mark=text("♠",30,ACCENT,true); top.addView(mark,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names=column();
        TextView brand=text("Poker Manu",24,TEXT,true); names.addView(brand);
        title=text("Tableau de bord",13,MUTED,false); names.addView(title);
        top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView v=text("V1",12,ACCENT,true); v.setGravity(Gravity.CENTER); v.setBackground(shape(0xff123e4b,20,0xff123e4b)); top.addView(v,new LinearLayout.LayoutParams(dp(48),dp(34)));
        root.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        HorizontalScrollView navScroll=new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false); navScroll.setBackgroundColor(TOP);
        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(8),dp(6),dp(8),dp(8));
        addNav(nav,"⌂\nAccueil",0); addNav(nav,"▶\nSession",1); addNav(nav,"€\nBankroll",2); addNav(nav,"♣\nMains",3); addNav(nav,"%\nCalcul",4); addNav(nav,"▥\nStats",5);
        navScroll.addView(nav,new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)));
        setContentView(root);
    }

    private void addNav(LinearLayout nav,String label,int screen){
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(11); b.setTextColor(TEXT); b.setGravity(Gravity.CENTER); b.setPadding(dp(8),0,dp(8),0); b.setMinWidth(dp(74)); b.setBackgroundColor(Color.TRANSPARENT); b.setStateListAnimator(null);
        b.setOnClickListener(v->showScreen(screen));
        nav.addView(b,new LinearLayout.LayoutParams(dp(82),dp(58)));
    }

    private void showScreen(int screen){
        currentScreen=screen;
        stopTicker();
        if(screen==0) showHome(); else if(screen==1) showSession(); else if(screen==2) showBankroll(); else if(screen==3) showHands(); else if(screen==4) showCalculator(); else showStats();
    }

    private void showHome(){
        currentScreen=0; title.setText("Tableau de bord");
        LinearLayout box=page();
        List<PokerStore.Session> sessions=store.sessions();
        double total=0,hours=0; int wins=0;
        for(PokerStore.Session s:sessions){ total+=s.result(); hours+=s.hours(); if(s.result()>0) wins++; }

        TextView hello=text("Ton cockpit poker",28,TEXT,true); box.addView(hello);
        TextView sub=text("Tout ce dont tu as besoin avant, pendant et après une session.",15,MUTED,false); sub.setPadding(0,dp(4),0,dp(18)); box.addView(sub);

        Button live=bigAction(prefs.getLong("active_start",0)>0?"●  REPRENDRE LA SESSION LIVE":"▶  DÉMARRER UNE SESSION",ACCENT);
        live.setOnClickListener(v->showScreen(1)); box.addView(live);

        LinearLayout stats=new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setPadding(0,dp(14),0,0);
        stats.addView(metric("BANKROLL",money(total),total>=0?GREEN:RED),new LinearLayout.LayoutParams(0,dp(112),1));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(112),1); gap.setMarginStart(dp(10));
        stats.addView(metric("€/HEURE",hours>0?money(total/hours):"—",total>=0?GREEN:RED),gap); box.addView(stats);

        LinearLayout stats2=new LinearLayout(this); stats2.setOrientation(LinearLayout.HORIZONTAL); stats2.setPadding(0,dp(10),0,0);
        stats2.addView(metric("SESSIONS",Integer.toString(sessions.size()),ACCENT),new LinearLayout.LayoutParams(0,dp(105),1));
        LinearLayout.LayoutParams gap2=new LinearLayout.LayoutParams(0,dp(105),1); gap2.setMarginStart(dp(10));
        stats2.addView(metric("WIN RATE",sessions.isEmpty()?"—":Math.round(wins*100f/sessions.size())+" %",ORANGE),gap2); box.addView(stats2);

        section(box,"Accès rapide");
        LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL);
        Button calc=smallAction("%  Pot odds"); calc.setOnClickListener(v->showScreen(4)); quick.addView(calc,new LinearLayout.LayoutParams(0,dp(62),1));
        Button hand=smallAction("♣  Nouvelle main"); hand.setOnClickListener(v->showScreen(3)); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(62),1); hp.setMarginStart(dp(10)); quick.addView(hand,hp); box.addView(quick);

        section(box,"Dernières sessions");
        if(sessions.isEmpty()) box.addView(empty("Aucune session enregistrée pour l’instant."));
        else for(int i=0;i<Math.min(3,sessions.size());i++) box.addView(sessionRow(sessions.get(i)));
        setPage(box);
    }

    private void showSession(){
        currentScreen=1; title.setText("Session Live");
        if(prefs.getLong("active_start",0)>0) showLiveSession(); else showSessionSetup();
    }

    private void showSessionSetup(){
        LinearLayout box=page();
        TextView h=text("Démarrer une session",28,TEXT,true); box.addView(h);
        TextView s=text("Simple, rapide : tu remplis l’essentiel et Poker Manu fait le reste.",14,MUTED,false); s.setPadding(0,dp(4),0,dp(16)); box.addView(s);
        LinearLayout form=panel();
        Spinner type=spinner(new String[]{"Cash Game","Tournoi"}); addField(form,"TYPE",type);
        Spinner game=spinner(new String[]{"Hold'em","Omaha 4","Omaha 5","Short Deck"}); addField(form,"JEU",game);
        EditText location=input("Ex. Aria, Wynn, Home Game"); addField(form,"LIEU",location);
        EditText stakes=input("Ex. 2/5, 5/10"); addField(form,"BLINDS / LIMITES",stakes);
        EditText buyin=numberInput("Ex. 1000"); addField(form,"BUY-IN €",buyin);
        Button start=bigAction("▶  DÉBUT",ACCENT); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)); sp.topMargin=dp(18); form.addView(start,sp);
        start.setOnClickListener(v->{
            double bi=parse(buyin.getText().toString());
            if(bi<=0){ buyin.setError("Indique ton buy-in"); return; }
            prefs.edit().putLong("active_start",System.currentTimeMillis())
                    .putString("active_type",type.getSelectedItem().toString())
                    .putString("active_game",game.getSelectedItem().toString())
                    .putString("active_location",location.getText().toString().trim())
                    .putString("active_stakes",stakes.getText().toString().trim())
                    .putFloat("active_buyin",(float)bi).putFloat("active_rebuys",0f).apply();
            showLiveSession();
        });
        box.addView(form);
        setPage(box);
    }

    private void showLiveSession(){
        LinearLayout box=page();
        long start=prefs.getLong("active_start",0);
        String game=prefs.getString("active_game","Hold'em"), location=prefs.getString("active_location",""), stakes=prefs.getString("active_stakes","");
        double buy=prefs.getFloat("active_buyin",0), rebuys=prefs.getFloat("active_rebuys",0);
        TextView live=text("● SESSION EN COURS",14,GREEN,true); box.addView(live);
        TextView timer=text("00:00:00",44,TEXT,true); timer.setGravity(Gravity.CENTER); timer.setPadding(0,dp(12),0,dp(10)); box.addView(timer);
        TextView meta=text(game+"  •  "+(stakes.isEmpty()?"Limite non renseignée":stakes)+"\n"+(location.isEmpty()?"Lieu non renseigné":location),16,MUTED,false); meta.setGravity(Gravity.CENTER); box.addView(meta);

        LinearLayout moneyRow=new LinearLayout(this); moneyRow.setOrientation(LinearLayout.HORIZONTAL); moneyRow.setPadding(0,dp(18),0,0);
        moneyRow.addView(metric("BUY-IN",money(buy),ACCENT),new LinearLayout.LayoutParams(0,dp(108),1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(108),1); rp.setMarginStart(dp(10)); moneyRow.addView(metric("REBUYS",money(rebuys),ORANGE),rp); box.addView(moneyRow);
        section(box,"Ajouter un rebuy");
        LinearLayout chips=new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL);
        int[] amounts={50,100,200,500};
        for(int amount:amounts){ Button b=smallAction("+"+amount+" €"); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(56),1); if(chips.getChildCount()>0)p.setMarginStart(dp(6)); chips.addView(b,p); b.setOnClickListener(v->{ prefs.edit().putFloat("active_rebuys",prefs.getFloat("active_rebuys",0)+amount).apply(); showLiveSession(); }); }
        box.addView(chips);
        Button finish=bigAction("■  TERMINER LA SESSION",GREEN); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(60)); fp.topMargin=dp(22); box.addView(finish,fp);
        finish.setOnClickListener(v->finishSession());
        Button cancel=smallAction("Annuler cette session"); cancel.setTextColor(RED); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); cp.topMargin=dp(8); box.addView(cancel,cp);
        cancel.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Annuler la session ?").setMessage("Elle ne sera pas enregistrée.").setNegativeButton("Non",null).setPositiveButton("Oui",(d,w)->{clearActive(); showSession();}).show());
        setPage(box);
        liveTicker=()->{ long elapsed=Math.max(0,System.currentTimeMillis()-start); timer.setText(duration(elapsed)); handler.postDelayed(liveTicker,1000); };
        handler.post(liveTicker);
    }

    private void finishSession(){
        final EditText out=numberInput("Montant final en €");
        int pad=dp(20); FrameLayout wrap=new FrameLayout(this); wrap.setPadding(pad,0,pad,0); wrap.addView(out,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
        new AlertDialog.Builder(this).setTitle("Cash-out").setMessage("Combien as-tu devant toi à la fin ?").setView(wrap).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create().show();
        AlertDialog dialog=(AlertDialog)out.getRootView().getParent();
        // Le fallback ci-dessous est remplacé par le bouton du prochain dialogue si Android ne fournit pas ce parent.
        handler.postDelayed(()->{
            if(dialog!=null){
                Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if(positive!=null) positive.setOnClickListener(v->{ saveFinishedSession(parse(out.getText().toString())); dialog.dismiss(); });
            }
        },50);
    }

    private void saveFinishedSession(double cashOut){
        if(cashOut<0) return;
        long started=prefs.getLong("active_start",System.currentTimeMillis());
        store.saveSession(started,System.currentTimeMillis(),prefs.getString("active_location",""),prefs.getString("active_game","Hold'em"),prefs.getString("active_stakes",""),prefs.getFloat("active_buyin",0),prefs.getFloat("active_rebuys",0),cashOut);
        double result=cashOut-prefs.getFloat("active_buyin",0)-prefs.getFloat("active_rebuys",0);
        clearActive(); Toast.makeText(this,(result>=0?"Session gagnante ":"Session perdante ")+money(result),Toast.LENGTH_LONG).show(); showHome();
    }

    private void clearActive(){ prefs.edit().remove("active_start").remove("active_type").remove("active_game").remove("active_location").remove("active_stakes").remove("active_buyin").remove("active_rebuys").apply(); stopTicker(); }

    private void showBankroll(){
        currentScreen=2; title.setText("Bankroll");
        LinearLayout box=page(); List<PokerStore.Session> sessions=store.sessions();
        double total=0,hours=0; for(PokerStore.Session s:sessions){total+=s.result();hours+=s.hours();}
        TextView h=text("Bankroll & historique",28,TEXT,true); box.addView(h);
        LinearLayout topStats=new LinearLayout(this); topStats.setOrientation(LinearLayout.HORIZONTAL); topStats.setPadding(0,dp(14),0,dp(8));
        topStats.addView(metric("RÉSULTAT CUMULÉ",money(total),total>=0?GREEN:RED),new LinearLayout.LayoutParams(0,dp(112),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(112),1); p.setMarginStart(dp(10)); topStats.addView(metric("TAUX HORAIRE",hours>0?money(total/hours)+"/h":"—",total>=0?GREEN:RED),p); box.addView(topStats);
        section(box,"Toutes les sessions");
        if(sessions.isEmpty()) box.addView(empty("Ta première session apparaîtra ici."));
        else for(PokerStore.Session s:sessions){ View row=sessionRow(s); row.setOnClickListener(v->sessionDetails(s)); box.addView(row); }
        setPage(box);
    }

    private void sessionDetails(PokerStore.Session s){
        String msg=s.game+"  •  "+s.stakes+"\n"+s.location+"\n\nBuy-in : "+money(s.buyIn)+"\nRebuys : "+money(s.rebuys)+"\nCash-out : "+money(s.cashOut)+"\nRésultat : "+money(s.result())+"\nDurée : "+duration((long)(s.hours()*3600000));
        new AlertDialog.Builder(this).setTitle(date(s.startedAt)).setMessage(msg).setNegativeButton("Fermer",null).setNeutralButton("Supprimer",(d,w)->{store.deleteSession(s.id);showBankroll();}).show();
    }

    private void showCalculator(){
        currentScreen=4; title.setText("Calculateur");
        LinearLayout box=page(); TextView h=text("Pot Odds",28,TEXT,true); box.addView(h);
        TextView sub=text("Le calcul le plus utile à table : combien il faut d’équité pour payer.",14,MUTED,false); sub.setPadding(0,dp(4),0,dp(14)); box.addView(sub);
        LinearLayout form=panel();
        EditText pot=numberInput("Ex. 300"); addField(form,"POT AVANT TON CALL €",pot);
        EditText call=numberInput("Ex. 100"); addField(form,"COMBIEN POUR CALL ? €",call);
        EditText outs=numberInput("Ex. 9"); addField(form,"OUTS ESTIMÉS",outs);
        Spinner street=spinner(new String[]{"Flop → River","Turn → River"}); addField(form,"CARTE(S) À VENIR",street);
        TextView result=text("Entre les montants puis appuie sur Calculer.",16,MUTED,false); result.setPadding(dp(4),dp(18),dp(4),dp(14)); form.addView(result);
        Button calculate=bigAction("◉  CALCULER",ACCENT); form.addView(calculate,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
        calculate.setOnClickListener(v->{
            double p=parse(pot.getText().toString()), c=parse(call.getText().toString()); int o=(int)Math.round(parse(outs.getText().toString()));
            if(p<=0||c<=0){result.setText("Indique le pot et le montant à payer."); result.setTextColor(RED); return;}
            o=Math.max(0,Math.min(20,o)); double needed=c/(p+c)*100.0; double eq;
            if(street.getSelectedItemPosition()==0){ double miss1=(47.0-o)/47.0, miss2=(46.0-o)/46.0; eq=(1.0-miss1*miss2)*100.0; }
            else eq=o/46.0*100.0;
            double ev=(eq/100.0)*p-(1.0-eq/100.0)*c;
            String verdict=o==0?"Ajoute tes outs pour comparer.":eq>=needed?"✓ Call mathématiquement défendable":"✕ Équité estimée insuffisante";
            result.setText(String.format(FR,"Pot odds requis : %.1f %%\nÉquité via %d outs : %.1f %%\nEV simplifié : %+.0f €\n\n%s",needed,o,eq,ev,verdict));
            result.setTextColor(o==0?MUTED:eq>=needed?GREEN:RED);
        });
        box.addView(form); setPage(box);
    }

    private void showHands(){
        currentScreen=3; title.setText("Hand Replayer");
        LinearLayout box=page(); TextView h=text("Hand Lab",28,TEXT,true); box.addView(h);
        TextView sub=text("Enregistre rapidement une main et garde les spots importants pour les revoir.",14,MUTED,false); sub.setPadding(0,dp(4),0,dp(12)); box.addView(sub);
        PokerTableView table=new PokerTableView(this); box.addView(table,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(340)));
        LinearLayout form=panel();
        Spinner game=spinner(new String[]{"Hold'em","Omaha 4","Omaha 5"}); addField(form,"JEU",game);
        EditText hero=input("Ex. As Kh"); addField(form,"TES CARTES",hero);
        EditText board=input("Ex. Qs Jh 4d / 2c / 9h"); addField(form,"BOARD",board);
        EditText note=input("Action, sizings, réflexion, résultat..."); note.setMinLines(4); note.setGravity(Gravity.TOP); addField(form,"COMMENTAIRE / ACTION",note);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(0,dp(14),0,0);
        Button preview=smallAction("◎ Prévisualiser"); actions.addView(preview,new LinearLayout.LayoutParams(0,dp(56),1));
        Button save=bigAction("✓ Enregistrer",ACCENT); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(56),1); sp.setMarginStart(dp(8)); actions.addView(save,sp); form.addView(actions);
        preview.setOnClickListener(v->table.setCards(hero.getText().toString(),board.getText().toString()));
        save.setOnClickListener(v->{store.saveHand(hero.getText().toString(),board.getText().toString(),note.getText().toString(),game.getSelectedItem().toString()); Toast.makeText(this,"Main enregistrée",Toast.LENGTH_SHORT).show(); showHands();});
        box.addView(form);
        section(box,"Mains enregistrées"); List<PokerStore.Hand> hands=store.hands();
        if(hands.isEmpty()) box.addView(empty("Aucune main sauvegardée."));
        else for(int i=0;i<Math.min(10,hands.size());i++){ PokerStore.Hand hand=hands.get(i); View row=handRow(hand); row.setOnClickListener(v->handDetails(hand)); box.addView(row); }
        setPage(box);
    }

    private void handDetails(PokerStore.Hand h){
        new AlertDialog.Builder(this).setTitle(h.game+"  •  "+h.hero).setMessage("Board : "+(h.board.isEmpty()?"—":h.board)+"\n\n"+(h.note.isEmpty()?"Aucun commentaire":h.note)).setNegativeButton("Fermer",null).setNeutralButton("Supprimer",(d,w)->{store.deleteHand(h.id);showHands();}).show();
    }

    private void showStats(){
        currentScreen=5; title.setText("Statistiques");
        LinearLayout box=page(); List<PokerStore.Session> sessions=store.sessions();
        double total=0,hours=0,best=Double.NEGATIVE_INFINITY,worst=Double.POSITIVE_INFINITY; int wins=0;
        for(PokerStore.Session s:sessions){ double r=s.result(); total+=r; hours+=s.hours(); if(r>0)wins++; best=Math.max(best,r); worst=Math.min(worst,r); }
        TextView h=text("Performance",28,TEXT,true); box.addView(h);
        TextView sub=text("Les chiffres qui comptent vraiment, sans bruit inutile.",14,MUTED,false); sub.setPadding(0,dp(4),0,dp(14)); box.addView(sub);
        box.addView(metric("PROFIT TOTAL",money(total),total>=0?GREEN:RED),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(118)));
        LinearLayout r1=new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setPadding(0,dp(10),0,0);
        r1.addView(metric("HEURES",String.format(FR,"%.1f h",hours),ACCENT),new LinearLayout.LayoutParams(0,dp(105),1)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(105),1);p.setMarginStart(dp(10));r1.addView(metric("€/HEURE",hours>0?money(total/hours):"—",total>=0?GREEN:RED),p); box.addView(r1);
        LinearLayout r2=new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL); r2.setPadding(0,dp(10),0,0);
        r2.addView(metric("WIN RATE",sessions.isEmpty()?"—":Math.round(wins*100f/sessions.size())+" %",ORANGE),new LinearLayout.LayoutParams(0,dp(105),1)); LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(105),1);p2.setMarginStart(dp(10));r2.addView(metric("SESSIONS",Integer.toString(sessions.size()),ACCENT),p2); box.addView(r2);
        section(box,"Records");
        LinearLayout records=panel(); records.addView(statLine("Meilleure session",sessions.isEmpty()?"—":money(best),GREEN)); records.addView(statLine("Pire session",sessions.isEmpty()?"—":money(worst),RED)); records.addView(statLine("Mains sauvegardées",Integer.toString(store.hands().size()),ACCENT)); box.addView(records);
        setPage(box);
    }

    private View sessionRow(PokerStore.Session s){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackground(shape(CARD,14,BORDER)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); rp.bottomMargin=dp(8); row.setLayoutParams(rp);
        LinearLayout left=column(); TextView a=text(s.game+"  •  "+s.stakes,16,TEXT,true); left.addView(a); TextView b=text(date(s.startedAt)+"  ·  "+s.location,13,MUTED,false); b.setPadding(0,dp(4),0,0); left.addView(b); row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView amount=text(money(s.result()),18,s.result()>=0?GREEN:RED,true); row.addView(amount); return row;
    }

    private View handRow(PokerStore.Hand h){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackground(shape(CARD,14,BORDER)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); rp.bottomMargin=dp(8); row.setLayoutParams(rp);
        TextView cards=text(h.hero,20,ACCENT,true); row.addView(cards,new LinearLayout.LayoutParams(dp(100),ViewGroup.LayoutParams.WRAP_CONTENT)); LinearLayout meta=column(); meta.addView(text(h.game,15,TEXT,true)); TextView b=text(h.board.isEmpty()?"Board non renseigné":h.board,12,MUTED,false); meta.addView(b); row.addView(meta,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); return row;
    }

    private View metric(String label,String value,int color){
        LinearLayout m=column(); m.setGravity(Gravity.CENTER); m.setPadding(dp(10),dp(10),dp(10),dp(10)); m.setBackground(shape(PANEL,16,BORDER)); TextView l=text(label,11,MUTED,true); l.setGravity(Gravity.CENTER); m.addView(l); TextView v=text(value,23,color,true); v.setGravity(Gravity.CENTER); v.setPadding(0,dp(8),0,0); m.addView(v); return m;
    }

    private View statLine(String label,String value,int color){ LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(10),0,dp(10));r.addView(text(label,15,MUTED,false),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(text(value,17,color,true));return r; }

    private LinearLayout page(){ LinearLayout box=column(); box.setPadding(dp(16),dp(18),dp(16),dp(24)); return box; }
    private LinearLayout panel(){ LinearLayout p=column(); p.setPadding(dp(16),dp(14),dp(16),dp(16)); p.setBackground(shape(PANEL,18,BORDER)); return p; }
    private void setPage(LinearLayout box){ ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.addView(box,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); content.removeAllViews(); content.addView(sv,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)); }
    private void section(LinearLayout box,String name){ TextView s=text(name.toUpperCase(FR),13,ACCENT,true); s.setPadding(dp(2),dp(22),0,dp(10)); box.addView(s); }
    private View empty(String msg){ TextView t=text(msg,14,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(18),dp(24),dp(18),dp(24));t.setBackground(shape(CARD,14,BORDER));return t; }

    private void addField(LinearLayout form,String label,View field){ TextView l=text(label,12,ACCENT,true); l.setPadding(0,dp(12),0,dp(6)); form.addView(l); form.addView(field,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(field instanceof EditText && ((EditText)field).getMinLines()>1?110:54))); }

    private Spinner spinner(String[] values){ Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int pos,View cv,ViewGroup parent){TextView v=(TextView)super.getView(pos,cv,parent);v.setTextColor(TEXT);v.setTextSize(16);v.setPadding(dp(12),0,dp(12),0);return v;}}; s.setAdapter(a); s.setPopupBackgroundDrawable(shape(PANEL,8,BORDER)); s.setBackground(shape(CARD,10,BORDER)); return s; }
    private EditText input(String hint){ EditText e=new EditText(this); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setTextSize(16); e.setHint(hint); e.setPadding(dp(12),dp(8),dp(12),dp(8)); e.setBackground(shape(CARD,10,BORDER)); e.setSingleLine(false); return e; }
    private EditText numberInput(String hint){ EditText e=input(hint); e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setSingleLine(true); return e; }

    private Button bigAction(String label,int color){ Button b=new Button(this); b.setText(label); b.setTextSize(16); b.setAllCaps(false); b.setTextColor(color==ACCENT?ACCENT:Color.rgb(5,32,39)); b.setTypeface(Typeface.DEFAULT_BOLD); b.setStateListAnimator(null); int fill=color==ACCENT?CARD:color; b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3347c9ff),shape(fill,28,color),null)); return b; }
    private Button smallAction(String label){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(ACCENT); b.setStateListAnimator(null); b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3347c9ff),shape(CARD,16,BORDER),null)); return b; }
    private TextView text(String value,float size,int color,boolean bold){ TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); t.setLineSpacing(dp(2),1f); return t; }
    private LinearLayout column(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private GradientDrawable shape(int fill,float radius,int border){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); if(border!=fill)g.setStroke(dp(1),border); return g; }

    private double parse(String s){ try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return 0;} }
    private String money(double v){ return String.format(FR,"%+.0f €",v).replace("+0 €","0 €"); }
    private String date(long t){ return new SimpleDateFormat("dd/MM/yy HH:mm",FR).format(new Date(t)); }
    private String duration(long ms){ long sec=ms/1000; return String.format(FR,"%02d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60); }
    private int dp(float v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private void stopTicker(){ if(liveTicker!=null){handler.removeCallbacks(liveTicker);liveTicker=null;} }
    @Override protected void onDestroy(){ stopTicker(); handler.removeCallbacksAndMessages(null); if(store!=null)store.close(); super.onDestroy(); }
}
