package app.oido;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Reads a text aloud with the phone's voice in the app's language (offline if installed).
 * Like SoundPlayer: the end notification always arrives exactly once, when finished, when stopped or on failure.
 */
final class Speaker {

    interface Done {
        /** spoken = false if it could not be said (no voice in the language or the engine failed). */
        void run(boolean spoken);
    }

    private enum State { STARTING, READY, NO_VOICE }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    /** The language the voice speaks, fixed when created: ES or EN. */
    private final String lang;
    private State state = State.STARTING;
    private boolean closed = false;
    private int nextId = 0;

    /** What is being said (or waiting for the engine to be ready). */
    private String currentId;
    private String pendingText;
    private float pendingRate;
    private float pendingVolume;
    private Done currentDone;

    Speaker(Context ctx) {
        lang = Lang.code(ctx);
        tts = new TextToSpeech(ctx.getApplicationContext(), status -> main.post(() -> onInit(status)));
    }

    private void onInit(int status) {
        if (closed) return;
        if (status != TextToSpeech.SUCCESS || !chooseLanguage()) {
            state = State.NO_VOICE;
            if (currentId != null) finish(currentId, false);
            return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                main.post(() -> finish(id, true));
            }
            @Override public void onError(String id) {
                main.post(() -> finish(id, false));
            }
            @Override public void onStop(String id, boolean interrupted) {
                main.post(() -> finish(id, true));
            }
        });
        state = State.READY;
        if (pendingText != null) {
            String text = pendingText;
            pendingText = null;
            say(currentId, text, pendingRate, pendingVolume);
        }
    }

    /**
     * The phone's own variant of the language if it has it (es-CO, es-MX, en-GB…), otherwise Spain's Spanish
     * or US English, otherwise any.
     */
    private boolean chooseLanguage() {
        Locale device = Locale.getDefault();
        String main = Lang.EN.equals(lang) ? "en-US" : "es-ES";
        Locale[] options = lang.equals(device.getLanguage())
                ? new Locale[]{device, Locale.forLanguageTag(main), Locale.forLanguageTag(lang)}
                : new Locale[]{Locale.forLanguageTag(main), Locale.forLanguageTag(lang)};
        for (Locale l : options) {
            try {
                int r = tts.setLanguage(l);
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** false if it is already known that the phone has no voice in the language. */
    boolean hasVoice() {
        return state != State.NO_VOICE;
    }

    boolean isPlaying() {
        return currentId != null;
    }

    /** Says text with the speed and volume from Settings. */
    void speak(Context ctx, String text, Done onDone) {
        stop();
        String id = "r" + (nextId++);
        currentId = id;
        currentDone = onDone;
        if (state == State.NO_VOICE || text == null || text.trim().isEmpty()) {
            finish(id, false);
            return;
        }
        float rate = Prefs.speechRate(ctx);
        float volume = Prefs.volume(ctx) / 100f;
        if (state == State.STARTING) {
            pendingText = text;
            pendingRate = rate;
            pendingVolume = volume;
            return;
        }
        say(id, text, rate, volume);
    }

    private void say(String id, String text, float rate, float volume) {
        tts.setSpeechRate(rate);
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
            finish(id, false);
        }
    }

    private void finish(String id, boolean spoken) {
        if (id == null || !id.equals(currentId)) return;
        currentId = null;
        pendingText = null;
        Done done = currentDone;
        currentDone = null;
        if (done != null) done.run(spoken);
    }

    /** Cuts the voice off immediately and notifies whoever requested it. */
    void stop() {
        if (currentId == null) return;
        String id = currentId;
        if (state == State.READY) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
        finish(id, true);
    }

    void shutdown() {
        stop();
        closed = true;
        try {
            tts.shutdown();
        } catch (Exception ignored) {
        }
    }
}
