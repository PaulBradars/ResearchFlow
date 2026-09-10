package researchflow.dataimport;

import researchflow.domain.QuestionType;

public record ImportColumnPlan(int columnIndex, String header, String variableKey, String label,
                               QuestionType type, boolean required, boolean included) { }
