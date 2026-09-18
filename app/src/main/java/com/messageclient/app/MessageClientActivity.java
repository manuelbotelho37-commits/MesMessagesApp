package com.messageclient.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

public class MessageClientActivity extends Activity {
    private static final int ORANGE = Color.rgb(244, 123, 32);
    private static final int BG = Color.rgb(15, 16, 18);
    private static final int PANEL = Color.rgb(24, 27, 31);
    private static final int PANEL2 = Color.rgb(34, 38, 44);
    private static final int TEXT = Color.rgb(245, 246, 247);
    private static final int MUTED = Color.rgb(165, 171, 180);

    private static final String PREFS = "message_client_settings";
    private static final String KEY_BUBBLE = "bubble_enabled";
    private static final int REQ_BACKUP_CREATE = 4101;
    private static final int REQ_BACKUP_OPEN = 4102;
    private static final int REQ_AUTO_BACKUP = 4103;

    private LinearLayout listContainer;
    private EditText search;
    private CheckBox favoritesOnly;
    private Switch bubbleSwitch;
    private Button autoBackupButton;
    private List<MessageStore.MessageTemplate> templates = new ArrayList<>();
    private final Set<String> expandedMessageIds = new HashSet<>();
    private boolean waitingOverlayPermission = false;
    private boolean suppressSwitch = false;
    private boolean compactTwoPane = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        compactTwoPane = screenWidthDp < 600;

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BG);
        root.setPadding(dp(compactTwoPane ? 6 : 14),
                dp(compactTwoPane ? 8 : 14),
                dp(compactTwoPane ? 6 : 14),
                dp(8));

        // Always use the two-panel layout, even on the Fold cover screen.
        buildTwoPaneLayout(root);

        setContentView(root);

        templates = MessageStore.load(this);
        renderList();
    }

    private void buildTwoPaneLayout(LinearLayout root) {
        root.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(compactTwoPane ? 6 : 14), 0);

        left.addView(compactTwoPane ? buildCompactHeader() : buildHeader());

        View bubble = compactTwoPane ? buildCompactBubbleCard() : buildBubbleCard();
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleLp.topMargin = dp(compactTwoPane ? 8 : 14);
        left.addView(bubble, bubbleLp);

        View directInsert = compactTwoPane ? buildCompactDirectInsertCard() : buildDirectInsertCard();
        LinearLayout.LayoutParams directLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        directLp.topMargin = dp(compactTwoPane ? 6 : 10);
        left.addView(directInsert, directLp);

        View searchRow = compactTwoPane ? buildCompactSearchRow() : buildSearchRow();
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(compactTwoPane ? 8 : 14);
        left.addView(searchRow, searchLp);

        View backupRow = buildBackupRow();
        LinearLayout.LayoutParams backupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backupLp.topMargin = dp(compactTwoPane ? 6 : 10);
        left.addView(backupRow, backupLp);

        View spacer = new View(this);
        left.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = buildAddButton();
        if (compactTwoPane) {
            add.setText("+ Ajouter");
            add.setTextSize(12);
            add.setPadding(dp(2), 0, dp(2), 0);
        }
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compactTwoPane ? 44 : 54));
        addLp.topMargin = dp(compactTwoPane ? 8 : 12);
        left.addView(add, addLp);

        if (!compactTwoPane) {
            TextView local = new TextView(this);
            local.setText("Enregistré sur ce téléphone");
            local.setTextColor(MUTED);
            local.setTextSize(11);
            local.setGravity(Gravity.CENTER);
            local.setPadding(0, dp(8), 0, dp(2));
            left.addView(local);
        }

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(compactTwoPane ? 7 : 16), 0, 0, 0);

        TextView listTitle = new TextView(this);
        listTitle.setText("Mes messages");
        listTitle.setTextColor(TEXT);
        listTitle.setTextSize(compactTwoPane ? 17 : 24);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        listTitle.setPadding(0, dp(2), 0, dp(2));
        right.addView(listTitle);

        if (!compactTwoPane) {
            TextView listSub = new TextView(this);
            listSub.setText("Faites défiler vos modèles et ouvrez-les avec « Voir tout »");
            listSub.setTextColor(MUTED);
            listSub.setTextSize(12);
            listSub.setPadding(0, 0, 0, dp(8));
            right.addView(listSub);
        }

        right.addView(buildMessagesScroll(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(45, 49, 56));

        float leftWeight = compactTwoPane ? 0.36f : 0.36f;
        float rightWeight = compactTwoPane ? 0.64f : 0.64f;

        root.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, leftWeight));
        root.addView(divider, new LinearLayout.LayoutParams(dp(1),
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(right, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, rightWeight));
    }

    private View buildCompactHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView logo = new TextView(this);
        logo.setText("M");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(18);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(ORANGE, 12, 0, 0));
        box.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(this);
        title.setText("Message\nClient");
        title.setTextColor(TEXT);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(5), 0, 0);
        box.addView(title);
        return box;
    }

    private View buildCompactBubbleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(5), dp(7), dp(5), dp(7));
        card.setBackground(rounded(PANEL, 12, Color.rgb(48, 52, 59), 1));

        TextView title = new TextView(this);
        title.setText("Bulle M");
        title.setTextColor(TEXT);
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        bubbleSwitch = new Switch(this);
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BUBBLE, false);
        bubbleSwitch.setChecked(enabled && Settings.canDrawOverlays(this));
        bubbleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitch) return;
            if (isChecked) enableBubble();
            else disableBubble();
        });
        card.addView(bubbleSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        return card;
    }

    private View buildCompactDirectInsertCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(5), dp(7), dp(5), dp(7));
        card.setBackground(rounded(PANEL, 12, Color.rgb(48, 52, 59), 1));

        TextView title = new TextView(this);
        title.setText("SMS direct");
        title.setTextColor(TEXT);
        title.setTextSize(11);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView state = new TextView(this);
        state.setText(MessageInsertAccessibilityService.isConnected() ? "Activée ✓" : "À activer");
        state.setTextColor(MessageInsertAccessibilityService.isConnected()
                ? Color.rgb(117, 214, 137) : MUTED);
        state.setTextSize(10);
        state.setGravity(Gravity.CENTER);
        card.addView(state);

        Button settings = new Button(this);
        settings.setText("Réglages");
        settings.setAllCaps(false);
        settings.setTextColor(Color.WHITE);
        settings.setTextSize(10);
        settings.setPadding(dp(2), 0, dp(2), 0);
        settings.setBackground(rounded(ORANGE, 9, 0, 0));
        settings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        lp.topMargin = dp(5);
        card.addView(settings, lp);
        return card;
    }

    private View buildCompactSearchRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Rechercher");
        search.setHintTextColor(Color.rgb(115, 121, 130));
        search.setTextColor(TEXT);
        search.setTextSize(11);
        search.setPadding(dp(7), 0, dp(5), 0);
        search.setBackground(rounded(PANEL2, 10, Color.rgb(54, 59, 67), 1));
        wrap.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        favoritesOnly = new CheckBox(this);
        favoritesOnly.setText("Favoris");
        favoritesOnly.setTextColor(MUTED);
        favoritesOnly.setTextSize(10);
        favoritesOnly.setPadding(0, 0, 0, 0);
        wrap.addView(favoritesOnly);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        favoritesOnly.setOnCheckedChangeListener((b, checked) -> renderList());
        return wrap;
    }

    private View buildBackupRow() {
        boolean narrow = isNarrowLeftPane();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        boolean autoConfigured = AutoBackupManager.isConfigured(this);
        autoBackupButton = smallButton(autoConfigured ? "Sauvegarde auto ✓" : "Sauvegarde auto");
        autoBackupButton.setSingleLine(true);
        autoBackupButton.setTextSize(narrow ? 10 : 12);
        autoBackupButton.setOnClickListener(v -> startAutoBackupSetup());

        Button restore = smallButton("Restaurer");
        restore.setSingleLine(true);
        restore.setTextSize(narrow ? 11 : 12);
        restore.setOnClickListener(v -> startBackupImport());

        if (narrow) {
            box.addView(autoBackupButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

            LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            restoreLp.topMargin = dp(6);
            box.addView(restore, restoreLp);
        } else {
            box.addView(autoBackupButton, new LinearLayout.LayoutParams(
                    0, dp(42), 1f));

            LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                    0, dp(42), 1f);
            restoreLp.leftMargin = dp(6);
            box.addView(restore, restoreLp);
        }

        return box;
    }

    private boolean isNarrowLeftPane() {
        float leftPaneDp = getResources().getConfiguration().screenWidthDp * 0.36f;
        return leftPaneDp < 280f;
    }

    private void startAutoBackupSetup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_AUTO_BACKUP);
    }

    private void configureAutoBackup(Uri uri, Intent data) {
        try {
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);

            if (!AutoBackupManager.writeNow(this, uri, templates)) {
                Toast.makeText(this,
                        "Impossible d'écrire dans ce fichier",
                        Toast.LENGTH_LONG).show();
                return;
            }

            AutoBackupManager.setBackupUri(this, uri);
            if (autoBackupButton != null) {
                autoBackupButton.setText("Sauvegarde auto ✓");
            }
            Toast.makeText(this,
                    "Sauvegarde automatique activée ✓",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Impossible d'activer la sauvegarde automatique avec ce fichier",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Message-Client-sauvegarde.json");
        startActivityForResult(intent, REQ_BACKUP_CREATE);
    }

    private void startBackupImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_BACKUP_OPEN);
    }

    private void exportBackup(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new Exception("Impossible d'ouvrir le fichier");
            String data = MessageStore.createBackup(templates);
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this,
                    templates.size() + " message(s) sauvegardé(s) ✓",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Échec de la sauvegarde", Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Impossible d'ouvrir le fichier");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line).append('\n');
            }

            List<MessageStore.MessageTemplate> restored =
                    MessageStore.parseBackup(raw.toString());

            new AlertDialog.Builder(this)
                    .setTitle("Restaurer la sauvegarde")
                    .setMessage("Cette sauvegarde contient " + restored.size()
                            + " message(s).\n\nElle remplacera les messages actuellement enregistrés dans Message Client.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Restaurer", (dialog, which) -> {
                        templates = new ArrayList<>(restored);
                        MessageStore.save(this, templates);
                        expandedMessageIds.clear();
                        renderList();
                        Toast.makeText(this,
                                restored.size() + " message(s) restauré(s) ✓",
                                Toast.LENGTH_LONG).show();
                    })
                    .show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Ce fichier n'est pas une sauvegarde Message Client valide",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == REQ_AUTO_BACKUP) {
            configureAutoBackup(uri, data);
        } else if (requestCode == REQ_BACKUP_CREATE) {
            exportBackup(uri);
        } else if (requestCode == REQ_BACKUP_OPEN) {
            importBackup(uri);
        }
    }

    private void buildSinglePaneLayout(LinearLayout root) {
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(buildHeader());

        View bubble = buildBubbleCard();
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleLp.topMargin = dp(12);
        root.addView(bubble, bubbleLp);

        View directInsert = buildDirectInsertCard();
        LinearLayout.LayoutParams directLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        directLp.topMargin = dp(8);
        root.addView(directInsert, directLp);

        View searchRow = buildSearchRow();
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(12);
        root.addView(searchRow, searchLp);

        View backupRow = buildBackupRow();
        LinearLayout.LayoutParams backupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backupLp.topMargin = dp(8);
        root.addView(backupRow, backupLp);

        Button add = buildAddButton();
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        addLp.topMargin = dp(10);
        root.addView(add, addLp);

        root.addView(buildMessagesScroll(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private Button buildAddButton() {
        Button add = new Button(this);
        add.setText("+  Nouveau message");
        add.setTextColor(Color.WHITE);
        add.setTextSize(16);
        add.setAllCaps(false);
        add.setBackground(rounded(ORANGE, 14, 0, 0));
        add.setOnClickListener(v -> showEditor(null));
        return add;
    }

    private ScrollView buildMessagesScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(10), 0, dp(24));

        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("M");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(22);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(ORANGE, 14, 0, 0));
        row.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Message Client");
        title.setTextColor(TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Vos modèles prêts à insérer dans vos SMS");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        titles.addView(sub);

        row.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View buildBubbleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(10), dp(10));
        card.setBackground(rounded(PANEL, 15, Color.rgb(48, 52, 59), 1));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Bulle flottante M");
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Accès rapide depuis vos SMS");
        sub.setTextColor(MUTED);
        sub.setTextSize(12);
        texts.addView(sub);

        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bubbleSwitch = new Switch(this);
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BUBBLE, false);
        bubbleSwitch.setChecked(enabled && Settings.canDrawOverlays(this));
        bubbleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitch) return;
            if (isChecked) enableBubble();
            else disableBubble();
        });
        card.addView(bubbleSwitch);

        return card;
    }

    private View buildDirectInsertCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(10), dp(10));
        card.setBackground(rounded(PANEL, 15, Color.rgb(48, 52, 59), 1));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Insertion directe dans les SMS");
        title.setTextColor(TEXT);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);

        TextView sub = new TextView(this);
        sub.setText(MessageInsertAccessibilityService.isConnected()
                ? "Activée ✓"
                : "À activer une seule fois dans Android");
        sub.setTextColor(MessageInsertAccessibilityService.isConnected()
                ? Color.rgb(117, 214, 137) : MUTED);
        sub.setTextSize(12);
        texts.addView(sub);

        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button activate = new Button(this);
        activate.setText(MessageInsertAccessibilityService.isConnected() ? "Réglages" : "Activer");
        activate.setAllCaps(false);
        activate.setTextColor(Color.WHITE);
        activate.setTextSize(13);
        activate.setBackground(rounded(ORANGE, 11, 0, 0));
        activate.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        card.addView(activate, new LinearLayout.LayoutParams(dp(92), dp(44)));

        return card;
    }

    private View buildSearchRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Rechercher un message...");
        search.setHintTextColor(Color.rgb(115, 121, 130));
        search.setTextColor(TEXT);
        search.setTextSize(16);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(rounded(PANEL2, 13, Color.rgb(54, 59, 67), 1));
        wrap.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        favoritesOnly = new CheckBox(this);
        favoritesOnly.setText("Afficher uniquement les favoris");
        favoritesOnly.setTextColor(MUTED);
        favoritesOnly.setPadding(0, dp(4), 0, 0);
        wrap.addView(favoritesOnly);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        favoritesOnly.setOnCheckedChangeListener((b, checked) -> renderList());

        return wrap;
    }

    private void renderList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean favOnly = favoritesOnly != null && favoritesOnly.isChecked();

        int shown = 0;
        for (MessageStore.MessageTemplate m : MessageStore.sorted(templates)) {
            if (favOnly && !m.favorite) continue;
            String hay = (m.title + " " + m.category + " " + m.text).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            addTemplateCard(m);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("Aucun message trouvé.");
            empty.setTextColor(MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(15);
            empty.setPadding(0, dp(50), 0, 0);
            listContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addTemplateCard(MessageStore.MessageTemplate m) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(compactTwoPane ? 8 : 14),
                dp(compactTwoPane ? 8 : 12),
                dp(compactTwoPane ? 8 : 14),
                dp(compactTwoPane ? 8 : 10));
        card.setBackground(rounded(PANEL, 15, Color.rgb(48, 52, 59), 1));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText((m.favorite ? "★  " : "") + m.title);
        title.setTextColor(TEXT);
        title.setTextSize(compactTwoPane ? 13 : 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (m.category != null && !m.category.trim().isEmpty()) {
            TextView cat = chip(m.category);
            heading.addView(cat);
        }

        card.addView(heading);

        boolean expanded = expandedMessageIds.contains(m.id);

        TextView preview = new TextView(this);
        preview.setText(m.text);
        preview.setTextColor(Color.rgb(207, 211, 216));
        preview.setTextSize(compactTwoPane ? 11 : 14);
        preview.setMaxLines(expanded ? Integer.MAX_VALUE : (compactTwoPane ? 3 : 4));
        preview.setPadding(0, dp(8), 0, dp(4));
        preview.setOnClickListener(v -> toggleExpanded(m.id));
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView expand = new TextView(this);
        expand.setText(expanded ? "Réduire  ▲" : "Voir tout  ▼");
        expand.setTextColor(ORANGE);
        expand.setTextSize(compactTwoPane ? 10 : 13);
        expand.setTypeface(Typeface.DEFAULT_BOLD);
        expand.setPadding(0, dp(4), 0, dp(8));
        expand.setOnClickListener(v -> toggleExpanded(m.id));
        card.addView(expand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button copy = smallButton(compactTwoPane ? "Copier" : "Copier");
        copy.setOnClickListener(v -> copyTemplate(m));
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(compactTwoPane ? 38 : 44), 1f));

        Button edit = smallButton("Modifier");
        edit.setOnClickListener(v -> showEditor(m));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, dp(compactTwoPane ? 38 : 44), 1f);
        editLp.leftMargin = dp(6);
        actions.addView(edit, editLp);

        Button fav = smallButton(m.favorite ? "★" : "☆");
        fav.setTextSize(20);
        fav.setOnClickListener(v -> {
            m.favorite = !m.favorite;
            persist();
        });
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(
                dp(compactTwoPane ? 40 : 54), dp(compactTwoPane ? 38 : 44));
        favLp.leftMargin = dp(6);
        actions.addView(fav, favLp);

        card.addView(actions);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        listContainer.addView(card, lp);
    }

    private void toggleExpanded(String id) {
        if (expandedMessageIds.contains(id)) expandedMessageIds.remove(id);
        else expandedMessageIds.add(id);
        renderList();
    }

    private TextView chip(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(compactTwoPane ? 9 : 11);
        v.setPadding(dp(9), dp(4), dp(9), dp(4));
        v.setBackground(rounded(Color.rgb(53, 58, 66), 999, 0, 0));
        return v;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(compactTwoPane ? 10 : 13);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(rounded(PANEL2, 10, Color.rgb(62, 67, 76), 1));
        return b;
    }

    private void copyTemplate(MessageStore.MessageTemplate m) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(m.title, m.text));
        m.useCount++;
        m.lastUsed = System.currentTimeMillis();
        MessageStore.save(this, templates);
        Toast.makeText(this, "Message copié ✓", Toast.LENGTH_SHORT).show();
        renderList();
    }

    private void showEditor(MessageStore.MessageTemplate existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), 0);

        EditText title = editorField("Nom du message");
        EditText category = editorField("Catégorie (facultatif)");
        EditText body = new EditText(this);
        body.setHint("Texte complet du message");
        body.setTextColor(Color.BLACK);
        body.setHintTextColor(Color.GRAY);
        body.setMinLines(8);
        body.setGravity(Gravity.TOP);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));

        CheckBox favorite = new CheckBox(this);
        favorite.setText("Mettre en favori");

        if (existing != null) {
            title.setText(existing.title);
            category.setText(existing.category);
            body.setText(existing.text);
            favorite.setChecked(existing.favorite);
        }

        form.addView(title);
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catLp.topMargin = dp(8);
        form.addView(category, catLp);

        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        bodyLp.topMargin = dp(8);
        form.addView(body, bodyLp);
        form.addView(favorite);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nouveau message" : "Modifier le message")
                .setView(form)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String t = title.getText().toString().trim();
                    String c = category.getText().toString().trim();
                    String b = body.getText().toString().trim();

                    if (b.isEmpty()) {
                        body.setError("Le message ne peut pas être vide");
                        return;
                    }
                    if (t.isEmpty()) t = "Message client";

                    if (existing == null) {
                        templates.add(new MessageStore.MessageTemplate(
                                UUID.randomUUID().toString(), t, c, b,
                                favorite.isChecked(), 0, 0L));
                    } else {
                        existing.title = t;
                        existing.category = c;
                        existing.text = b;
                        existing.favorite = favorite.isChecked();
                    }
                    MessageStore.save(this, templates);
                    renderList();
                    dialog.dismiss();
                    Toast.makeText(this, "Message enregistré", Toast.LENGTH_SHORT).show();
                }));

        if (existing != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Supprimer", (d, which) -> {});
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String t = title.getText().toString().trim();
                    String c = category.getText().toString().trim();
                    String b = body.getText().toString().trim();
                    if (b.isEmpty()) {
                        body.setError("Le message ne peut pas être vide");
                        return;
                    }
                    existing.title = t.isEmpty() ? "Message client" : t;
                    existing.category = c;
                    existing.text = b;
                    existing.favorite = favorite.isChecked();
                    MessageStore.save(this, templates);
                    renderList();
                    dialog.dismiss();
                    Toast.makeText(this, "Message enregistré", Toast.LENGTH_SHORT).show();
                });
                Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                neutral.setTextColor(Color.RED);
                neutral.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setMessage("Supprimer ce message ?")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Supprimer", (dd, ww) -> {
                            templates.remove(existing);
                            MessageStore.save(this, templates);
                            renderList();
                            dialog.dismiss();
                        }).show());
            });
        }

        dialog.show();
    }

    private EditText editorField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(Color.GRAY);
        return e;
    }

    private void enableBubble() {
        if (!Settings.canDrawOverlays(this)) {
            waitingOverlayPermission = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        saveBubbleEnabled(true);
        startBubbleService();
        requestNotificationPermissionIfNeeded();
    }

    private void disableBubble() {
        saveBubbleEnabled(false);
        stopService(new Intent(this, FloatingBubbleService.class));
    }

    private void startBubbleService() {
        Intent i = new Intent(this, FloatingBubbleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void saveBubbleEnabled(boolean enabled) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BUBBLE, enabled).apply();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 300);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingOverlayPermission) {
            waitingOverlayPermission = false;
            if (Settings.canDrawOverlays(this)) {
                suppressSwitch = true;
                bubbleSwitch.setChecked(true);
                suppressSwitch = false;
                saveBubbleEnabled(true);
                startBubbleService();
                requestNotificationPermissionIfNeeded();
                Toast.makeText(this, "Bulle M activée", Toast.LENGTH_SHORT).show();
            } else {
                suppressSwitch = true;
                bubbleSwitch.setChecked(false);
                suppressSwitch = false;
            }
        } else {
            boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(KEY_BUBBLE, false) && Settings.canDrawOverlays(this);
            suppressSwitch = true;
            if (bubbleSwitch != null) bubbleSwitch.setChecked(enabled);
            suppressSwitch = false;
            if (enabled) startBubbleService();
        }
    }

    private void persist() {
        MessageStore.save(this, templates);
        renderList();
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
