from pathlib import Path

p=Path(__file__).resolve().parents[1]/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"pokermanu"/"MainActivity.java"
s=p.read_text(encoding="utf-8")
old='''        new AlertDialog.Builder(this).setTitle("Cash-out").setMessage("Combien as-tu devant toi à la fin ?").setView(wrap).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",null).create().show();
        AlertDialog dialog=(AlertDialog)out.getRootView().getParent();
        // Le fallback ci-dessous est remplacé par le bouton du prochain dialogue si Android ne fournit pas ce parent.
        handler.postDelayed(()->{
            if(dialog!=null){
                Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if(positive!=null) positive.setOnClickListener(v->{ saveFinishedSession(parse(out.getText().toString())); dialog.dismiss(); });
            }
        },50);'''
new='''        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Cash-out")
                .setMessage("Combien as-tu devant toi à la fin ?")
                .setView(wrap)
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Enregistrer",null)
                .create();
        dialog.setOnShowListener(d->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v->{
                String raw=out.getText().toString().trim();
                if(raw.isEmpty()){ out.setError("Indique le cash-out"); return; }
                saveFinishedSession(parse(raw));
                dialog.dismiss();
            });
        });
        dialog.show();'''
if old not in s:
    raise SystemExit("Bloc cash-out V1 introuvable")
p.write_text(s.replace(old,new,1),encoding="utf-8")
print("Correctif Poker Manu V1 appliqué")
