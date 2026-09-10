package researchflow.persistence;

import researchflow.domain.Finding;
import researchflow.visualization.ChartSpec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindingRepository {

    UUID create(UUID studyId, UUID analysisId, UUID datasetVersionId, String text, String evidenceSummary, ChartSpec chart);

    void updateText(UUID findingId, String text);

    void approve(UUID findingId);

    void reject(UUID findingId);

    List<Finding> findByStudy(UUID studyId);

    Optional<Finding> findById(UUID findingId);
}
