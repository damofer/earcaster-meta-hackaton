package app.oido;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** The patient profile for the AI and what it may do without asking (spec 016). Caregiver mode only. */
public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final int REQ_CONTACT = 1;

    private String pid;
    private EditText name;
    private EditText callMe;
    private EditText home;
    private EditText family;
    private EditText routine;
    private EditText likes;
    private EditText sensitive;
    private EditText free;
    private TextView count;
    private MaterialSwitch callOn;
    private MaterialSwitch light;
    private MaterialSwitch dosesOn;
    private MaterialSwitch help;
    private LinearLayout callsView;
    private LinearLayout dosesView;
    /** Their settings and doses on this phone, for the preview's current context (spec 017). */
    private PatientContext patient;
    private ListenerRegistration dosesReg;

    /** The last saved state, to know whether there are changes on exit. Null while loading. */
    private Profile saved;
    private final List<Profile.Call> calls = new ArrayList<>();
    /** The doses that can be checked: those from their phrases and those already allowed. */
    private final List<String> doseNames = new ArrayList<>();
    private final List<String> checkedDoses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pid = getIntent().getStringExtra(MainActivity.EXTRA_PATIENT);
        if (pid == null || Cloud.caregiver(this) == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_profile);
        String patientName = getIntent().getStringExtra(MainActivity.EXTRA_PATIENT_NAME);
        ((TextView) findViewById(R.id.pf_title)).setText(
                patientName == null || patientName.isEmpty() ? getString(R.string.ficha) : getString(R.string.profile_of, patientName));

        name = findViewById(R.id.pf_name);
        callMe = findViewById(R.id.pf_call_me);
        home = findViewById(R.id.pf_home);
        family = findViewById(R.id.pf_family);
        routine = findViewById(R.id.pf_routine);
        likes = findViewById(R.id.pf_likes);
        sensitive = findViewById(R.id.pf_sensitive);
        free = findViewById(R.id.pf_free);
        count = findViewById(R.id.pf_count);
        callOn = findViewById(R.id.pf_call);
        light = findViewById(R.id.pf_light);
        dosesOn = findViewById(R.id.pf_doses_on);
        help = findViewById(R.id.pf_help);
        callsView = findViewById(R.id.pf_calls);
        dosesView = findViewById(R.id.pf_doses);

        TextWatcher counter = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                renderCount();
            }
        };
        for (EditText f : fields()) f.addTextChangedListener(counter);

        callOn.setOnCheckedChangeListener((b, on) -> {
            if (on && calls.isEmpty() && saved != null) pickContact();
            renderCalls();
        });
        dosesOn.setOnCheckedChangeListener((b, on) -> renderDoses());
        findViewById(R.id.pf_add_call).setOnClickListener(v -> pickContact());
        findViewById(R.id.pf_save).setOnClickListener(v -> save(true));
        findViewById(R.id.pf_delete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.pf_try_send).setOnClickListener(v -> tryQuestion());
        patient = new PatientContext(this, pid);
        dosesReg = Cloud.listenDoses(patient, (list, error) -> {
            if (list != null) DoseLog.merge(patient, list);
        });

        setEnabled(false);
        count.setText(R.string.profile_reading);
        Cloud.fetchProfile(pid, (p, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (p == null) {
                // Without the saved profile, no editing: saving an empty one would erase the existing one.
                toast(error != null ? error : getString(R.string.profile_cant_read));
                finish();
                return;
            }
            show(p);
            setEnabled(true);
        });
        Cloud.fetchConfig(pid, (config, error) -> {
            if (isFinishing() || isDestroyed() || config == null) return;
            // Their recurring visit schedule, for the preview: the same copy the phrase editor uses.
            if (!config.isEmpty()) Prefs.importConfig(patient, PatientSync.localSounds(patient, config));
            for (String d : doseNamesIn(config)) {
                if (!doseNames.contains(d)) doseNames.add(d);
            }
            renderDoses();
        });
    }

    @Override
    protected void onDestroy() {
        if (dosesReg != null) dosesReg.remove();
        super.onDestroy();
    }

    private EditText[] fields() {
        return new EditText[]{name, callMe, home, family, routine, likes, sensitive, free};
    }

    private void setEnabled(boolean on) {
        for (EditText f : fields()) f.setEnabled(on);
        callOn.setEnabled(on);
        light.setEnabled(on);
        dosesOn.setEnabled(on);
        help.setEnabled(on);
        findViewById(R.id.pf_add_call).setEnabled(on);
        findViewById(R.id.pf_save).setEnabled(on);
        findViewById(R.id.pf_delete).setEnabled(on);
    }

    /** The doses their phrases log: the «Anotar una toma» (Log a dose) action and the reminders with «Hecho» (Done). */
    private static List<String> doseNamesIn(String config) {
        List<String> names = new ArrayList<>();
        if (config.isEmpty()) return names;
        try {
            JSONObject o = new JSONObject(config);
            if (!o.has("triggers")) return names;
            for (Trigger t : Trigger.fromJson(o.getString("triggers"))) {
                String d = t.doseName.trim();
                if (!d.isEmpty() && !names.contains(d)) names.add(d);
            }
        } catch (JSONException ignored) {
        }
        return names;
    }

    private void show(Profile p) {
        saved = p;
        name.setText(p.name);
        callMe.setText(p.callMe);
        home.setText(p.home);
        family.setText(p.family);
        routine.setText(p.routine);
        likes.setText(p.likes);
        sensitive.setText(p.sensitive);
        free.setText(p.free);
        calls.clear();
        calls.addAll(p.calls);
        callOn.setChecked(!p.calls.isEmpty());
        light.setChecked(p.light);
        checkedDoses.clear();
        checkedDoses.addAll(p.doses);
        for (String d : p.doses) {
            if (!doseNames.contains(d)) doseNames.add(d);
        }
        dosesOn.setChecked(!p.doses.isEmpty());
        help.setChecked(p.help);
        renderCalls();
        renderDoses();
        renderCount();
    }

    /** What is on screen right now. A permission that is off does not save its list. */
    private Profile current() {
        Profile p = new Profile();
        p.name = text(name);
        p.callMe = text(callMe);
        p.home = text(home);
        p.family = text(family);
        p.routine = text(routine);
        p.likes = text(likes);
        p.sensitive = text(sensitive);
        p.free = text(free);
        if (callOn.isChecked()) p.calls.addAll(calls);
        p.light = light.isChecked();
        if (dosesOn.isChecked()) {
            for (String d : doseNames) {
                if (checkedDoses.contains(d)) p.doses.add(d);
            }
        }
        p.help = help.isChecked();
        return p;
    }

    private static String text(EditText f) {
        return f.getText().toString().trim();
    }

    private void renderCount() {
        if (saved == null) return;
        int left = Profile.MAX_CHARS - current().length();
        NumberFormat nf = NumberFormat.getIntegerInstance(Lang.locale(this));
        count.setText(left >= 0
                ? getString(R.string.chars_left, nf.format(left))
                : getString(R.string.chars_over, nf.format(-left)));
        count.setTextColor(getColor(left >= 0 ? R.color.text_muted : R.color.danger));
    }

    private void renderCalls() {
        callsView.removeAllViews();
        boolean on = callOn.isChecked();
        callsView.setVisibility(on ? View.VISIBLE : View.GONE);
        findViewById(R.id.pf_add_call).setVisibility(on ? View.VISIBLE : View.GONE);
        for (Profile.Call c : new ArrayList<>(calls)) {
            View row = getLayoutInflater().inflate(R.layout.item_row, callsView, false);
            ((TextView) row.findViewById(R.id.row_text)).setText(
                    c.relation.isEmpty() ? c.name : c.name + ", " + c.relation);
            row.findViewById(R.id.row_delete).setOnClickListener(v -> {
                calls.remove(c);
                if (calls.isEmpty()) callOn.setChecked(false);
                renderCalls();
                renderCount();
            });
            callsView.addView(row);
        }
        renderCount();
    }

    private void renderDoses() {
        dosesView.removeAllViews();
        boolean on = dosesOn.isChecked();
        dosesView.setVisibility(on ? View.VISIBLE : View.GONE);
        findViewById(R.id.pf_doses_empty).setVisibility(on && doseNames.isEmpty() ? View.VISIBLE : View.GONE);
        for (String d : doseNames) {
            CheckBox box = new CheckBox(this);
            box.setText(d);
            box.setChecked(checkedDoses.contains(d));
            box.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !checkedDoses.contains(d)) checkedDoses.add(d);
                if (!checked) checkedDoses.remove(d);
            });
            dosesView.addView(box);
        }
    }

    // --- Contacts it may call ---

    private void pickContact() {
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(i, REQ_CONTACT);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_contacts_app));
            if (calls.isEmpty()) callOn.setChecked(false);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CONTACT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (calls.isEmpty()) callOn.setChecked(false);
            return;
        }
        addContact(data.getData());
    }

    private void addContact(Uri uri) {
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        String contactName;
        String number;
        try (Cursor c = getContentResolver().query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return;
            contactName = c.getString(0);
            number = c.getString(1);
        } catch (RuntimeException e) {
            toast(getString(R.string.contact_unreadable));
            return;
        }
        if (number == null || number.trim().isEmpty()) {
            toast(getString(R.string.contact_no_number));
            if (calls.isEmpty()) callOn.setChecked(false);
            return;
        }
        String shown = contactName == null ? number : contactName;
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout layout = view.findViewById(R.id.nf_name_layout);
        view.findViewById(R.id.nf_extra_layout).setVisibility(View.GONE);
        EditText relation = view.findViewById(R.id.nf_name);
        layout.setHint(getString(R.string.relation_hint));
        new MaterialAlertDialogBuilder(this)
                .setTitle(shown)
                .setView(view)
                .setPositiveButton(R.string.add, (d, w) -> {
                    calls.add(new Profile.Call(shown, number, relation.getText().toString()));
                    callOn.setChecked(true);
                    renderCalls();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    if (calls.isEmpty()) callOn.setChecked(false);
                })
                .show();
    }

    // --- Preview (016 R3): what the AI would answer with the profile on screen ---

    private void tryQuestion() {
        EditText field = findViewById(R.id.pf_try);
        String q = text(field);
        if (q.isEmpty()) {
            field.requestFocus();
            return;
        }
        Profile p = current();
        if (p.tooLong()) {
            toast(getString(R.string.profile_too_long_try, Profile.MAX_CHARS));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            Calendar now = Calendar.getInstance();
            body.put("pid", pid);
            body.put("text", q);
            body.put("day", AiClient.day(now));
            // Without the calendar: this phone's calendar is not theirs.
            body.put("facts", AiClient.facts(patient, now, false));
            body.put("profile", new JSONObject(p.toMap()));
        } catch (JSONException e) {
            return;
        }
        TextView answer = findViewById(R.id.pf_try_answer);
        View send = findViewById(R.id.pf_try_send);
        answer.setVisibility(View.VISIBLE);
        answer.setText(R.string.thinking);
        send.setEnabled(false);
        AiClient.call("preview", body, (r, error) -> {
            if (isFinishing() || isDestroyed()) return;
            send.setEnabled(true);
            if (r == null) {
                answer.setText(error);
                return;
            }
            String said = r.optString("answer");
            String action = r.optString("action");
            answer.setText((said.isEmpty() ? getString(R.string.would_say_nothing) : getString(R.string.would_answer, said))
                    + (action.isEmpty() ? "" : getString(R.string.would_do, action)));
        });
    }

    // --- Save, delete and exit ---

    private boolean changed() {
        return saved != null && !current().toMap().equals(saved.toMap());
    }

    private void save(boolean stay) {
        Profile p = current();
        if (p.tooLong()) {
            toast(getString(R.string.profile_too_long_save, Profile.MAX_CHARS));
            return;
        }
        Cloud.saveProfile(pid, p);
        saved = p;
        toast(getString(R.string.profile_saved));
        if (!stay) finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_delete_q)
                .setMessage(R.string.profile_delete_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Cloud.deleteProfile(pid);
                    show(new Profile());
                    toast(getString(R.string.profile_deleted));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!changed()) {
            super.onBackPressed();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_changes_q)
                .setPositiveButton(R.string.guardar, (d, w) -> save(false))
                .setNegativeButton(R.string.discard, (d, w) -> finish())
                .setNeutralButton(R.string.keep_editing, null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
