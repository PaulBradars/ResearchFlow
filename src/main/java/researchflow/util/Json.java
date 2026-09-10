package researchflow.util;

import java.util.*;

/** Strict bounded JSON reader for persisted evidence; rejects duplicates, trailing input and nonfinite numbers. */
public final class Json {
    private final String input;
    private int index;
    private boolean exactNumbers;
    public static Object parseExact(String input) {
        if (input == null || input.length() > 10_000_000) throw new IllegalArgumentException("Missing or oversized JSON.");
        var reader = new Json(input); reader.exactNumbers = true;
        var value = reader.value(0); reader.space();
        if (reader.index != input.length()) throw reader.invalid();
        return value;
    }
    private Json(String input) { this.input = Objects.requireNonNull(input); }
    public static Object parse(String input) {
        if (input == null || input.length() > 10_000_000) throw new IllegalArgumentException("Missing or oversized JSON.");
        var reader = new Json(input); var value = reader.value(0); reader.space();
        if (reader.index != input.length()) throw reader.invalid();
        return value;
    }
    private Object value(int depth) {
        if (depth > 32) throw invalid();
        space(); if (index >= input.length()) throw invalid();
        char ch = input.charAt(index);
        if (ch == '"') return string();
        if (ch == '{') {
            index++; var map = new LinkedHashMap<String, Object>(); space();
            if (take('}')) return map;
            do {
                space(); if (index >= input.length() || input.charAt(index) != '"') throw invalid();
                var key = string(); space(); expect(':');
                if (map.containsKey(key)) throw invalid();
                map.put(key, value(depth + 1)); space();
                if (take('}')) return map;
                expect(',');
            } while (true);
        }
        if (ch == '[') {
            index++; var list = new ArrayList<Object>(); space();
            if (take(']')) return list;
            do { list.add(value(depth + 1)); space(); if (take(']')) return list; expect(','); } while (true);
        }
        for (var literal : List.of("true", "false", "null")) {
            if (input.startsWith(literal, index)) { index += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal); }
        }
        var match = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(input);
        match.region(index, input.length());
        if (!match.lookingAt()) throw invalid();
        index = match.end();
        if (exactNumbers) return new java.math.BigDecimal(match.group());
        var number = Double.parseDouble(match.group());
        if (!Double.isFinite(number)) throw invalid();
        return number;
    }
    private String string() {
        expect('"'); var result = new StringBuilder();
        while (index < input.length()) {
            char ch = input.charAt(index++);
            if (ch == '"') return result.toString();
            if (ch < 32) throw invalid();
            if (ch == '\\') {
                if (index >= input.length()) throw invalid();
                ch = input.charAt(index++);
                result.append(switch (ch) {
                    case '"', '\\', '/' -> ch;
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'b' -> '\b'; case 'f' -> '\f';
                    case 'u' -> {
                        if (index + 4 > input.length()) throw invalid();
                        try { char unicode = (char) Integer.parseInt(input.substring(index, index + 4), 16); index += 4; yield unicode; }
                        catch (NumberFormatException failure) { throw invalid(); }
                    }
                    default -> throw invalid();
                });
            } else result.append(ch);
        }
        throw invalid();
    }
    private void space() { while (index < input.length() && " \n\r\t".indexOf(input.charAt(index)) >= 0) index++; }
    private boolean take(char ch) { if (index < input.length() && input.charAt(index) == ch) { index++; return true; } return false; }
    private void expect(char ch) { if (!take(ch)) throw invalid(); }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid evidence JSON at offset " + index + "."); }

    @SuppressWarnings("unchecked") public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object.");
        return (Map<String, Object>) value;
    }
    public static List<?> array(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected JSON array."); return list;
    }
    public static String string(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected JSON string."); return text;
    }
    public static double number(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Expected finite number."); return n.doubleValue();
    }
    public static int count(Object value) {
        double n = number(value);
        if (n < 0 || n > Integer.MAX_VALUE || n != Math.rint(n)) throw new IllegalArgumentException("Expected nonnegative integer.");
        return (int) n;
    }
    public static String quote(String value) {
        var result = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            if (ch == '"' || ch == '\\') result.append('\\').append(ch);
            else if (ch < 32) result.append(String.format("\\u%04x", (int) ch));
            else result.append(ch);
        }
        return result.append('"').toString();
    }
}
