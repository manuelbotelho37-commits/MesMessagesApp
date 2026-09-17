from pathlib import Path

p=Path(__file__).resolve().parents[1]/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"pokermanu"/"MainActivity.java"
s=p.read_text(encoding="utf-8")

old='''        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        HorizontalScrollView navScroll=new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false); navScroll.setBackgroundColor(TOP);
        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(8),dp(6),dp(8),dp(8));
        addNav(nav,"⌂\\nAccueil",0); addNav(nav,"▶\\nSession",1); addNav(nav,"€\\nBankroll",2); addNav(nav,"♣\\nMains",3); addNav(nav,"%\\nCalcul",4); addNav(nav,"▥\\nStats",5);
        navScroll.addView(nav,new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)));
        setContentView(root);'''

new='''        // Navigation volontairement placée en haut : elle reste toujours visible
        // et ne peut plus être masquée par la barre des tâches / navigation Samsung.
        HorizontalScrollView navScroll=new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false); navScroll.setBackgroundColor(TOP);
        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(8),dp(4),dp(8),dp(6));
        addNav(nav,"⌂\\nAccueil",0); addNav(nav,"▶\\nSession",1); addNav(nav,"€\\nBankroll",2); addNav(nav,"♣\\nMains",3); addNav(nav,"%\\nCalcul",4); addNav(nav,"▥\\nStats",5);
        navScroll.addView(nav,new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navScroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(66)));

        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);'''

if old not in s:
    raise SystemExit("Bloc de navigation Poker Manu introuvable")

p.write_text(s.replace(old,new,1),encoding="utf-8")
print("Barre Poker Manu déplacée en haut")
