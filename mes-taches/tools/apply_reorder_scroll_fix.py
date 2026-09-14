from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DETAIL = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "TaskDetailActivity.java"

text = DETAIL.read_text(encoding="utf-8")

old = '''        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Choisis ton ordre")
                .setMessage("Utilise ↑ et ↓. Ton ordre sera gardé pour toutes les tâches.")
                .setView(box)
                .setNeutralButton("Ordre d’origine",(d,w)->getSharedPreferences(MENU_PREFS,MODE_PRIVATE).edit().remove(MENU_ORDER).apply())
                .setPositiveButton("Terminé",null)
                .create();
        box.setTag(dialog);
        dialog.show();
'''

new = '''        ScrollView orderScroll=new ScrollView(this);
        orderScroll.setFillViewport(false);
        orderScroll.setClipToPadding(false);
        orderScroll.setPadding(0,0,0,dp(36));
        int maxListHeight=(int)(getResources().getDisplayMetrics().heightPixels*0.56f);
        orderScroll.addView(box,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        orderScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,maxListHeight));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Choisis ton ordre")
                .setMessage("Utilise ↑ et ↓. Fais défiler la liste pour voir les derniers éléments.")
                .setView(orderScroll)
                .setNeutralButton("Ordre d’origine",(d,w)->getSharedPreferences(MENU_PREFS,MODE_PRIVATE).edit().remove(MENU_ORDER).apply())
                .setPositiveButton("Terminé",null)
                .create();
        box.setTag(dialog);
        dialog.show();
'''

if new in text:
    print("Correctif défilement déjà appliqué")
elif old in text:
    DETAIL.write_text(text.replace(old,new,1),encoding="utf-8")
    print("Correctif défilement ordre appliqué")
else:
    raise SystemExit(f"Bloc réorganisation introuvable dans {DETAIL}")
