from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "fr" / "manubotelho" / "mestaches"


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Patch introuvable: {label} dans {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Après validation de la date, ouvrir automatiquement le choix de l'heure.
main = JAVA / "MainActivity.java"
replace_once(
    main,
    """                updateDate.run();\n            },draftDue.get(Calendar.YEAR),draftDue.get(Calendar.MONTH),draftDue.get(Calendar.DAY_OF_MONTH));""",
    """                updateDate.run();\n                dateButton.postDelayed(() -> timeButton.performClick(), 180);\n            },draftDue.get(Calendar.YEAR),draftDue.get(Calendar.MONTH),draftDue.get(Calendar.DAY_OF_MONTH));""",
    "date puis heure",
)

# 2) Rendre les choix liés aux mails beaucoup plus explicites.
detail = JAVA / "TaskDetailActivity.java"
mail_replacements = [
    ('"✉️ Mail client (texte)",', '"✉️ Mail reçu - coller le texte",'),
    ('"📨 Mail téléchargé / pièce jointe",', '"📨 Mail enregistré (.eml/.msg)",'),
    ('"📧 Adresse e-mail",', '"📧 Adresse e-mail du client",'),
    ('case 3: askText("Mail client","Colle ici le mail reçu ou les points importants","mail",true); break;',
     'case 3: askText("Mail reçu - texte","Colle ici le texte du mail reçu ou les points importants","mail",true); break;'),
    ('case 4: pickDocument("mailfile",PICK_MAIL_FILE,"*/*"); break;',
     'case 4: showMailFileHelp(); break;'),
    ('case 9: askText("Adresse e-mail","client@exemple.fr","email",false); break;',
     'case 9: askText("Adresse e-mail du client","client@exemple.fr","email",false); break;'),
]
for old, new in mail_replacements:
    replace_once(detail, old, new, "libellé mail")

replace_once(
    detail,
    """    private void pickPhoto() {""",
    """    private void showMailFileHelp() {\n        new AlertDialog.Builder(this)\n                .setTitle("Ajouter un mail enregistré")\n                .setMessage("Ce bouton sert uniquement si le mail a été téléchargé sur le téléphone comme fichier .eml, .msg ou pièce jointe. Si tu veux simplement garder le contenu d’un mail reçu, utilise « Mail reçu - coller le texte » ou Partager → Mes tâches Manu depuis ton application mail quand cette option est disponible.")\n                .setNegativeButton("Annuler",null)\n                .setPositiveButton("Choisir le fichier",(d,w)->pickDocument("mailfile",PICK_MAIL_FILE,"*/*"))\n                .show();\n    }\n\n    private void pickPhoto() {""",
    "aide mail téléchargé",
)

# 3) Répéter le rappel toutes les heures tant que la tâche n'est pas terminée.
scheduler = JAVA / "ReminderScheduler.java"
replace_once(
    scheduler,
    """    static void sync(Context context, TaskStore.Task task) {\n        if (task == null) return;\n        cancelAlarmOnly(context, task.id);\n        if (task.done) {\n            cancelNotification(context, task.id);\n            return;\n        }\n        createChannel(context);\n        if (task.dueAt <= System.currentTimeMillis()) {\n            ReminderReceiver.showReminder(context, task);\n            return;\n        }\n        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);\n        if (manager == null) return;\n        PendingIntent reminder = pendingIntent(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT);\n        try {\n            if (Build.VERSION.SDK_INT >= 23) {\n                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);\n            } else {\n                manager.setExact(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);\n            }\n        } catch (SecurityException deniedExactAlarm) {\n            if (Build.VERSION.SDK_INT >= 23) {\n                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);\n            } else {\n                manager.set(AlarmManager.RTC_WAKEUP, task.dueAt, reminder);\n            }\n        }\n    }""",
    """    static void sync(Context context, TaskStore.Task task) {\n        if (task == null) return;\n        cancelAlarmOnly(context, task.id);\n        if (task.done) {\n            cancelNotification(context, task.id);\n            return;\n        }\n        createChannel(context);\n        long now = System.currentTimeMillis();\n        if (task.dueAt <= now) {\n            if (!hasActiveNotification(context, task.id)) {\n                ReminderReceiver.showReminder(context, task);\n            }\n            scheduleNextHourly(context, task);\n            return;\n        }\n        scheduleAt(context, task.id, task.dueAt);\n    }\n\n    static void scheduleNextHourly(Context context, TaskStore.Task task) {\n        if (task == null || task.done) return;\n        long hour = 60L * 60L * 1000L;\n        long now = System.currentTimeMillis();\n        long next;\n        if (now < task.dueAt) {\n            next = task.dueAt;\n        } else {\n            long elapsed = now - task.dueAt;\n            long steps = (elapsed / hour) + 1L;\n            next = task.dueAt + steps * hour;\n        }\n        scheduleAt(context, task.id, next);\n    }\n\n    private static boolean hasActiveNotification(Context context, long taskId) {\n        if (Build.VERSION.SDK_INT < 23) return false;\n        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (manager == null) return false;\n        try {\n            for (android.service.notification.StatusBarNotification notification : manager.getActiveNotifications()) {\n                if (notification.getId() == notificationId(taskId)) return true;\n            }\n        } catch (RuntimeException ignored) {}\n        return false;\n    }\n\n    private static void scheduleAt(Context context, long taskId, long when) {\n        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);\n        if (manager == null) return;\n        PendingIntent reminder = pendingIntent(context, taskId, PendingIntent.FLAG_UPDATE_CURRENT);\n        try {\n            if (Build.VERSION.SDK_INT >= 23) {\n                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, reminder);\n            } else {\n                manager.setExact(AlarmManager.RTC_WAKEUP, when, reminder);\n            }\n        } catch (SecurityException deniedExactAlarm) {\n            if (Build.VERSION.SDK_INT >= 23) {\n                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, reminder);\n            } else {\n                manager.set(AlarmManager.RTC_WAKEUP, when, reminder);\n            }\n        }\n    }""",
    "rappel horaire",
)

receiver = JAVA / "ReminderReceiver.java"
replace_once(
    receiver,
    """        showReminder(context, task);\n    }""",
    """        showReminder(context, task);\n        ReminderScheduler.scheduleNextHourly(context, task);\n    }""",
    "reprogrammation horaire",
)
replace_once(
    receiver,
    """        String detail = task.appointment ? "Rendez-vous à faire maintenant" : "Tâche à faire maintenant";""",
    """        String detail = task.appointment ? "Rendez-vous à faire maintenant · rappel dans 1 heure" : "Tâche à faire maintenant · rappel dans 1 heure";""",
    "texte rappel horaire",
)
replace_once(
    receiver,
    """                .setOnlyAlertOnce(true)""",
    """                .setOnlyAlertOnce(false)""",
    "réalerter chaque heure",
)
replace_once(
    receiver,
    """        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (manager != null) manager.notify(ReminderScheduler.notificationId(task.id), builder.build());""",
    """        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);\n        if (manager != null) {\n            int notificationId = ReminderScheduler.notificationId(task.id);\n            manager.cancel(notificationId);\n            manager.notify(notificationId, builder.build());\n        }""",
    "réaffichage notification",
)

# 4) Ajouter une ligne dans Couleurs pour le fond des tâches à effectuer.
replace_once(
    main,
    'private static final String PREFS="appearance", PREF_TODO="todoColor", PREF_DONE="doneColor", PREF_ADD="addColor";',
    'private static final String PREFS="appearance", PREF_TODO="todoColor", PREF_DONE="doneColor", PREF_ADD="addColor", PREF_TASK_BG="taskBackgroundColor";',
    "préférence fond tâches",
)
replace_once(
    main,
    'row.setBackground(shape(WHITE,wideLayout?12:16,BORDER));',
    'row.setBackground(shape(task.done?WHITE:taskBackgroundColor(),wideLayout?12:16,BORDER));',
    "fond carte tâche",
)
replace_once(
    main,
    'details.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16174ccb),null,shape(WHITE,10,WHITE)));',
    'int cardFill=task.done?WHITE:taskBackgroundColor();\n        details.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16174ccb),null,shape(cardFill,10,cardFill)));',
    "fond détail tâche",
)
replace_once(
    main,
    'String[] options={"À faire  ·  "+colorName(todoColor()),"Terminées  ·  "+colorName(doneColor()),"+ Ajouter  ·  "+colorName(addColor()),"Réinitialiser les couleurs"};',
    'String[] options={"À faire  ·  "+colorName(todoColor()),"Terminées  ·  "+colorName(doneColor()),"+ Ajouter  ·  "+colorName(addColor()),"Fond des tâches à effectuer  ·  "+colorName(taskBackgroundColor()),"Réinitialiser les couleurs"};',
    "ligne fond tâches dans couleurs",
)
replace_once(
    main,
    'if (which==3) {\n                prefs.edit().remove(PREF_TODO).remove(PREF_DONE).remove(PREF_ADD).apply(); refresh(); return;\n            }\n            String key=which==0?PREF_TODO:which==1?PREF_DONE:PREF_ADD;\n            String title=which==0?"Couleur de À faire":which==1?"Couleur de Terminées":"Couleur de + Ajouter";',
    'if (which==4) {\n                prefs.edit().remove(PREF_TODO).remove(PREF_DONE).remove(PREF_ADD).remove(PREF_TASK_BG).apply(); refresh(); return;\n            }\n            String key=which==0?PREF_TODO:which==1?PREF_DONE:which==2?PREF_ADD:PREF_TASK_BG;\n            String title=which==0?"Couleur de À faire":which==1?"Couleur de Terminées":which==2?"Couleur de + Ajouter":"Fond des tâches à effectuer";',
    "gestion ligne fond tâches",
)
replace_once(
    main,
    'String[] names={"Bleu","Jaune","Orange","Vert","Violet","Rouge","Rose","Turquoise","Gris foncé"};\n        int[] colors={0xff174ccb,0xfff2c94c,0xfff28c28,0xff2e9d52,0xff7b4cc9,0xffd04444,0xffd94f8a,0xff159aa6,0xff44546a};',
    'String[] names={"Blanc","Bleu clair","Jaune clair","Orange clair","Vert clair","Violet clair","Rose clair","Turquoise clair","Gris clair","Bleu","Jaune","Orange","Vert","Violet","Rouge","Rose","Turquoise","Gris foncé"};\n        int[] colors={0xffffffff,0xffeaf1ff,0xfffff5c7,0xffffead7,0xffe5f6e9,0xffefe8ff,0xffffe7f0,0xffe1f7f8,0xffeef1f5,0xff174ccb,0xfff2c94c,0xfff28c28,0xff2e9d52,0xff7b4cc9,0xffd04444,0xffd94f8a,0xff159aa6,0xff44546a};',
    "palette fonds clairs",
)
replace_once(
    main,
    'private int addColor() { return prefs==null?ORANGE:prefs.getInt(PREF_ADD,ORANGE); }',
    'private int addColor() { return prefs==null?ORANGE:prefs.getInt(PREF_ADD,ORANGE); }\n    private int taskBackgroundColor() { return prefs==null?WHITE:prefs.getInt(PREF_TASK_BG,WHITE); }',
    "lecture fond tâche",
)
replace_once(
    main,
    'if (color==0xff44546a) return "Gris foncé"; return "Personnalisée";',
    'if (color==0xffffffff) return "Blanc"; if (color==0xffeaf1ff) return "Bleu clair";\n        if (color==0xfffff5c7) return "Jaune clair"; if (color==0xffffead7) return "Orange clair";\n        if (color==0xffe5f6e9) return "Vert clair"; if (color==0xffefe8ff) return "Violet clair";\n        if (color==0xffffe7f0) return "Rose clair"; if (color==0xffe1f7f8) return "Turquoise clair";\n        if (color==0xffeef1f5) return "Gris clair"; if (color==0xff44546a) return "Gris foncé"; return "Personnalisée";',
    "noms fonds clairs",
)

print("Patches Mes tâches Manu appliqués")
