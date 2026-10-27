package app.oido;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The language dialog (Settings, the caregiver's menu, the first screen and the patient's options). */
final class LangPicker {

    private static final String[] CHOICES = {Lang.SYSTEM, Lang.ES, Lang.EN};

    private LangPicker() {}

    /** What the button shows: «Idioma: Español» or «Language: English». */
    static String label(Activity a) {
        return a.getString(R.string.language_label, name(a, Lang.choice(a)));
    }

    private static String name(Activity a, String choice) {
        if (Lang.ES.equals(choice)) return "Español";
        if (Lang.EN.equals(choice)) return "English";
        return a.getString(R.string.language_system);
    }

    /**
     * Asks for the language and, if it changes, restarts the screen with it. Listening stops: the speech
     * model and the voice are different for each language, and they are loaded again when listening starts.
     */
    static void show(Activity a) {
        String current = Lang.choice(a);
        String[] labels = new String[CHOICES.length];
        int checked = 0;
        for (int i = 0; i < CHOICES.length; i++) {
            labels[i] = name(a, CHOICES[i]);
            if (CHOICES[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.language)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    String choice = CHOICES[which];
                    if (choice.equals(current)) return;
                    String before = Lang.code(a);
                    Lang.setChoice(a, choice);
                    if (Lang.code(a).equals(before)) return;
                    if (AppState.isActive()) {
                        a.stopService(new Intent(a, ListenerService.class));
                        Toast.makeText(a, Lang.str(a, R.string.language_restart_listening), Toast.LENGTH_LONG).show();
                    }
                    a.recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
