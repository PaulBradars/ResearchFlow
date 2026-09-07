package researchflow.domain;

/** The result of one "Ask Your Data" turn: the computed, persisted evidence plus its plain-language explanation. */
public record AiAnswer(EvidenceBundle evidence, String explanation) { }
