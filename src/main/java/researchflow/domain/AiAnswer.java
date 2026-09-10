package researchflow.domain;

/** One chat reply, with optional computed evidence (null for ordinary conversation). */
public record AiAnswer(EvidenceBundle evidence, String explanation) { }
