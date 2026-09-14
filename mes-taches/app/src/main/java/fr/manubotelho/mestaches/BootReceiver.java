package fr.manubotelho.mestaches;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ReminderScheduler.rescheduleAll(context);
    }
}
