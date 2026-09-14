package fr.manubotelho.mestaches;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public final class PermissionActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION = 7001;
    private static final int EXACT_ALARM_ACCESS = 7002;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ReminderScheduler.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        } else {
            ensureExactAlarmThenOpen();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Autorise les notifications pour recevoir tes rappels.", Toast.LENGTH_LONG).show();
            }
            ensureExactAlarmThenOpen();
        }
    }

    private void ensureExactAlarmThenOpen() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager manager = getSystemService(AlarmManager.class);
            if (manager != null && !manager.canScheduleExactAlarms()) {
                try {
                    Toast.makeText(this,
                            "Active « Alarmes et rappels » pour recevoir tes tâches même écran verrouillé.",
                            Toast.LENGTH_LONG).show();
                    Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(settings, EXACT_ALARM_ACCESS);
                    return;
                } catch (ActivityNotFoundException ignored) {
                    // Si Samsung ne propose pas cet écran, les rappels utilisent le mode de secours Android.
                }
            }
        }
        finishSetup();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXACT_ALARM_ACCESS) finishSetup();
    }

    private void finishSetup() {
        ReminderScheduler.rescheduleAll(this);
        openApp();
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
