package fr.manubotelho.mestaches;

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
    // Nouveau canal afin qu'Android/Samsung recrée bien un canal PRIORITÉ HAUTE.
    static final String CHANNEL_ID = "mes_taches_manu_reminders_v2";
    static final String EXTRA_TASK_ID = "task_id";

    private ReminderScheduler() {}

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rappels visibles Mes tâches Manu",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Rappels visibles sur l’écran verrouillé jusqu’à ce que la tâche soit terminée");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        AudioAttributes audio = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audio);
        manager.createNotificationChannel(channel);
    }

    static void sync(Context context, TaskStore.Task task) {
        if (task == null) return;
        cancelAlarmOnly(context, task.id);
        if (task.done) {
            cancelNotification(context, task.id);
            return;
        }
        createChannel(context);
        if (task.dueAt <= System.currentTimeMillis()) {
            ReminderReceiver.showReminder(context, task);
            return;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent reminder = pendingIntent(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);
            }
        } catch (SecurityException deniedExactAlarm) {
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);
            }
        }
    }

    static void cancel(Context context, long taskId) {
        cancelAlarmOnly(context, taskId);
        cancelNotification(context, taskId);
    }

    static void cancelNotification(Context context, long taskId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId(taskId));
    }

    static int notificationId(long taskId) {
        int id=(int)(taskId & 0x7fffffff);
        return id==0?1:id;
    }

    private static void cancelAlarmOnly(Context context, long taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent reminder = pendingIntent(context, taskId, PendingIntent.FLAG_UPDATE_CURRENT);
        if (manager != null) manager.cancel(reminder);
        reminder.cancel();
    }

    static void rescheduleAll(Context context) {
        createChannel(context);
        try (TaskStore store = new TaskStore(context)) {
            for (TaskStore.Task task : store.list(false)) sync(context, task);
        } catch (RuntimeException ignored) {
            // Une erreur de rappel ne doit jamais empêcher l'application de s'ouvrir.
        }
    }

    private static PendingIntent pendingIntent(Context context, long taskId, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction("fr.manubotelho.mestaches.REMIND")
                .setData(Uri.parse("mestachesmanu://reminder/" + taskId))
                .putExtra(EXTRA_TASK_ID, taskId);
        return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
