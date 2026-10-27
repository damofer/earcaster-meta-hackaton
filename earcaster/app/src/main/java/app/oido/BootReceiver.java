package app.oido;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reschedules the reminders after a reboot, an app update or a time change (spec 006, R5). */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Reminders.schedule(ctx);
    }
}
