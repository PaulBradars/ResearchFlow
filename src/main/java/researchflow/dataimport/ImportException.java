package researchflow.dataimport;

/** A file-read or CSV-structure failure — never thrown for a single bad data row, only the file itself. */
public final class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
