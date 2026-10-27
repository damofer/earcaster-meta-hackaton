package app.oido;

/** «hace 5 minutos», «hace 2 horas» (5 minutes ago, 2 hours ago): how long since something, for the caregiver's list. */
final class Ago {

    private Ago() {}

    static String text(long thenMs, long nowMs) {
        long min = Math.max(0, nowMs - thenMs) / 60_000;
        if (Lang.en()) {
            if (min < 1) return "a moment ago";
            if (min < 60) return min == 1 ? "1 minute ago" : min + " minutes ago";
            long h = min / 60;
            if (h < 24) return h == 1 ? "1 hour ago" : h + " hours ago";
            long d = h / 24;
            return d == 1 ? "1 day ago" : d + " days ago";
        }
        if (min < 1) return "hace un momento";
        if (min < 60) return min == 1 ? "hace 1 minuto" : "hace " + min + " minutos";
        long h = min / 60;
        if (h < 24) return h == 1 ? "hace 1 hora" : "hace " + h + " horas";
        long d = h / 24;
        return d == 1 ? "hace 1 día" : "hace " + d + " días";
    }
}
