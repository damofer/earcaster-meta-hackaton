package app.oido;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A phrase and what it responds with when heard (its sound, or a text read aloud), and whether it also rings at a fixed time. */
final class Trigger {

    String phrase;
    /** null = the phone's default notification sound. */
    Uri soundUri;
    String soundName;
    /** Segment of the sound that plays, in milliseconds. endMs = 0 means until the end. */
    int startMs;
    int endMs;
    /** true = responds by reading speech aloud instead of with the sound (spec 001). */
    boolean speak;
    /** Text that is spoken, with data fields like {hora} or {fecha} (see SpokenText). */
    String speech = "";
    /** With speak: after the voice the sound plays too, for example a recording of Ana (spec 014). */
    boolean thenSound;

    // Reminder (spec 006): the same response also plays at a fixed time, without anyone asking.
    /** Minute of the day when it rings (8:00 = 480), or -1 if it is not a reminder. */
    int atMinute = -1;
    /** Days when it rings, as in Weekly (bit 0 = Sunday). */
    int days = Weekly.EVERY_DAY;
    /** Shows a large «Hecho» (Done) button for 30 minutes. */
    boolean confirm;
    /** When «Hecho» (Done) is tapped, a dose with this name is logged (spec 007). Empty = logs nothing. */
    String doseName = "";
    /** Rings even if the phone is on silent. */
    boolean evenSilent;

    // Action (spec 008): instead of playing or speaking, the phrase does something. Empty = not an action.
    static final String CALL = "call";
    static final String LIGHT_ON = "light_on";
    static final String LIGHT_OFF = "light_off";
    static final String DOSE = "dose";
    /** Help alert (spec 009): asks, waits 10 seconds and sends an SMS to the contacts. */
    static final String HELP = "help";
    String action = "";
    /** Who it calls (the first one) or who it alerts. */
    List<Contact> contacts = new ArrayList<>();

    /** A contact picked from the phone's contacts: their name and number are saved, not the contacts. */
    static final class Contact {
        final String name;
        final String number;

        Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    Trigger(String phrase, Uri soundUri, String soundName) {
        this.phrase = phrase == null ? "" : phrase;
        this.soundUri = soundUri;
        this.soundName = soundName;
    }

    /** The same response for another way of saying the phrase. */
    Trigger withPhrase(String otherPhrase) {
        Trigger t = new Trigger(otherPhrase, soundUri, soundName);
        t.startMs = startMs;
        t.endMs = endMs;
        t.speak = speak;
        t.speech = speech;
        t.thenSound = thenSound;
        t.atMinute = atMinute;
        t.days = days;
        t.confirm = confirm;
        t.doseName = doseName;
        t.evenSilent = evenSilent;
        t.action = action;
        t.contacts = new ArrayList<>(contacts);
        return t;
    }

    boolean isAction() {
        return !action.isEmpty();
    }

    /** Actions do not fire on their own at a set time: calling or turning on the light without anyone asking makes no sense. */
    boolean isReminder() {
        return !isAction() && atMinute >= 0 && (days & Weekly.EVERY_DAY) != 0;
    }

    static String toJson(List<Trigger> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Trigger t : list) {
                JSONObject o = new JSONObject();
                o.put("phrase", t.phrase);
                if (t.soundUri != null) o.put("sound_uri", t.soundUri.toString());
                if (t.soundName != null) o.put("sound_name", t.soundName);
                o.put("start_ms", t.startMs);
                o.put("end_ms", t.endMs);
                if (t.speak) o.put("speak", true);
                if (!t.speech.isEmpty()) o.put("speech", t.speech);
                if (t.thenSound) o.put("then_sound", true);
                if (t.atMinute >= 0) {
                    o.put("at_minute", t.atMinute);
                    o.put("days", t.days);
                    if (t.confirm) o.put("confirm", true);
                    if (!t.doseName.isEmpty()) o.put("dose_name", t.doseName);
                    if (t.evenSilent) o.put("even_silent", true);
                }
                if (t.isAction()) {
                    o.put("action", t.action);
                    if (!t.doseName.isEmpty()) o.put("dose_name", t.doseName);
                }
                if (!t.contacts.isEmpty()) {
                    JSONArray cs = new JSONArray();
                    for (Contact c : t.contacts) cs.put(new JSONObject().put("name", c.name).put("number", c.number));
                    o.put("contacts", cs);
                }
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        return arr.toString();
    }

    static List<Trigger> fromJson(String json) {
        List<Trigger> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String uri = o.optString("sound_uri", null);
                Trigger t = new Trigger(o.optString("phrase", ""),
                        uri == null || uri.isEmpty() ? null : Uri.parse(uri),
                        o.optString("sound_name", null));
                t.startMs = o.optInt("start_ms", 0);
                t.endMs = o.optInt("end_ms", 0);
                t.speak = o.optBoolean("speak", false);
                t.speech = o.optString("speech", "");
                t.thenSound = o.optBoolean("then_sound", false);
                t.atMinute = o.optInt("at_minute", -1);
                t.days = o.optInt("days", Weekly.EVERY_DAY);
                t.confirm = o.optBoolean("confirm", false);
                t.doseName = o.optString("dose_name", "");
                t.evenSilent = o.optBoolean("even_silent", false);
                t.action = o.optString("action", "");
                JSONArray cs = o.optJSONArray("contacts");
                for (int j = 0; cs != null && j < cs.length(); j++) {
                    JSONObject c = cs.getJSONObject(j);
                    t.contacts.add(new Contact(c.optString("name", ""), c.optString("number", "")));
                }
                out.add(t);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }
}
