package researchflow.domain;

public enum QualityIssueType {
    MISSING_REQUIRED("Missing required value"),
    INVALID_RANGE("Invalid numeric range"),
    DUPLICATE_RESPONSE("Duplicate response"),
    OUTLIER("Simple outlier"),
    FAST_SUBMISSION("Unusually fast submission");

    private final String displayName;

    QualityIssueType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
