package researchflow.ai;

import org.junit.jupiter.api.Test;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptBuilderTest {
    @Test
    void planPromptListsEveryVariableIdAndLabelAndEveryMethod() {
        var sleep = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, true, 0d, 16d, List.of());
        var prompt = AiPromptBuilder.planPrompt(List.of(sleep));

        assertTrue(prompt.contains(sleep.id().toString()));
        assertTrue(prompt.contains("Sleep hours"));
        for (var method : AnalysisMethod.values()) assertTrue(prompt.contains(method.name()));
        assertTrue(prompt.toLowerCase().contains("only the json object"));
    }

    @Test
    void explanationPromptIncludesOnlyTheStoredEvidence() {
        var evidence = new EvidenceBundle(UUID.randomUUID(), AnalysisMethod.NUMERIC_SUMMARY,
                List.of(new EvidenceBundle.VariableRef(UUID.randomUUID(), "Sleep hours")), List.<AnalysisFilter>of(), 5,
                new AnalysisResult.NumericSummary(5, 0, 6.5, 6.5, 1.29, 5, 8),
                List.of("Sample size is small (n=5); interpret results with caution."), UUID.randomUUID());

        var prompt = AiPromptBuilder.explanationPrompt(evidence);

        assertTrue(prompt.contains("Sleep hours"));
        assertTrue(prompt.contains("6.5"));
        assertTrue(prompt.contains("Sample size is small"));
    }
}

