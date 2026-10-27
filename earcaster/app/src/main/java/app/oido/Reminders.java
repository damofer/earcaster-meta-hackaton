package app.oido;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Reminders at fixed times (spec 006). There is a single alarm, for the next reminder:
 * when it rings, the following one is scheduled. It is also rescheduled on save, on reboot and when the time changes.
 */
final class Reminders {

    static final String ACTION_FIRE = "app.oido.REMINDER_FIRE";
    static final String ACTION_DONE = "app.oido.REMINDER_DONE";
    static final String EXTRA_AT = "at";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_DOSE = "dose";
    static final String EXTRA_NOTIFICATION = "notification";
    static final String CHANNEL = "recordatorios";
    /** How long the «Hecho» (Done) button is offered. */
    static final long CONFIRM_MS = 30 * 60_000L;

    private Reminders() {}

    /** Schedules (or cancels) the alarm for the next reminder. */
    static void schedule(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent intent = new Intent(ctx, ReminderReceiver.class).setAction(ACTION_FIRE);
        long next = nextAt(Prefs.triggers(ctx), Calendar.getInstance());
        if (next < 0) {
            PendingIntent old = PendingIntent.getBroadcast(ctx, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
            return;
        }
        intent.putExtra(EXTRA_AT, next);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                // Without the exact alarm permission it may ring a few minutes late.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Next time (ms) any reminder rings, or -1. */
    static long nextAt(List<Trigger> triggers, Calendar now) {
        long best = -1;
        for (Trigger t : triggers) {
            if (!t.isReminder()) continue;
            long at = Weekly.next(t.days, t.atMinute, 0, now);
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    /** The reminders due exactly at time at. */
    static List<Trigger> dueAt(List<Trigger> triggers, long at) {
        List<Trigger> out = new ArrayList<>();
        Calendar before = Calendar.getInstance();
        before.setTimeInMillis(at - 1);
        for (Trigger t : triggers) {
            if (t.isReminder() && Weekly.next(t.days, t.atMinute, 0, before) == at) out.add(t);
        }
        return out;
    }

    /** What is shown on screen: the text that is spoken, or the phrase, or the time. */
    static String title(Context ctx, Trigger t) {
        if (t.speak) {
            String text = SpokenText.fill(t.speech, Calendar.getInstance(), Responder.sources(ctx));
            if (!text.isEmpty()) return text;
        }
        if (!t.doseName.isEmpty()) return t.doseName;
        List<String> forms = Texts.variants(t.phrase);
        if (!forms.isEmpty()) return forms.get(0);
        return Lang.str(ctx, R.string.reminder_at, Weekly.clock(t.atMinute));
    }

    static int notificationId(Trigger t, int index) {
        return 2000 + t.atMinute * 8 + (index % 8);
    }

    static void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, Lang.str(ctx, R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(Lang.str(ctx, R.string.channel_reminders_desc));
        // The reminder is already heard with its own voice or sound.
        ch.setSound(null, null);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Shows the large «Hecho» (Done) button (full screen if the phone allows it) for 30 minutes. */
    static void showConfirm(Context ctx, Trigger t, int index) {
        createChannel(ctx);
        String text = title(ctx, t);
        int id = notificationId(t, index);
        Intent screen = new Intent(ctx, ReminderActivity.class)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_DOSE, t.doseName)
                .putExtra(EXTRA_NOTIFICATION, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(ctx, id, screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent done = new Intent(ctx, ReminderReceiver.class)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_DOSE, t.doseName)
                .putExtra(EXTRA_NOTIFICATION, id);
        PendingIntent donePi = PendingIntent.getBroadcast(ctx, id, done,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(text)
                .setContentText(Lang.str(ctx, R.string.reminder_tap_done))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_notif), Lang.str(ctx, R.string.hecho), donePi).build())
                .setTimeoutAfter(CONFIRM_MS)
                .setAutoCancel(true)
                .build();
        ctx.getSystemService(NotificationManager.class).notify(id, n);
    }
}
