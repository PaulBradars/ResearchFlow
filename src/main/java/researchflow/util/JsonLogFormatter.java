package researchflow.util;

import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public final class JsonLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        return "{\"timestamp\":\"" + Instant.ofEpochMilli(record.getMillis())
                + "\",\"level\":\"" + escape(record.getLevel().getName())
                + "\",\"logger\":\"" + escape(record.getLoggerName())
                + "\",\"message\":\"" + escape(formatMessage(record)) + "\"}\n";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}

