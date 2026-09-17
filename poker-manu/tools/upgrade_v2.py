from pathlib import Path
import re

p=Path(__file__).resolve().parents[1]/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"pokermanu"/"MainActivity.java"
s=p.read_text(encoding="utf-8")

s=s.replace('TextView v=text("V1",12,ACCENT,true);','TextView v=text("V2",12,ACCENT,true);')

finish=r'''    private void finishSession(){
        LinearLayout wrap=column(); wrap.setPadding(dp(20),0,dp(20),0);
        TextView cashLabel=text("CASH-OUT €",12,ACCENT,true); wrap.addView(cashLabel);
        final EditText out=numberInput("Montant final en €"); LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)); op.bottomMargin=dp(12); wrap.addView(out,op);
        TextView handsLabel=text("MAINS JOUÉES (OPTIONNEL)",12,ACCENT,true); wrap.addView(handsLabel);
        final EditText hands=numberInput("Ex. 120"); wrap.addView(hands,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Terminer la session")
                .setMessage("Poker Manu gardera la durée exacte de ta session.")
                .setView(wrap)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Enregistrer",null)
                .create();
        dialog.setOnShowListener(d->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v->{
                String raw=out.getText().toString().trim();
                if(raw.isEmpty()){ out.setError("Indique le cash-out"); return; }
                int handCount=(int)Math.max(0,Math.round(parse(hands.getText().toString())));
                saveFinishedSession(parse(raw),handCount);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void saveFinishedSession(double cashOut,int handsPlayed){
        if(cashOut<0) return;
        long started=prefs.getLong("active_start",System.currentTimeMillis());
        store.saveSession(started,System.currentTimeMillis(),prefs.getString("active_type","Cash Game"),prefs.getString("active_location",""),prefs.getString("active_game","Hold'em"),prefs.getString("active_stakes",""),prefs.getFloat("active_buyin",0),prefs.getFloat("active_rebuys",0),cashOut,handsPlayed);
        double result=cashOut-prefs.getFloat("active_buyin",0)-prefs.getFloat("active_rebuys",0);
        clearActive(); Toast.makeText(this,(result>=0?"Session gagnante ":"Session perdante ")+money(result),Toast.LENGTH_LONG).show(); showHome();
    }'''
s,n=re.subn(r'    private void finishSession\(\)\{.*?\n    \}\n\n    private void saveFinishedSession\(double cashOut\)\{.*?\n    \}',finish,s,count=1,flags=re.S)
if n!=1: raise SystemExit("Bloc fin de session introuvable")

bankroll=r'''    private void showBankroll(){
        currentScreen=2; title.setText("Bankroll");
        LinearLayout box=page(); List<PokerStore.Session> sessions=store.sessions();
        double total=0,buyIns=0,rebuys=0,cashOuts=0; long totalMs=0; int wins=0,totalHands=0;
        for(PokerStore.Session x:sessions){ total+=x.result(); buyIns+=x.buyIn; rebuys+=x.rebuys; cashOuts+=x.cashOut; totalMs+=x.durationMs(); totalHands+=x.handsPlayed; if(x.result()>0)wins++; }
        double invested=buyIns+rebuys;
        double roi=invested>0?total/invested*100.0:0;

        TextView h=text("Bankroll",28,TEXT,true); box.addView(h);
        TextView sub=text("Tes résultats réels, calculés sur la durée exacte de chaque session.",14,MUTED,false); sub.setPadding(0,dp(4),0,dp(14)); box.addView(sub);
        box.addView(metric("PROFIT NET",money(total),total>=0?GREEN:RED),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(118)));

        section(box,"Résumé");
        LinearLayout summary=panel();
        summary.addView(statLine("Buy-in",money(buyIns),TEXT));
        summary.addView(statLine("Rebuys",money(rebuys),ORANGE));
        summary.addView(statLine("Cash-out",money(cashOuts),TEXT));
        summary.addView(statLine("Profit net",money(total),total>=0?GREEN:RED));
        box.addView(summary);

        LinearLayout r1=new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setPadding(0,dp(10),0,0);
        r1.addView(metric("SESSIONS",Integer.toString(sessions.size()),ACCENT),new LinearLayout.LayoutParams(0,dp(106),1));
        LinearLayout.LayoutParams r1p=new LinearLayout.LayoutParams(0,dp(106),1); r1p.setMarginStart(dp(10));
        r1.addView(metric("DURÉE",exactDuration(totalMs),ACCENT),r1p); box.addView(r1);

        LinearLayout r2=new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL); r2.setPadding(0,dp(10),0,0);
        r2.addView(metric("€/HEURE",totalMs>0?money(total/(totalMs/3600000.0))+"/h":"—",total>=0?GREEN:RED),new LinearLayout.LayoutParams(0,dp(106),1));
        LinearLayout.LayoutParams r2p=new LinearLayout.LayoutParams(0,dp(106),1); r2p.setMarginStart(dp(10));
        r2.addView(metric("ROI",sessions.isEmpty()?"—":String.format(FR,"%+.1f %%",roi),roi>=0?GREEN:RED),r2p); box.addView(r2);

        LinearLayout r3=new LinearLayout(this); r3.setOrientation(LinearLayout.HORIZONTAL); r3.setPadding(0,dp(10),0,0);
        r3.addView(metric("SESSIONS GAGNÉES",sessions.isEmpty()?"—":Math.round(wins*100f/sessions.size())+" %",ORANGE),new LinearLayout.LayoutParams(0,dp(106),1));
        LinearLayout.LayoutParams r3p=new LinearLayout.LayoutParams(0,dp(106),1); r3p.setMarginStart(dp(10));
        r3.addView(metric("MAINS JOUÉES",totalHands>0?Integer.toString(totalHands):"—",ACCENT),r3p); box.addView(r3);

        section(box,"Moyennes");
        LinearLayout avg=panel();
        int n=sessions.size();
        avg.addView(statLine("Buy-in moyen",n>0?money(buyIns/n):"—",TEXT));
        avg.addView(statLine("Rebuys € moyens",n>0?money(rebuys/n):"—",ORANGE));
        avg.addView(statLine("Profit moyen",n>0?money(total/n):"—",total>=0?GREEN:RED));
        avg.addView(statLine("Durée moyenne",n>0?exactDuration(totalMs/n):"—",ACCENT));
        box.addView(avg);

        section(box,"Par jeu");
        LinearLayout games=panel();
        java.util.LinkedHashMap<String,double[]> gameMap=new java.util.LinkedHashMap<>();
        for(PokerStore.Session x:sessions){ double[] a=gameMap.get(x.game); if(a==null){a=new double[3];gameMap.put(x.game,a);} a[0]+=x.result();a[1]+=x.durationMs();a[2]++; }
        if(gameMap.isEmpty()) games.addView(text("Aucune donnée",14,MUTED,false));
        else for(java.util.Map.Entry<String,double[]> e:gameMap.entrySet()){ double[] a=e.getValue(); String right=money(a[0])+"  •  "+(a[1]>0?money(a[0]/(a[1]/3600000.0))+"/h":"—"); games.addView(statLine(e.getKey()+"  ·  "+(int)a[2]+" session"+((int)a[2]>1?"s":""),right,a[0]>=0?GREEN:RED)); }
        box.addView(games);

        section(box,"Top lieux");
        LinearLayout places=panel();
        java.util.LinkedHashMap<String,double[]> placeMap=new java.util.LinkedHashMap<>();
        for(PokerStore.Session x:sessions){ double[] a=placeMap.get(x.location); if(a==null){a=new double[3];placeMap.put(x.location,a);} a[0]+=x.result();a[1]+=x.durationMs();a[2]++; }
        java.util.ArrayList<java.util.Map.Entry<String,double[]>> ranked=new java.util.ArrayList<>(placeMap.entrySet());
        ranked.sort((a,b)->Double.compare(b.getValue()[0],a.getValue()[0]));
        if(ranked.isEmpty()) places.addView(text("Aucun lieu enregistré",14,MUTED,false));
        else for(java.util.Map.Entry<String,double[]> e:ranked){ double[] a=e.getValue(); String right=money(a[0])+"  •  "+(a[1]>0?money(a[0]/(a[1]/3600000.0))+"/h":"—"); places.addView(statLine(e.getKey()+"  ·  "+(int)a[2]+" session"+((int)a[2]>1?"s":""),right,a[0]>=0?GREEN:RED)); }
        box.addView(places);

        section(box,"Toutes les sessions");
        if(sessions.isEmpty()) box.addView(empty("Ta première session apparaîtra ici."));
        else for(PokerStore.Session x:sessions){ View row=sessionRow(x); row.setOnClickListener(v->sessionDetails(x)); box.addView(row); }
        setPage(box);
    }'''
s,n=re.subn(r'    private void showBankroll\(\)\{.*?\n    \}\n\n    private void sessionDetails',bankroll+'\n\n    private void sessionDetails',s,count=1,flags=re.S)
if n!=1: raise SystemExit("showBankroll introuvable")

session_details=r'''    private void sessionDetails(PokerStore.Session s){
        String hourly=s.durationMs()>0?money(s.result()/(s.durationMs()/3600000.0))+"/h":"—";
        double bb=parseBigBlind(s.stakes);
        String bbHour=(bb>0&&s.durationMs()>0)?String.format(FR,"%+.2f BB/h",(s.result()/bb)/(s.durationMs()/3600000.0)):"—";
        String per100=s.handsPlayed>0?money(s.result()/s.handsPlayed*100.0):"—";
        String bb100=(bb>0&&s.handsPlayed>0)?String.format(FR,"%+.2f BB/100",(s.result()/bb)/s.handsPlayed*100.0):"—";
        String msg=s.type+"  •  "+s.game+"  •  "+s.stakes+"\n"+s.location+"\n\nBuy-in : "+money(s.buyIn)+"\nRebuys : "+money(s.rebuys)+"\nCash-out : "+money(s.cashOut)+"\nRésultat : "+money(s.result())+"\nDurée exacte : "+exactDuration(s.durationMs())+"\nTaux horaire : "+hourly+"\nMains jouées : "+(s.handsPlayed>0?s.handsPlayed:"—")+"\n€/100 : "+per100+"\nBB/h : "+bbHour+"\nBB/100 : "+bb100;
        new AlertDialog.Builder(this).setTitle(date(s.startedAt)).setMessage(msg).setNegativeButton("Fermer",null).setNeutralButton("Supprimer",(d,w)->{store.deleteSession(s.id);showBankroll();}).show();
    }'''
s,n=re.subn(r'    private void sessionDetails\(PokerStore\.Session s\)\{.*?\n    \}',session_details,s,count=1,flags=re.S)
if n!=1: raise SystemExit("sessionDetails introuvable")

stats=r'''    private void showStats(){
        currentScreen=5; title.setText("Statistiques");
        LinearLayout box=page(); List<PokerStore.Session> sessions=store.sessions();
        double total=0,best=Double.NEGATIVE_INFINITY,worst=Double.POSITIVE_INFINITY,buy=0,rebuys=0; long totalMs=0; int wins=0,totalHands=0;
        double cashProfit=0,tourneyProfit=0; long cashMs=0,tourneyMs=0; int cashN=0,tourneyN=0;
        double resultForHands=0, bbTotal=0; int handsForRate=0; long msForHands=0, msForBB=0;
        double[] week=new double[7], months=new double[12], years=new double[3], durations=new double[5];
        String[] weekLabels={"Lu","Ma","Me","Je","Ve","Sa","Di"};
        String[] monthLabels={"Jan","Fév","Mar","Avr","Mai","Juin","Juil","Aoû","Sep","Oct","Nov","Déc"};
        java.util.Calendar now=java.util.Calendar.getInstance(); int thisYear=now.get(java.util.Calendar.YEAR);
        String[] yearLabels={Integer.toString(thisYear-2),Integer.toString(thisYear-1),Integer.toString(thisYear)};
        double currentMonth=0,previousMonth=0; int currentCount=0,previousCount=0; long currentMs=0,previousMs=0;
        java.util.Calendar prev=(java.util.Calendar)now.clone(); prev.add(java.util.Calendar.MONTH,-1);
        for(PokerStore.Session x:sessions){
            double r=x.result(); total+=r; buy+=x.buyIn; rebuys+=x.rebuys; totalMs+=x.durationMs(); totalHands+=x.handsPlayed; if(r>0)wins++; best=Math.max(best,r); worst=Math.min(worst,r);
            boolean tourney=x.type!=null&&x.type.toLowerCase(FR).contains("tour"); if(tourney){tourneyProfit+=r;tourneyMs+=x.durationMs();tourneyN++;}else{cashProfit+=r;cashMs+=x.durationMs();cashN++;}
            double bb=parseBigBlind(x.stakes);
            if(x.handsPlayed>0){ resultForHands+=r; handsForRate+=x.handsPlayed; msForHands+=x.durationMs(); if(bb>0) bbTotal+=r/bb; }
            if(bb>0) msForBB+=x.durationMs();
            java.util.Calendar c=java.util.Calendar.getInstance(); c.setTimeInMillis(x.startedAt);
            int wi=(c.get(java.util.Calendar.DAY_OF_WEEK)+5)%7; week[wi]+=r;
            months[c.get(java.util.Calendar.MONTH)]+=r;
            int yi=c.get(java.util.Calendar.YEAR)-(thisYear-2); if(yi>=0&&yi<3)years[yi]+=r;
            double hh=x.hours(); int bi=hh<1?0:hh<2?1:hh<4?2:hh<8?3:4; durations[bi]+=r;
            if(sameMonth(c,now)){currentMonth+=r;currentCount++;currentMs+=x.durationMs();}
            else if(sameMonth(c,prev)){previousMonth+=r;previousCount++;previousMs+=x.durationMs();}
        }
        double invested=buy+rebuys, roi=invested>0?total/invested*100.0:0;
        TextView h=text("Performance",28,TEXT,true); box.addView(h);
        TextView sub=text("Analyse complète de tes résultats, sans statistiques inventées.",14,MUTED,false); sub.setPadding(0,dp(4),0,dp(14)); box.addView(sub);
        box.addView(metric("PROFIT TOTAL",money(total),total>=0?GREEN:RED),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(118)));

        LinearLayout r1=new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL); r1.setPadding(0,dp(10),0,0);
        r1.addView(metric("TEMPS DE JEU",exactDuration(totalMs),ACCENT),new LinearLayout.LayoutParams(0,dp(106),1));
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(106),1);p1.setMarginStart(dp(10));
        r1.addView(metric("€/HEURE",totalMs>0?money(total/(totalMs/3600000.0))+"/h":"—",total>=0?GREEN:RED),p1);box.addView(r1);
        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);r2.setPadding(0,dp(10),0,0);
        r2.addView(metric("ROI",sessions.isEmpty()?"—":String.format(FR,"%+.1f %%",roi),roi>=0?GREEN:RED),new LinearLayout.LayoutParams(0,dp(106),1));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(106),1);p2.setMarginStart(dp(10));
        r2.addView(metric("GAGNÉES",sessions.isEmpty()?"—":Math.round(wins*100f/sessions.size())+" %",ORANGE),p2);box.addView(r2);

        section(box,"Stats avancées");
        LinearLayout adv=panel();
        adv.addView(statLine("Mains jouées",totalHands>0?Integer.toString(totalHands):"—",ACCENT));
        adv.addView(statLine("Mains / heure",handsForRate>0&&msForHands>0?String.format(FR,"%.1f",handsForRate/(msForHands/3600000.0)):"—",ACCENT));
        adv.addView(statLine("€/100",handsForRate>0?money(resultForHands/handsForRate*100.0):"—",resultForHands>=0?GREEN:RED));
        adv.addView(statLine("BB/h",msForBB>0?String.format(FR,"%+.2f",bbTotal/(msForBB/3600000.0)):"—",bbTotal>=0?GREEN:RED));
        adv.addView(statLine("BB/100",handsForRate>0?String.format(FR,"%+.2f",bbTotal/handsForRate*100.0):"—",bbTotal>=0?GREEN:RED));
        box.addView(adv);

        section(box,"Mois en cours / mois précédent");
        LinearLayout compare=panel();
        compare.addView(statLine("Sessions",currentCount+"  /  "+previousCount,ACCENT));
        compare.addView(statLine("Temps",exactDuration(currentMs)+"  /  "+exactDuration(previousMs),ACCENT));
        compare.addView(statLine("Profit net",money(currentMonth)+"  /  "+money(previousMonth),currentMonth>=0?GREEN:RED));
        compare.addView(statLine("€/h",(currentMs>0?money(currentMonth/(currentMs/3600000.0)):"—")+"  /  "+(previousMs>0?money(previousMonth/(previousMs/3600000.0)):"—"),currentMonth>=0?GREEN:RED));
        box.addView(compare);

        section(box,"Cash Game / Tournoi");
        LinearLayout types=panel();
        types.addView(statLine("Cash Game  ·  "+cashN+" session"+(cashN>1?"s":""),money(cashProfit)+"  •  "+(cashMs>0?money(cashProfit/(cashMs/3600000.0))+"/h":"—"),cashProfit>=0?GREEN:RED));
        types.addView(statLine("Tournoi  ·  "+tourneyN+" session"+(tourneyN>1?"s":""),money(tourneyProfit)+"  •  "+(tourneyMs>0?money(tourneyProfit/(tourneyMs/3600000.0))+"/h":"—"),tourneyProfit>=0?GREEN:RED));
        box.addView(types);

        section(box,"Graphiques");
        StatsChartView weekChart=new StatsChartView(this); weekChart.setData("Jours de la semaine",weekLabels,week); box.addView(weekChart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(250)));
        LinearLayout.LayoutParams chartGap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(250)); chartGap.topMargin=dp(10);
        StatsChartView monthChart=new StatsChartView(this); monthChart.setData("Mois",monthLabels,months); box.addView(monthChart,chartGap);
        LinearLayout.LayoutParams chartGap2=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(250)); chartGap2.topMargin=dp(10);
        StatsChartView yearChart=new StatsChartView(this); yearChart.setData("Années",yearLabels,years); box.addView(yearChart,chartGap2);
        LinearLayout.LayoutParams chartGap3=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(250)); chartGap3.topMargin=dp(10);
        StatsChartView durationChart=new StatsChartView(this); durationChart.setData("Durée des sessions",new String[]{"<1h","1-2h","2-4h","4-8h","8h+"},durations); box.addView(durationChart,chartGap3);

        section(box,"Records");
        LinearLayout records=panel();
        records.addView(statLine("Meilleure session",sessions.isEmpty()?"—":money(best),GREEN));
        records.addView(statLine("Pire session",sessions.isEmpty()?"—":money(worst),RED));
        records.addView(statLine("Sessions",Integer.toString(sessions.size()),ACCENT));
        records.addView(statLine("Temps total",exactDuration(totalMs),ACCENT));
        box.addView(records);
        setPage(box);
    }'''
s,n=re.subn(r'    private void showStats\(\)\{.*?\n    \}\n\n    private View sessionRow',stats+'\n\n    private View sessionRow',s,count=1,flags=re.S)
if n!=1: raise SystemExit("showStats introuvable")

session_row=r'''    private View sessionRow(PokerStore.Session s){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackground(shape(CARD,14,BORDER)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); rp.bottomMargin=dp(8); row.setLayoutParams(rp);
        LinearLayout left=column(); TextView a=text(s.game+"  •  "+s.stakes,16,TEXT,true); left.addView(a); TextView b=text(date(s.startedAt)+"  ·  "+s.location+"\n"+exactDuration(s.durationMs())+(s.handsPlayed>0?"  ·  "+s.handsPlayed+" mains":""),12,MUTED,false); b.setPadding(0,dp(4),0,0); left.addView(b); row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout right=column(); right.setGravity(Gravity.END); TextView amount=text(money(s.result()),18,s.result()>=0?GREEN:RED,true); amount.setGravity(Gravity.END); right.addView(amount); TextView hr=text(s.durationMs()>0?money(s.result()/(s.durationMs()/3600000.0))+"/h":"—",11,MUTED,false); hr.setGravity(Gravity.END); right.addView(hr); row.addView(right); return row;
    }'''
s,n=re.subn(r'    private View sessionRow\(PokerStore\.Session s\)\{.*?\n    \}',session_row,s,count=1,flags=re.S)
if n!=1: raise SystemExit("sessionRow introuvable")

helpers=r'''    private boolean sameMonth(java.util.Calendar a,java.util.Calendar b){ return a.get(java.util.Calendar.YEAR)==b.get(java.util.Calendar.YEAR)&&a.get(java.util.Calendar.MONTH)==b.get(java.util.Calendar.MONTH); }
    private String exactDuration(long ms){
        long sec=Math.max(0,ms/1000), h=sec/3600, m=(sec%3600)/60, ss=sec%60;
        if(h>0) return String.format(FR,"%d h %02d min %02d s",h,m,ss);
        if(m>0) return String.format(FR,"%d min %02d s",m,ss);
        return ss+" s";
    }
    private double parseBigBlind(String stakes){
        if(stakes==null) return 0;
        String[] parts=stakes.replace(',', '.').split("/");
        if(parts.length<2) return 0;
        try{ return Double.parseDouble(parts[1].replaceAll("[^0-9.]","")); }catch(Exception e){ return 0; }
    }

'''
needle='    private double parse(String s){'
if needle not in s: raise SystemExit("helpers insertion introuvable")
s=s.replace(needle,helpers+needle,1)

p.write_text(s,encoding="utf-8")
print("Poker Manu V2 appliqué")
