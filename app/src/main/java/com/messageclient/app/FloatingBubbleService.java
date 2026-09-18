package com.messageclient.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FloatingBubbleService extends Service {
    private static final int NOTIFICATION_ID = 1207;
    private static final String CHANNEL_ID = "message_client_bubble";
    private static final int ORANGE = Color.rgb(244, 123, 32);
    private static final int BG = Color.rgb(20, 22, 25);
    private static final int PANEL = Color.rgb(29, 32, 37);
    private static final int TEXT = Color.rgb(245, 246, 247);
    private static final int MUTED = Color.rgb(166, 171, 180);
    private static final String PREFS = "message_client_settings";
    private static final String KEY_BUBBLE = "bubble_enabled";

    private WindowManager windowManager;
    private TextView bubble;
    private View panel;
    private WindowManager.LayoutParams bubbleParams;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null && windowManager != null) showBubble();
        return START_STICKY;
    }

    private void showBubble() {
        if (bubble != null || windowManager == null) return;

        bubble = new TextView(this);
        bubble.setText("M");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(22);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(circle(ORANGE));

        int size = dp(58);
        bubbleParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        // Fixed position: the bubble cannot be dragged accidentally.
        bubbleParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        bubbleParams.x = dp(6);
        bubbleParams.y = 0;

        bubble.setOnClickListener(v -> togglePanel());

        windowManager.addView(bubble, bubbleParams);
    }

    private void togglePanel() {
        if (panel == null) showPanel();
        else removePanel();
    }

    private void showPanel() {
        if (windowManager == null || panel != null) return;

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12), dp(12), dp(12), dp(12));
        outer.setBackground(rounded(BG, 18, Color.rgb(65, 69, 77), 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Message Client");
        title.setTextColor(TEXT);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = miniButton("✕");
        close.setOnClickListener(v -> removePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(46), dp(42)));
        outer.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Touchez un message : il est inséré directement dans votre SMS.");
        hint.setTextColor(MUTED);
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(8));
        outer.addView(hint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        List<MessageStore.MessageTemplate> messages =
                MessageStore.sorted(MessageStore.load(this));

        if (messages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Aucun message enregistré.");
            empty.setTextColor(MUTED);
            empty.setPadding(dp(6), dp(24), dp(6), dp(24));
            list.addView(empty);
        } else {
            for (MessageStore.MessageTemplate m : messages) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(12), dp(10), dp(12), dp(10));
                item.setBackground(rounded(PANEL, 12, Color.rgb(55, 60, 68), 1));
                item.setOnClickListener(v -> copyAndClose(m));

                TextView name = new TextView(this);
                name.setText((m.favorite ? "★  " : "") + m.title);
                name.setTextColor(TEXT);
                name.setTextSize(15);
                name.setTypeface(Typeface.DEFAULT_BOLD);
                item.addView(name);

                if (m.category != null && !m.category.trim().isEmpty()) {
                    TextView cat = new TextView(this);
                    cat.setText(m.category);
                    cat.setTextColor(Color.rgb(208, 211, 217));
                    cat.setTextSize(11);
                    cat.setPadding(0, dp(2), 0, 0);
                    item.addView(cat);
                }

                String preview = m.text == null ? "" : m.text.replace("\n", " ").trim();
                if (preview.length() > 105) preview = preview.substring(0, 105) + "…";
                TextView pv = new TextView(this);
                pv.setText(preview);
                pv.setTextColor(MUTED);
                pv.setTextSize(12);
                pv.setPadding(0, dp(5), 0, 0);
                item.addView(pv);

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                itemLp.bottomMargin = dp(8);
                list.addView(item, itemLp);
            }
        }

        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button openApp = new Button(this);
        openApp.setText("Ouvrir l'application");
        openApp.setTextColor(Color.WHITE);
        openApp.setAllCaps(false);
        openApp.setBackground(rounded(ORANGE, 12, 0, 0));
        openApp.setOnClickListener(v -> {
            Intent i = new Intent(this, MessageClientActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            removePanel();
        });
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        openLp.topMargin = dp(6);
        outer.addView(openApp, openLp);

        Button hideBubble = new Button(this);
        hideBubble.setText("Masquer la bulle");
        hideBubble.setTextColor(TEXT);
        hideBubble.setAllCaps(false);
        hideBubble.setBackground(rounded(PANEL, 12, Color.rgb(65, 69, 77), 1));
        hideBubble.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_BUBBLE, false).apply();
            removePanel();
            stopSelf();
        });
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        hideLp.topMargin = dp(6);
        outer.addView(hideBubble, hideLp);

        int width = Math.min(dp(360),
                getResources().getDisplayMetrics().widthPixels - dp(24));
        int height = Math.min(dp(520),
                getResources().getDisplayMetrics().heightPixels - dp(120));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = dp(8);

        panel = outer;
        windowManager.addView(panel, params);
    }

    private void copyAndClose(MessageStore.MessageTemplate m) {
        boolean inserted = MessageInsertAccessibilityService.insertIntoCurrentMessage(m.text);
        MessageStore.markUsed(this, m.id);

        if (inserted) {
            Toast.makeText(this, "Message inséré ✓", Toast.LENGTH_SHORT).show();
        } else {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(m.title, m.text));
            Toast.makeText(this,
                    "Insertion directe non disponible : message copié ✓",
                    Toast.LENGTH_LONG).show();
        }
        removePanel();
    }

    private void removePanel() {
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
            panel = null;
        }
    }

    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(rounded(PANEL, 10, Color.rgb(65, 69, 77), 1));
        return b;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MessageClientActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.messageclient.app.R.drawable.message_client_icon)
                .setContentTitle("Message Client")
                .setContentText("La bulle M est active")
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Bulle Message Client", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Maintient la bulle M disponible au-dessus des SMS.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setShape(GradientDrawable.OVAL);
        return d;
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

    @Override
    public void onDestroy() {
        removePanel();
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
