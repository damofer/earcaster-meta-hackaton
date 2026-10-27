package app.oido;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/**
 * A patient seen from the caregiver's phone (spec 015): their settings and doses are read and saved
 * in their own files, as in the editor for their phrases.
 */
final class PatientContext extends ContextWrapper implements Cloud.PatientScope {

    private final String pid;

    PatientContext(Context base, String pid) {
        super(base);
        this.pid = pid;
    }

    @Override
    public String patientId() {
        return pid;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(MainActivity.prefsName(name, pid), mode);
    }
}
