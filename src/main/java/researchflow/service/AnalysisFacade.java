package researchflow.service;

import researchflow.ai.AiPromptBuilder;
import researchflow.ai.AnalysisPlanParser;
import researchflow.ai.LlmClient;
import researchflow.ai.LlmException;
import researchflow.domain.AiAnswer;
import researchflow.domain.ChatMessage;
import researchflow.domain.ModelMetadata;
import researchflow.domain.Question;
import researchflow.persistence.ChatRepository;
import researchflow.persistence.FormRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Ask Your Data": interpret a natural-language question into a validated {@code AnalysisPlan},
 * execute it through the exact same deterministic pipeline manual analysis uses, then produce a
 * plain-language explanation strictly constrained to the stored evidence. The LLM never computes a
 * result and never bypasses {@code AnalysisPlanValidator} — {@link AnalysisService#run} is the only
 * code that validates and executes, whether the plan came from this facade or the manual UI.
 */
public final class AnalysisFacade {
    private final LlmClient llm;
    private final FormRepository forms;
    private final AnalysisService analysisService;
    private final ChatRepository chat;

    public AnalysisFacade(LlmClient llm, FormRepository forms, AnalysisService analysisService, ChatRepository chat) {
        this.llm = llm;
        this.forms = forms;
        this.analysisService = analysisService;
        this.chat = chat;
    }

    public boolean aiAvailable() {
        return llm.isAvailable();
    }

    public AiAnswer ask(UUID studyId, String question) {
        var normalized = question == null ? "" : question.strip();
        if (normalized.isBlank()) {
            throw new ValidationException(Map.of("question", "Enter a question about this Study's data."));
        }
        if (!llm.isAvailable()) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "The local AI runtime is not reachable. Use manual analysis instead.");
        }

        var questions = questionsFor(studyId);
        var systemPrompt = AiPromptBuilder.planPrompt(questions.values());
        var raw = llm.complete(systemPrompt, normalized);
        var plan = AnalysisPlanParser.parse(raw, questions);

        var modelMetadata = new ModelMetadata(llm.modelIdentifier(), "local-http", AiPromptBuilder.PLAN_PROMPT_VERSION);
        var evidence = analysisService.run(studyId, plan, "AI", modelMetadata);
        chat.save(ChatMessage.user(studyId, evidence.id(), normalized));

        var explanation = explain(evidence);
        chat.save(ChatMessage.assistant(studyId, evidence.id(), explanation));
        return new AiAnswer(evidence, explanation);
    }

    public List<ChatMessage> history(UUID studyId) {
        return chat.findByStudy(studyId, 250);
    }

    /** An explanation failure never invalidates the already-computed, already-persisted evidence. */
    private String explain(researchflow.domain.EvidenceBundle evidence) {
        try {
            return llm.complete(AiPromptBuilder.EXPLANATION_SYSTEM_PROMPT, AiPromptBuilder.explanationPrompt(evidence));
        } catch (LlmException failure) {
            return "The evidence above was computed successfully, but a plain-language explanation "
                    + "could not be generated right now (" + failure.kind() + ").";
        }
    }

    private Map<UUID, Question> questionsFor(UUID studyId) {
        var map = new LinkedHashMap<UUID, Question>();
        for (var form : forms.findByStudy(studyId)) {
            for (var question : form.questions()) map.put(question.id(), question);
        }
        return map;
    }
}
