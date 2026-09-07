package researchflow.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A small, defensive extractor for the flat JSON shapes exchanged with the local LLM runtime and
 * parsed from its plan-generation output. Not a general-purpose JSON parser — only the specific
 * shapes this package needs (a flat object, string/null fields, and one array of flat objects).
 */
final class SimpleJson {
    private SimpleJson() { }

    /** Finds the first balanced {@code '{' ... '}'} object in {@code raw}, tolerating surrounding prose or markdown fences. */
    static String extractObject(String raw) {
        if (raw == null) return null;
        var start = raw.indexOf('{');
        if (start < 0) return null;
        var depth = 0;
        for (int index = start; index < raw.length(); index++) {
            var character = raw.charAt(index);
            if (character == '{') depth++;
            else if (character == '}') {
                depth--;
                if (depth == 0) return raw.substring(start, index + 1);
            }
        }
        return null;
    }

    static String stringField(String json, String key) {
        if (json == null) return null;
        if (Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*null").matcher(json).find()) return null;
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    /** Returns the raw {@code [...]} substring for an array-valued key, or {@code null} if the key is absent. */
    static String arrayField(String json, String key) {
        if (json == null) return null;
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[").matcher(json);
        if (!matcher.find()) return null;
        var start = matcher.end() - 1;
        var depth = 0;
        for (int index = start; index < json.length(); index++) {
            var character = json.charAt(index);
            if (character == '[') depth++;
            else if (character == ']') {
                depth--;
                if (depth == 0) return json.substring(start, index + 1);
            }
        }
        return null;
    }

    /** Splits a {@code [...]} array of flat objects into its individual {@code {...}} chunks. */
    static List<String> objectsIn(String arrayJson) {
        var objects = new ArrayList<String>();
        if (arrayJson == null) return objects;
        var depth = 0;
        var start = -1;
        for (int index = 0; index < arrayJson.length(); index++) {
            var character = arrayJson.charAt(index);
            if (character == '{') {
                if (depth == 0) start = index;
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(arrayJson.substring(start, index + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }
}
