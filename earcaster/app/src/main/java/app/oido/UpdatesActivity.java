package app.oido;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec 020: the caregiver shares a family message ("Llego el domingo a las 5, Lucía") and approves, one by one, the
 * visit and profile changes that Muse Spark proposes. Nothing changes without Approve; the message is not kept.
 */
public class UpdatesActivity extends AppCompatActivity {

    private String pid;
    private EditText message;
    private TextView status;
    private LinearLayout proposals;
    private View read;
    private ListenerRegistration patientsReg;
    /** The patient's settings, to show which weekly visit a proposal replaces. */
    private JSONObject config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Cloud.caregiver(this) == null) {
            toast(getString(R.string.up_caregiver_only));
            finish();
            return;
        }
        setContentView(R.layout.activity_updates);
        message = findViewById(R.id.up_message);
        status = findViewById(R.id.up_status);
        proposals = findViewById(R.id.up_proposals);
        read = findViewById(R.id.up_read);
        read.setOnClickListener(v -> readMessage());

        Intent i = getIntent();
        String shared = i.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null) message.setText(shared.trim());
        pid = i.getStringExtra(MainActivity.EXTRA_PATIENT);
        if (pid == null) {
            choosePatient();
        } else {
            setPatientName(i.getStringExtra(MainActivity.EXTRA_PATIENT_NAME));
            loadConfig();
        }
    }

    @Override
    protected void onDestroy() {
        if (patientsReg != null) patientsReg.remove();
        super.onDestroy();
    }

    private void setPatientName(String name) {
        ((TextView) findViewById(R.id.up_title)).setText(TextUtils.isEmpty(name)
                ? getString(R.string.actualizar_desde_un_mensaje) : getString(R.string.up_title_of, name));
    }

    /** Arriving from Share, the caregiver picks the patient; with only one, it is that one. */
    private void choosePatient() {
        read.setEnabled(false);
        patientsReg = Cloud.listenPatients(this, (list, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (list == null) {
                toast(error);
                finish();
                return;
            }
            // The first answer may come from an empty cache: keep listening until there is a patient.
            if (list.isEmpty()) {
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.up_no_patients);
                return;
            }
            if (patientsReg != null) patientsReg.remove();
            patientsReg = null;
            status.setVisibility(View.GONE);
            if (list.size() == 1) {
                pick(list.get(0));
                return;
            }
            String[] names = new String[list.size()];
            for (int k = 0; k < names.length; k++) names[k] = list.get(k).name;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.up_which_patient)
                    .setItems(names, (d, w) -> pick(list.get(w)))
                    .setOnCancelListener(d -> finish())
                    .show();
        });
    }

    private void pick(Cloud.Patient p) {
        pid = p.id;
        setPatientName(p.name);
        read.setEnabled(true);
        loadConfig();
    }

    private void loadConfig() {
        Cloud.fetchConfig(pid, (c, error) -> {
            try {
                if (c != null) config = new JSONObject(c.isEmpty() ? "{}" : c);
            } catch (JSONException ignored) {
            }
        });
    }

    private void readMessage() {
        String text = message.getText().toString().trim();
        if (pid == null || text.isEmpty()) return;
        JSONObject body = new JSONObject();
        try {
            body.put("pid", pid);
            body.put("message", text);
            body.put("today", AiClient.day(Calendar.getInstance()));
        } catch (JSONException e) {
            return;
        }
        proposals.removeAllViews();
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.up_reading);
        read.setEnabled(false);
        AiClient.call("proposeUpdates", body, (r, error) -> {
            if (isFinishing() || isDestroyed()) return;
            read.setEnabled(true);
            if (r == null) {
                status.setText(error);
                return;
            }
            if (r.optBoolean("medication")) {
                status.setText(R.string.up_medication);
                return;
            }
            JSONArray list = r.optJSONArray("proposals");
            if (list == null || list.length() == 0) {
                status.setText(R.string.up_nothing);
                return;
            }
            status.setText(R.string.up_found);
            for (int k = 0; k < list.length(); k++) {
                JSONObject p = list.optJSONObject(k);
                if (p != null) addCard(p);
            }
        });
    }

    // --- Proposals ---

    private static String clock(int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minute / 60);
        c.set(Calendar.MINUTE, minute % 60);
        return DateFormat.format("h:mm a", c).toString();
    }

    private static String dateLabel(String iso) {
        long start = new Agenda.Once("", iso, 0).start();
        if (start < 0) return iso;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        return DateFormat.format("EEEE d MMMM", c).toString();
    }

    private String fieldLabel(String field) {
        switch (field) {
            case "home": return getString(R.string.up_field_home);
            case "family": return getString(R.string.up_field_family);
            case "routine": return getString(R.string.up_field_routine);
            case "likes": return getString(R.string.up_field_likes);
            default: return getString(R.string.up_field_free);
        }
    }

    private void addCard(JSONObject p) {
        View card = getLayoutInflater().inflate(R.layout.item_proposal, proposals, false);
        TextView kind = card.findViewById(R.id.pr_kind);
        TextView before = card.findViewById(R.id.pr_before);
        TextView after = card.findViewById(R.id.pr_after);
        String type = p.optString("kind");
        String name = p.optString("name");
        int minute = p.optInt("minute");
        if ("visit".equals(type)) {
            kind.setText(R.string.up_kind_visit);
            after.setText(getString(R.string.up_visit_line, name, dateLabel(p.optString("date")), clock(minute)));
        } else if ("weekly".equals(type)) {
            kind.setText(R.string.up_kind_weekly);
            String replaces = p.optString("replaces", "");
            for (Agenda.Fixed f : weekly(config)) {
                if (!replaces.isEmpty() && f.name.trim().equalsIgnoreCase(replaces)) {
                    before.setText(getString(R.string.up_before, getString(R.string.up_visit_line, f.name, Weekly.describe(f.days), clock(f.minute))));
                    before.setVisibility(View.VISIBLE);
                }
            }
            after.setText(getString(R.string.up_visit_line, name, Weekly.describe(p.optInt("days")), clock(minute)));
        } else if ("profile".equals(type)) {
            kind.setText(getString(R.string.up_kind_profile, fieldLabel(p.optString("field"))));
            String old = p.optString("old", "");
            if (!old.isEmpty()) {
                before.setText(getString(R.string.up_before, old));
                before.setVisibility(View.VISIBLE);
            }
            after.setText(p.optString("new"));
        } else {
            return;
        }
        card.findViewById(R.id.pr_discard).setOnClickListener(v -> done(card, getString(R.string.up_discarded)));
        card.findViewById(R.id.pr_approve).setOnClickListener(v -> {
            card.findViewById(R.id.pr_buttons).setVisibility(View.GONE);
            approve(p, error -> {
                if (isFinishing() || isDestroyed()) return;
                if (error != null) {
                    card.findViewById(R.id.pr_buttons).setVisibility(View.VISIBLE);
                    toast(error);
                } else {
                    done(card, getString(R.string.up_saved));
                }
            });
        });
        proposals.addView(card);
    }

    private void done(View card, String text) {
        card.findViewById(R.id.pr_buttons).setVisibility(View.GONE);
        TextView t = card.findViewById(R.id.pr_done);
        t.setText(text);
        t.setVisibility(View.VISIBLE);
    }

    /** The weekly visits in a settings JSON (the app's fixed_visits), or none. */
    private static List<Agenda.Fixed> weekly(JSONObject config) {
        return config == null ? new ArrayList<>() : Agenda.Fixed.fromJson(config.optString("fixed_visits", "[]"));
    }

    private void approve(JSONObject p, Cloud.Done done) {
        String type = p.optString("kind");
        if ("profile".equals(type)) {
            Cloud.fetchProfile(pid, (profile, error) -> {
                if (profile == null) {
                    done.done(error);
                    return;
                }
                profile.apply(p.optString("field"), p.optString("old", ""), p.optString("new"));
                Cloud.saveProfile(pid, profile);
                done.done(null);
            });
            return;
        }
        Cloud.fetchConfig(pid, (config, error) -> {
            if (config == null) {
                done.done(error);
                return;
            }
            JSONObject o;
            try {
                o = new JSONObject(config.isEmpty() ? "{}" : config);
            } catch (JSONException e) {
                o = new JSONObject();
            }
            Map<String, Object> changes = new HashMap<>();
            String name = p.optString("name");
            int minute = p.optInt("minute");
            if ("visit".equals(type)) {
                List<Agenda.Once> list = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (Agenda.Once x : Agenda.Once.fromJson(o.optString("once_visits", "[]"))) if (!x.over(now)) list.add(x);
                list.add(new Agenda.Once(name, p.optString("date"), minute));
                changes.put("once_visits", Agenda.Once.toJson(list));
            } else {
                List<Agenda.Fixed> list = weekly(o);
                String replaces = p.optString("replaces", "");
                boolean replaced = false;
                for (int k = 0; k < list.size(); k++) {
                    if (!replaces.isEmpty() && list.get(k).name.trim().equalsIgnoreCase(replaces)) {
                        list.set(k, new Agenda.Fixed(name, p.optInt("days"), minute));
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) list.add(new Agenda.Fixed(name, p.optInt("days"), minute));
                changes.put("fixed_visits", Agenda.Fixed.toJson(list));
            }
            Cloud.updateConfig(pid, changes, done);
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
