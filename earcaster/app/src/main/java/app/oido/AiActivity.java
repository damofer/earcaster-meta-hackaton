package app.oido;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * A patient's AI assistant (spec 017), on the caregiver's phone: turn it on after accepting
 * what data leaves the phone (R13), its name, the model that writes the answers (spec 018), its voice (R7), the fallback
 * phrase (R8), the daily maximum (R11), the experimental option (R2) and the conversations from the last 7 days (R10).
 */
public class AiActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final int MAX_LIMIT = 500;

    private String pid;
    private MaterialSwitch on;
    private EditText name;
    private MaterialButtonToggleGroup model;
    private EditText fallback;
    private EditText max;
    private MaterialSwitch any;
    private TextView voiceName;
    private TextView status;
    private LinearLayout talksView;
    private ListenerRegistration talksReg;
    private MediaPlayer sample;

    /** The last saved state, to know whether there are changes on exit. Null while loading. */
    private Map<String, Object> saved;
    private String voice = "";
    private String voiceLabel = "";
    /** The caregiver already accepted what data leaves the phone (R13). Null while loading. */
    private Boolean consent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pid = getIntent().getStringExtra(MainActivity.EXTRA_PATIENT);
        if (pid == null || Cloud.caregiver(this) == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_ai);
        String patientName = getIntent().getStringExtra(MainActivity.EXTRA_PATIENT_NAME);
        ((TextView) findViewById(R.id.ai_title)).setText(
                TextUtils.isEmpty(patientName) ? getString(R.string.asistente) : getString(R.string.ai_title_of, patientName));
        on = findViewById(R.id.ai_on);
        name = findViewById(R.id.ai_name);
        model = findViewById(R.id.ai_model);
        fallback = findViewById(R.id.ai_fallback);
        max = findViewById(R.id.ai_max);
        any = findViewById(R.id.ai_any);
        voiceName = findViewById(R.id.ai_voice_name);
        status = findViewById(R.id.ai_status);
        talksView = findViewById(R.id.ai_talks);

        on.setOnCheckedChangeListener((b, checked) -> {
            if (checked && saved != null && Boolean.FALSE.equals(consent)) askConsent();
        });
        model.addOnButtonCheckedListener((g, id, checked) -> {
            // Switching to Meta sends the data to another company: the caregiver reads it first (R13, spec 018).
            if (checked && id == R.id.ai_model_meta && saved != null
                    && !Prefs.AI_MODEL_META.equals(saved.get(Prefs.AI_MODEL))) askMeta();
        });
        findViewById(R.id.ai_voice).setOnClickListener(v -> chooseVoice());
        findViewById(R.id.ai_save).setOnClickListener(v -> save(true));
        findViewById(R.id.ai_talks_delete).setOnClickListener(v -> confirmDeleteTalks());
        findViewById(R.id.ai_summary_now).setOnClickListener(v -> summarize());
        findViewById(R.id.ai_updates).setOnClickListener(v -> startActivity(new Intent(this, UpdatesActivity.class)
                .putExtra(MainActivity.EXTRA_PATIENT, pid)
                .putExtra(MainActivity.EXTRA_PATIENT_NAME, getIntent().getStringExtra(MainActivity.EXTRA_PATIENT_NAME))));

        setEnabled(false);
        status.setText(R.string.reading);
        Cloud.fetchConfig(pid, (config, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (config == null) {
                // Without the settings, no editing: saving blindly could turn off what is there.
                toast(error != null ? error : getString(R.string.ai_cant_read_settings));
                finish();
                return;
            }
            show(config);
            setEnabled(true);
        });
        Cloud.fetchAiConsent(pid, (accepted, error) -> {
            if (accepted != null) consent = accepted;
        });
        Cloud.fetchUsage(pid, AiClient.day(Calendar.getInstance()), (count, error) -> {
            if (count != null && !isFinishing()) renderUsage(count);
        });
        talksReg = Cloud.listenTalks(pid, (list, error) -> {
            if (list != null && !isFinishing()) renderTalks(list);
        });
    }

    @Override
    protected void onDestroy() {
        if (talksReg != null) talksReg.remove();
        stopSample();
        super.onDestroy();
    }

    private void setEnabled(boolean enabled) {
        for (View v : new View[]{on, name, model, fallback, max, any, findViewById(R.id.ai_voice), findViewById(R.id.ai_save)}) {
            v.setEnabled(enabled);
        }
    }

    private void show(String config) {
        JSONObject o;
        try {
            o = new JSONObject(config.isEmpty() ? "{}" : config);
        } catch (JSONException e) {
            o = new JSONObject();
        }
        on.setChecked(o.optBoolean(Prefs.AI_ON, false));
        name.setText(o.optString(Prefs.AI_NAME, Prefs.defaultAiName()));
        model.check(Prefs.AI_MODEL_META.equals(o.optString(Prefs.AI_MODEL)) ? R.id.ai_model_meta : R.id.ai_model_claude);
        fallback.setText(o.optString(Prefs.AI_FALLBACK, Prefs.defaultAiFallback()));
        max.setText(String.valueOf(o.optInt(Prefs.AI_MAX, Prefs.DEFAULT_AI_MAX)));
        any.setChecked(o.optBoolean(Prefs.AI_ANY, false));
        voice = o.optString(Prefs.AI_VOICE, "");
        voiceLabel = o.optString(Prefs.AI_VOICE_NAME, "");
        renderVoice();
        saved = current();
        if (status.getText().toString().equals(getString(R.string.reading))) status.setText("");
    }

    /** What is on screen, with the settings keys. */
    private Map<String, Object> current() {
        Map<String, Object> m = new HashMap<>();
        m.put(Prefs.AI_ON, on.isChecked());
        String n = name.getText().toString().trim();
        m.put(Prefs.AI_NAME, n.isEmpty() ? Prefs.defaultAiName() : n);
        m.put(Prefs.AI_MODEL, model.getCheckedButtonId() == R.id.ai_model_meta ? Prefs.AI_MODEL_META : Prefs.AI_MODEL_CLAUDE);
        String f = fallback.getText().toString().trim();
        m.put(Prefs.AI_FALLBACK, f.isEmpty() ? Prefs.defaultAiFallback() : f);
        int limit;
        try {
            limit = Integer.parseInt(max.getText().toString().trim());
        } catch (NumberFormatException e) {
            limit = Prefs.DEFAULT_AI_MAX;
        }
        m.put(Prefs.AI_MAX, Math.max(1, Math.min(MAX_LIMIT, limit)));
        m.put(Prefs.AI_ANY, any.isChecked());
        m.put(Prefs.AI_VOICE, voice);
        m.put(Prefs.AI_VOICE_NAME, voiceLabel);
        return m;
    }

    private void renderVoice() {
        // With nothing chosen, Clara's voice from the landing video plays (the function uses it by default).
        voiceName.setText(voice.isEmpty() ? getString(R.string.ai_default_voice) : voiceLabel);
    }

    private void renderUsage(long count) {
        int limit = (Integer) current().get(Prefs.AI_MAX);
        status.setText(count >= limit
                ? getString(R.string.ai_usage_max, (int) count)
                : getString(R.string.ai_usage, (int) count, limit));
        status.setTextColor(getColor(count >= limit ? R.color.danger : R.color.text_muted));
    }

    // --- Consent (R13) ---

    private void askConsent() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_consent_title)
                .setMessage(R.string.ai_consent_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, (d, w) -> Cloud.setAiConsent(pid, error -> {
                    if (error != null) {
                        toast(error);
                        on.setChecked(false);
                    } else {
                        consent = true;
                    }
                }))
                .setNegativeButton(R.string.cancel, (d, w) -> on.setChecked(false))
                .show();
    }

    private void askMeta() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_model_meta_title)
                .setMessage(R.string.ai_model_meta_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, null)
                .setNegativeButton(R.string.cancel, (d, w) -> model.check(R.id.ai_model_claude))
                .show();
    }

    // --- Voice (R7) ---

    private void chooseVoice() {
        toast(getString(R.string.ai_finding_voices));
        AiClient.call("voices", null, (result, error) -> {
            if (isFinishing() || isDestroyed()) return;
            JSONArray list = result == null ? null : result.optJSONArray("voices");
            if (list == null || list.length() == 0) {
                toast(error != null ? error : getString(R.string.ai_no_voices));
                return;
            }
            String[] labels = new String[list.length()];
            int checked = -1;
            // With no voice chosen, the default voice shows as selected.
            String current = voice.isEmpty() ? result.optString("default") : voice;
            for (int i = 0; i < list.length(); i++) {
                JSONObject v = list.optJSONObject(i);
                labels[i] = v.optString("name")
                        + (v.optBoolean(Lang.en() ? "english" : "spanish") ? getString(R.string.ai_voice_lang_tag) : "");
                if (v.optString("id").equals(current)) checked = i;
            }
            final int[] chosen = {checked};
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ai_choose_voice)
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        chosen[0] = which;
                        playSample(list.optJSONObject(which).optString("preview"));
                    })
                    .setPositiveButton(R.string.ai_use_voice, (d, w) -> {
                        if (chosen[0] < 0) return;
                        JSONObject v = list.optJSONObject(chosen[0]);
                        voice = v.optString("id");
                        voiceLabel = v.optString("name");
                        renderVoice();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener(d -> stopSample())
                    .show();
        });
    }

    /** The voice sample, when tapped in the list. */
    private void playSample(String url) {
        stopSample();
        if (url.isEmpty()) return;
        MediaPlayer mp = new MediaPlayer();
        sample = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(url);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((m, what, extra) -> {
                toast(getString(R.string.ai_sample_failed));
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            stopSample();
        }
    }

    private void stopSample() {
        if (sample == null) return;
        try {
            sample.stop();
        } catch (IllegalStateException ignored) {
        }
        sample.release();
        sample = null;
    }

    // --- Conversations (R10) ---

    private void renderTalks(List<Cloud.Talk> talks) {
        talksView.removeAllViews();
        findViewById(R.id.ai_talks_empty).setVisibility(talks.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.ai_talks_delete).setVisibility(talks.isEmpty() ? View.GONE : View.VISIBLE);
        long now = System.currentTimeMillis();
        for (Cloud.Talk t : talks) {
            View item = getLayoutInflater().inflate(R.layout.item_talk, talksView, false);
            ((TextView) item.findViewById(R.id.talk_when)).setText(t.at > 0 ? Ago.text(t.at, now) : getString(R.string.now));
            ((TextView) item.findViewById(R.id.talk_heard)).setText(getString(R.string.quoted, t.heard));
            ((TextView) item.findViewById(R.id.talk_answer)).setText(
                    t.answer.isEmpty() ? getString(R.string.ai_no_spoken_answer) : t.answer);
            if (!t.action.isEmpty()) {
                TextView action = item.findViewById(R.id.talk_action);
                action.setText(t.action);
                action.setVisibility(View.VISIBLE);
            }
            talksView.addView(item);
        }
    }

    // --- Today's summary (spec 019) ---

    private void summarize() {
        Calendar now = Calendar.getInstance();
        Calendar midnight = (Calendar) now.clone();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        JSONObject body = new JSONObject();
        try {
            body.put("pid", pid);
            body.put("day", AiClient.day(now));
            body.put("from", midnight.getTimeInMillis());
            body.put("to", now.getTimeInMillis());
            body.put("label", DateFormat.format("EEEE d MMMM yyyy", now).toString());
            body.put("zone", TimeZone.getDefault().getID());
        } catch (JSONException e) {
            return;
        }
        TextView text = findViewById(R.id.ai_summary);
        View button = findViewById(R.id.ai_summary_now);
        text.setVisibility(View.VISIBLE);
        text.setText(R.string.ai_summary_writing);
        button.setEnabled(false);
        AiClient.call("summaryNow", body, (r, error) -> {
            if (isFinishing() || isDestroyed()) return;
            button.setEnabled(true);
            if (r == null) {
                text.setText(error);
                return;
            }
            String said = r.optString("text");
            text.setText(said.isEmpty() ? getString(R.string.ai_summary_empty) : said);
        });
    }

    private void confirmDeleteTalks() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_delete_talks_title)
                .setMessage(R.string.ai_delete_talks_msg)
                .setPositiveButton(R.string.delete, (d, w) -> Cloud.deleteTalks(pid, error -> {
                    if (error != null) toast(error);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --- Save and exit ---

    private boolean changed() {
        return saved != null && !current().equals(saved);
    }

    private void save(boolean stay) {
        Map<String, Object> m = current();
        if (Boolean.TRUE.equals(m.get(Prefs.AI_ON)) && !Boolean.TRUE.equals(consent)) {
            askConsent();
            return;
        }
        findViewById(R.id.ai_save).setEnabled(false);
        Cloud.updateConfig(pid, m, error -> {
            findViewById(R.id.ai_save).setEnabled(true);
            if (error != null) {
                toast(error);
                return;
            }
            saved = m;
            // The phrase editor's copy too: if it is opened offline, it does not revert to the old values.
            Prefs.importConfig(new PatientContext(this, pid), new JSONObject(m).toString());
            toast(getString(R.string.saved_reaches_phone));
            if (!stay) finish();
        });
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
