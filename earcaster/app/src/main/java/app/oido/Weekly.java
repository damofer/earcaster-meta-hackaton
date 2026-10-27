package app.oido;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Something that repeats every week at the same time: recurring visits (spec 014) and reminders (spec 006). */
final class Weekly {

    /** One bit per day: bit 0 = Sunday … bit 6 = Saturday, like Calendar.DAY_OF_WEEK. */
    static final int EVERY_DAY = 0x7F;
    static final int WEEKDAYS = 0x3E;
    static final int WEEKEND = 0x41;

    /** Monday first, the way the week is read in Spanish. */
    static final int[] MONDAY_FIRST = {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
    private static final String[] PLURAL = {
            "domingos", "lunes", "martes", "miércoles", "jueves", "viernes", "sábados"};
    private static final String[] PLURAL_EN = {
            "Sundays", "Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays"};

    private Weekly() {}

    static int bit(int dayOfWeek) {
        return 1 << (dayOfWeek - Calendar.SUNDAY);
    }

    /**
     * Start of the due occurrence: the one in progress if it started less than lengthMs ago,
     * or the next one. With lengthMs = 0, always a time strictly after now. -1 if there are no days.
     */
    static long next(int days, int minuteOfDay, long lengthMs, Calendar now) {
        if ((days & EVERY_DAY) == 0) return -1;
        Calendar c = (Calendar) now.clone();
        for (int i = 0; i < 8; i++) {
            c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
            c.set(Calendar.MINUTE, minuteOfDay % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if ((days & bit(c.get(Calendar.DAY_OF_WEEK))) != 0
                    && c.getTimeInMillis() + lengthMs > now.getTimeInMillis()) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    /** «todos los días», «de lunes a viernes», «los domingos», «los lunes, miércoles y viernes» (every day, Monday to Friday, Sundays, Mondays, Wednesdays and Fridays). */
    static String describe(int days) {
        days &= EVERY_DAY;
        if (Lang.en()) return describeEn(days);
        if (days == EVERY_DAY) return "todos los días";
        if (days == WEEKDAYS) return "de lunes a viernes";
        if (days == WEEKEND) return "los fines de semana";
        List<String> names = new ArrayList<>();
        for (int d : MONDAY_FIRST) {
            if ((days & bit(d)) != 0) names.add(PLURAL[d - Calendar.SUNDAY]);
        }
        if (names.isEmpty()) return "ningún día";
        if (names.size() == 1) return "los " + names.get(0);
        return "los " + String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
    }

    /** «every day», «Monday to Friday», «on Mondays, Wednesdays and Fridays». */
    private static String describeEn(int days) {
        if (days == EVERY_DAY) return "every day";
        if (days == WEEKDAYS) return "Monday to Friday";
        if (days == WEEKEND) return "on weekends";
        List<String> names = new ArrayList<>();
        for (int d : MONDAY_FIRST) {
            if ((days & bit(d)) != 0) names.add(PLURAL_EN[d - Calendar.SUNDAY]);
        }
        if (names.isEmpty()) return "no day";
        if (names.size() == 1) return "on " + names.get(0);
        return "on " + String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** The time in 24-hour clock format, for the caregiver's screens: «8:05», «13:00». */
    static String clock(int minuteOfDay) {
        return (minuteOfDay / 60) + ":" + (minuteOfDay % 60 < 10 ? "0" : "") + (minuteOfDay % 60);
    }
}
