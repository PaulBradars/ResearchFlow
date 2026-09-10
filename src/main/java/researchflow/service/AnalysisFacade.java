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
        analysisService.requireWritableStudy(studyId);
        var normalized = question == null ? "" : question.strip();
        if (normalized.isBlank()) {
            throw new ValidationException(Map.of("question", "Enter a question about this Study's data."));
        }
        var greeting = normalized.toLowerCase(java.util.Locale.ROOT).replaceAll("[!?.,]", "")
                .replaceAll("\\s+", " ").strip();
        if (java.util.Set.of("hi", "hello", "hey", "how are you", "hi how are you", "hello how are you",
                "how are you doing").contains(greeting)) {
            return reply(studyId, normalized, "Hi! I'm here and ready to help. How can I help you today?");
        }
        if (!llm.isAvailable()) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "The local AI runtime is not reachable. Use manual analysis instead.");
        }

        var questions = questionsFor(studyId);
        var systemPrompt = AiPromptBuilder.planPrompt(questions.values());
        var raw = llm.complete(systemPrompt, AiPromptBuilder.conversationPrompt(chat.findByStudy(studyId, 12), normalized));
        try {
            var response = researchflow.util.Json.object(researchflow.util.Json.parse(raw));
            if (response.containsKey("reply")) {
                var content = researchflow.util.Json.string(response.get("reply")).strip();
                if (content.isBlank() || response.containsKey("method")) {
                    throw new IllegalArgumentException("Empty or conflicting reply.");
                }
                return reply(studyId, normalized, content);
            }
        } catch (IllegalArgumentException failure) {
            throw new LlmException(LlmException.Kind.MALFORMED_RESPONSE, "Expected a valid JSON reply or analysis plan.");
        }
        var plan = AnalysisPlanParser.parse(raw, questions);

        var modelMetadata = new ModelMetadata(llm.modelIdentifier(), "local-http",
                AiPromptBuilder.PLAN_PROMPT_VERSION + "/" + AiPromptBuilder.EXPLANATION_PROMPT_VERSION);
        var evidence = analysisService.run(studyId, plan, "AI", modelMetadata);
        analysisService.requireWritableStudy(studyId);
        chat.save(ChatMessage.user(studyId, evidence.id(), normalized));

        var paired = evidence.result() instanceof researchflow.domain.AnalysisResult.Correlation
                ? analysisService.chartValues(studyId, evidence) : null;
        var breakdown = researchflow.analysis.StatisticalBreakdown.render(evidence,
                paired == null ? List.of() : paired.primary(), paired == null ? List.of() : paired.secondary());
        var explanation = explain(evidence, normalized, breakdown) + "\n\n" + breakdown;
        analysisService.requireWritableStudy(studyId);
        chat.save(ChatMessage.assistant(studyId, evidence.id(), explanation));
        return new AiAnswer(evidence, explanation);
    }

    public List<ChatMessage> history(UUID studyId) {
        return chat.findByStudy(studyId, 250);
    }

    private AiAnswer reply(UUID studyId, String question, String content) {
        analysisService.requireWritableStudy(studyId);
        chat.save(ChatMessage.user(studyId, null, question));
        chat.save(ChatMessage.assistant(studyId, null, content));
        return new AiAnswer(null, content);
    }

    public void clearHistory(UUID studyId) {
        analysisService.requireWritableStudy(studyId);
        chat.deleteByStudy(studyId);
    }

    public String exportHistory(UUID studyId) {
        var text = new StringBuilder("ResearchFlow chat history\nStudy: " + studyId + "\n\n");
        for (var message : chat.findAllByStudy(studyId)) {
            text.append(message.createdAt()).append(" | ").append(message.role()).append('\n')
                    .append(message.content()).append('\n');
            if (message.analysisId() != null) text.append("Analysis: ").append(message.analysisId()).append('\n');
            text.append('\n');
        }
        return text.toString();
    }

    /** An explanation failure never invalidates the already-computed, already-persisted evidence. */
    private String explain(researchflow.domain.EvidenceBundle evidence, String question, String breakdown) {
        try {
            return llm.complete(AiPromptBuilder.EXPLANATION_SYSTEM_PROMPT,
                    "Original query (context only): " + question + "\n\n" + breakdown
                            + "\n\nExplain this computed evidence in relation to the original query.");
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
