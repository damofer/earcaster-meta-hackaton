package app.oido;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Patient mode (spec 015, R4–R5): link once with the caregiver's code and, after that, a screen
 * with no menus showing the time, the day and the date. To exit, hold the corner for 3 seconds and enter the PIN.
 */
public class PatientActivity extends AppCompatActivity implements AppState.Listener {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private static final int REQ_PERMS = 1;
    /** Just linked: the PIN must be chosen. */
    private static final String EXTRA_ASK_PIN = "ask_pin";
    private static final long CORNER_HOLD_MS = 3000;
    private static final int PIN_TRIES = 3;
    private static final long PIN_LOCK_MS = 60_000;

    private View linkPanel;
    private View mainPanel;
    private TextView linkStatus;
    private TextView day;
    private TextView date;
    private TextView status;
    private MaterialButton start;
    private MaterialButton silence;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            renderDate();
            handler.postDelayed(this, 30_000);
        }
    };
    private final Runnable cornerHeld = this::askPin;
    private boolean syncing = false;
    private boolean pendingStart = false;
    private boolean linking = false;
    private boolean wasLinked = false;

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient);

        linkPanel = findViewById(R.id.pa_link_panel);
        mainPanel = findViewById(R.id.pa_main_panel);
        linkStatus = findViewById(R.id.pa_link_status);
        day = findViewById(R.id.pa_day);
        date = findViewById(R.id.pa_date);
        status = findViewById(R.id.pa_status);
        start = findViewById(R.id.pa_start);
        silence = findViewById(R.id.pa_silence);

        findViewById(R.id.pa_scan).setOnClickListener(v -> scan());
        findViewById(R.id.pa_type).setOnClickListener(v -> typeCode());
        findViewById(R.id.pa_back).setOnClickListener(v -> back());
        start.setOnClickListener(v -> beginStart());
        silence.setOnClickListener(v -> startService(
                new Intent(this, ListenerService.class).setAction(ListenerService.ACTION_SILENCE)));

        findViewById(R.id.pa_corner).setOnTouchListener((v, e) -> {
            if (!linked()) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(cornerHeld, CORNER_HOLD_MS);
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(cornerHeld);
            }
            return true;
        });

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_ASK_PIN, false)) askNewPin();
    }

    private boolean linked() {
        return Prefs.linkedPatient(this) != null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (linked() && !syncing) {
            PatientSync.start(this);
            syncing = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppState.addListener(this);
        handler.post(clock);
        onAppStateChanged();
    }

    @Override
    protected void onPause() {
        AppState.removeListener(this);
        handler.removeCallbacks(clock);
        handler.removeCallbacks(cornerHeld);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (syncing) {
            PatientSync.stop();
            syncing = false;
        }
        super.onStop();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Once linked, "back" does not close the screen (spec 004).
        if (!linked()) back();
    }

    private void back() {
        if (!isTaskRoot()) {
            finish();
            return;
        }
        Prefs.setMode(this, Prefs.MODE_UNSET);
        WelcomeActivity.open(this);
    }

    @Override
    public void onAppStateChanged() {
        if (isFinishing()) return;
        boolean isLinked = linked();
        if (wasLinked && !isLinked) {
            // It was unlinked while the screen was open: PatientSync already deleted everything.
            WelcomeActivity.open(this);
            return;
        }
        wasLinked = isLinked;
        linkPanel.setVisibility(isLinked ? View.GONE : View.VISIBLE);
        mainPanel.setVisibility(isLinked ? View.VISIBLE : View.GONE);
        if (!isLinked) return;

        boolean active = AppState.isActive();
        boolean downloading = ModelManager.downloading;
        silence.setVisibility(AppState.playing ? View.VISIBLE : View.GONE);
        start.setVisibility(!active && !AppState.playing ? View.VISIBLE : View.GONE);
        start.setEnabled(!downloading);
        String text;
        if (downloading) {
            text = ModelManager.unpacking ? getString(R.string.listen_preparing)
                    : getString(R.string.preparing_pct, Math.max(0, ModelManager.progress));
        } else if (AppState.playing) {
            text = getString(R.string.quoted, AppState.lastPhrase);
        } else if (AppState.mode == AppState.Mode.LISTENING) {
            text = getString(R.string.patient_listening);
        } else if (active) {
            text = getString(R.string.listen_preparing);
        } else if (ModelManager.error != null) {
            text = getString(R.string.err_failed_with, ModelManager.error);
            pendingStart = false;
        } else {
            text = getString(R.string.patient_not_listening);
        }
        status.setText(text);

        if (pendingStart && !downloading && ModelManager.isReady(this)) beginStart();
    }

    private void renderDate() {
        Date now = new Date();
        Locale l = Lang.locale(this);
        day.setText(capitalize(new SimpleDateFormat("EEEE", l).format(now), l));
        date.setText(new SimpleDateFormat(getString(R.string.date_pattern), l).format(now));
    }

    private static String capitalize(String s, Locale l) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(l) + s.substring(1);
    }

    // --- Linking ---

    private void scan() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        try {
            GmsBarcodeScanning.getClient(this, options).startScan()
                    .addOnSuccessListener(barcode -> {
                        String code = LinkCode.parse(barcode.getRawValue());
                        if (code == null) toast(getString(R.string.not_earcaster_code));
                        else link(code);
                    })
                    .addOnFailureListener(e -> toast(getString(R.string.camera_failed)));
        } catch (RuntimeException e) {
            toast(getString(R.string.camera_failed));
        }
    }

    private void typeCode() {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout layout = view.findViewById(R.id.nf_name_layout);
        view.findViewById(R.id.nf_extra_layout).setVisibility(View.GONE);
        EditText field = view.findViewById(R.id.nf_name);
        layout.setHint(getString(R.string.code_hint));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(12)});
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.escribir_el_codigo)
                .setView(view)
                .setPositiveButton(R.string.link, (d, w) -> {
                    String code = LinkCode.parse(field.getText().toString());
                    if (code == null) toast(getString(R.string.code_format));
                    else link(code);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void link(String code) {
        if (linking) return;
        linking = true;
        linkStatus.setText(R.string.linking);
        Cloud.link(this, code, (pid, error) -> {
            linking = false;
            if (pid == null) {
                linkStatus.setText(error);
                return;
            }
            linkStatus.setText("");
            Prefs.setMode(this, Prefs.MODE_PATIENT);
            Prefs.setLinkedPatient(this, pid);
            Prefs.setSyncedConfig(this, "");
            Prefs.clearConfig(this);
            DoseLog.clear(this);
            if (!isTaskRoot()) {
                // So that "back" does not go to the initial question: this screen stands alone.
                startActivity(new Intent(this, PatientActivity.class).putExtra(EXTRA_ASK_PIN, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
                return;
            }
            if (!syncing) {
                PatientSync.start(this);
                syncing = true;
            }
            onAppStateChanged();
            askNewPin();
        });
    }

    // --- PIN to exit (spec 004, R4) ---

    private EditText pinField(View view, String hint) {
        TextInputLayout layout = view.findViewById(R.id.nf_name_layout);
        view.findViewById(R.id.nf_extra_layout).setVisibility(View.GONE);
        EditText field = view.findViewById(R.id.nf_name);
        layout.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        return field;
    }

    /** When linking, whoever sets it up chooses the PIN: without it, the person could exit by accident. */
    private void askNewPin() {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        EditText field = pinField(view, getString(R.string.pin_hint));
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pin_choose_title)
                .setMessage(R.string.pin_choose_msg)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.guardar, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String pin = field.getText().toString();
                    if (pin.length() != 4) {
                        toast(getString(R.string.pin_digits));
                        return;
                    }
                    Prefs.setPin(this, pin);
                    Prefs.setPinFails(this, 0, 0);
                    dialog.dismiss();
                    toast(getString(R.string.pin_ready));
                }));
        dialog.show();
    }

    private void askPin() {
        if (Prefs.pin(this).isEmpty()) {
            showExitOptions();
            return;
        }
        long wait = Prefs.pinLockedUntil(this) - System.currentTimeMillis();
        if (wait > 0) {
            toast(getString(R.string.pin_wait, (int) Math.max(1, wait / 1000)));
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        EditText field = pinField(view, "PIN");
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pin_type)
                .setView(view)
                .setPositiveButton(R.string.entrar, (d, w) -> {
                    if (Prefs.pin(this).equals(field.getText().toString())) {
                        Prefs.setPinFails(this, 0, 0);
                        showExitOptions();
                        return;
                    }
                    int fails = Prefs.pinFails(this) + 1;
                    if (fails >= PIN_TRIES) {
                        Prefs.setPinFails(this, 0, System.currentTimeMillis() + PIN_LOCK_MS);
                        toast(getString(R.string.pin_wrong_wait));
                    } else {
                        Prefs.setPinFails(this, fails, 0);
                        toast(getString(R.string.pin_wrong));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showExitOptions() {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (AppState.isActive()) {
            items.add(getString(R.string.stop_listening));
            actions.add(() -> stopService(new Intent(this, ListenerService.class)));
        }
        items.add(getString(R.string.pin_change));
        actions.add(this::askNewPin);
        items.add(getString(R.string.unlink_this_phone));
        actions.add(this::confirmUnlink);
        items.add(LangPicker.label(this));
        actions.add(() -> LangPicker.show(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.options)
                .setItems(items.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cerrar, null)
                .show();
    }

    private void confirmUnlink() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unlink_this_title)
                .setMessage(R.string.unlink_this_msg)
                .setPositiveButton(R.string.unlink, (d, w) -> {
                    String pid = Prefs.linkedPatient(this);
                    if (pid != null) Cloud.unlinkSelf(pid);
                    syncing = false;
                    PatientSync.forget(this);
                    if (!isFinishing()) WelcomeActivity.open(this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --- Start listening: permissions → model → service ---

    private void beginStart() {
        List<Trigger> triggers = Prefs.triggers(this);
        boolean needCall = false;
        boolean needSms = false;
        for (Trigger t : triggers) {
            if (Trigger.CALL.equals(t.action)) needCall = true;
            if (Trigger.HELP.equals(t.action)) {
                needSms = true;
                needCall = true;
            }
        }
        // The AI may call whoever the profile allows (spec 017).
        if (Prefs.aiOn(this)) needCall = true;
        List<String> perms = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!pendingStart) {
            // Optional ones are requested once per attempt; if denied, it carries on without them.
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (needCall && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.CALL_PHONE);
            }
            if (needSms && !HelpAlert.canSendSms(this)) perms.add(Manifest.permission.SEND_SMS);
        }
        if (!perms.isEmpty()) {
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
        if (requestCode != REQ_PERMS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginStart();
        } else {
            pendingStart = false;
            toast(getString(R.string.no_mic_permission));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
