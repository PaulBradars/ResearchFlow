package researchflow.dataimport;

import java.util.Set;

/** Turns a CSV header into a unique variable key matching {@code FormValidator}'s syntax: a letter
 * followed by up to 63 letters, digits, or underscores. */
final class VariableKeys {
    private static final int MAX_LENGTH = 64;

    private VariableKeys() { }

    static String sanitize(String header, Set<String> usedLowercase) {
        var cleaned = header.strip().replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank() || !Character.isLetter(cleaned.charAt(0))) cleaned = "c_" + cleaned;
        if (cleaned.length() > MAX_LENGTH) cleaned = cleaned.substring(0, MAX_LENGTH);

        var candidate = cleaned;
        var suffixNumber = 2;
        while (!usedLowercase.add(candidate.toLowerCase())) {
            var suffix = "_" + suffixNumber++;
            var maxBaseLength = MAX_LENGTH - suffix.length();
            var base = cleaned.length() > maxBaseLength ? cleaned.substring(0, maxBaseLength) : cleaned;
            candidate = base + suffix;
        }
        return candidate;
    }
}
