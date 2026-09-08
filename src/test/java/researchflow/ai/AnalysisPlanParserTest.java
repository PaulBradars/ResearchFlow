package researchflow.ai;

import org.junit.jupiter.api.Test;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisPlanParserTest {
    private final Question sleep = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, true, 0d, 16d, List.of());
    private final Question focus = Question.create("focus", "Focus", "", QuestionType.NUMBER, true, 0d, 10d, List.of());
    private final Map<UUID, Question> questions = Map.of(sleep.id(), sleep, focus.id(), focus);

    @Test
    void parsesAWellFormedPlanIgnoringSurroundingProseAndMarkdownFences() {
        var raw = "Sure, here is the plan:\n```json\n{\"method\":\"correlation\",\"primaryVariableId\":\""
                + sleep.id() + "\",\"secondaryVariableId\":\"" + focus.id() + "\",\"filters\":[]}\n```\nLet me know if you need more.";

        var plan = AnalysisPlanParser.parse(raw, questions);

        assertEquals(AnalysisMethod.CORRELATION, plan.method());
        assertEquals(sleep.id(), plan.primaryVariableId());
        assertEquals(focus.id(), plan.secondaryVariableId());
        assertTrue(plan.filters().isEmpty());
    }

    @Test
    void parsesFiltersWhenPresent() {
        var raw = "{\"method\":\"NUMERIC_SUMMARY\",\"primaryVariableId\":\"" + sleep.id() + "\",\"secondaryVariableId\":null,"
                + "\"filters\":[{\"questionId\":\"" + sleep.id() + "\",\"operator\":\"GREATER_THAN\",\"value\":\"6\"}]}";

        var plan = AnalysisPlanParser.parse(raw, questions);

        assertEquals(1, plan.filters().size());
        assertEquals(sleep.id(), plan.filters().getFirst().questionId());
        assertEquals("6", plan.filters().getFirst().value());
    }

    @Test
    void rejectsResponsesWithNoJsonObject() {
        var exception = assertThrows(LlmException.class,
                () -> AnalysisPlanParser.parse("I'm not sure how to answer that.", questions));
        assertEquals(LlmException.Kind.MALFORMED_RESPONSE, exception.kind());
    }

    @Test
    void rejectsAnUnsupportedMethodName() {
        var raw = "{\"method\":\"REGRESSION\",\"primaryVariableId\":\"" + sleep.id() + "\"}";
        assertThrows(LlmException.class, () -> AnalysisPlanParser.parse(raw, questions));
    }

    @Test
    void rejectsAVariableIdOutsideTheStudy() {
        var raw = "{\"method\":\"NUMERIC_SUMMARY\",\"primaryVariableId\":\"" + UUID.randomUUID() + "\"}";
        assertThrows(LlmException.class, () -> AnalysisPlanParser.parse(raw, questions));
    }

    @Test
    void rejectsAMalformedVariableId() {
        var raw = "{\"method\":\"NUMERIC_SUMMARY\",\"primaryVariableId\":\"not-a-uuid\"}";
        assertThrows(LlmException.class, () -> AnalysisPlanParser.parse(raw, questions));
    }

    @Test
    void rejectsAMissingPrimaryVariable() {
        assertThrows(LlmException.class, () -> AnalysisPlanParser.parse("{\"method\":\"NUMERIC_SUMMARY\"}", questions));
    }

    @Test
    void rejectsAnUnsupportedFilterOperator() {
        var raw = "{\"method\":\"NUMERIC_SUMMARY\",\"primaryVariableId\":\"" + sleep.id() + "\",\"filters\":"
                + "[{\"questionId\":\"" + sleep.id() + "\",\"operator\":\"MATCHES\",\"value\":\"6\"}]}";
        assertThrows(LlmException.class, () -> AnalysisPlanParser.parse(raw, questions));
    }
}

