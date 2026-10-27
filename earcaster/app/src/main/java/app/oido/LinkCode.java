package app.oido;

import java.util.Locale;
import java.util.Random;

/**
 * Code to link the person's phone with their caregiver (spec 015): 8 characters
 * without the ones that get confused when read (0 and O, 1 and I), written as «K7P2-9QXM».
 */
final class LinkCode {

    /** 32 symbols: 8 of them give a trillion codes, impossible to guess in 10 minutes. */
    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int LENGTH = 8;
    /** What the QR code carries before the code, so it is not confused with other QR codes. */
    static final String QR_PREFIX = "earcaster:";

    private LinkCode() {}

    static String generate(Random random) {
        StringBuilder s = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return s.toString();
    }

    /** «K7P2-9QXM», easier to read aloud or copy. */
    static String format(String code) {
        if (code == null || code.length() != LENGTH) return code;
        return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2);
    }

    /** The clean code from what was typed or from the QR, or null if it is not a valid code. */
    static String parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith(QR_PREFIX)) s = s.substring(QR_PREFIX.length());
        s = s.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (s.length() != LENGTH) return null;
        for (int i = 0; i < s.length(); i++) {
            if (ALPHABET.indexOf(s.charAt(i)) < 0) return null;
        }
        return s;
    }
}
