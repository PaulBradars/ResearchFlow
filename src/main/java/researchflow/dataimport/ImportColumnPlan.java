package researchflow.dataimport;

import researchflow.domain.QuestionType;

/**
 * A proposed (and researcher-editable) mapping from one CSV column to a Question. {@code included}
 * lets the researcher drop a column entirely (e.g. a source row-id) before import.
 */
public record ImportColumnPlan(int columnIndex, String header, String variableKey, String label,
                               QuestionType type, boolean required, boolean included) { }
