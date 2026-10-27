package app.oido;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** A reminder's alarm (spec 006) and the «Hecho» (Done) button in its notification (spec 007). */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Reminders.ACTION_FIRE.equals(action)) {
            long at = intent.getLongExtra(Reminders.EXTRA_AT, 0);
            // Schedule the next alarm first: if something fails while ringing, tomorrow's still stands.
            Reminders.schedule(ctx);
            try {
                ReminderService.start(ctx, at);
            } catch (RuntimeException e) {
                // Android did not allow playing from the background: at least the reminder is shown.
                List<Trigger> due = Reminders.dueAt(Prefs.triggers(ctx), at);
                for (int i = 0; i < due.size(); i++) Reminders.showConfirm(ctx, due.get(i), i);
            }
        } else if (Reminders.ACTION_DONE.equals(action)) {
            String dose = intent.getStringExtra(Reminders.EXTRA_DOSE);
            if (dose != null && !dose.isEmpty()) DoseLog.add(ctx, dose, DoseLog.BY_BUTTON);
            ctx.getSystemService(NotificationManager.class)
                    .cancel(intent.getIntExtra(Reminders.EXTRA_NOTIFICATION, 0));
        }
    }
}
