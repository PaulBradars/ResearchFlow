package researchflow.service;

import researchflow.domain.Answer;
import researchflow.domain.Question;
import researchflow.domain.Response;
import researchflow.persistence.FormRepository;
import researchflow.persistence.ResponseRepository;
import researchflow.state.FormState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResponseSubmissionService {
    public static final String MULTI_VALUE_SEPARATOR = AnswerValueParser.MULTI_VALUE_SEPARATOR;
    private final FormRepository forms;
    private final ResponseRepository responses;

    public ResponseSubmissionService(FormRepository forms, ResponseRepository responses) {
        this.forms = forms;
        this.responses = responses;
    }

    public Response submit(UUID formId, Instant startedAt, Map<UUID, String> rawAnswers) {
        var form = forms.findById(formId).orElseThrow(() -> new IllegalArgumentException("Form not found: " + formId));
        FormState.forStatus(form.status()).requireSubmission();
        var knownIds = form.questions().stream().map(Question::id).collect(java.util.stream.Collectors.toSet());
        if (rawAnswers.keySet().stream().anyMatch(id -> !knownIds.contains(id)))
            throw new IllegalArgumentException("The submission contains a question that is not in this form.");

        var errors = new LinkedHashMap<String, String>();
        var answers = new ArrayList<Answer>();
        for (var question : form.questions()) {
            var raw = rawAnswers.getOrDefault(question.id(), "");
            var value = raw == null ? "" : raw.strip();
            if (value.isBlank()) {
                if (question.required()) errors.put(question.id().toString(), "This question is required.");
                continue;
            }
            try {
                answers.add(AnswerValueParser.parse(question, value));
            } catch (IllegalArgumentException exception) {
                errors.put(question.id().toString(), exception.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        var response = Response.submit(form, startedAt, answers);
        responses.submit(form, response);
        return response;
    }

}
