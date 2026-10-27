package app.oido;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Caregiver mode (spec 015): the account, the patient list and linking their phones. */
public class CaregiverActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    /** While the phone is listening it checks in every 15 minutes: if 40 pass without a check-in, something is wrong. */
    private static final long SILENT_MS = 40 * 60_000L;
    private static final long HELP_SHOWN_MS = 24 * 3_600_000L;

    private View login;
    private LinearLayout patientsView;
    private View empty;
    private TextView email;
    private MaterialButton menu;
    private ExtendedFloatingActionButton add;
    private EditText emailInput;
    private EditText passwordInput;

    private ListenerRegistration patientsReg;
    private List<Cloud.Patient> patients = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Every minute, so that «hace 5 minutos» (5 minutes ago) does not go stale. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderPatients();
            handler.postDelayed(this, 60_000);
        }
    };

    private AlertDialog linkDialog;
    private ListenerRegistration linkReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Cloud.available(this)) {
            // A build without google-services.json: no cloud, only single-phone mode.
            Toast.makeText(this, R.string.no_cloud_build, Toast.LENGTH_LONG).show();
            Prefs.setMode(this, Prefs.MODE_SOLO);
            WelcomeActivity.open(this);
            return;
        }
        setContentView(R.layout.activity_caregiver);

        login = findViewById(R.id.cg_login);
        patientsView = findViewById(R.id.cg_patients);
        empty = findViewById(R.id.cg_empty);
        email = findViewById(R.id.cg_email);
        menu = findViewById(R.id.cg_menu);
        add = findViewById(R.id.cg_add);
        emailInput = findViewById(R.id.cg_email_input);
        passwordInput = findViewById(R.id.cg_password_input);

        findViewById(R.id.cg_sign_in).setOnClickListener(v -> signIn());
        findViewById(R.id.cg_create).setOnClickListener(v -> createAccount());
        findViewById(R.id.cg_forgot).setOnClickListener(v -> resetPassword());
        findViewById(R.id.cg_back).setOnClickListener(v -> back());
        menu.setOnClickListener(this::showAccountMenu);
        add.setOnClickListener(v -> addPatient());
    }

    @Override
    protected void onStart() {
        super.onStart();
        render();
    }

    @Override
    protected void onStop() {
        stopListening();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (linkDialog != null) linkDialog.dismiss();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Cloud.caregiver(this) == null) back();
        else super.onBackPressed();
    }

    /** Signed out, "back" goes to the initial question. */
    private void back() {
        if (!isTaskRoot()) {
            finish();
            return;
        }
        Prefs.setMode(this, Prefs.MODE_UNSET);
        WelcomeActivity.open(this);
    }

    private void render() {
        FirebaseUser user = Cloud.caregiver(this);
        boolean in = user != null;
        login.setVisibility(in ? View.GONE : View.VISIBLE);
        patientsView.setVisibility(in ? View.VISIBLE : View.GONE);
        menu.setVisibility(in ? View.VISIBLE : View.GONE);
        if (in) add.show(); else add.hide();
        ((TextView) findViewById(R.id.cg_title)).setText(in ? R.string.mis_pacientes : R.string.caregiver_account);
        email.setText(in ? user.getEmail() : "");
        email.setVisibility(in ? View.VISIBLE : View.GONE);
        if (!in) {
            empty.setVisibility(View.GONE);
            stopListening();
            return;
        }
        if (patientsReg == null) {
            patientsReg = Cloud.listenPatients(this, (list, error) -> {
                if (list == null) {
                    if (error != null) toast(error);
                    return;
                }
                patients = list;
                renderPatients();
            });
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 60_000);
        }
        renderPatients();
    }

    private void stopListening() {
        if (patientsReg != null) patientsReg.remove();
        patientsReg = null;
        handler.removeCallbacks(tick);
    }

    // --- Sign in ---

    private String typedEmail() {
        return emailInput.getText().toString().trim();
    }

    private boolean validFields() {
        TextInputLayout emailLayout = findViewById(R.id.cg_email_layout);
        TextInputLayout passwordLayout = findViewById(R.id.cg_password_layout);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        if (!typedEmail().contains("@")) {
            emailLayout.setError(getString(R.string.type_email));
            return false;
        }
        if (passwordInput.getText().length() < 6) {
            passwordLayout.setError(getString(R.string.err_weak_password));
            return false;
        }
        return true;
    }

    private void setBusy(boolean busy) {
        findViewById(R.id.cg_sign_in).setEnabled(!busy);
        findViewById(R.id.cg_create).setEnabled(!busy);
        findViewById(R.id.cg_forgot).setEnabled(!busy);
    }

    private void signIn() {
        if (!validFields()) return;
        setBusy(true);
        Cloud.signIn(typedEmail(), passwordInput.getText().toString(), error -> {
            setBusy(false);
            if (error != null) {
                toast(error);
                return;
            }
            signedIn();
        });
    }

    /** Before creating the account, what is stored in the cloud and why (R9). */
    private void createAccount() {
        if (!validFields()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.account_notice_title)
                .setMessage(R.string.account_notice_msg)
                .setPositiveButton(R.string.accept_create_account, (d, w) -> {
                    setBusy(true);
                    Cloud.createAccount(typedEmail(), passwordInput.getText().toString(), error -> {
                        setBusy(false);
                        if (error != null) {
                            toast(error);
                            return;
                        }
                        signedIn();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetPassword() {
        TextInputLayout emailLayout = findViewById(R.id.cg_email_layout);
        if (!typedEmail().contains("@")) {
            emailLayout.setError(getString(R.string.type_email_forgot));
            return;
        }
        emailLayout.setError(null);
        Cloud.resetPassword(typedEmail(), error ->
                toast(error != null ? error : getString(R.string.reset_sent, typedEmail())));
    }

    private void signedIn() {
        Prefs.setMode(this, Prefs.MODE_CAREGIVER);
        passwordInput.setText("");
        if (!isTaskRoot()) {
            // So that "back" does not go to the initial question: this screen stands alone.
            WelcomeActivity.open(this);
            return;
        }
        render();
    }

    private void showAccountMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.sign_out);
        popup.getMenu().add(0, 2, 1, R.string.delete_account_menu);
        popup.getMenu().add(0, 3, 2, LangPicker.label(this));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) signOut();
            else if (item.getItemId() == 3) LangPicker.show(this);
            else confirmDeleteAccount();
            return true;
        });
        popup.show();
    }

    private void signOut() {
        stopListening();
        Cloud.signOut();
        forgetLocalCopies(this);
        Prefs.setMode(this, Prefs.MODE_UNSET);
        WelcomeActivity.open(this);
    }

    private void confirmDeleteAccount() {
        int n = patients.size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(getString(R.string.delete_account_msg,
                        n == 1 ? getString(R.string.your_patient) : getString(R.string.your_n_patients, n)))
                .setPositiveButton(R.string.delete_all, (d, w) -> {
                    stopListening();
                    Cloud.deleteAccount(this, error -> {
                        if (error != null) {
                            toast(error);
                            render();
                            return;
                        }
                        forgetLocalCopies(this);
                        Prefs.setMode(this, Prefs.MODE_UNSET);
                        toast(getString(R.string.account_deleted));
                        WelcomeActivity.open(this);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The patients' copies left on this phone after editing them. */
    static void forgetLocalCopies(Context c) {
        File dir = new File(c.getApplicationInfo().dataDir, "shared_prefs");
        String[] files = dir.list();
        if (files != null) {
            for (String f : files) {
                if (!f.endsWith(".xml")) continue;
                String name = f.substring(0, f.length() - 4);
                if (name.startsWith(MainActivity.PATIENT_PREFS_PREFIX)) c.deleteSharedPreferences(name);
            }
        }
        File[] sounds = Recorder.dir(c).listFiles();
        if (sounds != null) {
            for (File f : sounds) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    // --- Patients ---

    private void renderPatients() {
        if (patientsView == null || Cloud.caregiver(this) == null) return;
        patientsView.removeAllViews();
        empty.setVisibility(patients.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        long now = System.currentTimeMillis();
        for (Cloud.Patient p : patients) {
            View card = inflater.inflate(R.layout.item_patient, patientsView, false);
            String name = displayName(p);
            ((TextView) card.findViewById(R.id.pt_name)).setText(name);
            ((TextView) card.findViewById(R.id.pt_status)).setText(status(p, now));
            TextView help = card.findViewById(R.id.pt_help);
            if (p.helpAt > 0 && now - p.helpAt < HELP_SHOWN_MS) {
                String said = TextUtils.isEmpty(p.helpSaid) ? "" : getString(R.string.help_said_suffix, p.helpSaid);
                help.setText(getString(R.string.help_asked, Ago.text(p.helpAt, now), said,
                        p.helpSent ? "" : getString(R.string.help_sms_not_sent)));
                help.setVisibility(View.VISIBLE);
            }
            card.findViewById(R.id.pt_edit).setOnClickListener(v -> openEditor(p.id, name));
            card.findViewById(R.id.pt_profile).setOnClickListener(v -> startActivity(
                    new Intent(this, ProfileActivity.class)
                            .putExtra(MainActivity.EXTRA_PATIENT, p.id)
                            .putExtra(MainActivity.EXTRA_PATIENT_NAME, name)));
            if (p.aiLimitAt > 0 && SpokenText.sameDay(Calendar.getInstance(), p.aiLimitAt)) {
                card.findViewById(R.id.pt_ai_limit).setVisibility(View.VISIBLE);
            }
            card.findViewById(R.id.pt_ai).setOnClickListener(v -> startActivity(
                    new Intent(this, AiActivity.class)
                            .putExtra(MainActivity.EXTRA_PATIENT, p.id)
                            .putExtra(MainActivity.EXTRA_PATIENT_NAME, name)));
            card.findViewById(R.id.pt_link).setOnClickListener(v -> showLink(p.id, name));
            card.findViewById(R.id.pt_more).setOnClickListener(v -> showPatientMenu(v, p.id, name));
            patientsView.addView(card);
        }
    }

    private static String displayName(Cloud.Patient p) {
        return p.name.isEmpty() ? App.str(R.string.no_name) : p.name;
    }

    /** «Su teléfono dejó de escuchar hace 2 horas» (Their phone stopped listening 2 hours ago) (R7). */
    static String status(Cloud.Patient p, long now) {
        if (p.seenAt == 0) return App.str(R.string.phone_no_news);
        if (p.listening) {
            if (now - p.seenAt > SILENT_MS) return App.str(R.string.phone_silent_since, Ago.text(p.seenAt, now));
            return App.str(R.string.phone_listening);
        }
        return App.str(R.string.phone_stopped, Ago.text(p.seenAt, now));
    }

    private void openEditor(String pid, String name) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PATIENT, pid)
                .putExtra(MainActivity.EXTRA_PATIENT_NAME, name));
    }

    private void addPatient() {
        askName(getString(R.string.anadir_paciente), getString(R.string.name_example), "", getString(R.string.add), name ->
                Cloud.addPatient(this, name, (pid, error) -> {
                    if (pid == null) {
                        toast(error);
                        return;
                    }
                    openEditor(pid, name);
                }));
    }

    private interface NameTyped {
        void typed(String name);
    }

    private void askName(String title, String hint, String current, String ok, NameTyped done) {
        View view = getLayoutInflater().inflate(R.layout.dialog_named_field, null);
        TextInputLayout nameLayout = view.findViewById(R.id.nf_name_layout);
        view.findViewById(R.id.nf_extra_layout).setVisibility(View.GONE);
        EditText name = view.findViewById(R.id.nf_name);
        nameLayout.setHint(hint);
        name.setText(current);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(ok, (d, w) -> {
                    String typed = name.getText().toString().trim();
                    if (!typed.isEmpty()) done.typed(typed);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPatientMenu(View anchor, String pid, String name) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.rename);
        popup.getMenu().add(0, 2, 1, R.string.unlink_phone_menu);
        popup.getMenu().add(0, 3, 2, getString(R.string.delete_name, name));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    askName(getString(R.string.rename), getString(R.string.nombre), name, getString(R.string.guardar),
                            typed -> Cloud.renamePatient(pid, typed));
                    break;
                case 2:
                    confirmUnlink(pid, name);
                    break;
                default:
                    confirmDelete(pid, name);
            }
            return true;
        });
        popup.show();
    }

    /** R8: the person's phone deletes its copy and returns to the initial question. */
    private void confirmUnlink(String pid, String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.unlink_title, name))
                .setMessage(getString(R.string.unlink_msg, name))
                .setPositiveButton(R.string.unlink, (d, w) -> Cloud.unlinkDevices(pid, error ->
                        toast(error != null ? error : getString(R.string.unlinked, name))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String pid, String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_name_q, name))
                .setMessage(R.string.delete_patient_msg)
                .setPositiveButton(R.string.delete, (d, w) -> Cloud.deletePatient(pid, error -> {
                    if (error != null) {
                        toast(error);
                        return;
                    }
                    deleteSharedPreferences(MainActivity.PATIENT_PREFS_PREFIX + "oido_" + pid);
                    deleteSharedPreferences(MainActivity.PATIENT_PREFS_PREFIX + "tomas_" + pid);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --- Linking (R4) ---

    private void showLink(String pid, String name) {
        Cloud.createLink(this, pid, (code, error) -> {
            if (code == null) {
                toast(error);
                return;
            }
            if (isFinishing()) {
                Cloud.deleteLink(code);
                return;
            }
            View view = getLayoutInflater().inflate(R.layout.dialog_link, null);
            Bitmap qr = qr(LinkCode.QR_PREFIX + code, 480);
            if (qr != null) ((ImageView) view.findViewById(R.id.link_qr)).setImageBitmap(qr);
            else view.findViewById(R.id.link_qr).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.link_code)).setText(LinkCode.format(code));
            linkDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.link_title, name))
                    .setView(view)
                    .setPositiveButton(R.string.cerrar, null)
                    .setOnDismissListener(d -> endLink())
                    .show();
            linkReg = Cloud.listenLinked(pid, code, () -> {
                toast(getString(R.string.linked_ok, name));
                if (linkDialog != null) linkDialog.dismiss();
            });
        });
    }

    /** On close, the code stays valid until it expires or a phone uses it (R4). */
    private void endLink() {
        if (linkReg != null) linkReg.remove();
        linkReg = null;
        linkDialog = null;
    }

    static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth();
            int h = m.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) pixels[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.setPixels(pixels, 0, w, 0, 0, w, h);
            return b;
        } catch (WriterException e) {
            return null;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
