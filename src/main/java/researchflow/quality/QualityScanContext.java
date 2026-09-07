package researchflow.quality;

import researchflow.domain.Form;
import researchflow.domain.Question;
import researchflow.domain.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The read-only input a {@link QualityHandler} evaluates. Built once per scan from the
 * authoritative Form/Question definitions and the full typed Response data for the Study.
 */
public final class QualityScanContext {
    private final UUID studyId;
    private final List<Form> forms;
    private final List<Response> responses;
    private final Map<UUID, Form> formsById = new LinkedHashMap<>();
    private final Map<UUID, Question> questionsById = new LinkedHashMap<>();

    public QualityScanContext(UUID studyId, List<Form> forms, List<Response> responses) {
        this.studyId = studyId;
        this.forms = List.copyOf(forms);
        this.responses = List.copyOf(responses);
        for (var form : this.forms) {
            formsById.put(form.id(), form);
            for (var question : form.questions()) questionsById.put(question.id(), question);
        }
    }

    public UUID studyId() { return studyId; }
    public List<Form> forms() { return forms; }
    public List<Response> responses() { return responses; }
    public Optional<Form> form(UUID formId) { return Optional.ofNullable(formsById.get(formId)); }
    public Optional<Question> question(UUID questionId) { return Optional.ofNullable(questionsById.get(questionId)); }
}
