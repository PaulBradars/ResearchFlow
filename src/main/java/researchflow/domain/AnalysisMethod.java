package researchflow.domain;

public enum AnalysisMethod {
    FREQUENCY("Frequency / percentage"),
    NUMERIC_SUMMARY("Numeric summary"),
    CORRELATION("Correlation"),
    CROSS_TABULATION("Cross-tabulation"),
    GROUP_COMPARISON("Two-group comparison");

    private final String displayName;

    AnalysisMethod(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
