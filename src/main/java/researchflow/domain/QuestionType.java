package researchflow.domain;

public enum QuestionType {
    SHORT_TEXT("Short text"),
    NUMBER("Number"),
    SINGLE_CHOICE("Single choice"),
    MULTIPLE_CHOICE("Multiple choice"),
    YES_NO("Yes / No"),
    LIKERT("Likert"),
    RATING("Rating"),
    DATE("Date");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
