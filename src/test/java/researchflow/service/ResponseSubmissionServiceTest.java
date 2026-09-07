package researchflow.service;

import org.junit.jupiter.api.Test;
import researchflow.domain.Form;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.persistence.FormRepository;
import researchflow.persistence.ResponseRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseSubmissionServiceTest {
    @Test
    void draftAndClosedFormsRejectSubmissionsBeforePersistence() {
        var form = Form.create(UUID.randomUUID(), "Draft", "");
        var question = Question.create("name", "Name", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        form = form.revise(form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(question))));
        var draft = form;
        var forms = new StubForms(form);
        var responses = new RejectingResponses();
        var service = new ResponseSubmissionService(forms, responses);
        assertThrows(IllegalStateException.class,
                () -> service.submit(draft.id(), Instant.now(), Map.of(question.id(), "Ada")));

        forms.form = draft.withStatus(researchflow.domain.FormStatus.CLOSED);
        assertThrows(IllegalStateException.class,
                () -> service.submit(draft.id(), Instant.now(), Map.of(question.id(), "Ada")));
    }

    private static final class StubForms implements FormRepository {
        private Form form;
        StubForms(Form form) { this.form = form; }
        public void save(Form value, String event) { form = value; }
        public Optional<Form> findById(UUID id) { return Optional.of(form); }
        public List<Form> findByStudy(UUID id) { return List.of(form); }
    }

    private static final class RejectingResponses implements ResponseRepository {
        public void submit(Form form, researchflow.domain.Response response) { throw new AssertionError("must not persist"); }
        public List<researchflow.domain.ResponseSummary> findByStudy(UUID id) { return List.of(); }
        public List<researchflow.domain.Response> findFullByStudy(UUID id) { return List.of(); }
    }

}
