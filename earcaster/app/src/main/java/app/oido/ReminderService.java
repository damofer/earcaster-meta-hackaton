package app.oido;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Plays the reminders that are due now (spec 006), one after another.
 * While they play, listening is paused so it does not react to its own voice.
 */
public class ReminderService extends Service {

    private static final String CHANNEL = "recordatorio_sonando";
    private static final int NOTIFICATION_ID = 14;
    private static final long WAKE_MS = 3 * 60_000L;

    private final ArrayDeque<Trigger> queue = new ArrayDeque<>();
    private Responder responder;
    private PowerManager.WakeLock wakeLock;
    private boolean playing = false;
    private boolean holding = false;

    static void start(Context ctx, long at) {
        ctx.startForegroundService(new Intent(ctx, ReminderService.class).putExtra(Reminders.EXTRA_AT, at));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_reminder_playing),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.reminder))
                .setContentText(getString(R.string.reminder_playing))
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (wakeLock == null) {
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "earcaster:recordatorio");
            wakeLock.acquire(WAKE_MS);
        }
        if (responder == null) responder = new Responder(this);

        long at = intent != null ? intent.getLongExtra(Reminders.EXTRA_AT, 0) : 0;
        List<Trigger> due = Reminders.dueAt(Prefs.triggers(this), at);
        for (int i = 0; i < due.size(); i++) {
            Trigger t = due.get(i);
            // The «Hecho» (Done) button appears right away, even if the phone is on silent.
            if (t.confirm) Reminders.showConfirm(this, t, i);
            queue.add(t);
        }
        if (!playing) playNext();
        return START_NOT_STICKY;
    }

    private void playNext() {
        Trigger t = queue.poll();
        while (t != null && !t.evenSilent && silenced()) t = queue.poll();
        if (t == null) {
            finish();
            return;
        }
        playing = true;
        if (!holding) {
            holding = true;
            ListenerService.hold(true);
        }
        responder.respond(t, this::playNext, null);
    }

    /** Total silence (spec 006, R6): no vibration and no sound. */
    private boolean silenced() {
        AudioManager am = getSystemService(AudioManager.class);
        return am != null && am.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
    }

    private void finish() {
        playing = false;
        if (holding) {
            holding = false;
            ListenerService.hold(false);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        queue.clear();
        if (responder != null) responder.release();
        if (holding) {
            holding = false;
            ListenerService.hold(false);
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
