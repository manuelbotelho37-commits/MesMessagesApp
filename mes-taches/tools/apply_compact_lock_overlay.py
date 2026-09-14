from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

# Full-screen intent is used only as a transport to show a small translucent card over the lock screen.
manifest=MANIFEST.read_text(encoding="utf-8")
if 'android.permission.USE_FULL_SCREEN_INTENT' not in manifest:
    manifest=manifest.replace(
        '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>',
        '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>\n    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>',1)
if 'android:name=".ReminderAlertActivity"' not in manifest:
    manifest=manifest.replace(
        '    </application>',
        '''        <activity\n            android:name=".ReminderAlertActivity"\n            android:exported="false"\n            android:excludeFromRecents="true"\n            android:launchMode="singleTop"\n            android:showWhenLocked="true"\n            android:turnScreenOn="true"\n            android:theme="@style/ReminderAlertTheme"/>\n\n    </application>''',1)
MANIFEST.write_text(manifest,encoding="utf-8")

# Use a fresh channel so Samsung applies high-importance alert behavior from scratch.
scheduler=JAVA/"ReminderScheduler.java"
text=scheduler.read_text(encoding="utf-8")
text=text.replace('static final String CHANNEL_ID="mes_taches_manu_lock_v4";',
                  'static final String CHANNEL_ID="mes_taches_manu_lock_v5";',1)
text=text.replace('"Tâches à faire - écran verrouillé",',
                  '"Alertes tâches - écran verrouillé",',1)
text=text.replace('"Rappels sonores persistants visibles sur l’écran verrouillé"',
                  '"Alertes prioritaires persistantes visibles sur l’écran verrouillé"',1)
scheduler.write_text(text,encoding="utf-8")

receiver=JAVA/"ReminderReceiver.java"
receiver.write_text(r'''package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        long taskId=intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        if (taskId==0) return;
        TaskStore.Task task;
        try (TaskStore store=new TaskStore(context)) { task=store.get(taskId); }
        catch (RuntimeException ex) { return; }
        if (task==null || task.done) return;
        showReminder(context,task);
        ReminderScheduler.scheduleNextHourly(context,task);
    }

    static void showReminder(Context context,TaskStore.Task task) {
        if (task==null || task.done) return;
        if (Build.VERSION.SDK_INT>=33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        ReminderScheduler.createChannel(context);

        Intent open=new Intent(context,TaskDetailActivity.class)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id),open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Intent alert=new Intent(context,ReminderAlertActivity.class)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent alertIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id)+100000,alert,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        String detail=task.appointment
                ? "Rendez-vous à faire maintenant · rappel dans 1 h"
                : "Tâche à faire maintenant · rappel dans 1 h";

        Notification.Builder builder=new Notification.Builder(context,ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setSubText("Mes tâches Manu")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setContentIntent(contentIntent);

        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        boolean locked=keyguard!=null && keyguard.isKeyguardLocked();
        boolean screenOff=power!=null && !power.isInteractive();
        if (locked || screenOff) {
            builder.setFullScreenIntent(alertIntent,true);
            if (power!=null && !power.isInteractive()) {
                try {
                    PowerManager.WakeLock wake=power.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "MesTachesManu:compact-alert");
                    wake.acquire(12000L);
                } catch (RuntimeException ignored) {}
            }
        }

        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager!=null) {
            int id=ReminderScheduler.notificationId(task.id);
            manager.cancel(id);
            manager.notify(id,builder.build());
        }
    }
}
''',encoding="utf-8")

permission=JAVA/"PermissionActivity.java"
permission.write_text(r'''package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public final class PermissionActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION=7001;
    private static final int EXACT_ALARM_ACCESS=7002;
    private static final int FULL_SCREEN_ACCESS=7003;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ReminderScheduler.createChannel(this);
        if (Build.VERSION.SDK_INT>=33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        } else ensureExactAlarmThenAlertAccess();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==NOTIFICATION_PERMISSION) {
            if (grantResults.length==0 || grantResults[0]!=PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this,"Autorise les notifications pour recevoir tes rappels.",Toast.LENGTH_LONG).show();
            ensureExactAlarmThenAlertAccess();
        }
    }

    private void ensureExactAlarmThenAlertAccess() {
        if (Build.VERSION.SDK_INT>=31) {
            AlarmManager manager=getSystemService(AlarmManager.class);
            if (manager!=null && !manager.canScheduleExactAlarms()) {
                try {
                    startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:"+getPackageName())),EXACT_ALARM_ACCESS);
                    return;
                } catch (ActivityNotFoundException ignored) {}
            }
        }
        ensureAlertAccessThenOpen();
    }

    private void ensureAlertAccessThenOpen() {
        if (Build.VERSION.SDK_INT>=34) {
            NotificationManager manager=getSystemService(NotificationManager.class);
            if (manager!=null && !manager.canUseFullScreenIntent()) {
                try {
                    Toast.makeText(this,
                            "Active « Alertes en plein écran ». Elle servira uniquement à afficher la petite carte de tâches sur l’écran verrouillé.",
                            Toast.LENGTH_LONG).show();
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:"+getPackageName())),FULL_SCREEN_ACCESS);
                    return;
                } catch (ActivityNotFoundException ignored) {}
            }
        }
        finishSetup();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==EXACT_ALARM_ACCESS) ensureAlertAccessThenOpen();
        else if (requestCode==FULL_SCREEN_ACCESS) finishSetup();
    }

    private void finishSetup() {
        ReminderScheduler.rescheduleAll(this);
        startActivity(new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
''',encoding="utf-8")

print("Petite alerte verrouillage forcée appliquée")
