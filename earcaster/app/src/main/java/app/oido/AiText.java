package app.oido;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Android-free part of the AI conversation (spec 017): deciding whether what was heard is meant for the AI
 * and reading the reply that arrives in frames from the responder function.
 */
final class AiText {

    /** The name may follow a few calling words: «oye Clara», «ay Clara» (hey Clara). */
    private static final int NAME_WITHIN_WORDS = 3;
    /** The largest frame accepted: audio arrives in chunks of a few kilobytes. */
    static final int MAX_FRAME_BYTES = 1 << 20;

    /** The words a question starts with. Vosk writes no punctuation, so only the beginning can be checked. */
    private static final Set<String> QUESTION_WORDS = new HashSet<>(Arrays.asList(
            "que", "quien", "quienes", "cuando", "donde", "adonde", "como", "cual", "cuales",
            "cuanto", "cuanta", "cuantos", "cuantas", "por", "para"));
    /** In English, also the auxiliaries a yes/no question starts with: «is ana coming today». */
    private static final Set<String> QUESTION_WORDS_EN = new HashSet<>(Arrays.asList(
            "what", "what's", "who", "who's", "whose", "when", "when's", "where", "where's", "why", "how", "how's",
            "which", "is", "are", "am", "was", "were", "do", "does", "did", "can", "could", "will", "would",
            "should", "have", "has"));

    private AiText() {}

    /**
     * What is asked of the AI, or null if what was heard is not for it.
     * With the name («clara ana viene hoy», "clara is ana coming today") returns what follows the name, and "" if only the name was said.
     * Without the name, only if its reply is awaited (followUp) or, with the experimental option (any),
     * if it sounds like a question.
     */
    static String question(String heard, String name, boolean any, boolean followUp) {
        // Compared without accents, but the AI receives what was heard with its accents and ñ.
        String said = Texts.forGrammar(heard);
        if (said.isEmpty()) return null;
        List<String> original = Arrays.asList(said.split(" "));
        List<String> words = words(said);
        List<String> wanted = words(name);
        if (!wanted.isEmpty()) {
            int last = Math.min(NAME_WITHIN_WORDS, words.size() - wanted.size());
            for (int i = 0; i <= last; i++) {
                if (words.subList(i, i + wanted.size()).equals(wanted)) {
                    return String.join(" ", original.subList(i + wanted.size(), original.size()));
                }
            }
        }
        if (followUp || (any && looksLikeQuestion(words))) return said;
        return null;
    }

    /** «cuándo viene ana», «por qué estoy aquí» (when is ana coming, why am I here): three or more words, starting like a question. */
    static boolean looksLikeQuestion(List<String> words) {
        if (Lang.en()) return words.size() >= 3 && QUESTION_WORDS_EN.contains(words.get(0));
        if (words.size() < 3 || !QUESTION_WORDS.contains(words.get(0))) return false;
        String first = words.get(0);
        // «por» and «para» only count as «por qué» and «para qué» (why).
        return !(first.equals("por") || first.equals("para")) || words.get(1).equals("que");
    }

    private static List<String> words(String text) {
        String clean = Texts.forMatch(text == null ? "" : text);
        return clean.isEmpty() ? Arrays.asList() : Arrays.asList(clean.split(" "));
    }

    // --- The reply in frames: 1 type byte, 4 length bytes (big endian) and the content ---

    static final char TEXT = 'T';
    static final char AUDIO = 'A';
    static final char ACTION = 'X';
    static final char END = 'E';

    static final class Frame {
        final char type;
        final byte[] body;

        Frame(char type, byte[] body) {
            this.type = type;
            this.body = body;
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** The next frame, or null if the reply has ended. A truncated or oversized frame is an error. */
    static Frame read(DataInputStream in) throws IOException {
        int type = in.read();
        if (type < 0) return null;
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            throw new IOException("Respuesta cortada");
        }
        if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Trozo de " + length + " bytes");
        byte[] body = new byte[length];
        in.readFully(body);
        return new Frame((char) type, body);
    }
}
