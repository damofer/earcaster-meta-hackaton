package app.oido;

import android.content.Context;

import java.util.Calendar;

/**
 * Responds to a phrase: with its sound, or by reading its text aloud (spec 001),
 * and if requested, with the sound after the voice (spec 014).
 * If there is no Spanish voice, the sound plays so the person hears something.
 * onDone is always called exactly once: when finished, when stopped or on failure.
 */
final class Responder {

    private final Context ctx;
    private final SoundPlayer sound = new SoundPlayer();
    /** Visits (spec 014) and logged doses (spec 007) for the text's data fields. */
    private final SpokenText.Sources sources;
    /** Created only if some phrase speaks: starting the speech engine takes a moment. */
    private Speaker speaker;
    /** Changes with every response and when stopped: so a stopped voice does not lead into the sound after it. */
    private int turn = 0;

    Responder(Context ctx) {
        this.ctx = ctx;
        this.sources = sources(ctx);
    }

    /** Where the text's named data fields come from: the visit schedule and the dose log. */
    static SpokenText.Sources sources(Context ctx) {
        Agenda agenda = new Agenda(ctx);
        return new SpokenText.Sources() {
            @Override
            public SpokenText.Visit nextVisit(String name, Calendar now) {
                return agenda.nextVisit(name, now);
            }

            @Override
            public long doseToday(String name, Calendar now) {
                return DoseLog.lastToday(ctx, name, now);
            }
        };
    }

    /** Starts the speech engine ahead of time, if any of these phrases will need it. */
    void prepare(Iterable<Trigger> triggers) {
        for (Trigger t : triggers) {
            if (t.speak) {
                speaker();
                return;
            }
        }
    }

    private Speaker speaker() {
        if (speaker == null) speaker = new Speaker(ctx);
        return speaker;
    }

    boolean isPlaying() {
        return sound.isPlaying() || (speaker != null && speaker.isPlaying());
    }

    /** onNoVoice (may be null) is called if the phrase should speak and the phone has no Spanish voice. */
    void respond(Trigger t, Runnable onDone, Runnable onNoVoice) {
        stop();
        final int mine = turn;
        if (!t.speak) {
            sound.play(ctx, t.soundUri, t.startMs, t.endMs, onDone);
            return;
        }
        String text = SpokenText.fill(t.speech, Calendar.getInstance(), sources);
        speaker().speak(ctx, text, spoken -> {
            if (mine != turn) {
                // Stopped halfway: does not continue with the sound.
                if (onDone != null) onDone.run();
                return;
            }
            if (!spoken && onNoVoice != null && !speaker().hasVoice()) onNoVoice.run();
            if (t.thenSound) {
                sound.play(ctx, t.soundUri, t.startMs, t.endMs, onDone);
            } else if (spoken) {
                if (onDone != null) onDone.run();
            } else {
                sound.play(ctx, null, onDone);
            }
        });
    }

    /** Says a fixed text (for example «Llamando a Carlos», Calling Carlos). Without a Spanish voice, the notification tone plays. */
    void say(String text, Runnable onDone) {
        stop();
        final int mine = turn;
        speaker().speak(ctx, text, spoken -> {
            if (spoken || mine != turn) {
                if (onDone != null) onDone.run();
            } else {
                sound.play(ctx, null, onDone);
            }
        });
    }

    void stop() {
        turn++;
        sound.stop();
        if (speaker != null) speaker.stop();
    }

    void release() {
        stop();
        if (speaker != null) {
            speaker.shutdown();
            speaker = null;
        }
    }
}
