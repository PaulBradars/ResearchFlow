package researchflow.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Shared read/write for the small JSON shape stored in {@code version_answer_snapshots.value_json}.
 * Used by {@link JdbcVersionRepository} (restore) and {@link JdbcAnalysisRepository} (analysis reads),
 * so the encoding stays in one place.
 */
final class SnapshotJson {
    private SnapshotJson() { }

    static String fromColumns(ResultSet row) throws SQLException {
        var text = row.getString("value_text");
        if (text != null) return "{\"value_text\":\"" + escape(text) + "\"}";
        var number = row.getObject("value_number");
        if (number != null) return "{\"value_number\":" + row.getDouble("value_number") + "}";
        var bool = row.getObject("value_boolean");
        if (bool != null) return "{\"value_boolean\":" + row.getInt("value_boolean") + "}";
        var date = row.getString("value_date");
        if (date != null) return "{\"value_date\":\"" + date + "\"}";
        return "{}";
    }

    static Value parse(String json) {
        var number = numberField(json, "value_number");
        var bool = numberField(json, "value_boolean");
        return new Value(stringField(json, "value_text"), number == null ? null : Double.valueOf(number),
                bool == null ? null : Integer.valueOf(bool), stringField(json, "value_date"));
    }

    private static String stringField(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static String numberField(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }

    /** {@code bool} is stored as SQLite's 0/1 integer, matching {@code answers.value_boolean}. */
    record Value(String text, Double number, Integer bool, String date) {
        boolean isMissing() {
            return text == null && number == null && bool == null && date == null;
        }
    }
}
