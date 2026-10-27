package app.oido;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * On the patient's phone (spec 015): copies what the caregiver configures into the local settings,
 * downloads the recordings and merges the doses from both phones. Used by the patient screen
 * and the listening service; while either of them is active, it stays connected.
 */
final class PatientSync {

    private static Context app;
    private static int users = 0;
    private static ListenerRegistration patientReg;
    private static ListenerRegistration soundsReg;
    private static ListenerRegistration dosesReg;

    private PatientSync() {}

    static synchronized void start(Context c) {
        users++;
        if (patientReg != null) return;
        app = c.getApplicationContext();
        String pid = Prefs.linkedPatient(app);
        if (pid == null || !Cloud.available(app)) return;
        patientReg = Cloud.listenPatient(pid, (config, error) -> apply(config), () -> lost(pid));
        soundsReg = Cloud.listenSounds(app, pid);
        dosesReg = Cloud.listenDoses(app, (list, error) -> {
            if (list != null) DoseLog.merge(app, list);
        });
    }

    static synchronized void stop() {
        if (users > 0) users--;
        if (users > 0) return;
        remove();
    }

    private static void remove() {
        if (patientReg != null) patientReg.remove();
        if (soundsReg != null) soundsReg.remove();
        if (dosesReg != null) dosesReg.remove();
        patientReg = soundsReg = dosesReg = null;
    }

    /** New settings arrived: if they changed, they are saved and the phrases and reminders are reloaded. */
    private static void apply(String config) {
        if (config == null || config.isEmpty() || config.equals(Prefs.syncedConfig(app))) return;
        Prefs.importConfig(app, localSounds(app, config));
        Prefs.setSyncedConfig(app, config);
        Reminders.schedule(app);
        ListenerService.reload();
        AppState.notifyChanged();
    }

    /** The phrases' recordings point to this phone's recordings folder. */
    static String localSounds(Context c, String config) {
        try {
            JSONObject o = new JSONObject(config);
            if (!o.has("triggers")) return config;
            List<Trigger> list = Trigger.fromJson(o.getString("triggers"));
            for (Trigger t : list) {
                String name = Cloud.recordingName(t.soundUri);
                if (name != null) t.soundUri = Uri.fromFile(new File(Recorder.dir(c), name));
            }
            o.put("triggers", Trigger.toJson(list));
            return o.toString();
        } catch (JSONException e) {
            return config;
        }
    }

    /** Permission to read the patient was lost: if it really was unlinked, everything is deleted (R8). */
    private static void lost(String pid) {
        Cloud.stillLinked(pid, (linked, error) -> {
            if (!Boolean.FALSE.equals(linked)) return;
            forget(app);
        });
    }

    /** This phone stops being the patient's: no phrases, no doses, no recordings, and it returns to the initial question. */
    static void forget(Context c) {
        Context a = c.getApplicationContext();
        synchronized (PatientSync.class) {
            remove();
            users = 0;
        }
        a.stopService(new Intent(a, ListenerService.class));
        Prefs.clearConfig(a);
        DoseLog.clear(a);
        File[] files = Recorder.dir(a).listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        Prefs.forgetDevice(a);
        Reminders.schedule(a);
        AppState.notifyChanged();
    }
}
