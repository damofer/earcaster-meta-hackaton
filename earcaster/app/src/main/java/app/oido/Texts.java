package app.oido;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Text cleanup for the Vosk grammar and for comparing what was heard. */
final class Texts {

    private static final Locale ES = Locale.forLanguageTag("es");

    private Texts() {}

    /**
     * Lowercase, without punctuation, keeping accents and ñ (that is how the model's vocabulary is written).
     * In English the apostrophe stays: the English model writes «don't» and «i'm» as one word.
     */
    static String forGrammar(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(ES).replace('’', '\'')
                .replaceAll(Lang.en() ? "[^\\p{L}\\p{N}']+" : "[^\\p{L}\\p{N}]+", " ")
                .replaceAll("(^| )'+|'+( |$)", " ").trim();
        return t.replaceAll("\\s+", " ");
    }

    /** Like forGrammar but also without accents, to compare regardless of accent marks. */
    static String forMatch(String s) {
        return Normalizer.normalize(forGrammar(s), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** The different forms of a phrase, separated by commas: «trece, el trece» → [trece, el trece] (thirteen, the thirteen). */
    static List<String> variants(String s) {
        List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String part : s.split("[,;/]")) {
            String v = forGrammar(part);
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
        return out;
    }

    static boolean hasDigits(String s) {
        return s != null && s.matches(".*\\d.*");
    }

    /** A recognized word and how confident the model is (0–1). */
    static final class Word {
        final String text;
        final float conf;
        /** Second of the audio where the word starts. */
        final float start;

        Word(String text, float conf, float start) {
            this.text = forMatch(text);
            this.conf = conf;
            this.start = start;
        }
    }

    /**
     * Were the phrase's words heard consecutively, all with at least minConf confidence?
     * Returns the second where the phrase starts, or -1 if it is not there.
     */
    static float findPhrase(List<Word> heard, String phraseForMatch, float minConf) {
        if (phraseForMatch == null || phraseForMatch.isEmpty()) return -1;
        String[] target = phraseForMatch.split(" ");
        for (int start = 0; start + target.length <= heard.size(); start++) {
            boolean ok = true;
            for (int j = 0; j < target.length; j++) {
                Word w = heard.get(start + j);
                if (!w.text.equals(target[j]) || w.conf < minConf) {
                    ok = false;
                    break;
                }
            }
            if (ok) return heard.get(start).start;
        }
        return -1;
    }
}
