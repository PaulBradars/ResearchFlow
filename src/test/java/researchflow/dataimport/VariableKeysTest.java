package researchflow.dataimport;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableKeysTest {
    private static final String VALID = "[A-Za-z][A-Za-z0-9_]{0,63}";

    @Test
    void sanitizesSpacesAndPunctuationIntoUnderscores() {
        var key = VariableKeys.sanitize("Sleep hours (per night)", new HashSet<>());
        assertTrue(key.matches(VALID));
        assertEquals("Sleep_hours__per_night_", key);
    }

    @Test
    void prefixesAHeaderThatDoesNotStartWithALetter() {
        var key = VariableKeys.sanitize("123", new HashSet<>());
        assertTrue(key.matches(VALID));
        assertEquals("c_123", key);
    }

    @Test
    void deduplicatesCollidingKeysCaseInsensitively() {
        var used = new HashSet<String>();
        var first = VariableKeys.sanitize("Age", used);
        var second = VariableKeys.sanitize("age", used);
        var third = VariableKeys.sanitize("AGE", used);

        assertEquals("Age", first);
        assertEquals("age_2", second);
        assertEquals("AGE_3", third);
    }

    @Test
    void truncatesToTheMaximumVariableKeyLength() {
        var longHeader = "a".repeat(100);
        var key = VariableKeys.sanitize(longHeader, new HashSet<>());
        assertTrue(key.matches(VALID));
        assertEquals(64, key.length());
    }
}
