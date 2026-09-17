from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

text = MAIN.read_text(encoding="utf-8")

old_colors = '''    private static final int BLUE=0xff174ccb, YELLOW=0xfff2c94c, ORANGE=0xfff28c28,
            INK=0xff152442, MUTED=0xff526078, BG=0xfff4f7fc, BORDER=0xffdce3ef,
            RED=0xffa53223, WHITE=Color.WHITE;'''
new_colors = '''    private static int BLUE=0xff174ccb, YELLOW=0xfff2c94c, ORANGE=0xfff28c28,
            INK=0xff152442, MUTED=0xff526078, BG=0xfff4f7fc, BORDER=0xffdce3ef,
            RED=0xffa53223, WHITE=Color.WHITE;'''
if old_colors not in text:
    raise SystemExit("Palette MainActivity introuvable")
text = text.replace(old_colors, new_colors, 1)

old_prefs = '    private static final String PREFS="appearance", PREF_TODO="todoColor", PREF_DONE="doneColor", PREF_ADD="addColor";'
new_prefs = '    private static final String PREFS="appearance", PREF_TODO="todoColor", PREF_DONE="doneColor", PREF_ADD="addColor", PREF_DARK="darkMode";'
if old_prefs not in text:
    raise SystemExit("Préférences d'apparence introuvables")
text = text.replace(old_prefs, new_prefs, 1)

old_field = '    private boolean showingDone=false;\n    private boolean wideLayout=false;'
new_field = '    private boolean showingDone=false;\n    private boolean wideLayout=false;\n    private boolean darkMode=false;'
if old_field not in text:
    raise SystemExit("Champ wideLayout introuvable")
text = text.replace(old_field, new_field, 1)

old_oncreate = '''        store=new TaskStore(this);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        if (state!=null) showingDone=state.getBoolean("showingDone",false);
        makeScreen();'''
new_oncreate = '''        store=new TaskStore(this);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        darkMode=prefs.getBoolean(PREF_DARK,false);
        applyAppearance();
        if (state!=null) showingDone=state.getBoolean("showingDone",false);
        makeScreen();'''
if old_oncreate not in text:
    raise SystemExit("onCreate MainActivity introuvable")
text = text.replace(old_oncreate, new_oncreate, 1)

old_color_button = '''        Button colors=button("🎨  Couleurs",false); colors.setId(COLORS);
        colors.setMinHeight(dp(44)); colors.setMinimumHeight(dp(44));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin=dp(8); heading.addView(colors,cp);
        colors.setOnClickListener(v->openColorSettings());
        controls.addView(heading);'''
new_color_button = '''        Button colors=button("🎨  Couleurs",false); colors.setId(COLORS);
        colors.setMinHeight(dp(44)); colors.setMinimumHeight(dp(44));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin=dp(8); heading.addView(colors,cp);
        colors.setOnClickListener(v->openColorSettings());

        Button appearance=button(darkMode?"☀  Mode normal":"🌙  Mode sombre",false);
        appearance.setMinHeight(dp(44)); appearance.setMinimumHeight(dp(44));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin=dp(7); heading.addView(appearance,ap);
        appearance.setOnClickListener(v->{
            prefs.edit().putBoolean(PREF_DARK,!darkMode).apply();
            recreate();
        });
        controls.addView(heading);'''
if old_color_button not in text:
    raise SystemExit("Bouton Couleurs introuvable")
text = text.replace(old_color_button, new_color_button, 1)

old_form = '        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(6));'
new_form = '        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(6)); form.setBackgroundColor(BG);'
if old_form not in text:
    raise SystemExit("Formulaire éditeur introuvable")
text = text.replace(old_form, new_form, 1)

old_hint = '        titleField.setHint("Ex. Appeler un client");'
new_hint = '        titleField.setHint("Ex. Appeler un client");\n        titleField.setHintTextColor(MUTED);'
if old_hint not in text:
    raise SystemExit("Hint titre introuvable")
text = text.replace(old_hint, new_hint, 1)

old_contrast = '''    private int contrastText(int color) {
        double luminance=(0.299*Color.red(color)+0.587*Color.green(color)+0.114*Color.blue(color));
        return luminance>165?INK:WHITE;
    }'''
new_contrast = '''    private int contrastText(int color) {
        double luminance=(0.299*Color.red(color)+0.587*Color.green(color)+0.114*Color.blue(color));
        return luminance>165?0xff152442:Color.WHITE;
    }'''
if old_contrast not in text:
    raise SystemExit("contrastText introuvable")
text = text.replace(old_contrast, new_contrast, 1)

marker = '    private void refresh() {'
appearance_method = '''    private void applyAppearance() {
        if (darkMode) {
            BLUE=0xff6f9cff;
            YELLOW=0xffffd965;
            ORANGE=0xffffa24b;
            INK=0xfff4f7fc;
            MUTED=0xffb4c0d4;
            BG=0xff0f1520;
            BORDER=0xff354158;
            RED=0xffff7b72;
            WHITE=0xff1b2433;
        } else {
            BLUE=0xff174ccb;
            YELLOW=0xfff2c94c;
            ORANGE=0xfff28c28;
            INK=0xff152442;
            MUTED=0xff526078;
            BG=0xfff4f7fc;
            BORDER=0xffdce3ef;
            RED=0xffa53223;
            WHITE=Color.WHITE;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT>=23) {
            int flags=getWindow().getDecorView().getSystemUiVisibility();
            if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT>=26) {
                if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

'''
if marker not in text:
    raise SystemExit("Point d'insertion refresh introuvable")
text = text.replace(marker, appearance_method + marker, 1)

MAIN.write_text(text, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s+\d+", "versionCode 109", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '3.8.0-dark-mode'", gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Insisto 3.8 : bouton Mode sombre / Mode normal ajouté et préférence mémorisée")
