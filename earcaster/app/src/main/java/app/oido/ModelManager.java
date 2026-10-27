package app.oido;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads (only once) Vosk's speech model for the app's language (Lang), ~40 MB each.
 * After that everything works offline.
 */
final class ModelManager {

    static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";
    static final String MODEL_URL_EN =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    static volatile boolean downloading = false;
    static volatile boolean unpacking = false;
    static volatile int progress = -1;
    static volatile String error = null;

    private ModelManager() {}

    static File modelDir(Context c) {
        return modelDir(c, Lang.code(c));
    }

    /** The Spanish one keeps its old folder, so phones that already have it do not download it again. */
    private static File modelDir(Context c, String lang) {
        return new File(c.getFilesDir(), Lang.EN.equals(lang) ? "vosk-model-en" : "vosk-model-es");
    }

    static boolean isReady(Context c) {
        return new File(modelDir(c), ".ok").exists();
    }

    static synchronized void download(Context ctx) {
        if (downloading || isReady(ctx)) return;
        final Context app = ctx.getApplicationContext();
        // The language at the start: if it changes during the download, this model is still saved in its folder.
        final String lang = Lang.code(app);
        downloading = true;
        unpacking = false;
        progress = 0;
        error = null;
        AppState.notifyChanged();

        new Thread(() -> {
            File zip = new File(app.getCacheDir(), "modelo.zip");
            File tmp = new File(app.getFilesDir(), "vosk-model-tmp");
            try {
                fetch(Lang.EN.equals(lang) ? MODEL_URL_EN : MODEL_URL, zip);
                unpacking = true;
                AppState.notifyChanged();
                deleteRecursive(tmp);
                unzip(zip, tmp);
                File root = findModelRoot(tmp);
                if (root == null) throw new IOException(Lang.str(app, R.string.model_bad_format));
                File dest = modelDir(app, lang);
                deleteRecursive(dest);
                if (!root.renameTo(dest)) throw new IOException(Lang.str(app, R.string.model_cant_save));
                if (!new File(dest, ".ok").createNewFile()) throw new IOException(Lang.str(app, R.string.model_cant_save));
            } catch (Exception e) {
                error = Lang.str(app, R.string.model_download_failed, e.getMessage());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                zip.delete();
                deleteRecursive(tmp);
                unpacking = false;
                downloading = false;
                AppState.notifyChanged();
            }
        }, "descarga-modelo").start();
    }

    private static void fetch(String url, File zip) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            long total = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new FileOutputStream(zip)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                int last = -1;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int p = (int) (done * 100 / total);
                        if (p != last) {
                            last = p;
                            progress = p;
                            AppState.notifyChanged();
                        }
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void unzip(File zip, File dest) throws IOException {
        if (!dest.mkdirs() && !dest.isDirectory()) throw new IOException("cannot create the folder");
        String destPath = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            byte[] buf = new byte[64 * 1024];
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(dest, entry.getName());
                if (!out.getCanonicalPath().startsWith(destPath)) throw new IOException("invalid file");
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    //noinspection ResultOfMethodCallIgnored
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean looksLikeModel(File dir) {
        return new File(dir, "conf").isDirectory() || new File(dir, "am").isDirectory();
    }

    private static File findModelRoot(File dir) {
        if (looksLikeModel(dir)) return dir;
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            if (k.isDirectory() && looksLikeModel(k)) return k;
        }
        return null;
    }

    /**
     * Words in the phrase that the model does not know (e.g. because of a misplaced accent).
     * If the model ships no word list, returns an empty list.
     */
    static List<String> missingWords(File modelDir, String phraseForGrammar) {
        Set<String> needed = new LinkedHashSet<>(Arrays.asList(phraseForGrammar.split(" ")));
        File words = new File(modelDir, "graph/words.txt");
        if (!words.isFile()) return new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(words), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && !needed.isEmpty()) {
                int sp = line.indexOf(' ');
                needed.remove(sp > 0 ? line.substring(0, sp) : line.trim());
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return new ArrayList<>(needed);
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
