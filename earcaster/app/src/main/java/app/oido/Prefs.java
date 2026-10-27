package app.oido;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Saved settings: phrases with their response, volume, precision, the visit schedule and the phone's mode. */
final class Prefs {

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("oido", Context.MODE_PRIVATE);
    }

    static List<Trigger> triggers(Context c) {
        SharedPreferences p = sp(c);
        String json = p.getString("triggers", null);
        if (json != null) return Trigger.fromJson(json);
        // First run, or coming from the single-phrase version.
        List<Trigger> list = new ArrayList<>();
        String uri = p.getString("sound_uri", null);
        list.add(new Trigger(p.getString("phrase", Lang.en() ? "thirteen" : "trece"),
                uri == null ? null : Uri.parse(uri), p.getString("sound_name", null)));
        return list;
    }

    static void setTriggers(Context c, List<Trigger> list) {
        sp(c).edit().putString("triggers", Trigger.toJson(list)).apply();
    }

    static int volume(Context c) {
        return sp(c).getInt("volume", 100);
    }

    static void setVolume(Context c, int value) {
        sp(c).edit().putInt("volume", value).apply();
    }

    /** Speed of the spoken responses' voice: 1 = normal. A little slower by default. */
    static float speechRate(Context c) {
        return sp(c).getFloat("speech_rate", 0.85f);
    }

    static void setSpeechRate(Context c, float value) {
        sp(c).edit().putFloat("speech_rate", value).apply();
    }

    // --- Visit schedule (spec 014) ---

    /** true if the caregiver turned on «Leer el calendario del teléfono» (Read the phone's calendar). */
    static boolean calendarOn(Context c) {
        return sp(c).getBoolean("visits_calendar", false);
    }

    static void setCalendarOn(Context c, boolean on) {
        sp(c).edit().putBoolean("visits_calendar", on).apply();
    }

    /** Chosen calendars (their ids). Empty = all visible ones. */
    static Set<String> calendarIds(Context c) {
        return new HashSet<>(sp(c).getStringSet("visits_calendar_ids", new HashSet<>()));
    }

    static void setCalendarIds(Context c, Set<String> ids) {
        sp(c).edit().putStringSet("visits_calendar_ids", new HashSet<>(ids)).apply();
    }

    static List<Agenda.Fixed> fixedVisits(Context c) {
        return Agenda.Fixed.fromJson(sp(c).getString("fixed_visits", null));
    }

    /** One-off visits approved from a family message (spec 020). */
    static List<Agenda.Once> onceVisits(Context c) {
        return Agenda.Once.fromJson(sp(c).getString("once_visits", null));
    }

    static void setFixedVisits(Context c, List<Agenda.Fixed> list) {
        sp(c).edit().putString("fixed_visits", Agenda.Fixed.toJson(list)).apply();
    }

    // --- Help alert (spec 009) ---

    /** The person's name, for the SMS: «María dijo "me caí"» (María said "I fell"). */
    static String personName(Context c) {
        return sp(c).getString("person_name", "");
    }

    static void setPersonName(Context c, String name) {
        sp(c).edit().putString("person_name", name).apply();
    }

    /** true if the caregiver accepted that Earcaster is not an emergency service. */
    static boolean helpAccepted(Context c) {
        return sp(c).getBoolean("help_accepted", false);
    }

    static void setHelpAccepted(Context c, boolean accepted) {
        sp(c).edit().putBoolean("help_accepted", accepted).apply();
    }

    /** 0 = triggers easily, 100 = only if the phrase is heard very clearly. */
    static int strictness(Context c) {
        return sp(c).getInt("strictness", 60);
    }

    static void setStrictness(Context c, int value) {
        sp(c).edit().putInt("strictness", value).apply();
    }

    /** Minimum confidence (0–1) each word of the phrase must have. */
    static float minConfidence(Context c) {
        return 0.5f + 0.49f * strictness(c) / 100f;
    }

    // --- AI conversation (spec 017): set up by the caregiver and travels with the settings ---

    static final String AI_ON = "ai_on";
    static final String AI_NAME = "ai_name";
    static final String AI_ANY = "ai_any";
    static final String AI_VOICE = "ai_voice";
    static final String AI_VOICE_NAME = "ai_voice_name";
    static final String AI_FALLBACK = "ai_fallback";
    static final String AI_MAX = "ai_max";
    static final int DEFAULT_AI_MAX = 50;
    /** Who writes the answers (spec 018): "claude" or "meta". Only the function reads it. */
    static final String AI_MODEL = "ai_model";
    static final String AI_MODEL_CLAUDE = "claude";
    static final String AI_MODEL_META = "meta";

    /** The assistant is Clara in Spanish and Grace in English. */
    static String defaultAiName() {
        return Lang.en() ? "Grace" : "Clara";
    }

    static String defaultAiFallback() {
        return Lang.en() ? "I did not quite understand you, could you say it another way?"
                : "No te entendí bien, ¿me lo dices de otra forma?";
    }

    static boolean aiOn(Context c) {
        return sp(c).getBoolean(AI_ON, false);
    }

    /** The name used to call the assistant: «Clara, ¿Ana viene hoy?» (Clara, is Ana coming today?). */
    static String aiName(Context c) {
        String name = sp(c).getString(AI_NAME, "").trim();
        return name.isEmpty() ? defaultAiName() : name;
    }

    /** Experimental option: answers any question it hears, without the name. */
    static boolean aiAny(Context c) {
        return sp(c).getBoolean(AI_ANY, false);
    }

    static String aiVoice(Context c) {
        return sp(c).getString(AI_VOICE, "");
    }

    static String aiVoiceName(Context c) {
        return sp(c).getString(AI_VOICE_NAME, "");
    }

    /** What the phone's voice says if the AI does not answer (R8). */
    static String aiFallback(Context c) {
        String text = sp(c).getString(AI_FALLBACK, "").trim();
        return text.isEmpty() ? defaultAiFallback() : text;
    }

    static int aiMax(Context c) {
        return sp(c).getInt(AI_MAX, DEFAULT_AI_MAX);
    }

    static String aiModel(Context c) {
        return AI_MODEL_META.equals(sp(c).getString(AI_MODEL, "")) ? AI_MODEL_META : AI_MODEL_CLAUDE;
    }

    // --- A patient's settings in the cloud (spec 015) ---

    /**
     * What the caregiver configures and travels to the person's phone. Does not include the chosen calendars:
     * their ids differ on each phone, so over there all visible ones are read.
     */
    static String exportConfig(Context c) {
        SharedPreferences p = sp(c);
        JSONObject o = new JSONObject();
        try {
            o.put("triggers", Trigger.toJson(triggers(c)));
            o.put("volume", volume(c));
            o.put("speech_rate", (double) speechRate(c));
            o.put("strictness", strictness(c));
            o.put("visits_calendar", calendarOn(c));
            o.put("fixed_visits", p.getString("fixed_visits", "[]"));
            o.put("once_visits", p.getString("once_visits", "[]"));
            o.put("person_name", personName(c));
            o.put("help_accepted", helpAccepted(c));
            o.put(AI_ON, aiOn(c));
            o.put(AI_NAME, aiName(c));
            o.put(AI_ANY, aiAny(c));
            o.put(AI_VOICE, aiVoice(c));
            o.put(AI_VOICE_NAME, aiVoiceName(c));
            o.put(AI_FALLBACK, aiFallback(c));
            o.put(AI_MAX, aiMax(c));
            o.put(AI_MODEL, aiModel(c));
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    /** Saves on this phone the settings that arrived from the cloud. Anything missing stays as it was. */
    static void importConfig(Context c, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = sp(c).edit();
            if (o.has("triggers")) e.putString("triggers", o.getString("triggers"));
            if (o.has("volume")) e.putInt("volume", o.getInt("volume"));
            if (o.has("speech_rate")) e.putFloat("speech_rate", (float) o.getDouble("speech_rate"));
            if (o.has("strictness")) e.putInt("strictness", o.getInt("strictness"));
            if (o.has("visits_calendar")) e.putBoolean("visits_calendar", o.getBoolean("visits_calendar"));
            if (o.has("fixed_visits")) e.putString("fixed_visits", o.getString("fixed_visits"));
            if (o.has("once_visits")) e.putString("once_visits", o.getString("once_visits"));
            if (o.has("person_name")) e.putString("person_name", o.getString("person_name"));
            if (o.has("help_accepted")) e.putBoolean("help_accepted", o.getBoolean("help_accepted"));
            if (o.has(AI_ON)) e.putBoolean(AI_ON, o.getBoolean(AI_ON));
            if (o.has(AI_NAME)) e.putString(AI_NAME, o.getString(AI_NAME));
            if (o.has(AI_ANY)) e.putBoolean(AI_ANY, o.getBoolean(AI_ANY));
            if (o.has(AI_VOICE)) e.putString(AI_VOICE, o.getString(AI_VOICE));
            if (o.has(AI_VOICE_NAME)) e.putString(AI_VOICE_NAME, o.getString(AI_VOICE_NAME));
            if (o.has(AI_FALLBACK)) e.putString(AI_FALLBACK, o.getString(AI_FALLBACK));
            if (o.has(AI_MAX)) e.putInt(AI_MAX, o.getInt(AI_MAX));
            if (o.has(AI_MODEL)) e.putString(AI_MODEL, o.getString(AI_MODEL));
            e.commit();
        } catch (JSONException ignored) {
        }
    }

    /** Deletes this phone's settings (when unlinking it). */
    static void clearConfig(Context c) {
        sp(c).edit().clear().commit();
    }

    // --- This phone (spec 015): does not travel to the cloud or change when editing a patient ---

    static final String MODE_UNSET = "";
    /** As before: everything on a single phone, no account. */
    static final String MODE_SOLO = "solo";
    static final String MODE_CAREGIVER = "cuidador";
    static final String MODE_PATIENT = "paciente";

    private static SharedPreferences device(Context c) {
        return c.getSharedPreferences("dispositivo", Context.MODE_PRIVATE);
    }

    static String mode(Context c) {
        return device(c).getString("mode", MODE_UNSET);
    }

    static void setMode(Context c, String mode) {
        device(c).edit().putString("mode", mode).commit();
    }

    /** The patient this phone is linked to, or null. */
    static String linkedPatient(Context c) {
        return MODE_PATIENT.equals(mode(c)) ? device(c).getString("patient_id", null) : null;
    }

    static void setLinkedPatient(Context c, String pid) {
        device(c).edit().putString("patient_id", pid).commit();
    }

    /** The last settings that arrived from the cloud, to avoid reloading if they did not change. */
    static String syncedConfig(Context c) {
        return device(c).getString("synced_config", "");
    }

    static void setSyncedConfig(Context c, String json) {
        device(c).edit().putString("synced_config", json).apply();
    }

    /** 4-digit PIN to exit the patient screen (spec 004). Empty = no PIN. */
    static String pin(Context c) {
        return device(c).getString("pin", "");
    }

    static void setPin(Context c, String pin) {
        device(c).edit().putString("pin", pin).apply();
    }

    static int pinFails(Context c) {
        return device(c).getInt("pin_fails", 0);
    }

    static long pinLockedUntil(Context c) {
        return device(c).getLong("pin_locked_until", 0);
    }

    static void setPinFails(Context c, int fails, long lockedUntil) {
        device(c).edit().putInt("pin_fails", fails).putLong("pin_locked_until", lockedUntil).apply();
    }

    /** Goes back to the first-run question: this phone no longer belongs to anyone. */
    static void forgetDevice(Context c) {
        device(c).edit().clear().commit();
    }
}
