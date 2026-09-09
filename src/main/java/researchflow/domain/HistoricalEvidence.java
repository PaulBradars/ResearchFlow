package researchflow.domain;

/** Unsupported records retain their raw storage for inspection; never return invented statistical values. */
public record HistoricalEvidence(StoredAnalysis stored, AnalysisPlan plan, EvidenceBundle evidence, String notice) {
    public boolean supported() { return evidence != null; }
}
