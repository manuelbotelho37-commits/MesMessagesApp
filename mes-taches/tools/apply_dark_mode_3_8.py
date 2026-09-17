from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches" / "MainActivity.java"
GRADLE = ROOT / "app" / "build.gradle"

text = MAIN.read_text(encoding="utf-8")

# Palette dynamique : claire par défaut, sombre quand l'utilisateur le choisit.
old_colors = '''    private static final int BLUE=0xff174ccb, YELLOW=0xfff2c94c, ORANGE=0xfff28c28,
            INK=0xff152442, MUTED=0xff526078, BG=0xfff4f7fc, BORDER=0xffdce3ef,
            RED=0xffa53223, WHITE=Color.WHITE;'''
new_colors = '''    private static int BLUE=0xff174ccb, YELLOW=0xfff2c94c, ORANGE=0xfff28c28,
            INK=0xff152442, MUTED=0xff526078, BG=0xfff4f7fc, BORDER=0xffdce3ef,
            RED=0xffa53223, WHITE=Color.WHITE;'''
if old_colors in text:
    text = text.replace(old_colors, new_colors, 1)
elif new_colors not in text:
    raise SystemExit("Palette MainActivity introuvable")

# Ajouter la préférence sombre quelle que soit la liste de préférences déjà enrichie.
if 'PREF_DARK="darkMode"' not in text:
    pattern = r'(private static final String PREFS="appearance"[^;]*)(;)'
    text, count = re.subn(pattern, r'\1, PREF_DARK="darkMode"\2', text, count=1)
    if count != 1:
        raise SystemExit("Préférences d'apparence introuvables")

if 'private boolean darkMode=false;' not in text:
    anchor = '    private boolean wideLayout=false;'
    if anchor not in text:
        raise SystemExit("Champ wideLayout introuvable")
    text = text.replace(anchor, anchor + '\n    private boolean darkMode=false;', 1)

# Charger l'apparence avant de construire l'interface.
load_anchor = '        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);'
load_block = '''        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        darkMode=prefs.getBoolean(PREF_DARK,false);
        applyAppearance();'''
if 'darkMode=prefs.getBoolean(PREF_DARK,false);' not in text:
    if load_anchor not in text:
        raise SystemExit("Chargement des préférences introuvable")
    text = text.replace(load_anchor, load_block, 1)

# Bouton dans la partie gauche, juste sous Couleurs.
if 'Mode sombre' not in text:
    button_anchor = '        colors.setOnClickListener(v->openColorSettings());'
    button_block = '''        colors.setOnClickListener(v->openColorSettings());

        Button appearance=button(darkMode?"☀  Mode normal":"🌙  Mode sombre",false);
        appearance.setMinHeight(dp(44)); appearance.setMinimumHeight(dp(44));
        if (compactWide) { appearance.setTextSize(10); appearance.setPadding(dp(4),dp(3),dp(4),dp(3)); }
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin=dp(7); heading.addView(appearance,ap);
        appearance.setOnClickListener(v->{
            prefs.edit().putBoolean(PREF_DARK,!darkMode).apply();
            recreate();
        });'''
    if button_anchor not in text:
        raise SystemExit("Bouton Couleurs introuvable")
    text = text.replace(button_anchor, button_block, 1)

# Formulaire de tâche lisible dans les deux modes.
form_anchor = '        LinearLayout form=column(); form.setPadding(dp(22),dp(10),dp(22),dp(6));'
if form_anchor in text and 'form.setBackgroundColor(BG);' not in text:
    text = text.replace(form_anchor, form_anchor + ' form.setBackgroundColor(BG);', 1)

hint_anchor = '        titleField.setHint("Ex. Appeler un client");'
if hint_anchor in text and 'titleField.setHintTextColor(MUTED);' not in text:
    text = text.replace(hint_anchor, hint_anchor + '\n        titleField.setHintTextColor(MUTED);', 1)

# Les boutons radio ajoutés par les versions précédentes suivent aussi la palette.
text = text.replace(
    'option.setText(repeatNames[i]); option.setTextSize(16); option.setTag(repeatValues[i]);',
    'option.setText(repeatNames[i]); option.setTextSize(16); option.setTextColor(INK); option.setButtonTintList(ColorStateList.valueOf(BLUE)); option.setTag(repeatValues[i]);',
    1,
)
text = text.replace(
    'option.setText(priorityNames[i]); option.setTextSize(15); option.setTag(i); option.setChecked(i==selectedLevel);',
    'option.setText(priorityNames[i]); option.setTextSize(15); option.setTextColor(INK); option.setButtonTintList(ColorStateList.valueOf(BLUE)); option.setTag(i); option.setChecked(i==selectedLevel);',
    1,
)

# Contraste stable des textes sur les couleurs personnalisées.
text = text.replace('return luminance>165?INK:WHITE;', 'return luminance>165?0xff152442:Color.WHITE;', 1)

if 'private void applyAppearance()' not in text:
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
