package researchflow.persistence;

import researchflow.domain.Finding;
import researchflow.visualization.ChartSpec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindingRepository {
    /** Creates a DRAFT finding bound permanently to {@code analysisId}/{@code datasetVersionId}. Returns the new id. */
    UUID create(UUID studyId, UUID analysisId, UUID datasetVersionId, String text, String evidenceSummary, ChartSpec chart);

    /** Changes only the wording; the analysis/version binding is never touched. */
    void updateText(UUID findingId, String text);

    void approve(UUID findingId);

    void reject(UUID findingId);

    List<Finding> findByStudy(UUID studyId);

    Optional<Finding> findById(UUID findingId);
}
