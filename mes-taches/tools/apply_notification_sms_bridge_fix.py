from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

# Restore full-screen intent permission only so Android can wake the lock screen.
manifest=MANIFEST.read_text(encoding="utf-8")
if 'android.permission.USE_FULL_SCREEN_INTENT' not in manifest:
    manifest=manifest.replace(
        '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>',
        '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>\n    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>',1)
MANIFEST.write_text(manifest,encoding="utf-8")

# Final reminder receiver: persistent notification + compact lock-screen panel only when locked/off.
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
    @Override public void onReceive(Context context, Intent intent) {
        long taskId=intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID,0);
        if (taskId==0) return;
        TaskStore.Task task;
        try (TaskStore store=new TaskStore(context)) {
            task=store.get(taskId);
        } catch (RuntimeException ex) {
            return;
        }
        if (task==null || task.done) return;
        showReminder(context,task);
        ReminderScheduler.scheduleNextHourly(context,task);
    }

    static void showReminder(Context context,TaskStore.Task task) {
        if (task==null || task.done) return;
        if (Build.VERSION.SDK_INT>=33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        ReminderScheduler.createChannel(context);

        Intent open=new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id),open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Intent alert=new Intent(context,ReminderAlertActivity.class)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID,task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreenIntent=PendingIntent.getActivity(
                context,ReminderScheduler.notificationId(task.id)+100000,alert,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        String detail=task.appointment
                ? "Rendez-vous à faire · rappel dans 1 heure"
                : "Tâche à faire · rappel dans 1 heure";
        Notification.Builder builder=new Notification.Builder(context,ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(task.title)
                .setContentText(detail)
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
        if (locked || screenOff) builder.setFullScreenIntent(fullScreenIntent,true);

        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager!=null) {
            int id=ReminderScheduler.notificationId(task.id);
            manager.cancel(id);
            manager.notify(id,builder.build());
        }
    }
}
''',encoding="utf-8")

# When a contact conversation is opened from a task, remember the target task for 5 minutes.
detail=JAVA/"TaskDetailActivity.java"
text=detail.read_text(encoding="utf-8")
old=r'''    private void openSmsConversation(String number) {
        if (number==null || number.trim().isEmpty()) return;
        try {
            Intent smsIntent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number.trim())));
            startActivity(smsIntent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application Messages n’est disponible.",Toast.LENGTH_LONG).show();
        }
    }
'''
new=r'''    private void openSmsConversation(String number) {
        if (number==null || number.trim().isEmpty()) return;
        getSharedPreferences("share_bridge",MODE_PRIVATE).edit()
                .putLong("pending_sms_task",taskId)
                .putLong("pending_sms_at",System.currentTimeMillis())
                .apply();
        try {
            Intent smsIntent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number.trim())));
            startActivity(smsIntent);
            Toast.makeText(this,"Dans Messages : appui long sur le SMS → Partager → Mes tâches Manu. Il sera ajouté directement à cette tâche.",Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this,"Aucune application Messages n’est disponible.",Toast.LENGTH_LONG).show();
        }
    }
'''
if old not in text:
    raise SystemExit("openSmsConversation introuvable après les patches SMS")
text=text.replace(old,new,1)
detail.write_text(text,encoding="utf-8")

print("Correctif notifications verrouillées + pont SMS appliqué")
