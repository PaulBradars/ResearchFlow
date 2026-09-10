package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.Question;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalysisContext {
    private final AnalysisPlan plan;
    private final Map<UUID, Question> questionsById;
    private final List<Map<UUID, Answer>> responses;

    public AnalysisContext(AnalysisPlan plan, Map<UUID, Question> questionsById, List<Map<UUID, Answer>> responses) {
        this.plan = plan;
        this.questionsById = questionsById;
        this.responses = responses;
    }

    public AnalysisPlan plan() {
        return plan;
    }

    public Question question(UUID questionId) {
        return questionsById.get(questionId);
    }

    public List<Map<UUID, Answer>> responses() {
        return responses;
    }
}
