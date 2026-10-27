package app.oido;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/** Records from the microphone to an .m4a file inside the app. */
final class Recorder {

    static final int MAX_MS = 30_000;

    private MediaRecorder rec;
    private long startedAt;

    boolean isRecording() {
        return rec != null;
    }

    long elapsedMs() {
        return rec == null ? 0 : System.currentTimeMillis() - startedAt;
    }

    @SuppressWarnings("deprecation")
    void start(Context ctx, File out, Runnable onMaxReached) throws IOException {
        stop();
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(ctx) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(128_000);
            r.setAudioSamplingRate(44_100);
            r.setMaxDuration(MAX_MS);
            r.setOutputFile(out.getAbsolutePath());
            r.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached.run();
            });
            r.prepare();
            r.start();
        } catch (IOException | RuntimeException e) {
            r.release();
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
        }
        rec = r;
        startedAt = System.currentTimeMillis();
    }

    /** Ends the recording. Returns false if nothing useful was captured (e.g. it lasted less than an instant). */
    boolean stop() {
        if (rec == null) return false;
        MediaRecorder r = rec;
        rec = null;
        try {
            r.stop();
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            r.release();
        }
    }

    /** Folder where recordings are saved. */
    static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "grabaciones");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static String format(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    /** Time with tenths, for example 0:03.5 */
    static String formatTenths(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d.%d", s % 60, (ms % 1000) / 100);
    }
}
