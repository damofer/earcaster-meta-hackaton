package app.oido;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the help alert SMS (spec 009) to the chosen contacts and reports once
 * whether at least one went out. If the phone gives no answer within 20 seconds, it counts as a failure.
 */
final class HelpAlert {

    interface Result {
        void done(boolean sent);
    }

    private static final String ACTION_SENT = "app.oido.HELP_SMS_SENT";
    private static final long TIMEOUT_MS = 20_000;

    private HelpAlert() {}

    static boolean canSendSms(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    static void send(Context ctx, List<Trigger.Contact> contacts, String text, Result result) {
        Handler main = new Handler(Looper.getMainLooper());
        if (!canSendSms(ctx) || contacts.isEmpty()) {
            main.post(() -> result.done(false));
            return;
        }
        SmsManager sms = Build.VERSION.SDK_INT >= 31 ? ctx.getSystemService(SmsManager.class) : SmsManager.getDefault();
        if (sms == null) {
            main.post(() -> result.done(false));
            return;
        }
        String action = ACTION_SENT + "." + System.nanoTime();
        ArrayList<String> parts = sms.divideMessage(text);
        int expected = parts.size() * contacts.size();
        int[] received = {0};
        boolean[] ok = {false};
        boolean[] finished = {false};
        Context app = ctx.getApplicationContext();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                received[0]++;
                if (getResultCode() == Activity.RESULT_OK) ok[0] = true;
                if (received[0] >= expected) finish(app, this, main, finished, ok[0], result);
            }
        };
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            app.registerReceiver(receiver, filter);
        }
        main.postDelayed(() -> finish(app, receiver, main, finished, ok[0], result), TIMEOUT_MS);

        int code = 0;
        for (Trigger.Contact c : contacts) {
            ArrayList<PendingIntent> sent = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Intent intent = new Intent(action).setPackage(ctx.getPackageName());
                sent.add(PendingIntent.getBroadcast(ctx, code++, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT));
            }
            try {
                if (parts.size() == 1) {
                    sms.sendTextMessage(c.number, null, text, sent.get(0), null);
                } else {
                    sms.sendMultipartTextMessage(c.number, null, parts, sent, null);
                }
            } catch (RuntimeException e) {
                // Odd number or no service: counts as parts that did not go out.
                received[0] += parts.size();
                if (received[0] >= expected) finish(app, receiver, main, finished, ok[0], result);
            }
        }
    }

    private static void finish(Context app, BroadcastReceiver receiver, Handler main, boolean[] finished,
                               boolean sent, Result result) {
        if (finished[0]) return;
        finished[0] = true;
        main.removeCallbacksAndMessages(null);
        try {
            app.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        main.post(() -> result.done(sent));
    }
}
