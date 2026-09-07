package researchflow.domain;

/** Identifies which model/runtime and prompt-template version produced an AI-sourced analysis. */
public record ModelMetadata(String model, String runtime, String promptVersion) { }
