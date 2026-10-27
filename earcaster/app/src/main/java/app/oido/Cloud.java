package app.oido;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything that touches Firebase (spec 015): the caregiver's account, their patients, linking the
 * person's phone and what travels between the two. Without google-services.json, available() is false and the app
 * works as always, on a single phone.
 */
final class Cloud {

    /** End of an operation: error is null on success, or a sentence to show. */
    interface Done {
        void done(String error);
    }

    interface Result<T> {
        void done(T value, String error);
    }

    /** A screen that works on a specific patient: the caregiver's editor. */
    interface PatientScope {
        String patientId();
    }

    /** A patient as their caregiver sees them in the list. */
    static final class Patient {
        final String id;
        final String name;
        /** Last check-in from the linked phone (milliseconds), or 0 if there never was one. */
        final long seenAt;
        final boolean listening;
        /** Last help alert: what was said, at what time and whether the SMS went out. */
        final String helpSaid;
        final long helpAt;
        final boolean helpSent;
        /** When the AI reached the daily maximum of replies (spec 017, R11), or 0. */
        final long aiLimitAt;

        Patient(DocumentSnapshot d) {
            id = d.getId();
            name = text(d.getString("name"));
            seenAt = millis(d.getTimestamp("seenAt"));
            listening = Boolean.TRUE.equals(d.getBoolean("listening"));
            aiLimitAt = millis(d.getTimestamp("aiLimitAt"));
            Object help = d.get("lastHelp");
            if (help instanceof Map) {
                Map<?, ?> h = (Map<?, ?>) help;
                helpSaid = h.get("said") instanceof String ? (String) h.get("said") : "";
                helpAt = h.get("at") instanceof Timestamp ? millis((Timestamp) h.get("at")) : 0;
                helpSent = Boolean.TRUE.equals(h.get("sent"));
            } else {
                helpSaid = "";
                helpAt = 0;
                helpSent = false;
            }
        }
    }

    /** A recording larger than this does not fit in a Firestore document (1 MB): it is not uploaded. */
    static final long MAX_SOUND_BYTES = 900_000;
    static final long LINK_VALID_MS = 10 * 60_000L;

    private Cloud() {}

    static boolean available(Context c) {
        try {
            return !FirebaseApp.getApps(c).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static FirebaseAuth auth() {
        FirebaseAuth a = FirebaseAuth.getInstance();
        a.setLanguageCode("es");
        return a;
    }

    private static DocumentReference patient(String pid) {
        return db().collection("patients").document(pid);
    }

    // --- Caregiver account ---

    /** The signed-in caregiver, or null. The patient's phone signs in anonymously and does not count. */
    static FirebaseUser caregiver(Context c) {
        if (!available(c)) return null;
        FirebaseUser u = auth().getCurrentUser();
        return u != null && !u.isAnonymous() ? u : null;
    }

    static void signIn(String email, String password, Done done) {
        auth().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> done.done(null))
                .addOnFailureListener(e -> done.done(message(e)));
    }

    static void createAccount(String email, String password, Done done) {
        auth().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> {
                    Map<String, Object> me = new HashMap<>();
                    me.put("email", email);
                    me.put("createdAt", FieldValue.serverTimestamp());
                    db().collection("caregivers").document(r.getUser().getUid()).set(me);
                    done.done(null);
                })
                .addOnFailureListener(e -> done.done(message(e)));
    }

    static void resetPassword(String email, Done done) {
        auth().sendPasswordResetEmail(email)
                .addOnSuccessListener(r -> done.done(null))
                .addOnFailureListener(e -> done.done(message(e)));
    }

    static void signOut() {
        auth().signOut();
    }

    /** Deletes the patients, their data and the account (spec 015, R9). */
    static void deleteAccount(Context c, Done done) {
        FirebaseUser u = caregiver(c);
        if (u == null) {
            done.done(App.str(R.string.cloud_no_session));
            return;
        }
        db().collection("patients").whereEqualTo("caregiverUid", u.getUid()).get()
                .addOnSuccessListener(q -> {
                    List<String> ids = new ArrayList<>();
                    for (DocumentSnapshot d : q.getDocuments()) ids.add(d.getId());
                    deletePatients(ids, 0, error -> {
                        if (error != null) {
                            done.done(error);
                            return;
                        }
                        db().collection("caregivers").document(u.getUid()).delete();
                        u.delete()
                                .addOnSuccessListener(r -> done.done(null))
                                .addOnFailureListener(e -> done.done(message(e)));
                    });
                })
                .addOnFailureListener(e -> done.done(message(e)));
    }

    private static void deletePatients(List<String> ids, int i, Done done) {
        if (i >= ids.size()) {
            done.done(null);
            return;
        }
        deletePatient(ids.get(i), error -> {
            if (error != null) done.done(error);
            else deletePatients(ids, i + 1, done);
        });
    }

    // --- Patients, from the caregiver's phone ---

    static ListenerRegistration listenPatients(Context c, Result<List<Patient>> cb) {
        FirebaseUser u = caregiver(c);
        if (u == null) return null;
        return db().collection("patients").whereEqualTo("caregiverUid", u.getUid())
                .addSnapshotListener((q, e) -> {
                    if (e != null || q == null) {
                        cb.done(null, message(e));
                        return;
                    }
                    List<Patient> list = new ArrayList<>();
                    for (DocumentSnapshot d : q.getDocuments()) list.add(new Patient(d));
                    list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    cb.done(list, null);
                });
    }

    static void addPatient(Context c, String name, Result<String> cb) {
        FirebaseUser u = caregiver(c);
        if (u == null) {
            cb.done(null, App.str(R.string.cloud_no_session));
            return;
        }
        DocumentReference ref = db().collection("patients").document();
        Map<String, Object> p = new HashMap<>();
        p.put("caregiverUid", u.getUid());
        p.put("name", name);
        p.put("config", "");
        p.put("createdAt", FieldValue.serverTimestamp());
        // With Firestore's local cache, the patient shows up even when offline.
        ref.set(p);
        cb.done(ref.getId(), null);
    }

    static void renamePatient(String pid, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        patient(pid).update(m);
    }

    /** Deletes the patient with their recordings, doses, alerts and linked phones. */
    static void deletePatient(String pid, Done done) {
        String[] subs = {"sounds", "doses", "events", "devices", "private", "talks", "usage"};
        deleteCollections(pid, subs, 0, error -> {
            if (error != null) {
                done.done(error);
                return;
            }
            patient(pid).delete()
                    .addOnSuccessListener(r -> done.done(null))
                    .addOnFailureListener(e -> done.done(message(e)));
        });
    }

    private static void deleteCollections(String pid, String[] subs, int i, Done done) {
        if (i >= subs.length) {
            done.done(null);
            return;
        }
        deleteAll(patient(pid).collection(subs[i]), error -> {
            if (error != null) done.done(error);
            else deleteCollections(pid, subs, i + 1, done);
        });
    }

    private static void deleteAll(CollectionReference col, Done done) {
        col.get().addOnSuccessListener(q -> {
            for (DocumentSnapshot d : q.getDocuments()) d.getReference().delete();
            done.done(null);
        }).addOnFailureListener(e -> done.done(message(e)));
    }

    /** Creates a linking code, valid for 10 minutes (spec 015, R4). */
    static void createLink(Context c, String pid, Result<String> cb) {
        FirebaseUser u = caregiver(c);
        if (u == null) {
            cb.done(null, App.str(R.string.cloud_no_session));
            return;
        }
        String code = LinkCode.generate(new SecureRandom());
        Map<String, Object> link = new HashMap<>();
        link.put("pid", pid);
        link.put("caregiverUid", u.getUid());
        link.put("expiresAt", new Timestamp(new Date(System.currentTimeMillis() + LINK_VALID_MS)));
        db().collection("links").document(code).set(link)
                .addOnSuccessListener(r -> cb.done(code, null))
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    /** Notifies when a phone links with this code. */
    static ListenerRegistration listenLinked(String pid, String code, Runnable onLinked) {
        return patient(pid).collection("devices").whereEqualTo("code", code).addSnapshotListener((q, e) -> {
            if (q != null && !q.getDocuments().isEmpty()) onLinked.run();
        });
    }

    /** The code has been used: it is deleted so nobody else can use it. */
    static void deleteLink(String code) {
        db().collection("links").document(code).delete();
    }

    /** Unlinks all of the patient's phones: when they notice, each one deletes its copy (R8). */
    static void unlinkDevices(String pid, Done done) {
        deleteAll(patient(pid).collection("devices"), error -> {
            if (error == null) {
                // Firestore re-checks the listener's permissions when the document changes:
                // that way the person's phone finds out right away that it can no longer read it.
                Map<String, Object> m = new HashMap<>();
                m.put("unlinkedAt", FieldValue.serverTimestamp());
                m.put("listening", false);
                patient(pid).update(m);
            }
            done.done(error);
        });
    }

    // --- Settings ---

    static void fetchConfig(String pid, Result<String> cb) {
        patient(pid).get()
                .addOnSuccessListener(d -> cb.done(d.exists() ? text(d.getString("config")) : "", null))
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    static void pushConfig(String pid, String config) {
        Map<String, Object> m = new HashMap<>();
        m.put("config", config);
        m.put("configAt", FieldValue.serverTimestamp());
        patient(pid).update(m);
    }

    /**
     * Changes only these settings keys (the AI settings, spec 017), without overwriting the rest.
     * Needs internet: it is a transaction.
     */
    static void updateConfig(String pid, Map<String, Object> changes, Done done) {
        DocumentReference ref = patient(pid);
        db().runTransaction(tx -> {
            DocumentSnapshot d = tx.get(ref);
            String config = text(d.getString("config"));
            JSONObject o;
            try {
                o = new JSONObject(config.isEmpty() ? "{}" : config);
                for (Map.Entry<String, Object> e : changes.entrySet()) o.put(e.getKey(), e.getValue());
            } catch (JSONException e) {
                o = new JSONObject(changes);
            }
            Map<String, Object> m = new HashMap<>();
            m.put("config", o.toString());
            m.put("configAt", FieldValue.serverTimestamp());
            tx.update(ref, m);
            return null;
        }).addOnSuccessListener(r -> done.done(null)).addOnFailureListener(e -> done.done(message(e)));
    }

    // --- AI conversation (spec 017) ---

    /** Has the caregiver already accepted what data leaves the phone (R13)? */
    static void fetchAiConsent(String pid, Result<Boolean> cb) {
        patient(pid).get()
                .addOnSuccessListener(d -> cb.done(d.getTimestamp("aiConsentAt") != null, null))
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    static void setAiConsent(String pid, Done done) {
        Map<String, Object> m = new HashMap<>();
        m.put("aiConsentAt", FieldValue.serverTimestamp());
        patient(pid).update(m)
                .addOnSuccessListener(r -> done.done(null))
                .addOnFailureListener(e -> done.done(message(e)));
    }

    /** A question to the AI and what it answered, for the caregiver (R10). */
    static final class Talk {
        final String heard;
        final String answer;
        final String action;
        final long at;

        Talk(DocumentSnapshot d) {
            heard = text(d.getString("heard"));
            answer = text(d.getString("answer"));
            action = text(d.getString("action"));
            at = millis(d.getTimestamp("at"));
        }
    }

    /** The conversations from the last 7 days, most recent first. They are deleted automatically after 7 days. */
    static ListenerRegistration listenTalks(String pid, Result<List<Talk>> cb) {
        return patient(pid).collection("talks").orderBy("at", Query.Direction.DESCENDING).limit(200)
                .addSnapshotListener((q, e) -> {
                    if (q == null) {
                        cb.done(null, message(e));
                        return;
                    }
                    List<Talk> list = new ArrayList<>();
                    for (DocumentSnapshot d : q.getDocuments()) list.add(new Talk(d));
                    cb.done(list, null);
                });
    }

    static void deleteTalks(String pid, Done done) {
        deleteAll(patient(pid).collection("talks"), done);
    }

    /** How many replies the AI gave that day (R11). */
    static void fetchUsage(String pid, String day, Result<Long> cb) {
        patient(pid).collection("usage").document(day).get()
                .addOnSuccessListener(d -> {
                    Long n = d.exists() ? d.getLong("count") : null;
                    cb.done(n == null ? 0L : n, null);
                })
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    // --- Patient profile for the AI (spec 016): only the caregiver reads it ---

    private static DocumentReference profile(String pid) {
        return patient(pid).collection("private").document("profile");
    }

    static void fetchProfile(String pid, Result<Profile> cb) {
        profile(pid).get()
                .addOnSuccessListener(d -> cb.done(Profile.fromMap(d.exists() ? d.getData() : null), null))
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    /** With Firestore's local cache it is saved even offline, and uploaded when back online. */
    static void saveProfile(String pid, Profile p) {
        Map<String, Object> m = p.toMap();
        m.put("updatedAt", FieldValue.serverTimestamp());
        profile(pid).set(m);
    }

    static void deleteProfile(String pid) {
        profile(pid).delete();
    }

    // --- Recordings ---

    /** File name if uri is an app recording ("grabaciones/…"), or null if it is a ringtone or another file. */
    static String recordingName(Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme()) || uri.getPath() == null) return null;
        File f = new File(uri.getPath());
        File parent = f.getParentFile();
        return parent != null && "grabaciones".equals(parent.getName()) ? f.getName() : null;
    }

    private static final Map<String, Set<String>> UPLOADED = new HashMap<>();

    /**
     * Uploads the recordings of these phrases that are not in the cloud yet. onTooBig receives the name
     * of the one that does not fit; may be null.
     */
    static void uploadSounds(Context c, String pid, List<Trigger> triggers, Result<String> onTooBig) {
        List<String> names = new ArrayList<>();
        for (Trigger t : triggers) {
            String name = recordingName(t.soundUri);
            if (name != null && !names.contains(name)) names.add(name);
        }
        if (names.isEmpty()) return;
        Set<String> known = UPLOADED.get(pid);
        if (known == null) {
            patient(pid).collection("sounds").get().addOnSuccessListener(q -> {
                Set<String> have = new HashSet<>();
                for (DocumentSnapshot d : q.getDocuments()) have.add(d.getId());
                UPLOADED.put(pid, have);
                uploadMissing(c, pid, names, have, onTooBig);
            });
        } else {
            uploadMissing(c, pid, names, known, onTooBig);
        }
    }

    private static void uploadMissing(Context c, String pid, List<String> names, Set<String> have,
                                      Result<String> onTooBig) {
        for (String name : names) {
            if (have.contains(name)) continue;
            File f = new File(Recorder.dir(c), name);
            if (!f.isFile()) continue;
            if (f.length() > MAX_SOUND_BYTES) {
                if (onTooBig != null) onTooBig.done(name, null);
                continue;
            }
            try {
                Map<String, Object> s = new HashMap<>();
                s.put("data", Blob.fromBytes(readAll(f)));
                s.put("size", f.length());
                patient(pid).collection("sounds").document(name).set(s);
                have.add(name);
            } catch (IOException ignored) {
            }
        }
    }

    /** On the patient's phone: downloads the recordings that are missing or have changed. */
    static ListenerRegistration listenSounds(Context c, String pid) {
        Context app = c.getApplicationContext();
        return patient(pid).collection("sounds").addSnapshotListener((q, e) -> {
            if (q == null) return;
            for (DocumentSnapshot d : q.getDocuments()) {
                Blob data = d.getBlob("data");
                if (data == null) continue;
                File f = new File(Recorder.dir(app), d.getId());
                byte[] bytes = data.toBytes();
                if (f.isFile() && f.length() == bytes.length) continue;
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return b;
    }

    // --- The patient's phone ---

    /** Links this phone with the caregiver's code. Returns the patient id. */
    static void link(Context c, String code, Result<String> cb) {
        FirebaseUser u = auth().getCurrentUser();
        if (u == null || !u.isAnonymous()) {
            if (u != null) auth().signOut();
            auth().signInAnonymously()
                    .addOnSuccessListener(r -> link(c, code, cb))
                    .addOnFailureListener(e -> cb.done(null, message(e)));
            return;
        }
        db().collection("links").document(code).get()
                .addOnSuccessListener(d -> {
                    String pid = d.exists() ? d.getString("pid") : null;
                    if (pid == null) {
                        cb.done(null, App.str(R.string.cloud_bad_code));
                        return;
                    }
                    Map<String, Object> dev = new HashMap<>();
                    dev.put("code", code);
                    dev.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                    dev.put("linkedAt", FieldValue.serverTimestamp());
                    patient(pid).collection("devices").document(u.getUid()).set(dev)
                            .addOnSuccessListener(r -> {
                                deleteLink(code);
                                cb.done(pid, null);
                            })
                            .addOnFailureListener(e -> cb.done(null, message(e)));
                })
                .addOnFailureListener(e -> cb.done(null, e instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ? App.str(R.string.cloud_bad_code)
                        : message(e)));
    }

    /** The patient's settings every time they change. onLost is called if permission to read them is lost. */
    static ListenerRegistration listenPatient(String pid, Result<String> onConfig, Runnable onLost) {
        return patient(pid).addSnapshotListener((d, e) -> {
            if (e != null) {
                if (e.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) onLost.run();
                return;
            }
            if (d == null) return;
            if (!d.exists()) {
                onLost.run();
                return;
            }
            onConfig.done(text(d.getString("config")), null);
        });
    }

    /** Is this phone still linked? Checked before deleting anything, in case the error was temporary. */
    static void stillLinked(String pid, Result<Boolean> cb) {
        FirebaseUser u = auth().getCurrentUser();
        if (u == null) {
            cb.done(false, null);
            return;
        }
        patient(pid).collection("devices").document(u.getUid()).get()
                .addOnSuccessListener(d -> cb.done(d.exists(), null))
                .addOnFailureListener(e -> cb.done(null, message(e)));
    }

    /** This phone unlinks itself (from its own screen, with the PIN). */
    static void unlinkSelf(String pid) {
        FirebaseUser u = auth().getCurrentUser();
        if (u != null) patient(pid).collection("devices").document(u.getUid()).delete();
    }

    /** Whether it is listening, so the caregiver can see it (R7). */
    static void deviceState(Context c, boolean listening) {
        String pid = Prefs.linkedPatient(c);
        if (pid == null || !available(c)) return;
        Map<String, Object> m = new HashMap<>();
        m.put("listening", listening);
        m.put("seenAt", FieldValue.serverTimestamp());
        patient(pid).update(m);
    }

    /** A help alert, for the caregiver's log (R7). The SMS goes out regardless, with or without internet. */
    static void helpEvent(Context c, String said, boolean sent) {
        String pid = Prefs.linkedPatient(c);
        if (pid == null || !available(c)) return;
        Map<String, Object> help = new HashMap<>();
        help.put("said", said);
        help.put("at", FieldValue.serverTimestamp());
        help.put("sent", sent);
        patient(pid).collection("events").add(new HashMap<>(help));
        Map<String, Object> m = new HashMap<>();
        m.put("lastHelp", help);
        patient(pid).update(m);
    }

    // --- Doses: both phones log them ---

    private static String patientFor(Context c) {
        if (c instanceof PatientScope) return ((PatientScope) c).patientId();
        return Prefs.linkedPatient(c);
    }

    static void doseAdded(Context c, DoseLog.Entry e) {
        String pid = patientFor(c);
        if (pid == null || !available(c)) return;
        Map<String, Object> m = new HashMap<>();
        m.put("name", e.name);
        m.put("at", e.at);
        m.put("by", e.by);
        // The id is the time plus the name: if the same dose arrives twice, it is not duplicated.
        patient(pid).collection("doses").document(e.at + "-" + Math.abs(e.name.hashCode()))
                .set(m, SetOptions.merge());
    }

    static ListenerRegistration listenDoses(Context c, Result<List<DoseLog.Entry>> cb) {
        String pid = patientFor(c);
        if (pid == null || !available(c)) return null;
        return patient(pid).collection("doses").addSnapshotListener((q, e) -> {
            if (q == null) return;
            List<DoseLog.Entry> list = new ArrayList<>();
            for (DocumentSnapshot d : q.getDocuments()) {
                Long at = d.getLong("at");
                if (at == null) continue;
                list.add(new DoseLog.Entry(text(d.getString("name")), at, text(d.getString("by"))));
            }
            cb.done(list, null);
        });
    }

    // --- Helpers ---

    private static String text(String s) {
        return s == null ? "" : s;
    }

    private static long millis(Timestamp t) {
        return t == null ? 0 : t.toDate().getTime();
    }

    /** A Spanish sentence to show, derived from the Firebase error. */
    static String message(Exception e) {
        if (e == null) return App.str(R.string.err_generic);
        if (e instanceof FirebaseNetworkException) return App.str(R.string.err_offline);
        if (e instanceof FirebaseAuthWeakPasswordException) return App.str(R.string.err_weak_password);
        if (e instanceof FirebaseAuthUserCollisionException) return App.str(R.string.err_email_taken);
        if (e instanceof FirebaseAuthInvalidUserException) return App.str(R.string.err_no_account);
        if (e instanceof FirebaseAuthInvalidCredentialsException) return App.str(R.string.err_bad_credentials);
        if (e instanceof FirebaseAuthRecentLoginRequiredException) {
            return App.str(R.string.err_reauth);
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) return App.str(R.string.err_offline);
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return App.str(R.string.err_no_permission);
        }
        return App.str(R.string.err_failed_with, e.getMessage());
    }
}
