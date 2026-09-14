from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"/"MainActivity.java"

text=MAIN.read_text(encoding="utf-8")

# Imports for Android's built-in speech recognizer UI.
if 'import android.speech.RecognizerIntent;' not in text:
    text=text.replace('import android.os.Looper;\n', 'import android.os.Looper;\nimport android.speech.RecognizerIntent;\n', 1)
if 'import java.util.ArrayList;' not in text:
    text=text.replace('import java.util.Calendar;\n', 'import java.util.ArrayList;\nimport java.util.Calendar;\n', 1)

# Request code used when the speech recognizer returns the dictated text.
text=text.replace(
    'static final int ADD=1001, TODO=1002, DONE=1003, TITLE=1004, DATE=1005, TIME=1006, COLORS=1007;',
    'static final int ADD=1001, TODO=1002, DONE=1003, TITLE=1004, DATE=1005, TIME=1006, COLORS=1007, VOICE_REQUEST=8001;',
    1)

# Put a simple microphone button immediately beside the task text field.
old='''        titleField.setText(saved!=null?saved.getString("draftTitle",""):task==null?"":task.title);\n        form.addView(titleField,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        form.addView(fieldLabel("Date"));'''
new='''        titleField.setText(saved!=null?saved.getString("draftTitle",""):task==null?"":task.title);\n\n        LinearLayout titleRow=new LinearLayout(this);\n        titleRow.setOrientation(LinearLayout.HORIZONTAL);\n        titleRow.setGravity(Gravity.CENTER_VERTICAL);\n        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);\n        titleRow.addView(titleField,titleParams);\n        Button mic=button("🎤",false);\n        mic.setTextSize(24);\n        mic.setContentDescription("Dicter la tâche");\n        mic.setMinWidth(dp(58)); mic.setMinimumWidth(dp(58));\n        LinearLayout.LayoutParams micParams=new LinearLayout.LayoutParams(dp(62),ViewGroup.LayoutParams.WRAP_CONTENT);\n        micParams.setMarginStart(dp(8));\n        titleRow.addView(mic,micParams);\n        mic.setOnClickListener(v->startVoiceInput());\n        form.addView(titleRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));\n\n        form.addView(fieldLabel("Date"));'''
if old not in text:
    raise SystemExit("Zone texte de tâche introuvable pour le bouton micro")
text=text.replace(old,new,1)

# Voice launcher and result handling. This uses the speech-recognition app already present on Android,
# so Mes tâches Manu does not need to record or store raw microphone audio itself.
anchor='''    @Override protected void onSaveInstanceState(Bundle out) {'''
insert='''    private void startVoiceInput() {\n        hideKeyboard();\n        Intent voice=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);\n        voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);\n        voice.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.FRANCE.toLanguageTag());\n        voice.putExtra(RecognizerIntent.EXTRA_PROMPT,"Dicte ta tâche");\n        try {\n            startActivityForResult(voice,VOICE_REQUEST);\n        } catch (android.content.ActivityNotFoundException ex) {\n            Toast.makeText(this,"La dictée vocale n’est pas disponible sur ce téléphone.",Toast.LENGTH_LONG).show();\n        }\n    }\n\n    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {\n        super.onActivityResult(requestCode,resultCode,data);\n        if (requestCode!=VOICE_REQUEST || resultCode!=RESULT_OK || data==null || titleField==null) return;\n        ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);\n        if (results==null || results.isEmpty()) return;\n        String spoken=results.get(0)==null?"":results.get(0).trim();\n        if (spoken.isEmpty()) return;\n        titleField.setText(spoken);\n        titleField.setSelection(titleField.length());\n    }\n\n    @Override protected void onSaveInstanceState(Bundle out) {'''
if anchor not in text:
    raise SystemExit("Point d'insertion dictée introuvable")
text=text.replace(anchor,insert,1)

MAIN.write_text(text,encoding="utf-8")
print("Bouton micro de dictée appliqué")
