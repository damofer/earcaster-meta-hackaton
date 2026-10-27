package app.oido;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The AI conversation on the person's phone (spec 017): sends what was heard and the current context
 * to the responder function and plays the ElevenLabs voice as it arrives. Only text leaves the microphone
 * (R3). If there is no voice after 2 seconds a soft tone plays (R9); if nothing has arrived after 6, it gives up and
 * the caller says the fallback phrase with the phone's voice (R8).
 */
final class AiClient {

    private static final String REGION = "us-central1";
    private static final int SAMPLE_RATE = 22_050;
    private static final long TONE_AFTER_MS = 2_000;
    private static final long GIVE_UP_MS = 6_000;
    private static final int CONNECT_TIMEOUT_MS = 6_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    /** Preview replies and the voice list do not speak: they may take a little longer. */
    // Muse Spark reasons before writing a summary or reading a family message (specs 019, 020).
    private static final int CAREGIVER_TIMEOUT_MS = 45_000;

    /** How a question ended. */
    static final class Result {
        /** It answered, even if only with an action. false: offline, failed or too slow: fallback phrase. */
        boolean ok;
        /** Stopped with «Parar sonido» (Stop sound): nothing more is said. */
        boolean cancelled;
        /** The AI is not serving this patient right now (daily maximum reached or turned off): fixed phrases only (R11). */
        boolean refused;
        /** What it answered, to remember it in the next question. */
        String answer = "";
        /** Phrases that arrived without audio: the phone's voice says them, in order. */
        final List<String> unspoken = new ArrayList<>();
        /** The action it requested (spec 008), already checked by the server, or null. */
        JSONObject action;
    }

    interface Done {
        /** On the main thread, exactly once. */
        void done(Result r);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Call current;

    AiClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    boolean busy() {
        return current != null;
    }

    /** history: the latest questions and answers {question, answer}, to understand a "yes" or an "and tomorrow?". */
    void ask(String pid, String text, List<String[]> history, Done done) {
        cancel();
        Call c = new Call(pid, text, history, done);
        current = c;
        main.postDelayed(c.tone, TONE_AFTER_MS);
        main.postDelayed(c.giveUp, GIVE_UP_MS);
        worker.execute(c::run);
    }

    /** Cuts off whatever is playing or about to arrive. The caller receives cancelled. */
    void cancel() {
        Call c = current;
        if (c != null) c.abort(true);
    }

    void release() {
        cancel();
        worker.shutdownNow();
    }

    private final class Call {
        final String pid;
        final String text;
        final List<String[]> history;
        final Done done;
        final Result result = new Result();
        final Object lock = new Object();
        volatile boolean stopped = false;
        /** Something to say or do has arrived: it no longer gives up at 6 seconds. */
        boolean heard = false;
        /** The voice is already playing: the tone is no longer needed. */
        boolean voiceStarted = false;
        long toneEndsAt = 0;
        final Runnable tone = this::playTone;
        final Runnable giveUp = () -> {
            synchronized (lock) {
                if (heard) return;
            }
            abort(false);
        };
        private boolean finished = false;
        private HttpURLConnection conn;
        private AudioTrack track;
        private AudioTrack toneTrack;
        private long framesWritten = 0;
        /** The phrases that arrived, and whether the last one came with its audio. */
        private final List<String> sentences = new ArrayList<>();
        private String last = null;
        private boolean lastHasVoice = true;

        Call(String pid, String text, List<String[]> history, Done done) {
            this.pid = pid;
            this.text = text;
            this.history = history;
            this.done = done;
        }

        /** On the worker thread: asks, reads the frames and plays the voice. */
        void run() {
            try {
                JSONObject body = new JSONObject();
                Calendar now = Calendar.getInstance();
                body.put("pid", pid);
                body.put("text", text);
                body.put("day", day(now));
                // To notice questions at night (spec 022).
                body.put("hour", now.get(Calendar.HOUR_OF_DAY));
                // The language the AI answers and speaks in.
                body.put("lang", Lang.code(ctx));
                body.put("facts", facts(ctx, now, true));
                JSONArray turns = new JSONArray();
                for (String[] t : history) turns.put(new JSONObject().put("q", t[0]).put("a", t[1]));
                body.put("history", turns);

                HttpURLConnection c = open("responder", body, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
                synchronized (lock) {
                    if (stopped) {
                        c.disconnect();
                        return;
                    }
                    conn = c;
                }
                int code = c.getResponseCode();
                if (code == 429 || code == 403) {
                    result.refused = true;
                    finishLater();
                    return;
                }
                if (code != 200) throw new IOException("HTTP " + code);

                DataInputStream in = new DataInputStream(new BufferedInputStream(c.getInputStream()));
                AiText.Frame f;
                while (!stopped && (f = AiText.read(in)) != null) {
                    switch (f.type) {
                        case AiText.TEXT:
                            if (last != null && !lastHasVoice) result.unspoken.add(last);
                            last = f.text();
                            lastHasVoice = false;
                            sentences.add(last);
                            heard();
                            break;
                        case AiText.AUDIO:
                            lastHasVoice = true;
                            heard();
                            play(f.body);
                            break;
                        case AiText.ACTION:
                            result.action = new JSONObject(f.text());
                            heard();
                            break;
                        default:
                            // E (end): whatever it carries changes nothing; if nothing to say arrived, fallback phrase.
                            break;
                    }
                }
                waitForVoice();
            } catch (Exception e) {
                // It was cut off: what already arrived counts; if nothing arrived, fallback phrase.
            } finally {
                releaseTrack();
            }
            synchronized (lock) {
                if (last != null && !lastHasVoice) result.unspoken.add(last);
                result.answer = String.join(" ", sentences);
                result.ok = heard;
            }
            finishLater();
        }

        private void heard() {
            synchronized (lock) {
                heard = true;
            }
        }

        /** Writes the audio without blocking, so it can be stopped at any moment. */
        private void play(byte[] pcm) throws IOException {
            if (track == null) {
                long wait;
                synchronized (lock) {
                    voiceStarted = true;
                    wait = toneEndsAt - SystemClock.elapsedRealtime();
                }
                // If the tone is playing, the voice waits for it to finish.
                if (wait > 0) SystemClock.sleep(wait);
                AudioTrack t = newTrack();
                synchronized (lock) {
                    if (stopped) {
                        t.release();
                        return;
                    }
                    track = t;
                }
                t.play();
            }
            write(pcm, pcm.length);
            framesWritten += pcm.length / 2;
        }

        private void write(byte[] pcm, int length) throws IOException {
            int off = 0;
            while (off < length && !stopped) {
                int n = track.write(pcm, off, length - off, AudioTrack.WRITE_NON_BLOCKING);
                if (n < 0) throw new IOException("AudioTrack " + n);
                if (n == 0) SystemClock.sleep(15);
                off += n;
            }
        }

        /** Waits for what was written to finish playing. A bit of silence at the end pushes out the last part. */
        private void waitForVoice() throws IOException {
            if (track == null || stopped) return;
            byte[] silence = new byte[SAMPLE_RATE / 5 * 2];
            write(silence, silence.length);
            long deadline = SystemClock.elapsedRealtime() + 1_000
                    + Math.max(0, framesWritten - track.getPlaybackHeadPosition()) * 1_000 / SAMPLE_RATE;
            while (!stopped && track.getPlaybackHeadPosition() < framesWritten
                    && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(30);
            }
        }

        private void releaseTrack() {
            AudioTrack t;
            synchronized (lock) {
                t = track;
                track = null;
            }
            if (t == null) return;
            try {
                t.pause();
                t.flush();
            } catch (IllegalStateException ignored) {
            }
            t.release();
        }

        /** On the main thread, after 2 seconds without voice (R9). */
        private void playTone() {
            synchronized (lock) {
                if (voiceStarted || stopped) return;
                toneEndsAt = SystemClock.elapsedRealtime() + TONE_MS;
            }
            short[] pcm = toneSamples(Prefs.volume(ctx) / 100f);
            try {
                AudioTrack t = new AudioTrack.Builder()
                        .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
                        .setAudioFormat(format())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                t.write(pcm, 0, pcm.length);
                t.play();
                toneTrack = t;
                main.postDelayed(this::releaseTone, TONE_MS + 300);
            } catch (RuntimeException ignored) {
                // No tone is no problem: the answer arrives anyway.
            }
        }

        private void releaseTone() {
            if (toneTrack == null) return;
            try {
                toneTrack.stop();
            } catch (IllegalStateException ignored) {
            }
            toneTrack.release();
            toneTrack = null;
        }

        /** Stops the question. byUser: with «Parar sonido» (Stop sound); otherwise, it gave up at 6 seconds. */
        void abort(boolean byUser) {
            HttpURLConnection c;
            synchronized (lock) {
                if (stopped) return;
                stopped = true;
                c = conn;
                if (byUser) result.cancelled = true;
                result.ok = false;
                if (track != null) {
                    try {
                        track.pause();
                        track.flush();
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
            // Closing the connection wakes the reading thread; it is done off the main thread.
            if (c != null) new Thread(c::disconnect).start();
            finish();
        }

        private void finishLater() {
            main.post(this::finish);
        }

        /** On the main thread: notifies the caller, exactly once. */
        private void finish() {
            if (finished) return;
            finished = true;
            main.removeCallbacks(tone);
            main.removeCallbacks(giveUp);
            releaseTone();
            if (current == this) current = null;
            if (stopped && !result.cancelled) result.ok = false;
            done.done(result);
        }
    }

    // --- The soft tone: two short rising notes, with gentle fade-in and fade-out ---

    private static final long TONE_MS = 450;

    static short[] toneSamples(float volume) {
        int n = (int) (SAMPLE_RATE * TONE_MS / 1000);
        short[] out = new short[n];
        double[] notes = {659.25, 880.0};
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            int half = i < n / 2 ? 0 : 1;
            int start = half == 0 ? 0 : n / 2;
            double local = (double) (i - start) / (n / 2.0);
            // Each note fades in and out (half a sine wave): no clicks.
            double env = Math.sin(Math.PI * local);
            out[i] = (short) (Math.sin(2 * Math.PI * notes[half] * t) * env * 0.25 * volume * Short.MAX_VALUE);
        }
        return out;
    }

    private static AudioAttributes attributes(int contentType) {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(contentType)
                .build();
    }

    private static AudioFormat format() {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
    }

    private AudioTrack newTrack() {
        int min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
                .setAudioFormat(format())
                // About 200 ms: starts playing right away and absorbs network hiccups.
                .setBufferSizeInBytes(Math.max(min * 2, SAMPLE_RATE / 5 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        t.setVolume(Prefs.volume(ctx) / 100f);
        return t;
    }

    // --- What the phone knows at the moment of asking (R3) ---

    /**
     * The time, the visits for the coming week (spec 014) and how today's doses are going (spec 007).
     * withCalendar = false on the caregiver's phone: their calendar is not the person's.
     */
    static JSONObject facts(Context c, Calendar now, boolean withCalendar) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("now", SpokenText.moment(now));
        o.put("visits", new JSONArray(new Agenda(c).upcoming(now, withCalendar)));
        List<String> names = DoseLog.knownNames(c);
        for (Trigger t : Prefs.triggers(c)) {
            if (!t.doseName.isEmpty() && !contains(names, t.doseName)) names.add(t.doseName);
        }
        JSONArray doses = new JSONArray();
        for (String name : names) {
            doses.put(name + ": " + SpokenText.dose(DoseLog.lastToday(c, name, now), now, "").toLowerCase(Locale.ROOT));
        }
        o.put("doses", doses);
        return o;
    }

    private static boolean contains(List<String> names, String name) {
        for (String n : names) {
            if (Texts.forMatch(n).equals(Texts.forMatch(name))) return true;
        }
        return false;
    }

    /** The phone's date, to count the day's replies. */
    static String day(Calendar now) {
        return String.format(Locale.ROOT, "%1$tY-%1$tm-%1$td", now);
    }

    // --- Calling the functions ---

    private static HttpURLConnection open(String function, JSONObject body, int connectMs, int readMs)
            throws Exception {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) throw new IOException("Sin sesión");
        String token = Tasks.await(u.getIdToken(false), 10, TimeUnit.SECONDS).getToken();
        String project = FirebaseApp.getInstance().getOptions().getProjectId();
        // The language goes in the address too, for the voice list (which has no body).
        URL url = new URL("https://" + REGION + "-" + project + ".cloudfunctions.net/" + function
                + "?lang=" + (Lang.en() ? Lang.EN : Lang.ES));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            if (!body.has("lang")) body.put("lang", Lang.en() ? Lang.EN : Lang.ES);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(bytes.length);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
            }
        }
        return c;
    }

    private static final ExecutorService CAREGIVER = Executors.newSingleThreadExecutor();

    /**
     * From the caregiver's phone: the preview (preview) or the voice list (voices, body null).
     * cb receives the JSON or an error to show, on the main thread.
     */
    static void call(String function, JSONObject body, Cloud.Result<JSONObject> cb) {
        Handler main = new Handler(Looper.getMainLooper());
        CAREGIVER.execute(() -> {
            JSONObject value = null;
            String error = null;
            try {
                HttpURLConnection c = open(function, body, CAREGIVER_TIMEOUT_MS, CAREGIVER_TIMEOUT_MS);
                int code = c.getResponseCode();
                if (code == 200) {
                    value = new JSONObject(readAll(c.getInputStream()));
                } else if (code == 404) {
                    error = App.str(R.string.ai_err_not_installed);
                } else if (code == 429) {
                    error = App.str(R.string.ai_err_too_many);
                } else if (code == 401 || code == 403) {
                    error = App.str(R.string.ai_err_no_permission);
                } else {
                    error = App.str(R.string.ai_err_code, code);
                }
                c.disconnect();
            } catch (IOException e) {
                error = App.str(R.string.err_no_internet);
            } catch (Exception e) {
                error = App.str(R.string.ai_err_no_answer);
            }
            JSONObject v = value;
            String err = error;
            main.post(() -> cb.done(v, err));
        });
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }
}
