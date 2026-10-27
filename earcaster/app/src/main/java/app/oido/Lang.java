package app.oido;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * The app's language: Spanish or English. By default it follows the phone (English only if the phone is in English,
 * Spanish otherwise, like the resources), and it can be fixed in Settings. It is a setting of this phone:
 * it does not travel with a patient's settings.
 */
final class Lang {

    static final String SYSTEM = "";
    static final String ES = "es";
    static final String EN = "en";

    /**
     * The current language for the code that has no Context (SpokenText, Weekly, Ago…).
     * Spanish until App sets it, which is also what the unit tests use.
     */
    private static volatile boolean english = false;

    private Lang() {}

    static boolean en() {
        return english;
    }

    /** For the unit tests and for App: switches the language of the code without Context. */
    static void setEnglish(boolean on) {
        english = on;
    }

    private static android.content.SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("dispositivo", Context.MODE_PRIVATE);
    }

    /** What was chosen in Settings: SYSTEM, ES or EN. */
    static String choice(Context c) {
        String v = sp(c).getString("lang", SYSTEM);
        return EN.equals(v) || ES.equals(v) ? v : SYSTEM;
    }

    static void setChoice(Context c, String choice) {
        sp(c).edit().putString("lang", choice).commit();
        refresh(c);
    }

    /** The language in use: ES or EN. */
    static String code(Context c) {
        String choice = choice(c);
        if (!choice.isEmpty()) return choice;
        return EN.equals(systemLocale().getLanguage()) ? EN : ES;
    }

    static Locale locale(Context c) {
        return EN.equals(code(c)) ? Locale.forLanguageTag("en") : Locale.forLanguageTag("es");
    }

    /** Updates the flag of the code without Context. Called at startup and when the language changes. */
    static void refresh(Context c) {
        english = EN.equals(code(c));
    }

    private static Locale systemLocale() {
        Configuration conf = Resources.getSystem().getConfiguration();
        return conf.getLocales().isEmpty() ? Locale.getDefault() : conf.getLocales().get(0);
    }

    /** A text in the app's language from any Context (receivers and the application Context do not have it). */
    static String str(Context c, int id, Object... args) {
        return wrap(c).getString(id, args);
    }

    /**
     * The Context with the chosen language, for attachBaseContext of activities and services.
     * Following the phone, it is returned as is.
     */
    static Context wrap(Context base) {
        String choice = choice(base);
        if (choice.isEmpty()) return base;
        Locale l = Locale.forLanguageTag(choice);
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        if (l.getLanguage().equals(conf.getLocales().isEmpty() ? "" : conf.getLocales().get(0).getLanguage())) {
            return base;
        }
        conf.setLocale(l);
        return base.createConfigurationContext(conf);
    }
}
