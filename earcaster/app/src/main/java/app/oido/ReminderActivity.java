package app.oido;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Reminder screen with a large «Hecho» (Done) button (spec 006, R4), also with the phone locked. */
public class ReminderActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_reminder);
        findViewById(R.id.rem_close).setOnClickListener(v -> finish());
        findViewById(R.id.rem_done).setOnClickListener(v -> done());
        show();
        // Once the button's time is up, the screen closes itself.
        handler.postDelayed(this::finish, Reminders.CONFIRM_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show();
    }

    private void show() {
        String text = getIntent().getStringExtra(Reminders.EXTRA_TEXT);
        ((TextView) findViewById(R.id.rem_text)).setText(text == null ? getString(R.string.reminder) : text);
    }

    private void done() {
        Intent i = getIntent();
        String dose = i.getStringExtra(Reminders.EXTRA_DOSE);
        if (dose != null && !dose.isEmpty()) DoseLog.add(this, dose, DoseLog.BY_BUTTON);
        getSystemService(NotificationManager.class).cancel(i.getIntExtra(Reminders.EXTRA_NOTIFICATION, 0));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
