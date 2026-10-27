package app.oido;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

/** Sets the app's language (Lang) before anything else runs. */
public class App extends Application {

    private static App instance;

    /** For the texts of code that has no Context at hand (Cloud's and AiClient's callbacks). */
    static Context get() {
        return instance;
    }

    /** A text in the app's language. */
    static String str(int id, Object... args) {
        return Lang.str(instance, id, args);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Lang.refresh(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Following the phone, its language may have just changed.
        Lang.refresh(this);
    }
}
