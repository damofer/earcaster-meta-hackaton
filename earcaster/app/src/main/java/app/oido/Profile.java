package app.oido;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The patient profile for the AI (spec 016): who they are and what the app may do without asking.
 * Stored in Firestore as a map; this class does not use Android so it can be tested.
 */
final class Profile {

    /** R6: texts, names and relationships combined; numbers do not count. */
    static final int MAX_CHARS = 4000;

    /** A contact the AI may call: «llama a mi hijo» (call my son) needs to know who the son is. */
    static final class Call {
        final String name;
        final String number;
        final String relation;

        Call(String name, String number, String relation) {
            this.name = clean(name);
            this.number = clean(number);
            this.relation = clean(relation);
        }
    }

    String name = "";
    String callMe = "";
    String home = "";
    String family = "";
    String routine = "";
    String likes = "";
    String sensitive = "";
    String free = "";

    List<Call> calls = new ArrayList<>();
    boolean light = false;
    List<String> doses = new ArrayList<>();
    boolean help = false;

    int length() {
        int n = name.length() + callMe.length() + home.length() + family.length() + routine.length()
                + likes.length() + sensitive.length() + free.length();
        for (Call c : calls) n += c.name.length() + c.relation.length();
        return n;
    }

    boolean tooLong() {
        return length() > MAX_CHARS;
    }

    boolean isEmpty() {
        return length() == 0 && calls.isEmpty() && !light && doses.isEmpty() && !help;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("callMe", callMe);
        m.put("home", home);
        m.put("family", family);
        m.put("routine", routine);
        m.put("likes", likes);
        m.put("sensitive", sensitive);
        m.put("free", free);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Call c : calls) {
            Map<String, Object> o = new HashMap<>();
            o.put("name", c.name);
            o.put("number", c.number);
            o.put("relation", c.relation);
            list.add(o);
        }
        m.put("calls", list);
        m.put("light", light);
        m.put("doses", new ArrayList<>(doses));
        m.put("help", help);
        return m;
    }

    /** Whatever comes from Firestore; anything missing or of another type is left empty or off. */
    static Profile fromMap(Map<String, Object> m) {
        Profile p = new Profile();
        if (m == null) return p;
        p.name = text(m.get("name"));
        p.callMe = text(m.get("callMe"));
        p.home = text(m.get("home"));
        p.family = text(m.get("family"));
        p.routine = text(m.get("routine"));
        p.likes = text(m.get("likes"));
        p.sensitive = text(m.get("sensitive"));
        p.free = text(m.get("free"));
        if (m.get("calls") instanceof List) {
            for (Object o : (List<?>) m.get("calls")) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> c = (Map<?, ?>) o;
                Call call = new Call(text(c.get("name")), text(c.get("number")), text(c.get("relation")));
                if (!call.number.isEmpty()) p.calls.add(call);
            }
        }
        p.light = Boolean.TRUE.equals(m.get("light"));
        if (m.get("doses") instanceof List) {
            for (Object o : (List<?>) m.get("doses")) {
                String d = text(o);
                if (!d.isEmpty() && !p.doses.contains(d)) p.doses.add(d);
            }
        }
        p.help = Boolean.TRUE.equals(m.get("help"));
        return p;
    }

    private static String text(Object o) {
        return o instanceof String ? clean((String) o) : "";
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
