package researchflow.domain;

public enum DatasetSort {
    NEWEST("Newest first"), OLDEST("Oldest first"), DURATION_ASC("Shortest duration"),
    DURATION_DESC("Longest duration"), VARIABLE_ASC("Variable ascending"), VARIABLE_DESC("Variable descending");
    private final String label;
    DatasetSort(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

