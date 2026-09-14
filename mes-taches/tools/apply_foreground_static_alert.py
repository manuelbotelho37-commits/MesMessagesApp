from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app"/"src"/"main"/"java"/"fr"/"manubotelho"/"mestaches"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

# Permission that lets the reminder activity appear above the app currently in use.
manifest=MANIFEST.read_text(encoding="utf-8")
if 'android.permission.SYSTEM_ALERT_WINDOW' not in manifest:
    manifest=manifest.replace(
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>',
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>\n    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>',1)
MANIFEST.write_text(manifest,encoding="utf-8")

# On an unlocked/interative phone, open the same compact reminder activity directly.
receiver=JAVA/"ReminderReceiver.java"
text=receiver.read_text(encoding="utf-8")
old=r'''        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
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
'''
new=r'''        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
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

        // If the phone is already being used, Android's heads-up banner disappears after a few
        // seconds. With the user-granted "display over other apps" permission, open the same
        // compact translucent activity and leave it there until the user chooses an action.
        if (!locked && !screenOff && android.provider.Settings.canDrawOverlays(context)) {
            try {
                context.startActivity(alert);
            } catch (RuntimeException ignored) {}
        }
'''
if old not in text:
    raise SystemExit("Bloc notification foreground introuvable")
receiver.write_text(text.replace(old,new,1),encoding="utf-8")

# Make the button wording match the user's decision model.
activity=JAVA/"ReminderAlertActivity.java"
text=activity.read_text(encoding="utf-8")
text=text.replace('button("Fermer pour l’instant",WHITE,MUTED,46)',
                  'button("Voir plus tard",WHITE,MUTED,46)',1)
activity.write_text(text,encoding="utf-8")

# Ask once for the Android "display over other apps" access after exact-alarm/full-screen access.
permission=JAVA/"PermissionActivity.java"
text=permission.read_text(encoding="utf-8")
text=text.replace('private static final int FULL_SCREEN_ACCESS=7003;',
                  'private static final int FULL_SCREEN_ACCESS=7003;\n    private static final int OVERLAY_ACCESS=7004;',1)
text=text.replace('''        finishSetup();\n    }\n\n    @Override protected void onActivityResult''',
'''        ensureOverlayAccessThenOpen();\n    }\n\n    private void ensureOverlayAccessThenOpen() {\n        if (Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) {\n            try {\n                Toast.makeText(this,\n                        "Active « Autoriser l’affichage par-dessus les autres applications ». Cela permet à l’alerte de rester visible jusqu’à ce que tu choisisses Fait ou Voir plus tard.",\n                        Toast.LENGTH_LONG).show();\n                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,\n                        Uri.parse("package:"+getPackageName())),OVERLAY_ACCESS);\n                return;\n            } catch (ActivityNotFoundException ignored) {}\n        }\n        finishSetup();\n    }\n\n    @Override protected void onActivityResult''',1)
text=text.replace('''        else if (requestCode==FULL_SCREEN_ACCESS) finishSetup();\n    }''',
'''        else if (requestCode==FULL_SCREEN_ACCESS) ensureOverlayAccessThenOpen();\n        else if (requestCode==OVERLAY_ACCESS) finishSetup();\n    }''',1)
permission.write_text(text,encoding="utf-8")

print("Alerte statique téléphone ouvert appliquée")
