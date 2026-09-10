package researchflow.persistence;

import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisResult;
import researchflow.domain.AnalysisSummary;
import researchflow.domain.ModelMetadata;
import researchflow.domain.StoredAnalysis;
import researchflow.domain.VersionAnswer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository {

    List<VersionAnswer> loadVersionData(UUID datasetVersionId);

    UUID save(UUID studyId, UUID datasetVersionId, AnalysisPlan plan, AnalysisResult result, int sampleSize,
              List<String> warnings, String source, ModelMetadata modelMetadata);

    List<AnalysisSummary> findByStudy(UUID studyId);

    Optional<StoredAnalysis> findById(UUID analysisId);
}
