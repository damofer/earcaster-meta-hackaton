package app.oido;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Voice actions (spec 008): call a contact on speakerphone, turn the flashlight on or off
 * and log a dose (spec 007). They always say out loud what they are doing.
 */
final class Actions {

    /** The flashlight turns itself off after 10 minutes to save battery. */
    private static final long LIGHT_MS = 10 * 60_000L;

    private final Context ctx;
    private final Responder responder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable lightOff = () -> setTorch(false);
    private boolean torchOn = false;
    /** Changes when stopped: this way «Parar sonido» (Stop sound) during «Llamando a Carlos» (Calling Carlos) never places the call. */
    private int turn = 0;

    Actions(Context ctx, Responder responder) {
        this.ctx = ctx;
        this.responder = responder;
    }

    /** What «Escuchar cómo suena» (Hear how it sounds) plays: the action's phrase, without performing it. */
    static String preview(Context ctx, Trigger t) {
        switch (t.action) {
            case Trigger.CALL:
                return t.contacts.isEmpty() ? Lang.str(ctx, R.string.action_missing_call)
                        : Lang.str(ctx, R.string.action_calling, t.contacts.get(0).name);
            case Trigger.LIGHT_ON:
                return Lang.str(ctx, R.string.action_light_on);
            case Trigger.LIGHT_OFF:
                return Lang.str(ctx, R.string.action_light_off);
            case Trigger.DOSE:
                return SpokenText.doseNoted(t.doseName, Calendar.getInstance());
            case Trigger.HELP:
                return t.contacts.isEmpty() ? Lang.str(ctx, R.string.action_missing_help)
                        : SpokenText.helpQuestion(contactNames(t));
            default:
                return "";
        }
    }

    static List<String> contactNames(Trigger t) {
        List<String> names = new ArrayList<>();
        for (Trigger.Contact c : t.contacts) names.add(c.name);
        return names;
    }

    /** Performs the action and says what it did. done is called once, when listening can resume. */
    void run(Trigger t, Runnable done) {
        switch (t.action) {
            case Trigger.CALL: {
                if (t.contacts.isEmpty()) {
                    responder.say(Lang.str(ctx, R.string.action_no_call), done);
                    return;
                }
                Trigger.Contact c = t.contacts.get(0);
                final int mine = ++turn;
                responder.say(Lang.str(ctx, R.string.action_calling, c.name), () -> {
                    if (mine != turn) {
                        done.run();
                    } else if (call(ctx, c.number)) {
                        done.run();
                    } else {
                        responder.say(Lang.str(ctx, R.string.action_call_failed, c.name), done);
                    }
                });
                return;
            }
            case Trigger.LIGHT_ON:
                responder.say(Lang.str(ctx, setTorch(true) ? R.string.action_light_on : R.string.action_light_failed), done);
                return;
            case Trigger.LIGHT_OFF:
                setTorch(false);
                responder.say(Lang.str(ctx, R.string.action_light_off), done);
                return;
            case Trigger.DOSE:
                if (t.doseName.isEmpty()) {
                    responder.say(Lang.str(ctx, R.string.action_no_dose), done);
                    return;
                }
                DoseLog.add(ctx, t.doseName, DoseLog.BY_VOICE);
                responder.say(SpokenText.doseNoted(t.doseName, Calendar.getInstance()), done);
                return;
            default:
                done.run();
        }
    }

    /** Calls on speakerphone. The microphone is muted for this app during the call and comes back on hang-up. */
    static boolean call(Context ctx, String number) {
        if (number == null || number.isEmpty()) return false;
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false;
        TelecomManager tm = ctx.getSystemService(TelecomManager.class);
        if (tm == null) return false;
        Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        try {
            tm.placeCall(Uri.fromParts("tel", number, null), extras);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean setTorch(boolean on) {
        main.removeCallbacks(lightOff);
        CameraManager cm = ctx.getSystemService(CameraManager.class);
        String id = flash(cm);
        if (id == null) return false;
        try {
            cm.setTorchMode(id, on);
        } catch (Exception e) {
            return false;
        }
        torchOn = on;
        if (on) main.postDelayed(lightOff, LIGHT_MS);
        return true;
    }

    /** The back camera with a flash, or any camera with a flash. */
    private static String flash(CameraManager cm) {
        if (cm == null) return null;
        String any = null;
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                if (!Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) continue;
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
                if (any == null) any = id;
            }
        } catch (Exception ignored) {
        }
        return any;
    }

    /** Cancels whatever is about to happen (the call after «Llamando a…», "Calling…"). */
    void stop() {
        turn++;
        responder.stop();
    }

    /** When listening stops, the flashlight we turned on is switched off. */
    void release() {
        main.removeCallbacks(lightOff);
        if (torchOn) setTorch(false);
        main.removeCallbacks(lightOff);
    }
}
