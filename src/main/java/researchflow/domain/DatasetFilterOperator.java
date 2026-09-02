package researchflow.domain;

public enum DatasetFilterOperator {
    CONTAINS("contains"), EQUALS("equals"), GREATER_THAN("greater than"),
    LESS_THAN("less than"), IS_MISSING("is missing");
    private final String label;
    DatasetFilterOperator(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
