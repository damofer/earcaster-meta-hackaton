package app.oido;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AppState.Listener, Cloud.PatientScope {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    /** With this, the screen edits a patient from the caregiver's phone (spec 015). */
    static final String EXTRA_PATIENT = "patient_id";
    static final String EXTRA_PATIENT_NAME = "patient_name";
    /** Each patient's copies on the caregiver's phone: paciente_oido_<id> and paciente_tomas_<id>. */
    static final String PATIENT_PREFS_PREFIX = "paciente_";
    /** Delay after the last change before sending it, so not every keystroke is sent. */
    private static final long PUSH_DELAY_MS = 1500;

    private static final int REQ_TONE = 1;
    private static final int REQ_FILE = 2;
    private static final int REQ_PERMS = 3;
    private static final int REQ_REC_PERM = 4;
    private static final int REQ_DICT_PERM = 5;
    private static final int REQ_CAL_PERM = 6;
    private static final int REQ_NOTIF_PERM = 7;
    private static final int REQ_CONTACT = 8;

    /** Chips from view_days, Monday to Sunday (like Weekly.MONDAY_FIRST). */
    private static final int[] DAY_CHIPS = {
            R.id.day_mon, R.id.day_tue, R.id.day_wed, R.id.day_thu, R.id.day_fri, R.id.day_sat, R.id.day_sun};

    private LinearLayout triggersView;
    private ExtendedFloatingActionButton addButton;
    private MaterialButton toggle;
    private MaterialButton silence;
    private TextView status;
    private TextView statusDetail;
    private LinearProgressIndicator progress;
    private TextView stats;
    private TextView count;
    private View empty;
    private View pulse1;
    private View pulse2;
    private AnimatorSet pulseAnim;

    /** To hear a phrase's response from the list or the editor. */
    private final Responder preview = new Responder(this);
    private List<Trigger> triggers;
    /** Phrase open in the editor (or whose sound is being chosen). */
    private int pickIndex = -1;
    /** Phrase whose sound is being previewed, or -1. */
    private int previewIndex = -1;

    private BottomSheetDialog editorSheet;
    private View editorView;
    private BottomSheetDialog settingsSheet;
    private BottomSheetDialog visitsSheet;
    private View visitsView;
    private AlertDialog recordDialog;
    private AlertDialog dictateDialog;
    private boolean pendingStart = false;
    private boolean askedNotifications = false;
    private boolean askedCall = false;
    private boolean askedSms = false;
    /** Which of the phrase's contacts is being chosen: 0 = the first one (spec 008). */
    private int contactSlot = 0;
    private boolean resumed = false;

    private ListenerRegistration dosesReg;
    private SharedPreferences.OnSharedPreferenceChangeListener configListener;
    private boolean pushPending = false;
    private final Handler pushHandler = new Handler(Looper.getMainLooper());
    private final Runnable push = this::pushConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (patientId() == null && Cloud.available(this) && !Prefs.MODE_SOLO.equals(Prefs.mode(this))) {
            // Caregiver, patient or first run: each has its own screen.
            WelcomeActivity.open(this);
            return;
        }
        setContentView(R.layout.activity_main);

        triggersView = findViewById(R.id.triggers);
        addButton = findViewById(R.id.btn_add);
        toggle = findViewById(R.id.btn_toggle);
        silence = findViewById(R.id.btn_silence);
        status = findViewById(R.id.status);
        statusDetail = findViewById(R.id.status_detail);
        progress = findViewById(R.id.progress);
        stats = findViewById(R.id.stats);
        count = findViewById(R.id.count);
        empty = findViewById(R.id.empty);
        pulse1 = findViewById(R.id.pulse1);
        pulse2 = findViewById(R.id.pulse2);

        triggers = Prefs.triggers(this);
        renderTriggers();
        if (patientId() != null) setUpPatientEditor();
        else Reminders.schedule(this);

        addButton.setOnClickListener(v -> {
            triggers.add(new Trigger("", null, null));
            saveTriggers();
            renderTriggers();
            openEditor(triggers.size() - 1, true);
        });
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        silence.setOnClickListener(v -> startService(
                new Intent(this, ListenerService.class).setAction(ListenerService.ACTION_SILENCE)));
        toggle.setOnClickListener(v -> {
            if (AppState.isActive()) {
                stopService(new Intent(this, ListenerService.class));
            } else {
                AppState.detections = 0;
                beginStart();
            }
        });

        if (savedInstanceState != null) {
            int editing = savedInstanceState.getInt("editing", -1);
            pickIndex = savedInstanceState.getInt("pick", -1);
            contactSlot = savedInstanceState.getInt("contact_slot", 0);
            if (editing >= 0 && editing < triggers.size()) openEditor(editing, false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("pick", pickIndex);
        out.putInt("contact_slot", contactSlot);
        out.putInt("editing", editorSheet != null && editorSheet.isShowing() ? pickIndex : -1);
    }

    @Override
    protected void onDestroy() {
        if (dosesReg != null) dosesReg.remove();
        if (configListener != null) getSharedPreferences("oido", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(configListener);
        // The sheets are reopened from onCreate after the screen rotates.
        if (editorSheet != null) {
            editorSheet.setOnDismissListener(null);
            editorSheet.dismiss();
        }
        if (settingsSheet != null) settingsSheet.dismiss();
        if (visitsSheet != null) visitsSheet.dismiss();
        preview.release();
        super.onDestroy();
    }

    // --- Phrase list ---

    private void saveTriggers() {
        Prefs.setTriggers(this, triggers);
        // A patient's reminders ring on their phone, not on the caregiver's.
        if (patientId() == null) Reminders.schedule(this);
    }

    // --- Editing a patient from the caregiver's phone (spec 015) ---

    @Override
    public String patientId() {
        Intent i = getIntent();
        return i == null ? null : i.getStringExtra(EXTRA_PATIENT);
    }

    /** The phrases and doses of the patient being edited go to their own files, not to this phone's. */
    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(prefsName(name, patientId()), mode);
    }

    /** The settings or doses file of patient pid; the other files belong to this phone. */
    static String prefsName(String name, String pid) {
        if (pid != null && ("oido".equals(name) || "tomas".equals(name))) {
            return PATIENT_PREFS_PREFIX + name + "_" + pid;
        }
        return name;
    }

    private void setUpPatientEditor() {
        String pid = patientId();
        String name = getIntent().getStringExtra(EXTRA_PATIENT_NAME);
        if (name == null || name.isEmpty()) name = getString(R.string.patient);
        ((TextView) findViewById(R.id.main_title)).setText(name);
        TextView subtitle = findViewById(R.id.main_subtitle);
        subtitle.setText(R.string.editor_subtitle);
        subtitle.setVisibility(View.VISIBLE);
        findViewById(R.id.listen_card).setVisibility(View.GONE);

        Cloud.fetchConfig(pid, (config, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (config == null) {
                toast(getString(R.string.offline_copy));
            } else if (!config.isEmpty() && !config.equals(Prefs.exportConfig(this))
                    && (editorSheet == null || !editorSheet.isShowing())) {
                Prefs.importConfig(this, PatientSync.localSounds(this, config));
                triggers = Prefs.triggers(this);
                renderTriggers();
            }
            // From here on, every change is sent.
            configListener = (prefs, key) -> {
                pushPending = true;
                pushHandler.removeCallbacks(push);
                pushHandler.postDelayed(push, PUSH_DELAY_MS);
            };
            getSharedPreferences("oido", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(configListener);
        });
        dosesReg = Cloud.listenDoses(this, (list, error) -> {
            if (list != null) DoseLog.merge(this, list);
        });
    }

    private void pushConfig() {
        String pid = patientId();
        if (pid == null || !pushPending) return;
        pushPending = false;
        pushHandler.removeCallbacks(push);
        Cloud.pushConfig(pid, Prefs.exportConfig(this));
        Cloud.uploadSounds(this, pid, Prefs.triggers(this), (file, e) ->
                toast(getString(R.string.recording_too_long)));
    }

    /** In the caregiver's editor, the chosen sound is copied into the recordings so it can be sent. */
    private Uri copyForPatient(Uri uri, String name) {
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 5) ext = name.substring(dot).toLowerCase();
        File out = new File(Recorder.dir(this), "elegido-" + System.currentTimeMillis() + ext);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("sin datos");
            byte[] buf = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > Cloud.MAX_SOUND_BYTES) {
                    os.close();
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    toast(getString(R.string.sound_too_long));
                    return null;
                }
                os.write(buf, 0, n);
            }
            return Uri.fromFile(out);
        } catch (IOException | SecurityException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            toast(getString(R.string.sound_unreadable));
            return null;
        }
    }

    private static String soundLabel(Trigger t) {
        if (t.isAction()) return actionLabel(t);
        if (t.speak) {
            String text = t.speech.trim();
            if (text.isEmpty()) return App.str(R.string.label_voice_missing);
            String label = App.str(R.string.label_voice, text.length() > 60 ? text.substring(0, 57) + "…" : text);
            if (t.thenSound) label += " + " + (t.soundName != null ? t.soundName : App.str(R.string.sound));
            return label;
        }
        String label = t.soundName != null ? t.soundName : App.str(R.string.notification_sound);
        if (t.startMs > 0 || t.endMs > 0) {
            label += " · " + Recorder.formatTenths(t.startMs) + "–"
                    + (t.endMs > 0 ? Recorder.formatTenths(t.endMs) : App.str(R.string.end));
        }
        return label;
    }

    private static String actionLabel(Trigger t) {
        switch (t.action) {
            case Trigger.CALL:
                return t.contacts.isEmpty() ? App.str(R.string.label_call_missing)
                        : App.str(R.string.label_calls, t.contacts.get(0).name);
            case Trigger.LIGHT_ON:
                return App.str(R.string.label_light_on);
            case Trigger.LIGHT_OFF:
                return App.str(R.string.label_light_off);
            case Trigger.DOSE:
                return t.doseName.isEmpty() ? App.str(R.string.label_dose_missing)
                        : App.str(R.string.label_dose, t.doseName);
            case Trigger.HELP:
                return t.contacts.isEmpty() ? App.str(R.string.label_help_missing)
                        : App.str(R.string.label_help, SpokenText.names(Actions.contactNames(t)));
            default:
                return App.str(R.string.action);
        }
    }

    private void renderTriggers() {
        stopPreview();
        triggersView.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < triggers.size(); i++) {
            final Trigger t = triggers.get(i);
            final int index = i;
            View row = inflater.inflate(R.layout.item_trigger, triggersView, false);
            List<String> forms = Arrays.asList(t.phrase.split("\\s*[,;/]\\s*"));
            TextView title = row.findViewById(R.id.title);
            TextView variants = row.findViewById(R.id.variants);
            List<String> details = new ArrayList<>();
            boolean scheduled = t.atMinute >= 0 && !t.isAction();
            if (t.phrase.trim().isEmpty()) {
                // A reminder may have no phrase: it only rings at its time (spec 006).
                title.setText(scheduled ? getString(R.string.reminder_at_label, clockLabel(t.atMinute))
                        : getString(R.string.no_phrase));
                title.setAlpha(scheduled ? 1f : 0.5f);
                if (scheduled) details.add(Weekly.describe(t.days));
            } else {
                title.setText(forms.get(0));
                if (forms.size() > 1) {
                    details.add(getString(R.string.also, String.join(", ", forms.subList(1, forms.size()))));
                }
                if (scheduled) {
                    details.add(getString(R.string.reminder_at_days, clockLabel(t.atMinute), Weekly.describe(t.days)));
                }
            }
            if (details.isEmpty()) {
                variants.setVisibility(View.GONE);
            } else {
                variants.setText(String.join(" · ", details));
            }
            ((TextView) row.findViewById(R.id.sound)).setText(soundLabel(t));
            row.findViewById(R.id.btn_play).setOnClickListener(v -> togglePreview(index));
            row.findViewById(R.id.card).setOnClickListener(v -> {
                if (AppState.isActive()) {
                    toast(getString(R.string.stop_to_edit));
                    return;
                }
                openEditor(index, false);
            });
            triggersView.addView(row);
        }
        int n = triggers.size();
        count.setText(n == 0 ? "" : String.valueOf(n));
        empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        updatePreviewButtons();
        onAppStateChanged();
    }

    // --- Hearing a phrase's sound ---

    private void togglePreview(int index) {
        if (previewIndex == index) {
            stopPreview();
            return;
        }
        stopPreview();
        if (index < 0 || index >= triggers.size()) return;
        Trigger t = triggers.get(index);
        if (t.isAction()) {
            // You hear what it would say, without calling or turning anything on.
            previewIndex = index;
            updatePreviewButtons();
            preview.say(Actions.preview(this, t), () -> {
                previewIndex = -1;
                updatePreviewButtons();
            });
            return;
        }
        if (t.speak && t.speech.trim().isEmpty()) {
            toast(getString(R.string.write_speech_first));
            return;
        }
        previewIndex = index;
        updatePreviewButtons();
        preview.respond(t, () -> {
            previewIndex = -1;
            updatePreviewButtons();
        }, this::showNoVoice);
    }

    private void stopPreview() {
        preview.stop();
        previewIndex = -1;
        updatePreviewButtons();
    }

    private void updatePreviewButtons() {
        if (triggersView == null) return;
        for (int i = 0; i < triggersView.getChildCount(); i++) {
            MaterialButton b = triggersView.getChildAt(i).findViewById(R.id.btn_play);
            setPlayIcon(b, i == previewIndex);
        }
        if (editorView != null) {
            boolean editing = previewIndex >= 0 && previewIndex == pickIndex;
            setPlayIcon(editorView.findViewById(R.id.ed_play), editing);
            for (int id : new int[]{R.id.ed_speech_play, R.id.ed_action_play}) {
                MaterialButton speak = editorView.findViewById(id);
                speak.setText(editing ? R.string.parar : R.string.escuchar_como_suena);
                speak.setIconResource(editing ? R.drawable.ic_stop_round : R.drawable.ic_play);
            }
        }
    }

    private void setPlayIcon(MaterialButton b, boolean playing) {
        b.setIconResource(playing ? R.drawable.ic_stop_round : R.drawable.ic_play);
        b.setContentDescription(getString(playing ? R.string.parar : R.string.escuchar));
    }

    // --- Phrase editor (bottom sheet) ---

    private void openEditor(int index, boolean isNew) {
        if (index < 0 || index >= triggers.size()) return;
        stopPreview();
        pickIndex = index;
        final Trigger t = triggers.get(index);

        View view = getLayoutInflater().inflate(R.layout.sheet_editor, null);
        editorView = view;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        editorSheet = sheet;

        ((TextView) view.findViewById(R.id.ed_title)).setText(isNew ? R.string.nueva_frase : R.string.editar_frase);

        TextInputLayout phraseLayout = view.findViewById(R.id.ed_phrase_layout);
        EditText phrase = view.findViewById(R.id.ed_phrase);
        phrase.setSaveEnabled(false);
        phrase.setText(t.phrase);
        phrase.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                t.phrase = s.toString().trim();
                phraseLayout.setError(Texts.hasDigits(Texts.forGrammar(t.phrase))
                        ? getString(R.string.digits_in_words) : null);
                saveTriggers();
            }
        });
        phraseLayout.setEndIconOnClickListener(v -> openDictation());
        if (isNew) phrase.requestFocus();

        view.findViewById(R.id.ed_play).setOnClickListener(v -> togglePreview(triggers.indexOf(t)));
        view.findViewById(R.id.ed_record).setOnClickListener(v -> openRecorder());
        view.findViewById(R.id.ed_tones).setOnClickListener(v -> pickTone());
        view.findViewById(R.id.ed_file).setOnClickListener(v -> pickFile());
        view.findViewById(R.id.ed_done).setOnClickListener(v -> sheet.dismiss());
        view.findViewById(R.id.ed_delete).setOnClickListener(v -> {
            String what = t.phrase.isEmpty() ? getString(R.string.this_phrase) : getString(R.string.quoted, t.phrase);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_what_q, what))
                    .setMessage(R.string.delete_phrase_msg)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        deleteRecording(t.soundUri);
                        triggers.remove(t);
                        saveTriggers();
                        sheet.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        bindEditorSound(t);
        bindEditorSpeech(t);
        bindEditorAction(t);
        bindEditorSchedule(t);
        showEditorGroups(t);

        sheet.setOnDismissListener(d -> {
            stopPreview();
            // A new phrase left with no words and no response is useless: it is discarded.
            if (triggers.contains(t) && t.phrase.isEmpty() && t.soundUri == null && t.speech.isEmpty()
                    && t.atMinute < 0 && !t.isAction()) {
                triggers.remove(t);
                saveTriggers();
            }
            if (editorSheet == sheet) {
                editorSheet = null;
                editorView = null;
            }
            renderTriggers();
        });
        sheet.show();
        updatePreviewButtons();
    }

    /** Shows the chosen sound and prepares the trim controls. */
    private void bindEditorSound(Trigger t) {
        if (editorView == null) return;
        View view = editorView;
        ((TextView) view.findViewById(R.id.ed_sound_name)).setText(
                t.soundName != null ? t.soundName : getString(R.string.notification_sound));
        TextView info = view.findViewById(R.id.ed_sound_info);
        TextView trimLabel = view.findViewById(R.id.ed_trim_label);
        RangeSlider trim = view.findViewById(R.id.ed_trim);
        View trimHint = view.findViewById(R.id.ed_trim_hint);

        final int total = SoundPlayer.duration(this, t.soundUri);
        boolean canTrim = total >= 1000;
        info.setText(total > 0 ? getString(R.string.lasts, Recorder.formatTenths(total)) : getString(R.string.tap_play_to_hear));
        trim.setEnabled(canTrim);
        trimHint.setAlpha(canTrim ? 1f : 0.5f);
        trim.clearOnChangeListeners();
        trim.clearOnSliderTouchListeners();
        trim.setStepSize(0.1f);
        trim.setMinSeparationValue(0.5f);
        if (!canTrim) {
            trim.setValueFrom(0f);
            trim.setValueTo(1f);
            trim.setValues(0f, 1f);
            trimLabel.setText("");
            return;
        }
        // The slider moves in tenths of a second.
        final float max = (total / 100) / 10f;
        trim.setValueFrom(0f);
        trim.setValueTo(max);
        float start = Math.min(max, tenths(t.startMs));
        float end = t.endMs > 0 ? Math.min(max, tenths(t.endMs)) : max;
        if (end - start < 0.5f) {
            start = 0f;
            end = max;
        }
        trim.setValues(start, end);
        final Runnable label = () -> {
            List<Float> v = trim.getValues();
            trimLabel.setText(Recorder.formatTenths(Math.round(v.get(0) * 1000)) + " – "
                    + Recorder.formatTenths(Math.round(v.get(1) * 1000)));
        };
        label.run();
        trim.addOnChangeListener((slider, value, fromUser) -> label.run());
        trim.addOnSliderTouchListener(new RangeSlider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull RangeSlider slider) {
                stopPreview();
            }
            @Override public void onStopTrackingTouch(@NonNull RangeSlider slider) {
                List<Float> v = slider.getValues();
                t.startMs = Math.round(v.get(0) * 1000);
                int endMs = Math.round(v.get(1) * 1000);
                t.endMs = endMs >= Math.round(max * 1000) - 50 ? 0 : endMs;
                if (t.startMs < 50) t.startMs = 0;
                saveTriggers();
            }
        });
    }

    /** «Un sonido / Una voz» (A sound / A voice) selector and the text read aloud (spec 001). */
    private void bindEditorSpeech(Trigger t) {
        if (editorView == null) return;
        View view = editorView;
        MaterialButtonToggleGroup kind = view.findViewById(R.id.ed_kind);
        View soundGroup = view.findViewById(R.id.ed_sound_group);
        View speechGroup = view.findViewById(R.id.ed_speech_group);
        TextInputLayout speechLayout = view.findViewById(R.id.ed_speech_layout);
        EditText speech = view.findViewById(R.id.ed_speech);
        MaterialSwitch thenSound = view.findViewById(R.id.ed_then_sound);

        final Runnable show = () -> showEditorGroups(t);
        kind.check(t.isAction() ? R.id.ed_kind_action : t.speak ? R.id.ed_kind_voice : R.id.ed_kind_sound);
        kind.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean voice = id == R.id.ed_kind_voice;
            // Switching to «Una acción» (An action) proposes calling: it is the most requested action (spec 008).
            String action = id == R.id.ed_kind_action ? (t.isAction() ? t.action : Trigger.CALL) : "";
            if (action.equals(t.action) && (t.isAction() || voice == t.speak)) return;
            stopPreview();
            t.action = action;
            if (action.isEmpty()) t.speak = voice;
            saveTriggers();
            ((RadioGroup) view.findViewById(R.id.ed_actions)).check(actionRadio(t.action));
            show.run();
            if (voice && speech.length() == 0) speech.requestFocus();
        });
        thenSound.setChecked(t.thenSound);
        thenSound.setOnCheckedChangeListener((b, on) -> {
            if (on == t.thenSound) return;
            stopPreview();
            t.thenSound = on;
            saveTriggers();
            show.run();
        });

        speech.setSaveEnabled(false);
        speech.setText(t.speech);
        speech.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                t.speech = s.toString().trim();
                speechLayout.setError(null);
                saveTriggers();
            }
        });

        // One button per data field: «+ hora» (+ time) inserts {hora} at the cursor. In English, {time}:
        // SpokenText understands both names in both languages.
        ChipGroup fields = view.findViewById(R.id.ed_fields);
        fields.removeAllViews();
        boolean en = Lang.en();
        String[][] options = {
                {getString(R.string.field_time), en ? "{time}" : "{hora}"},
                {getString(R.string.field_day), en ? "{day}" : "{día}"},
                {getString(R.string.field_date), en ? "{date}" : "{fecha}"},
                {getString(R.string.field_greeting), en ? "{greeting}" : "{saludo}"}};
        for (String[] option : options) {
            Chip chip = new Chip(this);
            chip.setText("+ " + option[0]);
            chip.setOnClickListener(v -> insertAtCursor(speech, option[1]));
            fields.addView(chip);
        }
        Chip visit = new Chip(this);
        visit.setText(R.string.chip_visit);
        visit.setOnClickListener(v -> askVisitField(speech));
        fields.addView(visit);
        Chip dose = new Chip(this);
        dose.setText(R.string.chip_dose);
        dose.setOnClickListener(v -> askDoseField(speech));
        fields.addView(dose);
        view.findViewById(R.id.ed_speech_play).setOnClickListener(v -> togglePreview(triggers.indexOf(t)));
    }

    /** Which parts of the editor are shown for the response: sound, voice (and a recording afterwards) or action. */
    private void showEditorGroups(Trigger t) {
        if (editorView == null) return;
        View v = editorView;
        boolean action = t.isAction();
        // With «Después, que suene una grabación» (Then play a recording) both voice and sound are shown (spec 014).
        v.findViewById(R.id.ed_sound_group).setVisibility(!action && (!t.speak || t.thenSound) ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_speech_group).setVisibility(!action && t.speak ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_action_group).setVisibility(action ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_sched_section).setVisibility(action ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.ed_call_group).setVisibility(Trigger.CALL.equals(t.action) ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_light_hint).setVisibility(
                Trigger.LIGHT_ON.equals(t.action) || Trigger.LIGHT_OFF.equals(t.action) ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_dose_act_layout).setVisibility(Trigger.DOSE.equals(t.action) ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.ed_help_group).setVisibility(Trigger.HELP.equals(t.action) ? View.VISIBLE : View.GONE);
        MaterialButton contact = v.findViewById(R.id.ed_contact);
        contact.setText(t.contacts.isEmpty() ? getString(R.string.elegir_contacto)
                : t.contacts.get(0).name + " · " + t.contacts.get(0).number);
        MaterialButton help1 = v.findViewById(R.id.ed_help_contact1);
        help1.setText(t.contacts.isEmpty() ? getString(R.string.elegir_a_quien_avisar)
                : getString(R.string.alert_who, t.contacts.get(0).name));
        MaterialButton help2 = v.findViewById(R.id.ed_help_contact2);
        help2.setVisibility(t.contacts.isEmpty() ? View.GONE : View.VISIBLE);
        help2.setText(t.contacts.size() < 2 ? getString(R.string.anadir_un_segundo_contacto)
                : getString(R.string.and_who, t.contacts.get(1).name));
        // The dose name is the same in the action and in the reminder.
        syncText(v.findViewById(R.id.ed_dose_act), t.doseName);
        syncText(v.findViewById(R.id.ed_dose), t.doseName);
    }

    private static void syncText(EditText field, String text) {
        if (!field.getText().toString().trim().equals(text)) field.setText(text);
    }

    private static final int[] ACTION_RADIOS = {
            R.id.ed_act_call, R.id.ed_act_light_on, R.id.ed_act_light_off, R.id.ed_act_dose, R.id.ed_act_help};
    private static final String[] ACTION_NAMES = {
            Trigger.CALL, Trigger.LIGHT_ON, Trigger.LIGHT_OFF, Trigger.DOSE, Trigger.HELP};

    private static int actionRadio(String action) {
        for (int i = 0; i < ACTION_NAMES.length; i++) {
            if (ACTION_NAMES[i].equals(action)) return ACTION_RADIOS[i];
        }
        return -1;
    }

    /** Voice actions (spec 008): which one, who it calls and which dose it logs. */
    private void bindEditorAction(Trigger t) {
        if (editorView == null) return;
        View view = editorView;
        RadioGroup radios = view.findViewById(R.id.ed_actions);
        radios.check(actionRadio(t.action));
        radios.setOnCheckedChangeListener((g, id) -> {
            String action = "";
            for (int i = 0; i < ACTION_RADIOS.length; i++) {
                if (ACTION_RADIOS[i] == id) action = ACTION_NAMES[i];
            }
            if (action.isEmpty() || action.equals(t.action)) return;
            if (Trigger.HELP.equals(action) && !Prefs.helpAccepted(this)) {
                // Spec 009, R5: without accepting the disclaimer, it is not enabled.
                radios.check(actionRadio(t.action));
                askHelpDisclaimer(() -> radios.check(R.id.ed_act_help));
                return;
            }
            stopPreview();
            t.action = action;
            saveTriggers();
            showEditorGroups(t);
        });
        view.findViewById(R.id.ed_contact).setOnClickListener(v -> pickContact(0));
        view.findViewById(R.id.ed_help_contact1).setOnClickListener(v -> pickContact(0));
        view.findViewById(R.id.ed_help_contact2).setOnClickListener(v -> {
            if (t.contacts.size() < 2) {
                pickContact(1);
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.second_contact)
                    .setItems(new String[]{getString(R.string.change), getString(R.string.quitar)}, (d, which) -> {
                        if (which == 0) {
                            pickContact(1);
                        } else {
                            t.contacts = new ArrayList<>(t.contacts.subList(0, 1));
                            saveTriggers();
                            showEditorGroups(t);
                        }
                    })
                    .show();
        });
        EditText person = view.findViewById(R.id.ed_person);
        person.setSaveEnabled(false);
        person.setText(Prefs.personName(this));
        person.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Prefs.setPersonName(MainActivity.this, s.toString().trim());
            }
        });
        EditText dose = view.findViewById(R.id.ed_dose_act);
        dose.setSaveEnabled(false);
        dose.setText(t.doseName);
        dose.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String name = s.toString().replaceAll("[{}|:]", "").trim();
                if (name.equals(t.doseName)) return;
                t.doseName = name;
                saveTriggers();
            }
        });
        view.findViewById(R.id.ed_action_play).setOnClickListener(v -> togglePreview(triggers.indexOf(t)));
    }

    /** Spec 009, R5: the caregiver accepts that this is not an emergency service. */
    private void askHelpDisclaimer(Runnable accepted) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_disclaimer_title)
                .setMessage(R.string.help_disclaimer_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.understand_accept, (d, w) -> {
                    Prefs.setHelpAccepted(this, true);
                    accepted.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Picks a contact with Android's picker: no permission is needed to read the contacts. */
    private void pickContact(int slot) {
        stopPreview();
        contactSlot = slot;
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(i, REQ_CONTACT);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_contacts_app));
        }
    }

    private void setContact(Uri uri) {
        Trigger t = picked();
        if (t == null) return;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return;
            String name = c.getString(0);
            String number = c.getString(1);
            if (number == null || number.trim().isEmpty()) {
                toast(getString(R.string.contact_no_number));
                return;
            }
            List<Trigger.Contact> list = new ArrayList<>(t.contacts);
            Trigger.Contact contact = new Trigger.Contact(name == null ? number : name, number.trim());
            if (contactSlot < list.size()) list.set(contactSlot, contact);
            else list.add(contact);
            t.contacts = list;
            saveTriggers();
            showEditorGroups(t);
        } catch (RuntimeException e) {
            toast(getString(R.string.contact_unreadable));
        }
    }

    /** «+ visita» (+ visit): who is coming and what to say if no visit is scheduled (spec 014). */
    private void askVisitField(EditText speech) {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout nameLayout = view.findViewById(R.id.nf_name_layout);
        TextInputLayout extraLayout = view.findViewById(R.id.nf_extra_layout);
        EditText name = view.findViewById(R.id.nf_name);
        EditText extra = view.findViewById(R.id.nf_extra);
        nameLayout.setHint(getString(R.string.visit_who_hint));
        extraLayout.setHint(getString(R.string.visit_fallback_hint));
        extraLayout.setHelperText(getString(R.string.visit_fallback_helper));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.next_visit)
                .setMessage(R.string.next_visit_msg)
                .setView(view)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String who = name.getText().toString().replaceAll("[{}|:]", "").trim();
            if (who.isEmpty()) {
                nameLayout.setError(getString(R.string.type_who_comes));
                return;
            }
            String fallback = extra.getText().toString().replaceAll("[{}]", "").trim();
            dialog.dismiss();
            insertAtCursor(speech, (Lang.en() ? "{visit:" : "{visita:") + who
                    + (fallback.isEmpty() ? "" : "|" + fallback) + "}");
            if (!Prefs.calendarOn(this) && Prefs.fixedVisits(this).isEmpty()) {
                toast(getString(R.string.visits_missing));
            }
        }));
        dialog.show();
    }

    /** «+ toma» (+ dose): whether today's dose has been logged (spec 007). It never says «tómala» (take it). */
    private void askDoseField(EditText speech) {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout nameLayout = view.findViewById(R.id.nf_name_layout);
        TextInputLayout extraLayout = view.findViewById(R.id.nf_extra_layout);
        EditText name = view.findViewById(R.id.nf_name);
        EditText extra = view.findViewById(R.id.nf_extra);
        nameLayout.setHint(getString(R.string.dose_what_hint));
        List<String> known = DoseLog.knownNames(this);
        if (!known.isEmpty()) {
            name.setText(known.get(0));
            nameLayout.setHelperText(getString(R.string.doses_so_far, String.join(", ", known)));
        } else {
            nameLayout.setHelperText(getString(R.string.dose_same_name));
        }
        extraLayout.setHint(getString(R.string.dose_extra_hint));
        extraLayout.setHelperText(getString(R.string.dose_extra_helper));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dose_field_title)
                .setMessage(R.string.dose_field_msg)
                .setView(view)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String what = name.getText().toString().replaceAll("[{}|:]", "").trim();
            if (what.isEmpty()) {
                nameLayout.setError(getString(R.string.type_which_dose));
                return;
            }
            String more = extra.getText().toString().replaceAll("[{}]", "").trim();
            dialog.dismiss();
            insertAtCursor(speech, (Lang.en() ? "{dose:" : "{toma:") + what
                    + (more.isEmpty() ? "" : "|" + more) + "}");
        }));
        dialog.show();
    }

    private static void insertAtCursor(EditText field, String text) {
        Editable e = field.getText();
        if (e == null) return;
        int a = Math.max(0, field.getSelectionStart());
        int b = Math.max(0, field.getSelectionEnd());
        int from = Math.min(a, b);
        String space = from > 0 && !Character.isWhitespace(e.charAt(from - 1)) ? " " : "";
        e.replace(from, Math.max(a, b), space + text);
        field.requestFocus();
    }

    /** The phone has no voice in the app's language: explains how to install one (spec 001, R9). */
    private void showNoVoice() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.no_voice_title)
                .setMessage(R.string.no_voice_msg)
                .setPositiveButton(R.string.open_voice_settings, (d, w) -> openVoiceSettings())
                .setNegativeButton(R.string.cerrar, null)
                .show();
    }

    private void openVoiceSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            } catch (ActivityNotFoundException ignored) {
                toast(getString(R.string.find_tts));
            }
        }
    }

    private static float tenths(int ms) {
        return Math.round(ms / 100f) / 10f;
    }

    // --- Settings (bottom sheet) ---

    private void openSettings() {
        View view = getLayoutInflater().inflate(R.layout.sheet_settings, null);
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        settingsSheet = sheet;

        Slider volume = view.findViewById(R.id.set_volume);
        volume.setValue(Math.max(0, Math.min(100, Prefs.volume(this))));
        volume.setLabelFormatter(v -> Math.round(v) + "%");
        volume.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) Prefs.setVolume(this, Math.round(v));
        });

        Slider rate = view.findViewById(R.id.set_speech_rate);
        // In steps of 0.05: the slider fails if the value does not land exactly on a step.
        rate.setValue(Math.round(Math.max(0.5f, Math.min(1.5f, Prefs.speechRate(this))) * 20) / 20f);
        rate.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) Prefs.setSpeechRate(this, v);
        });

        Slider strict = view.findViewById(R.id.set_strictness);
        strict.setValue(Math.max(0, Math.min(100, Prefs.strictness(this))));
        strict.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) Prefs.setStrictness(this, Math.round(v));
        });

        MaterialButton battery = view.findViewById(R.id.set_battery);
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            battery.setText(R.string.battery_ok);
            battery.setEnabled(false);
        }
        battery.setOnClickListener(v -> askBatteryExemption());
        if (patientId() != null) {
            // Battery and the microphone notice belong to the listening phone, not the caregiver's.
            battery.setVisibility(View.GONE);
            view.findViewById(R.id.set_listen_note).setVisibility(View.GONE);
        }
        MaterialButton language = view.findViewById(R.id.set_language);
        language.setText(LangPicker.label(this));
        language.setOnClickListener(v -> {
            sheet.dismiss();
            LangPicker.show(this);
        });
        MaterialButton mode = view.findViewById(R.id.set_mode);
        if (patientId() == null && Cloud.available(this)) {
            mode.setVisibility(View.VISIBLE);
            mode.setOnClickListener(v -> {
                sheet.dismiss();
                stopService(new Intent(this, ListenerService.class));
                startActivity(new Intent(this, WelcomeActivity.class));
            });
        }
        view.findViewById(R.id.set_visits).setOnClickListener(v -> {
            sheet.dismiss();
            openVisits();
        });
        view.findViewById(R.id.set_doses).setOnClickListener(v -> {
            sheet.dismiss();
            showDoseLog();
        });
        sheet.setOnDismissListener(d -> settingsSheet = null);
        sheet.show();
    }

    // --- Visit schedule (spec 014) ---

    private void openVisits() {
        View view = getLayoutInflater().inflate(R.layout.sheet_visits, null);
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        visitsSheet = sheet;
        visitsView = view;

        MaterialSwitch calendar = view.findViewById(R.id.vis_calendar);
        if (patientId() != null) {
            // The calendar is read on the listening phone: from here, only recurring visits.
            calendar.setVisibility(View.GONE);
            view.findViewById(R.id.vis_calendar_note).setVisibility(View.GONE);
        }
        calendar.setChecked(Prefs.calendarOn(this) && Agenda.canReadCalendar(this));
        calendar.setOnCheckedChangeListener((b, on) -> {
            if (on && !Agenda.canReadCalendar(this)) {
                b.setChecked(false);
                askCalendarPermission();
                return;
            }
            Prefs.setCalendarOn(this, on);
            renderVisits();
        });
        view.findViewById(R.id.vis_calendars).setOnClickListener(v -> chooseCalendars());
        view.findViewById(R.id.vis_add).setOnClickListener(v -> addFixedVisit());
        view.findViewById(R.id.vis_done).setOnClickListener(v -> sheet.dismiss());
        sheet.setOnDismissListener(d -> {
            if (visitsSheet == sheet) {
                visitsSheet = null;
                visitsView = null;
            }
        });
        renderVisits();
        sheet.show();
    }

    private void renderVisits() {
        if (visitsView == null) return;
        View view = visitsView;
        boolean on = Prefs.calendarOn(this) && Agenda.canReadCalendar(this);
        MaterialSwitch calendar = view.findViewById(R.id.vis_calendar);
        if (calendar.isChecked() != on) calendar.setChecked(on);
        MaterialButton calendars = view.findViewById(R.id.vis_calendars);
        calendars.setVisibility(on && patientId() == null ? View.VISIBLE : View.GONE);
        int chosen = Prefs.calendarIds(this).size();
        calendars.setText(chosen == 0 ? getString(R.string.calendarios_todos)
                : getString(chosen == 1 ? R.string.calendars_n : R.string.calendars_n_plural, chosen));

        LinearLayout list = view.findViewById(R.id.vis_fixed);
        list.removeAllViews();
        List<Agenda.Fixed> fixed = Prefs.fixedVisits(this);
        for (int i = 0; i < fixed.size(); i++) {
            Agenda.Fixed f = fixed.get(i);
            final int index = i;
            View row = getLayoutInflater().inflate(R.layout.item_row, list, false);
            ((TextView) row.findViewById(R.id.row_text)).setText(
                    f.name + " · " + Weekly.describe(f.days) + " · " + clockLabel(f.minute));
            row.findViewById(R.id.row_delete).setOnClickListener(v -> {
                List<Agenda.Fixed> all = Prefs.fixedVisits(this);
                if (index < all.size()) all.remove(index);
                Prefs.setFixedVisits(this, all);
                renderVisits();
            });
            list.addView(row);
        }
        view.findViewById(R.id.vis_fixed_empty).setVisibility(fixed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Before Android's permission prompt, explain what it is for (spec 014, R4). */
    private void askCalendarPermission() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.read_calendar)
                .setMessage(R.string.read_calendar_msg)
                .setPositiveButton(R.string.continue_, (d, w) ->
                        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL_PERM))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseCalendars() {
        List<String[]> calendars = Agenda.calendars(this);
        if (calendars.isEmpty()) {
            toast(getString(R.string.no_calendars));
            return;
        }
        Set<String> chosen = Prefs.calendarIds(this);
        String[] names = new String[calendars.size()];
        boolean[] checked = new boolean[calendars.size()];
        for (int i = 0; i < calendars.size(); i++) {
            names[i] = calendars.get(i)[1];
            checked[i] = chosen.isEmpty() || chosen.contains(calendars.get(i)[0]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.which_calendars)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.guardar, (d, w) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) ids.add(calendars.get(i)[0]);
                    }
                    if (ids.isEmpty()) {
                        toast(getString(R.string.choose_one_calendar));
                        return;
                    }
                    // All checked is saved as "all", so a new calendar is included too.
                    Prefs.setCalendarIds(this, ids.size() == calendars.size() ? new HashSet<>() : ids);
                    renderVisits();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addFixedVisit() {
        View view = getLayoutInflater().inflate(R.layout.dialog_fixed_visit, null);
        EditText name = view.findViewById(R.id.fv_name);
        MaterialButton time = view.findViewById(R.id.fv_time);
        final int[] minute = {13 * 60};
        setDays(view, Weekly.bit(Calendar.SUNDAY));
        time.setText(getString(R.string.time_label, clockLabel(minute[0])));
        time.setOnClickListener(v -> pickTime(getString(R.string.visit_time), minute[0], m -> {
            minute[0] = m;
            time.setText(getString(R.string.time_label, clockLabel(m)));
        }));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fixed_visit)
                .setView(view)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String who = name.getText().toString().replaceAll("[{}|:]", "").trim();
            int days = getDays(view);
            if (who.isEmpty()) {
                toast(getString(R.string.type_who_comes));
                return;
            }
            if (days == 0) {
                toast(getString(R.string.choose_one_day));
                return;
            }
            List<Agenda.Fixed> all = Prefs.fixedVisits(this);
            all.add(new Agenda.Fixed(who, days, minute[0]));
            Prefs.setFixedVisits(this, all);
            dialog.dismiss();
            renderVisits();
        }));
        dialog.show();
    }

    // --- Reminder at a fixed time (spec 006) ---

    private void bindEditorSchedule(Trigger t) {
        if (editorView == null) return;
        View view = editorView;
        MaterialSwitch on = view.findViewById(R.id.ed_sched);
        View group = view.findViewById(R.id.ed_sched_group);
        MaterialButton time = view.findViewById(R.id.ed_sched_time);
        MaterialSwitch confirm = view.findViewById(R.id.ed_confirm);
        View doseLayout = view.findViewById(R.id.ed_dose_layout);
        EditText dose = view.findViewById(R.id.ed_dose);
        MaterialSwitch silent = view.findViewById(R.id.ed_even_silent);

        final Runnable show = () -> {
            group.setVisibility(t.atMinute >= 0 ? View.VISIBLE : View.GONE);
            doseLayout.setVisibility(t.confirm ? View.VISIBLE : View.GONE);
            if (t.atMinute >= 0) time.setText(getString(R.string.time_label, clockLabel(t.atMinute)));
        };
        setDays(group, t.days);
        for (int id : DAY_CHIPS) {
            Chip chip = group.findViewById(id);
            chip.setOnCheckedChangeListener((b, checked) -> {
                int days = getDays(group);
                if (days == t.days) return;
                t.days = days;
                saveTriggers();
                if (days == 0) toast(getString(R.string.choose_one_day_ring));
            });
        }
        on.setChecked(t.atMinute >= 0);
        on.setOnCheckedChangeListener((b, checked) -> {
            if (checked == (t.atMinute >= 0)) return;
            t.atMinute = checked ? 9 * 60 : -1;
            if (checked && (t.days & Weekly.EVERY_DAY) == 0) {
                t.days = Weekly.EVERY_DAY;
                setDays(group, t.days);
            }
            saveTriggers();
            show.run();
            if (checked && patientId() == null) checkReminderPermissions();
        });
        time.setOnClickListener(v -> pickTime(getString(R.string.reminder_time), t.atMinute, m -> {
            t.atMinute = m;
            saveTriggers();
            show.run();
        }));
        confirm.setChecked(t.confirm);
        confirm.setOnCheckedChangeListener((b, checked) -> {
            if (checked == t.confirm) return;
            t.confirm = checked;
            saveTriggers();
            show.run();
            if (checked && patientId() == null) checkFullScreen();
        });
        dose.setSaveEnabled(false);
        dose.setText(t.doseName);
        dose.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                t.doseName = s.toString().replaceAll("[{}|:]", "").trim();
                saveTriggers();
            }
        });
        silent.setChecked(t.evenSilent);
        silent.setOnCheckedChangeListener((b, checked) -> {
            t.evenSilent = checked;
            saveTriggers();
        });
        show.run();
    }

    /** What a reminder needs to ring on time and show «Hecho» (Done). */
    private void checkReminderPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERM);
        }
        if (!Reminders.canScheduleExact(this) && Build.VERSION.SDK_INT >= 31) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.allow_alarms)
                    .setMessage(R.string.allow_alarms_msg)
                    .setPositiveButton(R.string.open_settings, (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.fromParts("package", getPackageName(), null)));
                        } catch (ActivityNotFoundException e) {
                            openAppSettings();
                        }
                    })
                    .setNegativeButton(R.string.not_now, null)
                    .show();
        }
    }

    /** Since Android 14, showing «Hecho» (Done) full screen needs a separate permission. */
    private void checkFullScreen() {
        if (Build.VERSION.SDK_INT < 34) return;
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm == null || nm.canUseFullScreenIntent()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.full_screen)
                .setMessage(R.string.full_screen_msg)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.fromParts("package", getPackageName(), null)));
                    } catch (ActivityNotFoundException e) {
                        openAppSettings();
                    }
                })
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    // --- Dose log (spec 007) ---

    private void showDoseLog() {
        List<DoseLog.Entry> all = DoseLog.entries(this);
        Calendar now = Calendar.getInstance();
        StringBuilder text = new StringBuilder();
        for (int i = all.size() - 1; i >= 0; i--) {
            DoseLog.Entry e = all.get(i);
            Calendar when = Calendar.getInstance();
            when.setTimeInMillis(e.at);
            int ago = daysAgo(now, when);
            if (ago > 6) break;
            String day = ago == 0 ? getString(R.string.today) : ago == 1 ? getString(R.string.yesterday)
                    : new java.text.SimpleDateFormat(Lang.en() ? "EEEE, MMMM d" : "EEEE d", Lang.locale(this))
                            .format(when.getTime());
            String by = getString(DoseLog.BY_CAREGIVER.equals(e.by) ? R.string.dose_by_caregiver
                    : DoseLog.BY_BUTTON.equals(e.by) ? R.string.dose_by_button : R.string.dose_by_voice);
            text.append(day).append(", ")
                    .append(clockLabel(when.get(Calendar.HOUR_OF_DAY) * 60 + when.get(Calendar.MINUTE)))
                    .append(" · ").append(e.name).append(" (").append(by).append(")\n");
        }
        if (text.length() == 0) text.append(getString(R.string.no_doses_7d));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.doses_7d)
                .setMessage(text.toString().trim())
                .setPositiveButton(R.string.log_a_dose, (d, w) -> annotateDose())
                .setNegativeButton(R.string.cerrar, null)
                .show();
    }

    private static int daysAgo(Calendar now, Calendar then) {
        Calendar a = (Calendar) now.clone();
        Calendar b = (Calendar) then.clone();
        for (Calendar c : new Calendar[]{a, b}) {
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
        }
        return (int) Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86_400_000.0);
    }

    /** The caregiver logs a dose now, choosing the name or typing a new one (spec 007, R2). */
    private void annotateDose() {
        List<String> names = DoseLog.knownNames(this);
        if (names.isEmpty()) {
            annotateNewDose();
            return;
        }
        String[] items = new String[names.size() + 1];
        for (int i = 0; i < names.size(); i++) items[i] = names.get(i);
        items[names.size()] = getString(R.string.other);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.which_dose_now)
                .setItems(items, (d, which) -> {
                    if (which == names.size()) {
                        annotateNewDose();
                    } else {
                        DoseLog.add(this, names.get(which), DoseLog.BY_CAREGIVER);
                        toast(getString(R.string.logged_dose, names.get(which)));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void annotateNewDose() {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout nameLayout = view.findViewById(R.id.nf_name_layout);
        view.findViewById(R.id.nf_extra_layout).setVisibility(View.GONE);
        EditText name = view.findViewById(R.id.nf_name);
        nameLayout.setHint(getString(R.string.dose_what_hint));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_a_dose)
                .setView(view)
                .setPositiveButton(R.string.log, (d, w) -> {
                    String what = name.getText().toString().replaceAll("[{}|:]", "").trim();
                    if (what.isEmpty()) return;
                    DoseLog.add(this, what, DoseLog.BY_CAREGIVER);
                    toast(getString(R.string.logged_dose, what));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --- Days and times (recurring visits and reminders) ---

    private interface MinutePicked {
        void picked(int minuteOfDay);
    }

    private void pickTime(String title, int minuteOfDay, MinutePicked done) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(android.text.format.DateFormat.is24HourFormat(this)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(minuteOfDay / 60)
                .setMinute(minuteOfDay % 60)
                .setTitleText(title)
                .build();
        picker.addOnPositiveButtonClickListener(v -> done.picked(picker.getHour() * 60 + picker.getMinute()));
        picker.show(getSupportFragmentManager(), "hora");
    }

    /** The time as this phone shows it: «13:00» or «1:00 p. m.». */
    private String clockLabel(int minuteOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        c.set(Calendar.MINUTE, minuteOfDay % 60);
        return android.text.format.DateFormat.getTimeFormat(this).format(c.getTime());
    }

    private static void setDays(View root, int days) {
        for (int i = 0; i < DAY_CHIPS.length; i++) {
            Chip chip = root.findViewById(DAY_CHIPS[i]);
            chip.setChecked((days & Weekly.bit(Weekly.MONDAY_FIRST[i])) != 0);
        }
    }

    private static int getDays(View root) {
        int days = 0;
        for (int i = 0; i < DAY_CHIPS.length; i++) {
            Chip chip = root.findViewById(DAY_CHIPS[i]);
            if (chip.isChecked()) days |= Weekly.bit(Weekly.MONDAY_FIRST[i]);
        }
        return days;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        AppState.addListener(this);
        onAppStateChanged();
    }

    @Override
    protected void onPause() {
        pushConfig();
        resumed = false;
        AppState.removeListener(this);
        stopPreview();
        stopPulse();
        if (recordDialog != null) recordDialog.dismiss();
        if (dictateDialog != null) dictateDialog.dismiss();
        super.onPause();
    }

    // --- Start listening: phrase → permissions → model → service ---

    private void beginStart() {
        boolean any = false;
        boolean needCall = false;
        boolean needSms = false;
        for (Trigger t : triggers) {
            String phrase = Texts.forGrammar(t.phrase);
            if (Texts.variants(t.phrase).isEmpty()) continue;
            any = true;
            String name = getString(R.string.quoted, Texts.variants(t.phrase).get(0));
            if (Trigger.CALL.equals(t.action)) {
                if (t.contacts.isEmpty()) {
                    toast(getString(R.string.start_call_no_contact, name));
                    pendingStart = false;
                    return;
                }
                for (String form : Texts.variants(t.phrase)) {
                    if (form.split(" ").length < 2) {
                        toast(getString(R.string.start_call_two_words, t.contacts.get(0).name, form));
                        pendingStart = false;
                        return;
                    }
                }
                needCall = true;
            }
            if (Trigger.HELP.equals(t.action)) {
                if (t.contacts.isEmpty()) {
                    toast(getString(R.string.start_help_no_contact, name));
                    pendingStart = false;
                    return;
                }
                if (!Prefs.helpAccepted(this)) {
                    toast(getString(R.string.start_help_accept, name));
                    pendingStart = false;
                    return;
                }
                needSms = true;
                // If the SMS does not go out, the first contact is called.
                needCall = true;
            }
            if (Trigger.DOSE.equals(t.action) && t.doseName.isEmpty()) {
                toast(getString(R.string.start_dose_no_name, name));
                pendingStart = false;
                return;
            }
            if (!t.isAction() && t.speak && t.speech.trim().isEmpty()) {
                toast(getString(R.string.start_speech_missing, name));
                pendingStart = false;
                return;
            }
            if (Texts.hasDigits(phrase)) {
                toast(getString(R.string.start_digits));
                pendingStart = false;
                return;
            }
        }
        if (!any) {
            toast(getString(R.string.start_no_phrases));
            pendingStart = false;
            return;
        }
        saveTriggers();

        List<String> perms = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needCall && !askedCall
                && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CALL_PHONE);
        }
        if (needSms && !askedSms && !HelpAlert.canSendSms(this)) {
            perms.add(Manifest.permission.SEND_SMS);
        }
        if (!perms.isEmpty()) {
            askedNotifications = true;
            askedCall = askedCall || needCall;
            askedSms = askedSms || needSms;
            pendingStart = true;
            requestPermissions(perms.toArray(new String[0]), REQ_PERMS);
            return;
        }

        if (!ModelManager.isReady(this)) {
            pendingStart = true;
            ModelManager.download(this);
            onAppStateChanged();
            return;
        }

        pendingStart = false;
        try {
            startForegroundService(new Intent(this, ListenerService.class));
        } catch (Exception e) {
            toast(getString(R.string.start_failed, e.getMessage()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_NOTIF_PERM) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                toast(getString(R.string.no_notif_permission));
            }
            return;
        }
        if (requestCode == REQ_CAL_PERM) {
            if (Agenda.canReadCalendar(this)) {
                Prefs.setCalendarOn(this, true);
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                toast(getString(R.string.calendar_permission_settings));
                openAppSettings();
            } else {
                toast(getString(R.string.calendar_permission_denied));
            }
            renderVisits();
            return;
        }
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_DICT_PERM) {
            if (mic) openDictation();
            else toast(getString(R.string.dictate_needs_mic));
            return;
        }
        if (requestCode == REQ_REC_PERM) {
            if (mic) openRecorder();
            else toast(getString(R.string.record_needs_mic));
            return;
        }
        if (requestCode != REQ_PERMS) return;
        if (mic) {
            if (pendingStart) beginStart();
            return;
        }
        pendingStart = false;
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            toast(getString(R.string.mic_permission_settings));
            openAppSettings();
        } else {
            toast(getString(R.string.mic_denied_phrase));
        }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    // --- Screen ---

    @Override
    public void onAppStateChanged() {
        boolean active = AppState.isActive();
        boolean listening = AppState.mode == AppState.Mode.LISTENING;
        boolean downloading = ModelManager.downloading;

        toggle.setEnabled(!downloading);
        toggle.setIconResource(active ? R.drawable.ic_stop_round : R.drawable.ic_mic);
        toggle.setContentDescription(getString(active ? R.string.stop_listening : R.string.empezar_a_escuchar));
        toggle.setBackgroundTintList(ColorStateList.valueOf(
                getColor(active ? R.color.danger : R.color.accent)));
        if (active || downloading) addButton.hide(); else addButton.show();
        silence.setVisibility(AppState.playing ? View.VISIBLE : View.GONE);

        String title;
        String detail;
        if (downloading) {
            progress.setVisibility(View.VISIBLE);
            boolean indeterminate = ModelManager.unpacking || ModelManager.progress < 0;
            if (indeterminate != progress.isIndeterminate()) {
                progress.setVisibility(View.INVISIBLE);
                progress.setIndeterminate(indeterminate);
                progress.setVisibility(View.VISIBLE);
            }
            progress.setProgressCompat(Math.max(0, ModelManager.progress), true);
            title = getString(R.string.listen_preparing);
            detail = ModelManager.unpacking
                    ? getString(R.string.installing_recognition)
                    : getString(R.string.downloading_recognition, Math.max(0, ModelManager.progress));
        } else {
            progress.setVisibility(View.GONE);
            if (ModelManager.error != null && !active && pendingStart) pendingStart = false;
            if (AppState.playing) {
                title = getString(R.string.playing_now);
                detail = getString(R.string.quoted, AppState.lastPhrase);
            } else if (listening) {
                title = getString(R.string.listening);
                String prefix = getString(R.string.listen_listening, "");
                detail = AppState.message.startsWith(prefix)
                        ? AppState.message.substring(prefix.length()) : AppState.message;
            } else if (active) {
                title = getString(R.string.listen_preparing);
                detail = AppState.message;
            } else if (AppState.mode == AppState.Mode.ERROR) {
                title = getString(R.string.failed);
                detail = AppState.message;
            } else if (ModelManager.error != null) {
                title = getString(R.string.failed);
                detail = ModelManager.error;
            } else {
                title = getString(R.string.paused);
                detail = getString(ModelManager.isReady(this)
                        ? R.string.tap_mic_to_start : R.string.tap_mic_first_time);
            }
        }
        status.setText(title);
        statusDetail.setText(detail);
        statusDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);

        if (AppState.detections > 0) {
            String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(AppState.lastDetectionAt));
            stats.setText(AppState.detections == 1 ? getString(R.string.stats_once, when)
                    : getString(R.string.stats_many, AppState.detections, when));
            stats.setVisibility(View.VISIBLE);
        } else {
            stats.setVisibility(View.GONE);
        }

        if (listening && resumed) startPulse(); else stopPulse();

        // If the model finished downloading while we were waiting, continue.
        if (pendingStart && resumed && !downloading && ModelManager.isReady(this)) {
            beginStart();
        }
    }

    private void startPulse() {
        if (pulseAnim != null) return;
        pulseAnim = new AnimatorSet();
        pulseAnim.playTogether(pulse(pulse1, 0), pulse(pulse2, 900));
        pulseAnim.start();
    }

    private static AnimatorSet pulse(View v, long delay) {
        v.setVisibility(View.VISIBLE);
        ObjectAnimator sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.55f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.55f);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 0.6f, 0f);
        for (ObjectAnimator o : new ObjectAnimator[]{sx, sy, a}) {
            o.setRepeatCount(ValueAnimator.INFINITE);
            o.setDuration(1800);
            o.setStartDelay(delay);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, a);
        return set;
    }

    private void stopPulse() {
        if (pulseAnim != null) {
            pulseAnim.cancel();
            pulseAnim = null;
        }
        for (View v : new View[]{pulse1, pulse2}) {
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setAlpha(0f);
        }
    }

    // --- Sound ---

    private Trigger picked() {
        return pickIndex >= 0 && pickIndex < triggers.size() ? triggers.get(pickIndex) : null;
    }

    private void setSound(Uri uri, String name) {
        Trigger t = picked();
        if (t == null) return;
        if (t.soundUri != null && !t.soundUri.equals(uri)) deleteRecording(t.soundUri);
        t.soundUri = uri;
        t.soundName = name;
        t.startMs = 0;
        t.endMs = 0;
        saveTriggers();
        if (editorSheet != null && editorSheet.isShowing()) {
            bindEditorSound(t);
        } else {
            renderTriggers();
        }
        togglePreview(triggers.indexOf(t));
    }

    /** Deletes the file if it is a recording made in the app (ringtones and the user's files are left alone). */
    private void deleteRecording(Uri uri) {
        if (uri == null || !"file".equals(uri.getScheme()) || uri.getPath() == null) return;
        File f = new File(uri.getPath());
        if (Recorder.dir(this).equals(f.getParentFile())) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // --- Dictating the phrase: speech to text with the same offline recognizer ---

    private void openDictation() {
        final Trigger t = picked();
        if (t == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_DICT_PERM);
            return;
        }
        if (!ModelManager.isReady(this)) {
            toast(getString(R.string.download_first));
            ModelManager.download(this);
            onAppStateChanged();
            return;
        }
        stopPreview();

        View view = getLayoutInflater().inflate(R.layout.dialog_dictate, null);
        final TextView heard = view.findViewById(R.id.dict_heard);
        final TextView hint = view.findViewById(R.id.dict_hint);
        final String[] result = {""};
        final Object[] engine = new Object[3]; // Model, Recognizer, SpeechService
        final boolean[] closed = {false};
        final Handler handler = new Handler(Looper.getMainLooper());

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.say_the_phrase)
                .setView(view)
                .setPositiveButton(R.string.add, (d, w) -> {
                    String said = result[0].trim();
                    if (said.isEmpty()) return;
                    String phrase;
                    List<String> current = Texts.variants(t.phrase);
                    if (current.isEmpty()) phrase = said;
                    else if (!current.contains(Texts.forGrammar(said))) phrase = t.phrase.trim() + ", " + said;
                    else phrase = t.phrase;
                    if (editorView != null) {
                        EditText field = editorView.findViewById(R.id.ed_phrase);
                        field.setText(phrase);
                        field.setSelection(field.length());
                    } else {
                        t.phrase = phrase;
                        saveTriggers();
                        renderTriggers();
                    }
                })
                .setNeutralButton(R.string.repeat, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        final org.vosk.android.RecognitionListener listener = new org.vosk.android.RecognitionListener() {
            @Override public void onPartialResult(String json) {
                String p = jsonField(json, "partial");
                if (!p.isEmpty()) heard.setText(p);
            }
            @Override public void onResult(String json) {
                onFinalResult(json);
            }
            @Override public void onFinalResult(String json) {
                String text = jsonField(json, "text");
                if (text.isEmpty() || !result[0].isEmpty()) return;
                result[0] = text;
                heard.setText(text);
                hint.setText(R.string.is_that_what_you_said);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                org.vosk.android.SpeechService sp = (org.vosk.android.SpeechService) engine[2];
                if (sp != null) sp.setPause(true);
            }
            @Override public void onError(Exception e) {
                hint.setText(getString(R.string.mic_failed, e.getMessage()));
            }
            @Override public void onTimeout() {}
        };

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                org.vosk.android.SpeechService sp = (org.vosk.android.SpeechService) engine[2];
                if (sp == null) return;
                result[0] = "";
                heard.setText("");
                hint.setText(R.string.listening_say_phrase);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                sp.reset();
                sp.setPause(false);
            });
        });
        dialog.setOnDismissListener(d -> {
            closed[0] = true;
            releaseDictation(engine);
            dictateDialog = null;
        });
        dictateDialog = dialog;
        dialog.show();

        final File dir = ModelManager.modelDir(this);
        new Thread(() -> {
            try {
                org.vosk.Model m = new org.vosk.Model(dir.getAbsolutePath());
                org.vosk.Recognizer r = new org.vosk.Recognizer(m, 16000f);
                handler.post(() -> {
                    engine[0] = m;
                    engine[1] = r;
                    if (closed[0]) {
                        releaseDictation(engine);
                        return;
                    }
                    try {
                        org.vosk.android.SpeechService sp = new org.vosk.android.SpeechService(r, 16000f);
                        engine[2] = sp;
                        sp.startListening(listener);
                        hint.setText(R.string.listening_say_phrase);
                    } catch (Exception e) {
                        hint.setText(R.string.listen_mic_busy);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> hint.setText(R.string.recognition_load_failed));
            }
        }, "dictado").start();
    }

    private static void releaseDictation(Object[] engine) {
        if (engine[2] != null) {
            org.vosk.android.SpeechService sp = (org.vosk.android.SpeechService) engine[2];
            sp.stop();
            sp.shutdown();
            engine[2] = null;
        }
        if (engine[1] != null) {
            ((org.vosk.Recognizer) engine[1]).close();
            engine[1] = null;
        }
        if (engine[0] != null) {
            ((org.vosk.Model) engine[0]).close();
            engine[0] = null;
        }
    }

    private static String jsonField(String json, String key) {
        try {
            return new org.json.JSONObject(json).optString(key, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // --- Recording a sound ---

    private void openRecorder() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_REC_PERM);
            return;
        }
        stopPreview();

        final View view = getLayoutInflater().inflate(R.layout.dialog_record, null);
        final TextView time = view.findViewById(R.id.rec_time);
        final TextView hint = view.findViewById(R.id.rec_hint);
        final MaterialButton toggleRec = view.findViewById(R.id.rec_toggle);
        final MaterialButton play = view.findViewById(R.id.rec_play);
        final Recorder recorder = new Recorder();
        final SoundPlayer player = new SoundPlayer();
        final File tmp = new File(Recorder.dir(this), "nueva.m4a");
        final long[] recordedMs = {0};
        final Handler handler = new Handler(Looper.getMainLooper());
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();

        final Runnable[] tick = new Runnable[1];
        final Runnable stopRec = () -> {
            if (!recorder.isRecording()) return;
            long ms = recorder.elapsedMs();
            boolean ok = recorder.stop();
            handler.removeCallbacks(tick[0]);
            toggleRec.setText(R.string.record_again);
            if (ok && tmp.length() > 0) {
                recordedMs[0] = ms;
                time.setText(Recorder.format(ms));
                hint.setText(R.string.record_ready);
                play.setEnabled(true);
            } else {
                recordedMs[0] = 0;
                time.setText("0:00");
                hint.setText(R.string.record_too_short);
            }
            updateRecordDialogButtons(recordedMs[0] > 0);
        };
        tick[0] = () -> {
            time.setText(Recorder.format(recorder.elapsedMs()));
            handler.postDelayed(tick[0], 200);
        };

        toggleRec.setOnClickListener(v -> {
            if (recorder.isRecording()) {
                stopRec.run();
                return;
            }
            player.stop();
            try {
                recorder.start(this, tmp, () -> handler.post(stopRec));
            } catch (Exception e) {
                toast(getString(R.string.record_mic_failed));
                return;
            }
            play.setEnabled(false);
            play.setText(R.string.escuchar_la_grabacion);
            updateRecordDialogButtons(false);
            toggleRec.setText(R.string.finish_rec);
            hint.setText(R.string.recording_hint);
            handler.post(tick[0]);
        });

        play.setOnClickListener(v -> {
            if (player.isPlaying()) {
                player.stop();
                return;
            }
            play.setText(R.string.stop_play);
            player.play(this, Uri.fromFile(tmp), () -> play.setText(R.string.escuchar_la_grabacion));
        });

        recordDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.record_a_sound)
                .setView(view)
                .setPositiveButton(R.string.use, (d, w) -> {
                    player.stop();
                    File dest = new File(Recorder.dir(this), "grabacion-" + System.currentTimeMillis() + ".m4a");
                    if (tmp.renameTo(dest)) {
                        setSound(Uri.fromFile(dest), getString(R.string.my_recording, Recorder.format(recordedMs[0])));
                    } else {
                        toast(getString(R.string.record_save_failed));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        recordDialog.setOnDismissListener(d -> {
            handler.removeCallbacks(tick[0]);
            recorder.stop();
            player.stop();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            recordDialog = null;
        });
        recordDialog.show();
        updateRecordDialogButtons(false);
    }

    private void updateRecordDialogButtons(boolean canUse) {
        if (recordDialog == null) return;
        android.widget.Button use = recordDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (use != null) use.setEnabled(canUse);
    }

    // --- Choosing a ringtone or a file ---

    private void pickTone() {
        Trigger t = picked();
        stopPreview();
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.choose_sound));
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, t == null ? null : t.soundUri);
        try {
            startActivityForResult(i, REQ_TONE);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_tone_picker));
        }
    }

    private void pickFile() {
        stopPreview();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_file_picker));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_CONTACT) {
            if (data.getData() != null) setContact(data.getData());
            return;
        }
        if (requestCode == REQ_TONE) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) return;
            String name = getString(R.string.phone_tone);
            try {
                Ringtone r = RingtoneManager.getRingtone(this, uri);
                if (r != null) name = r.getTitle(this);
            } catch (Exception ignored) {
            }
            if (patientId() != null) uri = copyForPatient(uri, name);
            if (uri != null) setSound(uri, name);
        } else if (requestCode == REQ_FILE) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                // So the file can be read later, even after the phone restarts.
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            String name = displayName(uri);
            if (patientId() != null) uri = copyForPatient(uri, name);
            if (uri != null) setSound(uri, name);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.audio_file);
    }

    // --- Battery ---

    private void askBatteryExemption() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast(getString(R.string.battery_done));
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                toast(getString(R.string.battery_find));
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
