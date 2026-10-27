package app.oido;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Dose log (spec 007): on the phone, and also in the cloud if a caregiver is connected (spec 015).
 * Anything older than 30 days is deleted.
 * Stores what, when and how it was logged: by the caregiver, the «Hecho» (Done) button or the person's voice.
 */
final class DoseLog {

    static final String BY_CAREGIVER = "cuidador";
    static final String BY_BUTTON = "botón";
    static final String BY_VOICE = "voz";
    private static final long KEEP_MS = 30L * 86_400_000L;

    static final class Entry {
        final String name;
        final long at;
        final String by;

        Entry(String name, long at, String by) {
            this.name = name;
            this.at = at;
            this.by = by;
        }
    }

    private DoseLog() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tomas", Context.MODE_PRIVATE);
    }

    /** All entries from the last 30 days, oldest to newest. */
    static synchronized List<Entry> entries(Context c) {
        List<Entry> out = new ArrayList<>();
        long oldest = System.currentTimeMillis() - KEEP_MS;
        try {
            JSONArray arr = new JSONArray(sp(c).getString("log", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.optLong("at", 0);
                if (at >= oldest) out.add(new Entry(o.optString("name", ""), at, o.optString("by", "")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    static void add(Context c, String name, String by) {
        if (name == null || name.trim().isEmpty()) return;
        Entry e = new Entry(name.trim(), System.currentTimeMillis(), by);
        synchronized (DoseLog.class) {
            List<Entry> list = entries(c);
            list.add(e);
            save(c, list);
        }
        // With a caregiver connected (spec 015), the dose also reaches the other phone.
        Cloud.doseAdded(c, e);
    }

    /** Merges the doses that came from the other phone (spec 015). Repeats are not duplicated. */
    static synchronized void merge(Context c, List<Entry> remote) {
        List<Entry> list = entries(c);
        boolean changed = false;
        long oldest = System.currentTimeMillis() - KEEP_MS;
        for (Entry r : remote) {
            if (r.at < oldest) continue;
            boolean known = false;
            for (Entry e : list) {
                if (e.at == r.at && e.name.equals(r.name)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                list.add(r);
                changed = true;
            }
        }
        if (!changed) return;
        list.sort((a, b) -> Long.compare(a.at, b.at));
        save(c, list);
    }

    static synchronized void clear(Context c) {
        sp(c).edit().clear().apply();
    }

    private static void save(Context c, List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) arr.put(new JSONObject().put("name", e.name).put("at", e.at).put("by", e.by));
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("log", arr.toString()).apply();
    }

    /** Time of the last dose of name logged today, or 0. The name is compared ignoring accents and case. */
    static long lastToday(Context c, String name, Calendar now) {
        String wanted = Texts.forMatch(name);
        long last = 0;
        for (Entry e : entries(c)) {
            if (Texts.forMatch(e.name).equals(wanted) && SpokenText.sameDay(now, e.at) && e.at > last) last = e.at;
        }
        return last;
    }

    /** Known names: those from reminders with «Hecho» (Done) and those already logged. */
    static List<String> knownNames(Context c) {
        List<String> names = new ArrayList<>();
        for (Trigger t : Prefs.triggers(c)) {
            if (t.isReminder() && !t.doseName.isEmpty()) addName(names, t.doseName);
        }
        List<Entry> all = entries(c);
        for (int i = all.size() - 1; i >= 0; i--) addName(names, all.get(i).name);
        return names;
    }

    private static void addName(List<String> names, String name) {
        for (String n : names) {
            if (Texts.forMatch(n).equals(Texts.forMatch(name))) return;
        }
        names.add(name);
    }
}
