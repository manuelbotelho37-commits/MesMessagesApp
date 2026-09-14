from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

# Wake the screen briefly for reminders, but do not use full-screen intents.
manifest=MANIFEST.read_text(encoding="utf-8")
if 'android.permission.WAKE_LOCK' not in manifest:
    manifest=manifest.replace(
        '    <uses-permission android:name="android.permission.VIBRATE"/>',
        '    <uses-permission android:name="android.permission.VIBRATE"/>\n    <uses-permission android:name="android.permission.WAKE_LOCK"/>',1)
manifest=manifest.replace('    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>\n','')
MANIFEST.write_text(manifest,encoding="utf-8")

# Fresh high-importance channel so Samsung does not reuse the previous presentation settings.
scheduler=JAVA/"ReminderScheduler.java"
scheduler.write_text(r'''package fr.manubotelho.mestaches;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class ReminderScheduler {
    static final String CHANNEL_ID="mes_taches_manu_lock_v4";
    static final String EXTRA_TASK_ID="task_id";

    private ReminderScheduler() {}

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT<26) return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if (manager==null || manager.getNotificationChannel(CHANNEL_ID)!=null) return;
        NotificationChannel channel=new NotificationChannel(
                CHANNEL_ID,
                "Tâches à faire - écran verrouillé",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Rappels sonores persistants visibles sur l’écran verrouillé");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        AudioAttributes audio=new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build();
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,audio);
        manager.createNotificationChannel(channel);
    }

    static void sync(Context context,TaskStore.Task task) {
        if (task==null) return;
        cancelAlarmOnly(context,task.id);
        if (task.done) {
            cancelNotification(context,task.id);
            return;
        }
        createChannel(context);
        long now=System.currentTimeMillis();
        if (task.dueAt<=now) {
            if (!hasActiveNotification(context,task.id)) ReminderReceiver.showReminder(context,task);
            scheduleNextHourly(context,task);
            return;
        }
        scheduleAt(context,task.id,task.dueAt);
    }

    static void scheduleNextHourly(Context context,TaskStore.Task task) {
        if (task==null || task.done) return;
        long hour=60L*60L*1000L;
        long now=System.currentTimeMillis();
        long next;
        if (now<task.dueAt) next=task.dueAt;
        else {
            long elapsed=now-task.dueAt;
            long steps=(elapsed/hour)+1L;
            next=task.dueAt+steps*hour;
        }
        scheduleAt(context,task.id,next);
    }

    private static boolean hasActiveNotification(Context context,long taskId) {
        if (Build.VERSION.SDK_INT<23) return false;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager==null) return false;
        try {
            for (android.service.notification.StatusBarNotification n:manager.getActiveNotifications()) {
                if (n.getId()==notificationId(taskId)) return true;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    private static void scheduleAt(Context context,long taskId,long when) {
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (manager==null) return;
        PendingIntent reminder=pendingIntent(context,taskId,PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT>=23) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,reminder);
            else manager.setExact(AlarmManager.RTC_WAKEUP,when,reminder);
        } catch (SecurityException deniedExactAlarm) {
            if (Build.VERSION.SDK_INT>=23) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,reminder);
            else manager.set(AlarmManager.RTC_WAKEUP,when,reminder);
        }
    }

    static void cancel(Context context,long taskId) {
        cancelAlarmOnly(context,taskId);
        cancelNotification(context,taskId);
    }

    static void cancelNotification(Context context,long taskId) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager!=null) manager.cancel(notificationId(taskId));
    }

    static int notificationId(long taskId) {
        int id=(int)(taskId&0x7fffffff);
        return id==0?1:id;
    }

    private static void cancelAlarmOnly(Context context,long taskId) {
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent reminder=pendingIntent(context,taskId,PendingIntent.FLAG_UPDATE_CURRENT);
        if (manager!=null) manager.cancel(reminder);
        reminder.cancel();
    }

    static void rescheduleAll(Context context) {
        createChannel(context);
        try (TaskStore store=new TaskStore(context)) {
            for (TaskStore.Task task:store.list(false)) sync(context,task);
        } catch (RuntimeException ignored) {}
    }

    private static PendingIntent pendingIntent(Context context,long taskId,int flags) {
        Intent intent=new Intent(context,ReminderReceiver.class)
                .setAction("fr.manubotelho.mestaches.REMIND")
                .setData(Uri.parse("mestachesmanu://reminder/"+taskId))
                .putExtra(EXTRA_TASK_ID,taskId);
        return PendingIntent.getBroadcast(context,0,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
}
''',encoding="utf-8")

receiver=JAVA/"ReminderReceiver.java"
receiver.write_text(r'''package fr.manubotelho.mestaches;

import android.Manifest;
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

        // Light the display briefly. Samsung will then show the persistent lock-screen card
        // when lock-screen notifications are configured as Cards/Show content.
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if (power!=null && !power.isInteractive()) {
            try {
                PowerManager.WakeLock wake=power.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "MesTachesManu:reminder");
                wake.acquire(12000L);
            } catch (RuntimeException ignored) {}
        }

        Intent open=new Intent(context,TaskDetailActivity.class)
                .putExtra(TaskDetailActivity.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id),open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        String detail=task.appointment
                ? "Rendez-vous à faire maintenant · nouveau rappel dans 1 h"
                : "Tâche à faire maintenant · nouveau rappel dans 1 h";
        Notification.Builder builder=new Notification.Builder(context,ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setSubText("Mes tâches Manu")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setGroup("mes_taches_manu_task_"+task.id)
                .setContentIntent(contentIntent);

        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager!=null) {
            int id=ReminderScheduler.notificationId(task.id);
            manager.cancel(id);
            manager.notify(id,builder.build());
        }
    }
}
''',encoding="utf-8")

# Remove the obsolete full-screen permission prompt. Keep exact alarms and explain Samsung lock-screen setup once.
permission=JAVA/"PermissionActivity.java"
permission.write_text(r'''package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ReminderScheduler.createChannel(this);
        if (Build.VERSION.SDK_INT>=33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        } else ensureExactAlarmThenOpen();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==NOTIFICATION_PERMISSION) {
            if (grantResults.length==0 || grantResults[0]!=PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this,"Autorise les notifications pour recevoir tes rappels.",Toast.LENGTH_LONG).show();
            ensureExactAlarmThenOpen();
        }
    }

    private void ensureExactAlarmThenOpen() {
        if (Build.VERSION.SDK_INT>=31) {
            AlarmManager manager=getSystemService(AlarmManager.class);
            if (manager!=null && !manager.canScheduleExactAlarms()) {
                try {
                    Intent settings=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:"+getPackageName()));
                    startActivityForResult(settings,EXACT_ALARM_ACCESS);
                    return;
                } catch (ActivityNotFoundException ignored) {}
            }
        }
        finishSetup();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode==EXACT_ALARM_ACCESS) finishSetup();
    }

    private void finishSetup() {
        ReminderScheduler.rescheduleAll(this);
        boolean shown=getSharedPreferences("appearance",MODE_PRIVATE)
                .getBoolean("lockscreen_help_v22",false);
        if (!shown) {
            getSharedPreferences("appearance",MODE_PRIVATE).edit()
                    .putBoolean("lockscreen_help_v22",true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Afficher les tâches écran éteint")
                    .setMessage("Sur Samsung, choisis pour Mes tâches Manu un pop-up de notification et autorise l’affichage sur l’écran verrouillé avec le contenu visible. L’application réveillera ensuite l’écran et la tâche restera dans les notifications jusqu’à ce qu’elle soit terminée.")
                    .setNegativeButton("Plus tard",(d,w)->openApp())
                    .setPositiveButton("Ouvrir les réglages",(d,w)->openNotificationSettings())
                    .setOnCancelListener(d->openApp())
                    .show();
            return;
        }
        openApp();
    }

    private void openNotificationSettings() {
        try {
            Intent intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            openApp();
            return;
        }
        openApp();
    }

    private void openApp() {
        startActivity(new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
''',encoding="utf-8")

print("Correctif écran verrouillé Samsung appliqué")
