package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, 0);
        if (taskId == 0) return;
        TaskStore.Task task;
        try (TaskStore store = new TaskStore(context)) {
            task = store.get(taskId);
        } catch (RuntimeException ex) {
            return;
        }
        if (task == null || task.done) return;
        showReminder(context, task);
    }

    static void showReminder(Context context, TaskStore.Task task) {
        if (task == null || task.done) return;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ReminderScheduler.createChannel(context);

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                ReminderScheduler.notificationId(task.id),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent alert = new Intent(context, ReminderAlertActivity.class)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context,
                ReminderScheduler.notificationId(task.id) + 100000,
                alert,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String detail = task.appointment ? "Rendez-vous à faire maintenant" : "Tâche à faire maintenant";
        Notification.Builder builder = new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(task.title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().setBigContentTitle(task.title).bigText(detail + "\nElle restera affichée jusqu’à ce que tu la termines."))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(task.dueAt)
                .setShowWhen(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(ReminderScheduler.notificationId(task.id), builder.build());
    }
}
