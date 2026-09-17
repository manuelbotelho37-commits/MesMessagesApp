from pathlib import Path

p=Path(__file__).resolve().parents[1]/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"pokermanu"/"MainActivity.java"
s=p.read_text(encoding="utf-8")

# re.sub interprète les \\n des blocs de remplacement : on répare uniquement
# les deux chaînes multi-lignes produites par le générateur V2.
old1='''String msg=s.type+"  •  "+s.game+"  •  "+s.stakes+"
"+s.location+"

Buy-in : "+money(s.buyIn)+"
Rebuys : "+money(s.rebuys)+"
Cash-out : "+money(s.cashOut)+"
Résultat : "+money(s.result())+"
Durée exacte : "+exactDuration(s.durationMs())+"
Taux horaire : "+hourly+"
Mains jouées : "+(s.handsPlayed>0?s.handsPlayed:"—")+"
€/100 : "+per100+"
BB/h : "+bbHour+"
BB/100 : "+bb100;'''
new1='''String msg=s.type+"  •  "+s.game+"  •  "+s.stakes+"\\n"+s.location+"\\n\\nBuy-in : "+money(s.buyIn)+"\\nRebuys : "+money(s.rebuys)+"\\nCash-out : "+money(s.cashOut)+"\\nRésultat : "+money(s.result())+"\\nDurée exacte : "+exactDuration(s.durationMs())+"\\nTaux horaire : "+hourly+"\\nMains jouées : "+(s.handsPlayed>0?s.handsPlayed:"—")+"\\n€/100 : "+per100+"\\nBB/h : "+bbHour+"\\nBB/100 : "+bb100;'''

old2='''TextView b=text(date(s.startedAt)+"  ·  "+s.location+"
"+exactDuration(s.durationMs())+(s.handsPlayed>0?"  ·  "+s.handsPlayed+" mains":""),12,MUTED,false);'''
new2='''TextView b=text(date(s.startedAt)+"  ·  "+s.location+"\\n"+exactDuration(s.durationMs())+(s.handsPlayed>0?"  ·  "+s.handsPlayed+" mains":""),12,MUTED,false);'''

if old1 not in s:
    raise SystemExit("Fiche session V2 introuvable")
if old2 not in s:
    raise SystemExit("Ligne session V2 introuvable")

s=s.replace(old1,new1,1).replace(old2,new2,1)
p.write_text(s,encoding="utf-8")
print("Retours ligne V2 corrigés")
