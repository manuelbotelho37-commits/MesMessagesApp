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
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ReminderScheduler.createChannel(context);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (taskId & 0x7fffffff),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String heading = task.appointment ? "Rendez-vous maintenant" : "Tâche à faire";
        Notification notification = new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(fr.manubotelho.mestaches.R.drawable.ic_notification)
                .setContentTitle(heading)
                .setContentText(task.title)
                .setStyle(new Notification.BigTextStyle().bigText(task.title))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(task.dueAt)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notificationId = (int) (taskId & 0x7fffffff);
            if (notificationId == 0) notificationId = 1;
            manager.notify(notificationId, notification);
        }
    }
}
