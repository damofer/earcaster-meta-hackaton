package app.oido;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/** State shared between the listening service and the screen. */
public final class AppState {

    public enum Mode { STOPPED, LOADING, LISTENING, ERROR }

    public interface Listener {
        void onAppStateChanged();
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static volatile Mode mode = Mode.STOPPED;
    public static volatile String message = "";
    public static volatile int detections = 0;
    public static volatile long lastDetectionAt = 0;
    public static volatile String lastPhrase = "";
    /** true while a prank sound is playing. */
    public static volatile boolean playing = false;

    private AppState() {}

    public static boolean isActive() {
        return mode == Mode.LOADING || mode == Mode.LISTENING;
    }

    public static void setPlaying(boolean value) {
        playing = value;
        notifyChanged();
    }

    public static void set(Mode newMode, String newMessage) {
        mode = newMode;
        message = newMessage == null ? "" : newMessage;
        notifyChanged();
    }

    /** Called on the main thread every time the prank plays. */
    public static void detected(String phrase) {
        detections++;
        lastPhrase = phrase;
        lastDetectionAt = System.currentTimeMillis();
        notifyChanged();
    }

    public static void notifyChanged() {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) {
                l.onAppStateChanged();
            }
        });
    }

    public static void addListener(Listener l) {
        LISTENERS.addIfAbsent(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }
}
