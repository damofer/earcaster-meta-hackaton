package app.oido;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** First run: who is going to use this phone? (spec 015, R1). */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // The mode is saved once it is really ready: on signing in to the account or on linking.
        findViewById(R.id.welcome_caregiver).setOnClickListener(v ->
                startActivity(new Intent(this, CaregiverActivity.class)));
        findViewById(R.id.welcome_patient).setOnClickListener(v ->
                startActivity(new Intent(this, PatientActivity.class)));
        android.widget.TextView language = findViewById(R.id.welcome_language);
        language.setText(LangPicker.label(this));
        language.setOnClickListener(v -> LangPicker.show(this));
        findViewById(R.id.welcome_solo).setOnClickListener(v -> {
            Prefs.setMode(this, Prefs.MODE_SOLO);
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }

    /** Opens the right screen for this phone's mode, clearing the previous ones. */
    static void open(android.app.Activity from) {
        Class<?> target;
        switch (Prefs.mode(from)) {
            case Prefs.MODE_CAREGIVER:
                target = CaregiverActivity.class;
                break;
            case Prefs.MODE_PATIENT:
                target = PatientActivity.class;
                break;
            case Prefs.MODE_SOLO:
                target = MainActivity.class;
                break;
            default:
                target = WelcomeActivity.class;
        }
        from.startActivity(new Intent(from, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        from.finish();
    }
}
