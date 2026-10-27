package app.oido;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Family visits (spec 014): those in the phone's calendar and the recurring visits entered in the app.
 * It is queried at answer time and keeps no copy; the calendar is read-only.
 */
final class Agenda implements SpokenText.Sources {

    private static final long DAY_MS = 86_400_000L;
    /** How far ahead to look in the calendar. */
    private static final long LOOKAHEAD_MS = 30 * DAY_MS;
    /** A recurring visit counts as "currently visiting" for two hours from its start. */
    static final long FIXED_LENGTH_MS = 2 * 3_600_000L;
    /** The AI (spec 017) receives the visits for the coming week, at most this many. */
    private static final long UPCOMING_MS = 7 * DAY_MS;
    private static final int MAX_UPCOMING = 10;

    /** A visit that repeats every week: «Ana · los domingos · 13:00» (Ana · Sundays · 13:00). */
    static final class Fixed {
        String name;
        int days;
        int minute;

        Fixed(String name, int days, int minute) {
            this.name = name;
            this.days = days;
            this.minute = minute;
        }

        static String toJson(List<Fixed> list) {
            JSONArray arr = new JSONArray();
            try {
                for (Fixed f : list) {
                    arr.put(new JSONObject().put("name", f.name).put("days", f.days).put("minute", f.minute));
                }
            } catch (JSONException ignored) {
            }
            return arr.toString();
        }

        static List<Fixed> fromJson(String json) {
            List<Fixed> out = new ArrayList<>();
            if (json == null) return out;
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.add(new Fixed(o.optString("name", ""), o.optInt("days", 0), o.optInt("minute", 0)));
                }
            } catch (JSONException ignored) {
            }
            return out;
        }
    }

    private final Context ctx;

    /** ctx is used when querying, not here: an activity creates this before it is ready. */
    Agenda(Context ctx) {
        this.ctx = ctx;
    }

    static boolean canReadCalendar(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public SpokenText.Visit nextVisit(String name, Calendar now) {
        SpokenText.Visit best = null;
        for (Fixed f : Prefs.fixedVisits(ctx)) {
            if (!SpokenText.mentions(f.name, name)) continue;
            long start = Weekly.next(f.days, f.minute, FIXED_LENGTH_MS, now);
            if (start >= 0) best = sooner(best, new SpokenText.Visit(start, start + FIXED_LENGTH_MS, false));
        }
        if (Prefs.calendarOn(ctx) && canReadCalendar(ctx)) {
            best = sooner(best, fromCalendar(name, now));
        }
        return best;
    }

    private static SpokenText.Visit sooner(SpokenText.Visit a, SpokenText.Visit b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.start < a.start ? b : a;
    }

    /** The first calendar visit whose title names the person and that has not ended yet. */
    private SpokenText.Visit fromCalendar(String name, Calendar now) {
        SpokenText.Visit best = null;
        for (Event e : calendarEvents(now, LOOKAHEAD_MS)) {
            if (SpokenText.mentions(e.title, name)) best = sooner(best, e.visit);
        }
        return best;
    }

    /** A calendar event with its title. */
    private static final class Event {
        final String title;
        final SpokenText.Visit visit;

        Event(String title, SpokenText.Visit visit) {
            this.title = title;
            this.visit = visit;
        }
    }

    /** Events from the chosen calendars that have not ended yet, from now until aheadMs. */
    private List<Event> calendarEvents(Calendar now, long aheadMs) {
        List<Event> out = new ArrayList<>();
        long t = now.getTimeInMillis();
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        // One day earlier: all-day events are stored in UTC and may start "yesterday".
        ContentUris.appendId(uri, t - DAY_MS);
        ContentUris.appendId(uri, t + aheadMs);
        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY};
        StringBuilder where = new StringBuilder(CalendarContract.Instances.VISIBLE + " = 1");
        Set<String> chosen = Prefs.calendarIds(ctx);
        List<String> args = new ArrayList<>();
        if (!chosen.isEmpty()) {
            where.append(" AND ").append(CalendarContract.Instances.CALENDAR_ID).append(" IN (");
            int i = 0;
            for (String id : chosen) {
                where.append(i++ == 0 ? "?" : ",?");
                args.add(id);
            }
            where.append(')');
        }
        try (Cursor c = ctx.getContentResolver().query(uri.build(), projection, where.toString(),
                args.toArray(new String[0]), CalendarContract.Instances.BEGIN + " ASC")) {
            if (c == null) return out;
            TimeZone local = now.getTimeZone();
            while (c.moveToNext()) {
                String title = c.getString(0);
                if (title == null) continue;
                boolean allDay = c.getInt(3) != 0;
                long begin = allDay ? localMidnight(c.getLong(1), local) : c.getLong(1);
                long end = allDay ? localMidnight(c.getLong(2), local) : c.getLong(2);
                if (end <= t) continue;
                out.add(new Event(title, new SpokenText.Visit(begin, Math.max(end, begin), allDay)));
            }
        } catch (RuntimeException e) {
            // No permission or the calendar was deleted: no known visit (R9).
            out.clear();
        }
        return out;
    }

    /**
     * The visits for the next 7 days for the AI (spec 017), soonest first:
     * «Ana: mañana lunes 20 de octubre a las 5 en punto de la tarde» (Ana: tomorrow, Monday October 20 at 5 pm sharp). withCalendar = false on the
     * caregiver's phone, whose calendar is not the person's.
     */
    List<String> upcoming(Calendar now, boolean withCalendar) {
        List<Event> all = new ArrayList<>();
        for (Fixed f : Prefs.fixedVisits(ctx)) {
            if (f.name.trim().isEmpty()) continue;
            long start = Weekly.next(f.days, f.minute, FIXED_LENGTH_MS, now);
            if (start >= 0 && start - now.getTimeInMillis() <= UPCOMING_MS) {
                all.add(new Event(f.name.trim(), new SpokenText.Visit(start, start + FIXED_LENGTH_MS, false)));
            }
        }
        if (withCalendar && Prefs.calendarOn(ctx) && canReadCalendar(ctx)) {
            all.addAll(calendarEvents(now, UPCOMING_MS));
        }
        all.sort((a, b) -> Long.compare(a.visit.start, b.visit.start));
        List<String> out = new ArrayList<>();
        for (Event e : all) {
            String when = SpokenText.when(e.visit, now);
            if (when != null && out.size() < MAX_UPCOMING) out.add(e.title.trim() + ": " + when);
        }
        return out;
    }

    /** All-day events arrive at UTC midnight: they are moved to that day's local midnight here. */
    private static long localMidnight(long utcMillis, TimeZone local) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar day = Calendar.getInstance(local);
        day.clear();
        day.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return day.getTimeInMillis();
    }

    /** The phone's visible calendars: {id, display name}. Empty if there is no permission. */
    static List<String[]> calendars(Context ctx) {
        List<String[]> out = new ArrayList<>();
        if (!canReadCalendar(ctx)) return out;
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME};
        try (Cursor c = ctx.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection,
                CalendarContract.Calendars.VISIBLE + " = 1", null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(1);
                String account = c.getString(2);
                if (name == null) name = Lang.str(ctx, R.string.calendar);
                if (account != null && !account.equals(name)) name += " · " + account;
                out.add(new String[]{String.valueOf(c.getLong(0)), name});
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }
}
