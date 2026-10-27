package app.oido;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/** Plays a sound. onDone is always called: when finished, when stopped or on failure. */
final class SoundPlayer {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cutOff = this::stop;
    private MediaPlayer player;
    private Runnable pendingDone;

    boolean isPlaying() {
        return player != null;
    }

    void play(Context ctx, Uri uri, Runnable onDone) {
        play(ctx, uri, 0, 0, onDone);
    }

    /** Plays only the segment [startMs, endMs). endMs = 0 means until the end. */
    void play(Context ctx, Uri uri, int startMs, int endMs, Runnable onDone) {
        stop();

        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        float volume = Prefs.volume(ctx) / 100f;

        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(ctx, uri);
            mp.setVolume(volume, volume);
            mp.setOnCompletionListener(this::finish);
            mp.setOnErrorListener((m, what, extra) -> {
                finish(m);
                return true;
            });
            mp.prepare();
            if (startMs > 0) mp.seekTo(startMs);
            mp.start();
            player = mp;
            pendingDone = onDone;
            if (endMs > startMs) handler.postDelayed(cutOff, endMs - startMs);
        } catch (Exception e) {
            mp.release();
            if (onDone != null) onDone.run();
        }
    }

    private void finish(MediaPlayer m) {
        if (player != m) {
            m.release();
            return;
        }
        player = null;
        m.release();
        runDone();
    }

    /** Duration of the sound in milliseconds, or 0 if it could not be read. */
    static int duration(Context ctx, Uri uri) {
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(ctx, uri);
            mp.prepare();
            return Math.max(0, mp.getDuration());
        } catch (Exception e) {
            return 0;
        } finally {
            mp.release();
        }
    }

    private void runDone() {
        handler.removeCallbacks(cutOff);
        Runnable done = pendingDone;
        pendingDone = null;
        if (done != null) done.run();
    }

    /** Stops the sound immediately and notifies whoever requested it. */
    void stop() {
        if (player != null) {
            MediaPlayer m = player;
            player = null;
            try {
                m.stop();
            } catch (IllegalStateException ignored) {
            }
            m.release();
            runDone();
        }
    }
}
