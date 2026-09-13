package com.mesmessages.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 100;

    private final List<MessageItem> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private TextView status;
    private Button permissionButton;
    private Button rcsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("Mes Messages");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        permissionButton = new Button(this);
        permissionButton.setText("Autoriser SMS/MMS");
        permissionButton.setOnClickListener(v -> requestSmsPermission());
        buttons.addView(permissionButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = new Button(this);
        refresh.setText("Actualiser");
        refresh.setOnClickListener(v -> loadAllMessages());
        buttons.addView(refresh, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(buttons);

        rcsButton = new Button(this);
        rcsButton.setText("Activer accès RCS");
        rcsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });
        root.addView(rcsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("SMS/MMS : historique du téléphone. RCS : les messages accessibles via les notifications Google Messages sont ajoutés localement.");
        info.setTextSize(12);
        info.setPadding(0, dp(4), 0, dp(8));
        root.addView(info);

        ListView list = new ListView(this);
        adapter = new MessageAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadAllMessages();
        } else {
            status.setText("Autorise l’accès aux SMS/MMS pour charger l’historique du téléphone.");
            requestSmsPermission();
        }
        updateRcsButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRcsButton();
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadAllMessages();
        }
    }

    private void requestSmsPermission() {
        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadAllMessages();
        } else if (requestCode == REQ_SMS) {
            status.setText("Accès SMS/MMS refusé. Appuie sur « Autoriser SMS/MMS » puis accepte.");
        }
    }

    private void updateRcsButton() {
        boolean enabled = isNotificationAccessEnabled();
        rcsButton.setText(enabled ? "RCS : accès notifications activé" : "Activer accès RCS");
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void loadAllMessages() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("L’autorisation SMS/MMS est nécessaire.");
            return;
        }

        permissionButton.setEnabled(false);
        permissionButton.setText("SMS/MMS autorisés");
        status.setText("Chargement de tous les messages disponibles…");

        new Thread(() -> {
            ArrayList<MessageItem> loaded = new ArrayList<>();
            loadSms(loaded);
            loadMms(loaded);
            loadCapturedRcs(loaded);

            Collections.sort(loaded, (a, b) -> Long.compare(b.date, a.date));

            int smsCount = 0;
            int mmsCount = 0;
            int rcsCount = 0;
            for (MessageItem item : loaded) {
                if ("SMS".equals(item.source)) smsCount++;
                else if ("MMS".equals(item.source)) mmsCount++;
                else if ("RCS".equals(item.source)) rcsCount++;
            }
            final int finalSmsCount = smsCount;
            final int finalMmsCount = mmsCount;
            final int finalRcsCount = rcsCount;

            runOnUiThread(() -> {
                messages.clear();
                messages.addAll(loaded);
                adapter.notifyDataSetChanged();
                status.setText(messages.size() + " messages • " + finalSmsCount + " SMS • "
                        + finalMmsCount + " MMS • " + finalRcsCount + " RCS capturés");
            });
        }).start();
    }

    private void loadSms(List<MessageItem> out) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.TYPE
                    },
                    null,
                    null,
                    Telephony.Sms.DATE + " DESC");

            if (cursor == null) return;
            int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
            int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
            int typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE);

            while (cursor.moveToNext()) {
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";
                long date = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0L;
                int type = typeIndex >= 0 ? cursor.getInt(typeIndex) : 0;
                int direction = type == Telephony.Sms.MESSAGE_TYPE_SENT ? 2 : 1;
                out.add(new MessageItem(address, body, date, direction, "SMS"));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void loadMms(List<MessageItem> out) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Telephony.Mms.CONTENT_URI,
                    new String[]{"_id", "date", "msg_box", "sub"},
                    null,
                    null,
                    "date DESC");
            if (cursor == null) return;

            int idIndex = cursor.getColumnIndex("_id");
            int dateIndex = cursor.getColumnIndex("date");
            int boxIndex = cursor.getColumnIndex("msg_box");
            int subjectIndex = cursor.getColumnIndex("sub");

            while (cursor.moveToNext()) {
                String id = idIndex >= 0 ? cursor.getString(idIndex) : "";
                long date = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0L;
                if (date > 0 && date < 100000000000L) date *= 1000L;
                int msgBox = boxIndex >= 0 ? cursor.getInt(boxIndex) : 0;
                boolean sent = msgBox == 2;
                String address = getMmsAddress(id, sent);
                String body = getMmsText(id);
                String subject = subjectIndex >= 0 ? cursor.getString(subjectIndex) : "";
                if ((body == null || body.trim().isEmpty()) && subject != null && !subject.trim().isEmpty()) {
                    body = subject;
                }
                if (body == null || body.trim().isEmpty()) body = "(MMS avec pièce jointe)";
                out.add(new MessageItem(address, body, date, sent ? 2 : 1, "MMS"));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getMmsText(String messageId) {
        if (messageId == null || messageId.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Uri.parse("content://mms/part"),
                    new String[]{"ct", "text"},
                    "mid=?",
                    new String[]{messageId},
                    null);
            if (cursor == null) return "";
            int ctIndex = cursor.getColumnIndex("ct");
            int textIndex = cursor.getColumnIndex("text");
            while (cursor.moveToNext()) {
                String ct = ctIndex >= 0 ? cursor.getString(ctIndex) : "";
                String text = textIndex >= 0 ? cursor.getString(textIndex) : "";
                if ("text/plain".equals(ct) && text != null && !text.trim().isEmpty()) {
                    if (result.length() > 0) result.append("\n");
                    result.append(text.trim());
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return result.toString();
    }

    private String getMmsAddress(String messageId, boolean sent) {
        if (messageId == null || messageId.isEmpty()) return "";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Uri.parse("content://mms/" + messageId + "/addr"),
                    new String[]{"address", "type"},
                    null, null, null);
            if (cursor == null) return "";
            int addressIndex = cursor.getColumnIndex("address");
            int typeIndex = cursor.getColumnIndex("type");
            int wantedType = sent ? 151 : 137;
            while (cursor.moveToNext()) {
                int type = typeIndex >= 0 ? cursor.getInt(typeIndex) : 0;
                String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                if (type == wantedType && address != null
                        && !address.contains("insert-address-token")) {
                    return address;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "";
    }

    private void loadCapturedRcs(List<MessageItem> out) {
        CapturedMessageStore store = new CapturedMessageStore(this);
        try {
            for (CapturedMessageStore.Item item : store.getAll()) {
                String who = !item.sender.isEmpty() ? item.sender : item.conversation;
                out.add(new MessageItem(who, item.body, item.date,
                        item.direction == CapturedMessageStore.DIRECTION_SENT ? 2 : 1,
                        "RCS"));
            }
        } finally {
            store.close();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class MessageItem {
        final String address;
        final String body;
        final long date;
        final int direction;
        final String source;

        MessageItem(String address, String body, long date, int direction, String source) {
            this.address = address == null ? "" : address;
            this.body = body == null ? "" : body;
            this.date = date;
            this.direction = direction;
            this.source = source == null ? "" : source;
        }
    }

    private final class MessageAdapter extends BaseAdapter {
        private final SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);

        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView header;
            TextView body;
            TextView date;

            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));

                header = new TextView(MainActivity.this);
                header.setTextSize(16);
                header.setTypeface(null, android.graphics.Typeface.BOLD);
                row.addView(header);

                body = new TextView(MainActivity.this);
                body.setTextSize(15);
                body.setPadding(0, dp(4), 0, dp(4));
                row.addView(body);

                date = new TextView(MainActivity.this);
                date.setTextSize(12);
                row.addView(date);

                row.setTag(new ViewHolder(header, body, date));
                convertView = row;
            }

            ViewHolder holder = (ViewHolder) convertView.getTag();
            MessageItem item = messages.get(position);
            String direction = item.direction == 2 ? "Envoyé à " : "Reçu de ";
            holder.header.setText("[" + item.source + "] " + direction
                    + (item.address.isEmpty() ? "contact inconnu" : item.address));
            holder.body.setText(item.body.isEmpty() ? "(message sans texte)" : item.body);
            holder.date.setText(format.format(new Date(item.date)));
            return convertView;
        }
    }

    private static final class ViewHolder {
        final TextView header;
        final TextView body;
        final TextView date;

        ViewHolder(TextView header, TextView body, TextView date) {
            this.header = header;
            this.body = body;
            this.date = date;
        }
    }
}
