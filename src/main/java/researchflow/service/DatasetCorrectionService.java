package researchflow.service;

import researchflow.persistence.DatasetRepository;
import researchflow.persistence.FormRepository;

import java.util.Map;
import java.util.UUID;

public final class DatasetCorrectionService {
    private final DatasetRepository datasets;
    private final FormRepository forms;

    public DatasetCorrectionService(DatasetRepository datasets, FormRepository forms) {
        this.datasets = datasets;
        this.forms = forms;
    }

    public void correct(UUID studyId, UUID responseId, UUID questionId, String rawValue, String reason) {
        var normalizedReason = reason == null ? "" : reason.strip();
        var errors = new java.util.LinkedHashMap<String, String>();
        if (normalizedReason.length() < 3) errors.put("reason", "Provide a correction reason of at least 3 characters.");
        else if (normalizedReason.length() > 1_000) errors.put("reason", "Use 1,000 characters or fewer.");
        var target = datasets.findCorrectionTarget(studyId, responseId, questionId)
                .orElseThrow(() -> new IllegalArgumentException("The response or question no longer exists."));
        var form = forms.findById(target.formId())
                .orElseThrow(() -> new IllegalArgumentException("The form no longer exists."));
        var question = form.questions().stream().filter(value -> value.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The question no longer exists."));
        var raw = rawValue == null ? "" : rawValue.strip();
        if (raw.isBlank()) errors.put("value", "A correction value is required.");
        if (!errors.isEmpty()) throw new ValidationException(errors);
        final researchflow.domain.Answer replacement;
        try {
            replacement = AnswerValueParser.parse(question, raw,
                    target.answerId() == null ? UUID.randomUUID() : target.answerId());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(Map.of("value", exception.getMessage()));
        }
        datasets.correct(target, replacement, normalizedReason);
    }
}
