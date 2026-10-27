package app.oido;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text of a spoken response: replaces {hora}, {dia}, {fecha}, {año} and {saludo} (time, day, date, year, greeting) with their value
 * in the app's language (Lang), {visita:Ana|reserva} with Ana's next visit (spec 014)
 * and {toma:Pastilla|extra} with whether today's dose has been logged (spec 007).
 * The English names of the fields ({time}, {day}, {date}, {year}, {greeting}, {visit:…}, {dose:…}) work in both languages.
 */
final class SpokenText {

    /** What the text needs to know from outside: the visit schedule and the logged doses. */
    interface Sources {
        /** name's visit that is in progress or the next one within 30 days; null if there is none or it cannot be read. */
        Visit nextVisit(String name, Calendar now);

        /** Time (milliseconds) of the last dose of name logged today, or 0 if there is none. */
        default long doseToday(String name, Calendar now) {
            return 0;
        }
    }

    /** A visit: from start to end (milliseconds). allDay = no time, all day. */
    static final class Visit {
        final long start;
        final long end;
        final boolean allDay;

        Visit(long start, long end, boolean allDay) {
            this.start = start;
            this.end = end;
            this.allDay = allDay;
        }
    }

    private static final Pattern FIELD = Pattern.compile("\\{([^{}]+)\\}");
    private static final String[] DAYS = {
            "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};
    private static final String[] MONTHS = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String[] DAYS_EN = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] MONTHS_EN = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private static String day(Calendar c) {
        int i = c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        return Lang.en() ? DAYS_EN[i] : DAYS[i];
    }

    /** «19 de octubre» or «October 19». */
    private static String dayMonth(Calendar c) {
        int d = c.get(Calendar.DAY_OF_MONTH);
        int m = c.get(Calendar.MONTH);
        return Lang.en() ? MONTHS_EN[m] + " " + d : d + " de " + MONTHS[m];
    }

    private SpokenText() {}

    /** The text ready to be said at time now. An unknown data field is left as is. */
    static String fill(String template, Calendar now) {
        return fill(template, now, null);
    }

    /** Same, reading the visits from sources (may be null: then the fallback phrase is used). */
    static String fill(String template, Calendar now, Sources sources) {
        if (template == null) return "";
        Matcher m = FIELD.matcher(template);
        // StringBuffer and not StringBuilder: appendReplacement(StringBuilder) only exists since Android 14.
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String value = value(m.group(1), now, sources);
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    /** Without accents, without spaces and lowercase: {Día} and {dia} are the same. */
    private static String key(String raw) {
        String k = raw.trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(k, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String value(String raw, Calendar now, Sources sources) {
        int colon = raw.indexOf(':');
        if (colon >= 0) return valueWithName(key(raw.substring(0, colon)), raw.substring(colon + 1), now, sources);
        switch (key(raw)) {
            case "hora":
            case "time":
                return time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
            case "dia":
            case "day":
                return day(now);
            case "fecha":
            case "date":
                return dayMonth(now);
            case "ano":
            case "year":
                return String.valueOf(now.get(Calendar.YEAR));
            case "saludo":
            case "greeting":
                return greeting(now.get(Calendar.HOUR_OF_DAY));
            default:
                return null;
        }
    }

    /** Named data fields, like {visita:Ana|Ana te llama pronto}: what follows «|» is the fallback phrase. */
    private static String valueWithName(String key, String rest, Calendar now, Sources sources) {
        int bar = rest.indexOf('|');
        String name = (bar < 0 ? rest : rest.substring(0, bar)).trim();
        String fallback = bar < 0 ? "" : rest.substring(bar + 1).trim();
        if (name.isEmpty()) return null;
        if (key.equals("visita") || key.equals("visit")) {
            Visit v = null;
            try {
                if (sources != null) v = sources.nextVisit(name, now);
            } catch (RuntimeException ignored) {
                // If the schedule fails, a date is never made up: the fallback phrase is said.
            }
            return visit(name, v, now, fallback);
        }
        if (key.equals("toma") || key.equals("dose")) {
            long at = 0;
            try {
                if (sources != null) at = sources.doseToday(name, now);
            } catch (RuntimeException ignored) {
                // If the log fails, it says the dose is not logged: a dose is never claimed.
            }
            return dose(at, now, fallback);
        }
        return null;
    }

    /**
     * Whether today's dose has been logged (spec 007). It never says «tómala» (take it) or «no la has tomado» (you haven't taken it):
     * only what was logged. extra is added when there is no entry, for example «Pregúntale a Carlos» (Ask Carlos).
     */
    static String dose(long at, Calendar now, String extra) {
        if (at <= 0) {
            return (Lang.en() ? "It is not logged yet today" : "Hoy todavía no está anotada")
                    + (extra == null || extra.isEmpty() ? "" : ". " + extra);
        }
        Calendar c = (Calendar) now.clone();
        c.setTimeInMillis(at);
        return (Lang.en() ? "Yes, today it was logged " : "Sí, hoy está anotada ") + at(c);
    }

    /** What is said when a dose is logged by voice (spec 008): «Lo anoto: pastilla de la mañana, a las 9 y 5 de la mañana» (Noted: morning pill, at 9:05 am). */
    static String doseNoted(String name, Calendar now) {
        String what = name == null || name.isEmpty() ? (Lang.en() ? "the dose" : "la toma") : name;
        return (Lang.en() ? "Noted: " : "Lo anoto: ") + what + ", " + at(now);
    }

    // --- Help alert (spec 009) ---

    /** «Carlos», «Carlos y Lucía» (Carlos and Lucía). */
    static String names(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + (Lang.en() ? " and " : " y ")
                + names.get(names.size() - 1);
    }

    /** What is said before alerting, to allow 10 seconds to cancel. */
    static String helpQuestion(List<String> contacts) {
        if (Lang.en()) return "Do you need help? I am going to alert " + names(contacts) + ". If you are fine, say: I am fine.";
        return "¿Necesitas ayuda? Voy a avisar a " + names(contacts) + ". Si estás bien, di: estoy bien.";
    }

    /** The SMS: who, what they said and at what time. Nothing else leaves the phone (R6). */
    static String helpMessage(String person, String said, Calendar now) {
        boolean en = Lang.en();
        String who = person == null || person.trim().isEmpty() ? (en ? "Someone" : "Alguien") : person.trim();
        int minute = now.get(Calendar.MINUTE);
        String clock = now.get(Calendar.HOUR_OF_DAY) + ":" + (minute < 10 ? "0" : "") + minute;
        if (en) return "Earcaster: " + who + " said \"" + said + "\" at " + clock + ". Call to check on them.";
        return "Earcaster: " + who + " dijo \"" + said + "\" a las " + clock + ". Llama para saber cómo está.";
    }

    /** Does what was heard cancel the alert? «estoy bien», «cancela», «falsa alarma» (I'm fine, cancel, false alarm); «no» only if it is a clear answer. */
    static boolean cancelsHelp(String heard, boolean finalResult) {
        String h = " " + Texts.forMatch(heard) + " ";
        String[] phrases = Lang.en()
                ? new String[]{"i am fine", "i'm fine", "i am ok", "i'm ok", "i am okay", "i'm okay", "i'm all right",
                        "cancel", "false alarm", "nothing happened", "i don't need", "no need"}
                : new String[]{"estoy bien", "cancela", "cancelar", "falsa alarma", "no pasa nada", "no necesito"};
        for (String phrase : phrases) {
            if (h.contains(" " + phrase + " ")) return true;
        }
        if (Lang.en()) {
            return finalResult && (h.equals(" no ") || h.startsWith(" no no ") || h.startsWith(" no thank"));
        }
        return finalResult && (h.equals(" no ") || h.startsWith(" no no ") || h.startsWith(" no gracias "));
    }

    /** Are they the same calendar day? */
    static boolean sameDay(Calendar a, long millis) {
        Calendar b = (Calendar) a.clone();
        b.setTimeInMillis(millis);
        return daysBetween(a, b) == 0;
    }

    /** How the next visit is said: «Ana viene mañana a las 5 y media de la tarde» (Ana is coming tomorrow at 5:30 pm). */
    static String visit(String name, Visit v, Calendar now, String fallback) {
        if (Lang.en()) return visitEn(name, v, now, fallback);
        if (v == null) return fallback == null || fallback.isEmpty() ? name + " vendrá pronto" : fallback;
        Calendar start = (Calendar) now.clone();
        start.setTimeInMillis(v.start);
        int days = daysBetween(now, start);
        long t = now.getTimeInMillis();
        if (v.allDay) {
            // An all-day event that started yesterday (Ana is staying for the weekend) is already in progress.
            if (days < 0) return name + " está de visita hoy";
            if (days == 0) return name + " viene hoy";
        } else if (v.start <= t && t < v.end) {
            return name + " está de visita hoy";
        }
        String when;
        if (days <= 0) when = "hoy";
        else if (days == 1) when = "mañana";
        else if (days <= 6) when = "el " + DAYS[start.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY];
        else return name + " viene el " + start.get(Calendar.DAY_OF_MONTH) + " de " + MONTHS[start.get(Calendar.MONTH)];
        if (v.allDay) return name + " viene " + when;
        return name + " viene " + when + " " + at(start);
    }

    /** «Ana is coming tomorrow at half past 5 in the afternoon». */
    private static String visitEn(String name, Visit v, Calendar now, String fallback) {
        if (v == null) return fallback == null || fallback.isEmpty() ? name + " will come soon" : fallback;
        Calendar start = (Calendar) now.clone();
        start.setTimeInMillis(v.start);
        int days = daysBetween(now, start);
        long t = now.getTimeInMillis();
        if (v.allDay) {
            if (days < 0) return name + " is visiting today";
            if (days == 0) return name + " is coming today";
        } else if (v.start <= t && t < v.end) {
            return name + " is visiting today";
        }
        String when;
        if (days <= 0) when = "today";
        else if (days == 1) when = "tomorrow";
        else if (days <= 6) when = "on " + day(start);
        else return name + " is coming on " + dayMonth(start);
        if (v.allDay) return name + " is coming " + when;
        return name + " is coming " + when + " " + at(start);
    }

    // --- Current context for the AI (spec 017): always with the weekday and the time in digits ---

    /** «domingo 19 de octubre de 2026, las 10 y cuarto de la mañana» (Sunday October 19, 2026, a quarter past 10 in the morning). */
    static String moment(Calendar now) {
        return date(now) + (Lang.en() ? ", " : " de ") + now.get(Calendar.YEAR) + ", "
                + time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    /**
     * When a visit is: «mañana lunes 20 de octubre a las 5 en punto de la tarde» (tomorrow, Monday October 20 at 5 pm sharp), or «ahora» (now) if it is
     * in progress. null if it has already ended.
     */
    static String when(Visit v, Calendar now) {
        long t = now.getTimeInMillis();
        if (v.end <= t) return null;
        Calendar start = (Calendar) now.clone();
        start.setTimeInMillis(v.start);
        int days = daysBetween(now, start);
        boolean en = Lang.en();
        if (v.allDay ? days <= 0 : v.start <= t) return en ? "now" : "ahora";
        String day = en
                ? (days == 1 ? "tomorrow, " : days == 0 ? "today, " : "on ")
                : (days == 1 ? "mañana " : days == 0 ? "hoy " : "el ");
        String out = day + date(start);
        return v.allDay ? out : out + " " + at(start);
    }

    /** «lunes 20 de octubre» (Monday October 20). */
    private static String date(Calendar c) {
        return day(c) + (Lang.en() ? ", " : " ") + dayMonth(c);
    }

    /** Calendar days between the dates of a and b, regardless of the times or daylight saving changes. */
    private static int daysBetween(Calendar a, Calendar b) {
        return (int) (epochDay(b) - epochDay(a));
    }

    private static long epochDay(Calendar c) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        return u.getTimeInMillis() / 86_400_000L;
    }

    /** Does the name appear as whole words in the text? «Visita de Ána» does contain «ana»; «Mariana» does not. */
    static boolean mentions(String text, String name) {
        List<String> words = Arrays.asList(Texts.forMatch(text).split(" "));
        String[] wanted = Texts.forMatch(name).split(" ");
        if (wanted.length == 0 || wanted[0].isEmpty()) return false;
        for (int i = 0; i + wanted.length <= words.size(); i++) {
            if (words.subList(i, i + wanted.length).equals(Arrays.asList(wanted))) return true;
        }
        return false;
    }

    /** The time as a person would say it: «la 1 y 5 de la tarde», «las 8 y media de la mañana» (1:05 pm, 8:30 am). */
    static String time(int hour, int minute) {
        if (Lang.en()) return timeEn(hour, minute);
        int h = hour % 12 == 0 ? 12 : hour % 12;
        StringBuilder s = new StringBuilder(h == 1 ? "la 1" : "las " + h);
        if (minute == 0) s.append(" en punto");
        else if (minute == 15) s.append(" y cuarto");
        else if (minute == 30) s.append(" y media");
        else s.append(" y ").append(minute);
        s.append(' ').append(period(hour));
        return s.toString();
    }

    /** «a las 5 y media de la tarde» or «at half past 5 in the afternoon». */
    static String at(Calendar c) {
        return (Lang.en() ? "at " : "a ") + time(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** «1 o'clock in the afternoon», «a quarter past 3», «half past 8», «5:10». */
    private static String timeEn(int hour, int minute) {
        int h = hour % 12 == 0 ? 12 : hour % 12;
        String s;
        if (minute == 0) s = h + " o'clock";
        else if (minute == 15) s = "a quarter past " + h;
        else if (minute == 30) s = "half past " + h;
        else s = h + ":" + (minute < 10 ? "0" : "") + minute;
        return s + " " + periodEn(hour);
    }

    private static String periodEn(int hour) {
        if (hour == 12) return "noon";
        if (hour >= 1 && hour < 6) return "in the early morning";
        if (hour >= 6 && hour < 12) return "in the morning";
        if (hour > 12 && hour < 18) return "in the afternoon";
        if (hour >= 18 && hour < 21) return "in the evening";
        return "at night";
    }

    private static String period(int hour) {
        if (hour == 0 || hour >= 20) return "de la noche";
        if (hour < 6) return "de la madrugada";
        if (hour < 12) return "de la mañana";
        if (hour == 12) return "del mediodía";
        return "de la tarde";
    }

    private static String greeting(int hour) {
        if (Lang.en()) {
            if (hour >= 6 && hour < 12) return "Good morning";
            if (hour >= 12 && hour < 18) return "Good afternoon";
            return "Good evening";
        }
        if (hour >= 6 && hour < 12) return "Buenos días";
        if (hour >= 12 && hour < 20) return "Buenas tardes";
        return "Buenas noches";
    }
}
