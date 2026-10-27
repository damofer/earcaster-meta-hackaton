package app.oido;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that listens to the microphone with Vosk (offline)
 * and responds to the phrase it hears: with its sound or by reading its text aloud.
 */
public class ListenerService extends Service implements RecognitionListener {

    static final String ACTION_STOP = "app.oido.STOP";
    static final String ACTION_SILENCE = "app.oido.SILENCE";
    private static final String CHANNEL_ID = "escucha";
    private static final int NOTIFICATION_ID = 13;
    private static final float SAMPLE_RATE = 16000f;
    private static final long COOLDOWN_MS = 1500;
    /** Minimum confidence for calling phrases: the one for 90 % precision. */
    private static final float STRICT_CONFIDENCE = 0.5f + 0.49f * 0.9f;

    /** The service that is listening, to pause it while a reminder plays (spec 006). */
    private static ListenerService running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Responder responder;
    private Actions actions;
    /** true while a reminder is playing: not listening. */
    private boolean held = false;

    // Help alert (spec 009): after the question, 10 seconds to say «estoy bien» (I'm fine).
    private static final long HELP_WAIT_MS = 10_000;
    private Trigger helpPending;
    private Runnable helpFinished;
    private final Runnable helpTimeout = this::sendHelp;
    /** Changes with «Parar sonido» (Stop sound): so stopping it during the question does not leave the alert running. */
    private int helpTurn = 0;

    // AI conversation (spec 017)
    /** After a reply, for 15 seconds it can be answered without saying the name: «sí», «¿y mañana?» (yes, and tomorrow?). */
    private static final long AI_FOLLOW_MS = 15_000;
    /** The last two questions are remembered, and only if they are less than 3 minutes old. */
    private static final long AI_MEMORY_MS = 3 * 60_000L;
    private static final int AI_MEMORY_TURNS = 2;
    private AiClient ai;
    private final List<String[]> aiHistory = new ArrayList<>();
    private long aiFollowUntil = 0;
    private long aiLastAt = 0;
    /** Changes with «Parar sonido» (Stop sound): cuts off the phrases the phone's voice was about to say. */
    private int aiTurn = 0;
    /** The day the AI stopped serving (daily maximum): until tomorrow, fixed phrases only (R11). */
    private String aiRefusedDay = "";

    private Model model;
    private Recognizer recognizer;
    private SpeechService speech;

    private boolean started = false;
    private boolean destroyed = false;
    /** Active phrases, already normalized for comparison, with their sound. */
    private final List<Trigger> active = new ArrayList<>();
    private final List<String> activeMatch = new ArrayList<>();
    /** Phrases that require the highest precision regardless of Settings: the calling ones (spec 008, R5). */
    private boolean[] activeStrict = new boolean[0];
    private String listeningText = "";
    private long lastFireAt = 0;
    /** Already played for the phrase being spoken: the final result must not play it again. */
    private boolean firedThisUtterance = false;
    /** For each phrase: where it was seen in the last partial result and how many times in a row. */
    private float[] partialStart = new float[0];
    private int[] partialSeen = new int[0];

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    /** Every 15 minutes, while listening on the patient's phone, tells the caregiver it is still there (spec 015). */
    private static final long HEARTBEAT_MS = 15 * 60_000L;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            Cloud.deviceState(ListenerService.this, true);
            main.postDelayed(this, HEARTBEAT_MS);
        }
    };
    private boolean synced = false;

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        if (Prefs.linkedPatient(this) != null) {
            PatientSync.start(this);
            synced = true;
        }
    }

    /**
     * The caregiver changed the phrases from their phone (spec 015): they are reloaded without closing the microphone,
     * because Android does not allow reopening it from the background.
     */
    static void reload() {
        ListenerService s = running;
        if (s == null || s.destroyed || s.model == null) return;
        final List<Trigger> triggers = Prefs.triggers(s);
        s.executor.execute(() -> {
            File dir = ModelManager.modelDir(s);
            List<Trigger> usable = new ArrayList<>();
            for (Trigger t : triggers) {
                for (String phrase : Texts.variants(t.phrase)) {
                    // A phrase with a word the recognizer does not know cannot be heard: it is skipped.
                    if (ModelManager.missingWords(dir, phrase).isEmpty()) usable.add(t.withPhrase(phrase));
                }
            }
            s.main.post(() -> {
                if (s.destroyed) return;
                s.responder.prepare(triggers);
                s.activate(usable);
                // The caregiver may have raised the maximum or turned the AI back on.
                s.aiRefusedDay = "";
            });
        });
    }

    /** Pauses (on = true) or resumes listening. Called on the main thread. */
    static void hold(boolean on) {
        ListenerService s = running;
        if (s == null || s.destroyed) return;
        s.held = on;
        // A reminder cuts off the AI's reply.
        if (on && s.ai != null) s.ai.cancel();
        if (s.speech == null) return;
        if (on) {
            s.speech.setPause(true);
        } else if (!s.responder.isPlaying()) {
            s.resumeListening();
        }
    }

    /** Starts from scratch: whatever was being said while something played no longer counts. */
    private void resumeListening() {
        firedThisUtterance = false;
        java.util.Arrays.fill(partialSeen, 0);
        if (speech != null) {
            speech.reset();
            speech.setPause(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_SILENCE.equals(intent.getAction())) {
            helpTurn++;
            aiTurn++;
            if (helpPending != null) {
                cancelHelp();
            } else {
                if (ai != null) ai.cancel();
                if (actions != null) actions.stop();
                if (responder != null) responder.stop();
            }
            if (!started) stopSelf();
            return START_NOT_STICKY;
        }
        if (started) return START_NOT_STICKY;
        started = true;

        createChannel();
        try {
            Notification n = buildNotification(getString(R.string.listen_preparing), false);
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            AppState.set(AppState.Mode.ERROR, getString(R.string.listen_mic_denied));
            stopSelf();
            return START_NOT_STICKY;
        }

        final List<Trigger> triggers = Prefs.triggers(this);
        responder = new Responder(this);
        actions = new Actions(this, responder);
        ai = new AiClient(this);
        // The speech engine is slow to start: better now than when someone asks.
        responder.prepare(triggers);
        AppState.set(AppState.Mode.LOADING, getString(R.string.listen_loading));
        executor.execute(() -> load(triggers));
        // It does not restart on its own: Android does not allow reopening the microphone from the background.
        return START_NOT_STICKY;
    }

    /** Runs in the background: loading the model takes one or two seconds. */
    private void load(List<Trigger> triggers) {
        try {
            File dir = ModelManager.modelDir(this);
            List<Trigger> usable = new ArrayList<>();
            Set<String> missing = new LinkedHashSet<>();
            for (Trigger t : triggers) {
                // Each way of saying the phrase counts separately, with the same sound.
                for (String phrase : Texts.variants(t.phrase)) {
                    List<String> unknown = ModelManager.missingWords(dir, phrase);
                    // On the patient's phone nobody can fix the phrase: it is skipped and the rest are listened for.
                    if (synced && !unknown.isEmpty()) continue;
                    missing.addAll(unknown);
                    usable.add(t.withPhrase(phrase));
                }
            }
            // The patient's phone listens even if the caregiver has not created phrases yet: they will arrive on their own.
            if (usable.isEmpty() && !synced) throw new IllegalStateException(getString(R.string.listen_no_phrases));
            if (!missing.isEmpty()) {
                fail(getString(R.string.listen_unknown_word, String.join(" ", missing)));
                return;
            }

            Model m = new Model(dir.getAbsolutePath());
            // Full vocabulary of the language: so other similar words are not confused with the phrase.
            Recognizer r = new Recognizer(m, SAMPLE_RATE);
            // Asks for each word's confidence to discard what was not heard clearly.
            r.setWords(true);
            // Words in partial results too, to react without waiting for the phrase to end.
            r.setPartialWords(true);
            try {
                // Ends utterances after shorter pauses, so the final result arrives sooner.
                r.setEndpointerMode(1);
            } catch (Throwable ignored) {
            }

            main.post(() -> {
                if (destroyed) {
                    r.close();
                    m.close();
                    return;
                }
                model = m;
                recognizer = r;
                try {
                    speech = new SpeechService(r, SAMPLE_RATE);
                    speech.startListening(this);
                    if (held) speech.setPause(true);
                } catch (Exception e) {
                    fail(getString(R.string.listen_mic_busy));
                    return;
                }
                activate(usable);
                if (synced) main.post(heartbeat);
            });
        } catch (Exception e) {
            fail(getString(R.string.listen_load_failed, e.getMessage()));
        }
    }

    /** The phrases being listened for. Called on the main thread. */
    private void activate(List<Trigger> usable) {
        active.clear();
        activeMatch.clear();
        List<String> names = new ArrayList<>();
        for (Trigger t : usable) {
            active.add(t);
            activeMatch.add(Texts.forMatch(t.phrase));
            names.add(getString(R.string.quoted, t.phrase));
        }
        partialStart = new float[active.size()];
        partialSeen = new int[active.size()];
        activeStrict = new boolean[active.size()];
        for (int i = 0; i < active.size(); i++) activeStrict[i] = Trigger.CALL.equals(active.get(i).action);
        listeningText = active.isEmpty() ? getString(R.string.listen_no_phrases_listening)
                : getString(R.string.listen_listening, String.join(", ", names));
        AppState.set(AppState.Mode.LISTENING, listeningText);
        updateNotification(listeningText, AppState.playing);
    }

    private void fail(String message) {
        main.post(() -> {
            if (destroyed) return;
            AppState.set(AppState.Mode.ERROR, message);
            stopSelf();
        });
    }

    // --- Vosk results (they arrive on the main thread) ---

    @Override
    public void onPartialResult(String hypothesis) {
        if (helpPending != null) {
            if (!responder.isPlaying() && SpokenText.cancelsHelp(text(hypothesis, "partial"), false)) cancelHelp();
            return;
        }
        if (destroyed || held || responder.isPlaying() || firedThisUtterance) return;
        if (SystemClock.elapsedRealtime() - lastFireAt <= COOLDOWN_MS) return;
        // The partial changes while someone speaks: it only counts if the phrase stays in the same
        // place across several consecutive partials (they arrive about five times per second).
        List<Texts.Word> heard = words(hypothesis, "partial_result");
        int needed = partialsNeeded();
        for (int i = 0; i < active.size(); i++) {
            float start = Texts.findPhrase(heard, activeMatch.get(i), 0f);
            if (start < 0) {
                partialSeen[i] = 0;
                continue;
            }
            partialSeen[i] = start == partialStart[i] ? partialSeen[i] + 1 : 1;
            partialStart[i] = start;
            if (partialSeen[i] >= (activeStrict[i] ? Math.max(needed, 3) : needed)) {
                fire(active.get(i));
                return;
            }
        }
    }

    /** How many consecutive partials are needed for the chosen precision. */
    private int partialsNeeded() {
        int s = Prefs.strictness(this);
        return s < 35 ? 1 : s < 75 ? 2 : 3;
    }

    @Override
    public void onResult(String hypothesis) {
        handleFinal(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        handleFinal(hypothesis);
    }

    @Override
    public void onError(Exception exception) {
        AppState.set(AppState.Mode.ERROR, getString(R.string.listen_mic_error, exception.getMessage()));
        stopSelf();
    }

    @Override
    public void onTimeout() {
    }

    private static String text(String json, String key) {
        try {
            return new JSONObject(json).optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Recognized words with their confidence, from Vosk's JSON with setWords(true). */
    private static List<Texts.Word> words(String json, String key) {
        List<Texts.Word> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(json).optJSONArray(key);
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject w = arr.getJSONObject(i);
                out.add(new Texts.Word(w.optString("word", ""), (float) w.optDouble("conf", 0),
                        (float) w.optDouble("start", 0)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** End of a spoken utterance: in case the partials were not enough, it is checked with each word's confidence. */
    private void handleFinal(String json) {
        if (helpPending != null) {
            if (!responder.isPlaying() && SpokenText.cancelsHelp(text(json, "text"), true)) cancelHelp();
            return;
        }
        boolean alreadyFired = firedThisUtterance;
        firedThisUtterance = false;
        java.util.Arrays.fill(partialSeen, 0);
        if (destroyed || held || responder.isPlaying() || alreadyFired) return;
        if (SystemClock.elapsedRealtime() - lastFireAt <= COOLDOWN_MS) return;
        List<Texts.Word> heard = words(json, "result");
        float minConf = Prefs.minConfidence(this);
        for (int i = 0; i < active.size(); i++) {
            float conf = activeStrict[i] ? Math.max(minConf, STRICT_CONFIDENCE) : minConf;
            if (Texts.findPhrase(heard, activeMatch.get(i), conf) >= 0) {
                fire(active.get(i));
                return;
            }
        }
        // Fixed phrases go first (R1): only what does not match can go to the AI.
        maybeAskAi(text(json, "text"));
    }

    private void fire(Trigger t) {
        firedThisUtterance = true;
        Runnable finished = startAnswer(t.phrase, getString(t.speak || t.isAction()
                ? R.string.listen_answering : R.string.listen_playing, t.phrase));
        if (Trigger.HELP.equals(t.action)) {
            askHelp(t, finished);
        } else if (t.isAction()) {
            actions.run(t, finished);
        } else {
            responder.respond(t, finished, null);
        }
    }

    /** Pauses listening while responding. Returns what to call when finished to resume listening. */
    private Runnable startAnswer(String heard, String notification) {
        lastFireAt = SystemClock.elapsedRealtime();
        AppState.detected(heard);
        // Pauses listening while it plays, so it does not react to its own sound.
        if (speech != null) speech.setPause(true);
        AppState.setPlaying(true);
        updateNotification(notification, true);
        return () -> {
            lastFireAt = SystemClock.elapsedRealtime();
            AppState.setPlaying(false);
            if (destroyed) return;
            if (!held) resumeListening();
            updateNotification(listeningText, false);
        };
    }

    // --- AI conversation (spec 017) ---

    /** What was heard is not a fixed phrase: if it is for the AI (R2), the AI is asked. */
    private void maybeAskAi(String heard) {
        String pid = Prefs.linkedPatient(this);
        if (pid == null || ai == null || !Prefs.aiOn(this) || !Cloud.available(this)) return;
        if (AiClient.day(Calendar.getInstance()).equals(aiRefusedDay)) return;
        String name = Prefs.aiName(this);
        boolean followUp = SystemClock.elapsedRealtime() < aiFollowUntil;
        String q = AiText.question(heard, name, Prefs.aiAny(this), followUp);
        if (q == null) return;
        aiFollowUntil = 0;
        Runnable finished = startAnswer(q.isEmpty() ? name : q, getString(R.string.listen_asking, name));
        if (q.isEmpty()) {
            // Only the name was said: it answers and waits for the question without it having to be repeated.
            responder.say(getString(R.string.ai_yes), listenForMore(finished));
            return;
        }
        if (SystemClock.elapsedRealtime() - aiLastAt > AI_MEMORY_MS) aiHistory.clear();
        ai.ask(pid, q, new ArrayList<>(aiHistory), r -> answered(q, r, finished));
    }

    private Runnable listenForMore(Runnable finished) {
        return () -> {
            aiFollowUntil = SystemClock.elapsedRealtime() + AI_FOLLOW_MS;
            finished.run();
        };
    }

    private void answered(String q, AiClient.Result r, Runnable finished) {
        if (r.cancelled) {
            finished.run();
            return;
        }
        if (r.refused) {
            aiRefusedDay = AiClient.day(Calendar.getInstance());
            finished.run();
            return;
        }
        if (!r.ok) {
            responder.say(Prefs.aiFallback(this), finished);
            return;
        }
        aiLastAt = SystemClock.elapsedRealtime();
        if (!r.answer.isEmpty()) {
            aiHistory.add(new String[]{q, r.answer});
            while (aiHistory.size() > AI_MEMORY_TURNS) aiHistory.remove(0);
        }
        Runnable then = r.action != null ? () -> aiAction(q, r.action, finished) : listenForMore(finished);
        sayInOrder(r.unspoken, 0, aiTurn, then, finished);
    }

    /** The phrases that arrived without the ElevenLabs voice, with the phone's voice. */
    private void sayInOrder(List<String> texts, int i, int mine, Runnable then, Runnable stopped) {
        if (destroyed || mine != aiTurn) {
            stopped.run();
        } else if (i >= texts.size()) {
            then.run();
        } else {
            responder.say(texts.get(i), () -> sayInOrder(texts, i + 1, mine, then, stopped));
        }
    }

    /** The action the AI requested, with the usual safeguards (R5): it says first what it will do and can be stopped. */
    private void aiAction(String q, JSONObject a, Runnable finished) {
        Trigger t = new Trigger(q, null, null);
        switch (a.optString("type")) {
            case "call":
                t.action = Trigger.CALL;
                t.contacts.add(new Trigger.Contact(a.optString("name"), a.optString("number")));
                actions.run(t, finished);
                return;
            case "light":
                t.action = a.optBoolean("on") ? Trigger.LIGHT_ON : Trigger.LIGHT_OFF;
                actions.run(t, finished);
                return;
            case "dose":
                t.action = Trigger.DOSE;
                t.doseName = a.optString("name");
                actions.run(t, finished);
                return;
            case "help":
                // Who to alert is given by the help phrase (spec 009); the message carries what was said.
                for (Trigger h : Prefs.triggers(this)) {
                    if (Trigger.HELP.equals(h.action) && !h.contacts.isEmpty()) {
                        askHelp(h.withPhrase(q), finished);
                        return;
                    }
                }
                responder.say(getString(R.string.help_no_one), finished);
                return;
            default:
                finished.run();
        }
    }

    // --- Help alert (spec 009) ---

    /** Asks and allows 10 seconds to cancel by saying «estoy bien» (I'm fine) or «no». */
    private void askHelp(Trigger t, Runnable finished) {
        if (t.contacts.isEmpty()) {
            responder.say(getString(R.string.help_no_one), finished);
            return;
        }
        final int mine = ++helpTurn;
        responder.say(SpokenText.helpQuestion(Actions.contactNames(t)), () -> {
            if (destroyed) return;
            if (mine != helpTurn) {
                finished.run();
                return;
            }
            if (helpPending == null && helpFinished == null) {
                helpPending = t;
                helpFinished = finished;
                // Listens for the answer: only «estoy bien» (I'm fine), «cancela» (cancel) or a clear «no» count.
                resumeListening();
                main.postDelayed(helpTimeout, HELP_WAIT_MS);
            }
        });
    }

    private void cancelHelp() {
        main.removeCallbacks(helpTimeout);
        helpPending = null;
        if (speech != null) speech.setPause(true);
        Runnable finished = takeHelpFinished();
        responder.say(getString(R.string.help_cancelled), finished);
    }

    private Runnable takeHelpFinished() {
        Runnable r = helpFinished;
        helpFinished = null;
        return r != null ? r : () -> {};
    }

    /** The 10 seconds have passed: SMS to the contacts; if it fails, calls the first one. */
    private void sendHelp() {
        Trigger t = helpPending;
        if (t == null || destroyed) return;
        helpPending = null;
        if (speech != null) speech.setPause(true);
        String text = SpokenText.helpMessage(Prefs.personName(this), t.phrase, java.util.Calendar.getInstance());
        HelpAlert.send(this, t.contacts, text, sent -> {
            Cloud.helpEvent(this, t.phrase, sent);
            Runnable finished = takeHelpFinished();
            if (destroyed) return;
            String who = SpokenText.names(Actions.contactNames(t));
            if (sent) {
                responder.say(getString(R.string.help_sent, who), finished);
                return;
            }
            Trigger.Contact first = t.contacts.get(0);
            final int mine = ++helpTurn;
            responder.say(getString(R.string.help_sms_failed_calling, first.name), () -> {
                if (mine != helpTurn) {
                    finished.run();
                } else if (!Actions.call(this, first.number)) {
                    responder.say(getString(R.string.help_failed), finished);
                } else {
                    finished.run();
                }
            });
        });
    }

    // --- Mandatory notification ---

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.channel_listening_desc));
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text, boolean playing) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ListenerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.notif_listening_title, getString(R.string.app_name)))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open);
        if (playing) {
            PendingIntent silence = PendingIntent.getService(this, 2,
                    new Intent(this, ListenerService.class).setAction(ACTION_SILENCE),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop), getString(R.string.parar_sonido), silence).build());
        }
        b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stop), getString(R.string.stop_listening), stop).build());
        return b.build();
    }

    private void updateNotification(String text, boolean playing) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text, playing));
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        if (running == this) running = null;
        main.removeCallbacks(helpTimeout);
        main.removeCallbacks(heartbeat);
        if (synced) {
            Cloud.deviceState(this, false);
            PatientSync.stop();
        }
        helpPending = null;
        if (ai != null) ai.release();
        if (responder != null) responder.release();
        if (actions != null) actions.release();
        AppState.playing = false;
        if (speech != null) {
            speech.stop();
            speech.shutdown();
            speech = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        executor.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (AppState.mode != AppState.Mode.ERROR) {
            AppState.set(AppState.Mode.STOPPED, "");
        }
        super.onDestroy();
    }
}
