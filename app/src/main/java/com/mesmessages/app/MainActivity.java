package com.mesmessages.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 100;
    private final List<SmsItem> messages = new ArrayList<>();
    private SmsAdapter adapter;
    private TextView status;
    private Button permissionButton;

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
        permissionButton.setText("Autoriser les SMS");
        permissionButton.setOnClickListener(v -> requestSmsPermission());
        buttons.addView(permissionButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = new Button(this);
        refresh.setText("Actualiser");
        refresh.setOnClickListener(v -> loadSms());
        buttons.addView(refresh, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(buttons);

        ListView list = new ListView(this);
        adapter = new SmsAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSms();
        } else {
            status.setText("Autorise l’accès aux SMS pour afficher tout l’historique disponible sur le téléphone.");
            requestSmsPermission();
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
            loadSms();
        } else if (requestCode == REQ_SMS) {
            status.setText("Accès SMS refusé. Appuie sur ‘Autoriser les SMS’ puis accepte l’autorisation.");
        }
    }

    private void loadSms() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("L’autorisation SMS est nécessaire.");
            return;
        }

        permissionButton.setEnabled(false);
        permissionButton.setText("SMS autorisés");
        status.setText("Chargement de tous les SMS…");

        new Thread(() -> {
            ArrayList<SmsItem> loaded = new ArrayList<>();
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(
                        Telephony.Sms.CONTENT_URI,
                        new String[]{
                                Telephony.Sms._ID,
                                Telephony.Sms.ADDRESS,
                                Telephony.Sms.BODY,
                                Telephony.Sms.DATE,
                                Telephony.Sms.TYPE
                        },
                        null,
                        null,
                        Telephony.Sms.DATE + " DESC");

                if (cursor != null) {
                    int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                    int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                    int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
                    int typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE);

                    while (cursor.moveToNext()) {
                        String address = addressIndex >= 0 ? cursor.getString(addressIndex) : "";
                        String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";
                        long date = dateIndex >= 0 ? cursor.getLong(dateIndex) : 0L;
                        int type = typeIndex >= 0 ? cursor.getInt(typeIndex) : 0;
                        loaded.add(new SmsItem(address, body, date, type));
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }

            runOnUiThread(() -> {
                messages.clear();
                messages.addAll(loaded);
                adapter.notifyDataSetChanged();
                status.setText(messages.size() + " SMS chargés • les plus récents sont affichés en premier");
            });
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SmsItem {
        final String address;
        final String body;
        final long date;
        final int type;

        SmsItem(String address, String body, long date, int type) {
            this.address = address == null ? "" : address;
            this.body = body == null ? "" : body;
            this.date = date;
            this.type = type;
        }
    }

    private final class SmsAdapter extends BaseAdapter {
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
            SmsItem item = messages.get(position);
            String direction = item.type == Telephony.Sms.MESSAGE_TYPE_SENT ? "Envoyé à " : "Reçu de ";
            holder.header.setText(direction + (item.address.isEmpty() ? "numéro inconnu" : item.address));
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
