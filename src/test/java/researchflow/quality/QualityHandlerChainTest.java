package researchflow.quality;

import org.junit.jupiter.api.Test;
import researchflow.domain.Answer;
import researchflow.domain.Form;
import researchflow.domain.QualityIssueType;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.domain.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityHandlerChainTest {
    private final UUID studyId = UUID.randomUUID();

    @Test
    void flagsMissingRequiredValueButNotAnsweredOnes() {
        var required = Question.create("required_q", "Required Q", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        var form = formWith(List.of(required));
        var missing = response(form.id(), List.of());
        var answered = response(form.id(), List.of(new Answer.Text(UUID.randomUUID(), required.id(), "value", Instant.now())));

        var issues = new MissingRequiredHandler().handle(context(form, missing, answered));

        assertEquals(1, issues.size());
        assertEquals(QualityIssueType.MISSING_REQUIRED, issues.getFirst().type());
        assertEquals(missing.id(), issues.getFirst().responseId());
    }

    @Test
    void flagsNumericValueOutsideConfiguredRange() {
        var ranged = Question.create("num_q", "Num Q", "", QuestionType.NUMBER, false, 0d, 10d, List.of());
        var form = formWith(List.of(ranged));
        var invalid = responseWithNumber(form.id(), ranged.id(), 15d);
        var valid = responseWithNumber(form.id(), ranged.id(), 5d);

        var issues = new InvalidRangeHandler().handle(context(form, invalid, valid));

        assertEquals(1, issues.size());
        assertEquals(QualityIssueType.INVALID_RANGE, issues.getFirst().type());
        assertEquals(invalid.id(), issues.getFirst().responseId());
    }

    @Test
    void flagsLaterDuplicateButNotTheOriginal() {
        var question = Question.create("q", "Q", "", QuestionType.SHORT_TEXT, false, null, null, List.of());
        var form = formWith(List.of(question));
        var original = new Response(UUID.randomUUID(), form.id(), 1, null, Instant.parse("2026-01-01T00:00:00Z"), 30L,
                List.of(new Answer.Text(UUID.randomUUID(), question.id(), "same value", Instant.now())));
        var duplicate = new Response(UUID.randomUUID(), form.id(), 1, null, Instant.parse("2026-01-01T00:05:00Z"), 30L,
                List.of(new Answer.Text(UUID.randomUUID(), question.id(), "same value", Instant.now())));
        var distinct = new Response(UUID.randomUUID(), form.id(), 1, null, Instant.parse("2026-01-01T00:10:00Z"), 30L,
                List.of(new Answer.Text(UUID.randomUUID(), question.id(), "different value", Instant.now())));

        var issues = new DuplicateResponseHandler().handle(context(form, original, duplicate, distinct));

        assertEquals(1, issues.size());
        assertEquals(QualityIssueType.DUPLICATE_RESPONSE, issues.getFirst().type());
        assertEquals(duplicate.id(), issues.getFirst().responseId());
    }

    @Test
    void flagsValueOutsideTukeyFencesOnly() {
        var question = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, false, null, null, List.of());
        var form = formWith(List.of(question));
        var values = List.of(7.5, 6d, 8d, 5d, 7d, 14d, 6.5, 8d);
        var responses = values.stream().map(value -> responseWithNumber(form.id(), question.id(), value)).toList();

        var issues = new OutlierHandler().handle(context(form, responses.toArray(new Response[0])));

        assertEquals(1, issues.size());
        assertEquals(QualityIssueType.OUTLIER, issues.getFirst().type());
        assertEquals(responses.get(5).id(), issues.getFirst().responseId());
    }

    @Test
    void doesNotFlagOutliersBelowMinimumSampleSize() {
        var question = Question.create("q", "Q", "", QuestionType.NUMBER, false, null, null, List.of());
        var form = formWith(List.of(question));
        var responses = List.of(1d, 2d, 100d).stream().map(value -> responseWithNumber(form.id(), question.id(), value)).toList();

        var issues = new OutlierHandler().handle(context(form, responses.toArray(new Response[0])));

        assertTrue(issues.isEmpty());
    }

    @Test
    void flagsSubmissionsFasterThanThePerQuestionFloorButNotOrdinaryOnes() {
        var a = Question.create("a", "A", "", QuestionType.SHORT_TEXT, false, null, null, List.of());
        var b = Question.create("b", "B", "", QuestionType.SHORT_TEXT, false, null, null, List.of());
        var form = formWith(List.of(a, b));
        var fast = new Response(UUID.randomUUID(), form.id(), 1, null, Instant.now(), 3L, List.of());
        var normal = new Response(UUID.randomUUID(), form.id(), 1, null, Instant.now(), 38L, List.of());

        var issues = new FastSubmissionHandler().handle(context(form, fast, normal));

        assertEquals(1, issues.size());
        assertEquals(QualityIssueType.FAST_SUBMISSION, issues.getFirst().type());
        assertEquals(fast.id(), issues.getFirst().responseId());
    }

    @Test
    void defaultChainRunsEveryHandler() {
        var required = Question.create("required_q", "Required Q", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        var form = formWith(List.of(required));
        var missing = response(form.id(), List.of());

        var issues = QualityHandlerChain.buildDefault().handle(context(form, missing));

        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(issue -> issue.type() == QualityIssueType.MISSING_REQUIRED));
    }

    private Form formWith(List<Question> questions) {
        var form = Form.create(studyId, "Form", "");
        return form.revise(form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(questions)));
    }

    private Response response(UUID formId, List<Answer> answers) {
        return new Response(UUID.randomUUID(), formId, 1, null, Instant.now(), 30L, answers);
    }

    private Response responseWithNumber(UUID formId, UUID questionId, double value) {
        return response(formId, List.of(new Answer.Number(UUID.randomUUID(), questionId, value, Instant.now())));
    }

    private QualityScanContext context(Form form, Response... responses) {
        return new QualityScanContext(studyId, List.of(form), List.of(responses));
    }
}
